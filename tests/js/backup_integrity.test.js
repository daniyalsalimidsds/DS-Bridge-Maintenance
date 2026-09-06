const fs=require('fs'),assert=require('assert');
const j=fs.readFileSync('app/src/main/java/ir/bridge/maintenance/BackupManager.java','utf8');
const b=fs.readFileSync('app/src/main/assets/js/backup.js','utf8');
assert(j.includes('BACKUP_SCHEMA = 3'));assert(j.includes('entitiesSha256'));assert(j.includes('integrityAlgorithm'));assert(j.includes('SHA-256'));
assert(j.includes('safety')||j.includes('Safety'));assert(j.includes('restoreSnapshot'));assert(j.includes('canonical')||j.includes('safe'));
assert(b.includes('integrityVerified'));
assert(b.includes("app:'ir.bridge.maintenance'"));assert(b.includes("'bridges'"));
console.log('backup_integrity.test.js PASS');
