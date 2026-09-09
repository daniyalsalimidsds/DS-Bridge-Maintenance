const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const events = [];
const context = {
  console,
  Date,
  Set,
  Promise,
  JSON,
  CustomEvent: class CustomEvent {
    constructor(type, init) { this.type = type; this.detail = init?.detail; }
  },
  document: {
    dispatchEvent(event) { events.push(event); },
    addEventListener() {},
    createElement() { return {}; },
    head: { appendChild() {} },
  },
};
let calls = 0;
context.window = context;
context.addEventListener = () => {};
context.BridgeAndroid = {
  deleteBatch(raw) {
    calls += 1;
    const payload = JSON.parse(raw);
    return JSON.stringify({ ok: true, requestId: payload.requestId, deleted: payload.entries.length });
  },
};
vm.createContext(context);
vm.runInContext(fs.readFileSync('app/src/main/assets/js/bridge.js', 'utf8'), context);
context.BridgeNativeClient.bootstrap(JSON.stringify({ bridges: [{ id: 'b1', name: 'پل آزمون' }] }), '1.4.0', 10400);

context.dbDeleteBatch([{ kind: 'bridges', id: 'b1' }]).then(result => {
  assert.strictEqual(result.ok, true);
  assert.strictEqual(calls, 1);
  assert.strictEqual(context.dbList('bridges').length, 0);
  assert.strictEqual(events.some(event => event.type === 'bridge-entities-deleted'), true);
  console.log('deletion_bridge.test.js PASS');
}).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
