'use strict';
const APPEARANCE_DEFAULTS=Object.freeze({
  theme:'day',preset:'recommended',fontMain:'vazirmatn',fontTitle:'vazirmatn',fontLabel:'vazirmatn',fontInput:'vazirmatn',fontTable:'vazirmatn',
  textSize:'m',bodyWeight:'400',headingWeight:'600',uiDensity:'standard',controlSize:'standard',cornerStyle:'standard',animationMode:'reduced',contrastMode:'standard',accentPalette:'engineering-teal',mapLabelScale:'1'
});
const FONT_STACKS={
  'noto-sans':'"Noto Sans Arabic",Tahoma,Arial,sans-serif',
  'vazirmatn':'"Vazirmatn","Noto Sans Arabic",Tahoma,Arial,sans-serif',
  'noto-naskh':'"Noto Naskh Arabic","Noto Sans Arabic",Tahoma,Arial,sans-serif'
};
const TEXT_SCALE={xs:.86,s:.93,m:1,l:1.12,xl:1.25};
const DENSITY={compact:.88,standard:1,large:1.12};
const CONTROL={standard:48,large:52};
const RADIUS={low:9,standard:16,round:22};
const ACCENTS={'engineering-teal':['#0f786b','#22a08d'],blue:['#286b9b','#3b8bc4'],green:['#26764e','#3a9a69'],'slate-blue':['#536b7d','#708da1']};
function migrateThemeName(v){
  const s=String(v??'').trim();
  if(['dark','night','تیره','شب'].includes(s))return'night';
  if(['light','day','روشن مهندسی','روشن','روز'].includes(s))return'day';
  return'';
}
function normalizedAppearance(raw={}){
  const nested=raw.appearance||{};
  const legacyTheme=migrateThemeName(raw.theme)||migrateThemeName(nested.theme);
  const a={...APPEARANCE_DEFAULTS,...nested,theme:legacyTheme||APPEARANCE_DEFAULTS.theme};
  for(const k of ['fontMain','fontTitle','fontLabel','fontInput','fontTable'])if(!FONT_STACKS[a[k]])a[k]=APPEARANCE_DEFAULTS[k];
  if(!TEXT_SCALE[a.textSize])a.textSize='m';if(!DENSITY[a.uiDensity])a.uiDensity='standard';if(!CONTROL[a.controlSize])a.controlSize='standard';if(!RADIUS[a.cornerStyle])a.cornerStyle='standard';if(!ACCENTS[a.accentPalette])a.accentPalette='engineering-teal';
  if(!['400','500','600'].includes(String(a.bodyWeight)))a.bodyWeight='400';if(!['500','600','700'].includes(String(a.headingWeight)))a.headingWeight='600';
  if(!['full','reduced','off'].includes(a.animationMode))a.animationMode='full';if(!['standard','high'].includes(a.contrastMode))a.contrastMode='standard';a.mapLabelScale=String(Math.max(.9,Math.min(1.15,Number(a.mapLabelScale)||1)));return a;
}
function applyAppearance(a){a=normalizedAppearance({appearance:a,theme:a.theme});const r=document.documentElement.style,b=document.body;b.dataset.theme=a.theme;b.classList.toggle('dark',a.theme==='night');b.dataset.contrast=a.contrastMode;b.dataset.animation=a.animationMode;b.dataset.density=a.uiDensity;r.setProperty('--font-main',FONT_STACKS[a.fontMain]);r.setProperty('--font-title',FONT_STACKS[a.fontTitle]);r.setProperty('--font-label',FONT_STACKS[a.fontLabel]);r.setProperty('--font-input',FONT_STACKS[a.fontInput]);r.setProperty('--font-table',FONT_STACKS[a.fontTable]);r.setProperty('--text-scale',TEXT_SCALE[a.textSize]);r.setProperty('--body-weight',a.bodyWeight);r.setProperty('--heading-weight',a.headingWeight);r.setProperty('--density-scale',DENSITY[a.uiDensity]);r.setProperty('--control-min',CONTROL[a.controlSize]+'px');r.setProperty('--radius-user',RADIUS[a.cornerStyle]+'px');r.setProperty('--map-label-scale',a.mapLabelScale);const acc=ACCENTS[a.accentPalette];r.setProperty('--accent',acc[0]);r.setProperty('--accent2',acc[1]);if(typeof setTheme==='function')setTheme(a.theme);window.scheduleMarqueeRefresh?.(document);}
function appearanceFromControls(){const get=(id,def)=>document.getElementById(id)?.value??def;return normalizedAppearance({theme:get('themeMode','day'),appearance:{theme:get('themeMode','day'),preset:get('appearancePreset','custom'),fontMain:get('fontMain','noto-sans'),fontTitle:get('fontTitle','noto-sans'),fontLabel:get('fontLabel','noto-sans'),fontInput:get('fontInput','noto-sans'),fontTable:get('fontTable','noto-sans'),textSize:get('textSize','m'),bodyWeight:get('bodyWeight','400'),headingWeight:get('headingWeight','600'),uiDensity:get('uiDensity','standard'),controlSize:get('controlSize','standard'),cornerStyle:get('cornerStyle','standard'),animationMode:get('animationMode','full'),contrastMode:get('contrastMode','standard'),accentPalette:get('accentPalette','engineering-teal'),mapLabelScale:get('mapLabelScale','1')}});}
function writeAppearanceControls(a){a=normalizedAppearance({appearance:a,theme:a.theme});const idMap={preset:'appearancePreset'};Object.entries(a).forEach(([k,v])=>{const el=document.getElementById(idMap[k]||k);if(el)el.value=String(v)});if(document.getElementById('themeMode'))themeMode.value=a.theme;}
function appearanceChanged(){const a=appearanceFromControls();if(document.getElementById('appearancePreset'))appearancePreset.value='custom';a.preset='custom';applyAppearance(a);}
function applyAppearancePreset(name){const presets={recommended:{...APPEARANCE_DEFAULTS},readable:{...APPEARANCE_DEFAULTS,preset:'readable',textSize:'l',uiDensity:'large',controlSize:'large',contrastMode:'high'},compact:{...APPEARANCE_DEFAULTS,preset:'compact',uiDensity:'compact',textSize:'m'}};const a=presets[name]||{...APPEARANCE_DEFAULTS,preset:'custom'};const current=appearanceFromControls();a.theme=current.theme;writeAppearanceControls(a);applyAppearance(a);}
function resetAppearance(){if(!confirm('تنظیمات ظاهری به حالت پیشنهادی بازگردد؟ این کار هیچ داده، بازدید، تصویر یا تنظیم سازمانی را حذف نمی‌کند.'))return;const a={...APPEARANCE_DEFAULTS};writeAppearanceControls(a);applyAppearance(a);const s=dbList('settings').find(x=>x.id==='main')||{id:'main'};dbSave('settings',{...s,theme:a.theme,appearance:a});toast('تنظیمات ظاهری بازنشانی شد');}
function showSettingsPanel(name){document.querySelectorAll('.settings-panel').forEach(x=>x.classList.remove('active'));document.getElementById('settings'+name.charAt(0).toUpperCase()+name.slice(1))?.classList.add('active');}
window.migrateThemeName=migrateThemeName;window.normalizedAppearance=normalizedAppearance;window.applyAppearance=applyAppearance;window.writeAppearanceControls=writeAppearanceControls;window.appearanceFromControls=appearanceFromControls;window.appearanceChanged=appearanceChanged;window.applyAppearancePreset=applyAppearancePreset;window.resetAppearance=resetAppearance;window.showSettingsPanel=showSettingsPanel;
