const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const ctx={window:null,console};ctx.window=ctx;vm.createContext(ctx);
for(const f of ['js/severity.js','data/municipal-catalog.js','js/scoring.js','data/bridge-profile-schema.js','data/bridge-checklists.js'])
  vm.runInContext(fs.readFileSync('app/src/main/assets/'+f,'utf8'),ctx);
const S=ctx.BridgeScoring,C=ctx.BRIDGE_MUNICIPAL_CATALOG;
const item=(row,status='low',extra={})=>({itemId:row.existing[0] || 'municipal-'+row.number,statusId:status,assessed:true,...extra});
const record=items=>({checklistSchemaVersion:'1.6.0',items});
test('all 79 workbook rows resolve to their exact score and weight',()=>{
  for(const row of C.rows){
    for(const [status,base] of Object.entries(C.scores)){
      const result=S.scoreItem(item(row,status));
      assert.equal(result.weight,row.weight,row.name);assert.equal(result.key,row.id,row.name);assert.equal(result.penalty,base*row.weight,row.name);
    }
    for(const id of row.existing)assert.equal(S.sourceRow(id).id,row.id,id);
  }
});
test('the supplied workbook sample reproduces -240',()=>{
  const result=S.municipal(record(C.rows.filter(x=>x.sampleStatus).map(row=>item(row,row.sampleStatus))));
  assert.equal(result.total,-240);assert.equal(result.referenceTotal,-240);assert.equal(result.uninspectableCount,5);
});
test('split defects and repeated locations charge the worst source row once',()=>{
  const row=C.rows.find(x=>x.number===48);
  const result=S.municipal(record([item(row,'low'),item(row,'medium',{occurrenceIndex:2}),item(row,'emergency',{itemId:row.existing[1]})]));
  assert.equal(result.groups.length,1);assert.equal(result.total,-100*row.weight);assert.equal(result.emergencyCount,1);
});
test('pending, inaccessible, excluded and historical defaults remain distinct',()=>{
  const row=C.rows[0];
  const pending=S.municipal(record([item(row,'none',{assessed:false})]));
  assert.equal(pending.total,null);assert.equal(pending.coverage,0);assert.equal(pending.pendingCount,1);
  const unknown=S.municipal(record([item(row,'invalid')]));assert.equal(unknown.total,null);
  const inaccessible=S.municipal(record([item(row,'uninspectable')]));assert.equal(inaccessible.coverage,0);assert.equal(inaccessible.total,-10*row.weight);
  const excluded=S.municipal(record([item(row,'emergency',{applicable:false})]));assert.equal(excluded.total,null);assert.equal(excluded.emergencyCount,0);
  const legacy=S.municipal({items:[{itemId:row.existing[0],statusId:'none'}]});assert.equal(legacy.total,null);assert.equal(legacy.pendingCount,1);assert(legacy.legacy);
  assert.equal(ctx.inspectionOverall([item(row,'emergency',{applicable:false}),item(row,'uninspectable')]).id,'uninspectable');
});
test('supplemental weights are labeled proposed and separate from the reference',()=>{
  const result=S.municipal(record([item(C.rows[0]),{itemId:'new-item',itemName:'پارگی عضو باربر',statusId:'low',assessed:true}]));
  assert.equal(result.referenceTotal,-50);assert.equal(result.supplementalTotal,-50);assert.equal(result.total,-100);
  assert(result.groups.some(g=>g.sourceType==='proposed' && g.rationale));
});
test('SNBI uses the lowest applicable rating and requires evidence and a reviewer',()=>{
  const assessment={mode:'bridge',reviewer:'بازبین',components:{deck:{rating:'8',note:'شاهد'},superstructure:{rating:'5',note:'شاهد'},substructure:{rating:'7',note:'شاهد'}}};
  let result=S.international({internationalAssessment:assessment});assert.equal(result.minimum,5);assert.equal(result.condition,'متوسط');
  assessment.components.substructure.rating='4';result=S.international({internationalAssessment:assessment});assert.equal(result.condition,'ضعیف');
  assessment.components.substructure.note='';result=S.international({internationalAssessment:assessment});assert.equal(result.complete,false);assert.equal(result.condition,null);
  assessment.components.substructure={rating:'N',note:'طبق نقشه جزء وجود ندارد'};
  assessment.components.culvert={rating:'2',note:'آسیب عمده'};
  result=S.international({internationalAssessment:assessment});assert.equal(result.minimum,2);assert(result.critical);
  assessment.reviewer='';assert.equal(S.international({internationalAssessment:assessment}).complete,false);
});
test('element quantities reject empty, negative, non-finite and mismatching inputs',()=>{
  const e={total:100,cs1:40,cs2:30,cs3:20,cs4:10};
  assert.equal(S.elementCondition(e).cs34Percent,30);
  for(const patch of [{cs1:''},{cs2:-1},{total:0},{total:Infinity},{cs1:41},{cs4:NaN}])assert.equal(S.elementCondition({...e,...patch}).valid,false);
  assert.equal(S.elementCondition({total:.3,cs1:.1,cs2:.2,cs3:0,cs4:0}).valid,true);
});
test('all original checklist identifiers survive; one-to-one names match workbook',()=>{
  const catalog=ctx.BRIDGE_CHECKLIST_CATALOG.flatMap(c=>c.items),ids=new Set(catalog.map(i=>i.id));
  const originals=JSON.parse(fs.readFileSync('tests/fixtures/checklist-v150.json','utf8'));
  for(const original of originals){
    assert(ids.has(original.id),original.id);
    if(!C.rows.some(r=>r.rename===original.id))assert.equal(catalog.find(i=>i.id===original.id).name,original.name,original.id);
  }
  for(const row of C.rows){if(row.rename)assert.equal(catalog.find(i=>i.id===row.rename).name,row.name);if(row.addTo)assert(ids.has('municipal-'+row.number));}
});
