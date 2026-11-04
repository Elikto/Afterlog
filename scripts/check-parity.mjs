#!/usr/bin/env node
// NØCTARIA cross-platform contract: validate both implementations stay present.
// This is an executable coverage baseline, not a visual equivalence guarantee.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const contract=JSON.parse(fs.readFileSync(path.join(root,'shared/features.json'),'utf8'));
const failures=[];
assert.deepEqual(contract.platforms,['android','ios-pwa']);
for(const feature of contract.common){
 for(const platform of contract.platforms){
  const probes=feature[platform==='android'?'android':'web'];
  if(!Array.isArray(probes)||!probes.length){
   failures.push(feature.id+': missing '+platform+' implementation markers');
   continue;
  }
  for(const [file,marker] of probes){
   const target=path.resolve(root,file);
   if(!target.startsWith(root+path.sep)||!fs.existsSync(target)){
    failures.push(feature.id+': missing file '+file);
   }else if(!fs.readFileSync(target,'utf8').includes(marker)){
    failures.push(feature.id+': '+platform+' missing '+JSON.stringify(marker)+' in '+file);
   }
  }
 }
}
if(!Array.isArray(contract.platformGaps))failures.push('Platform gap register missing');
if(failures.length){
 console.error('NØCTARIA cross-platform contract FAILED:\n'+failures.map(x=>' - '+x).join('\n'));
 process.exit(1);
}
console.log('NØCTARIA contract OK: '+contract.common.length+
 ' common features have both platform implementation markers.');
if(contract.platformGaps.length)
 console.log('Documented platform gaps (NOT feature parity): '+
 contract.platformGaps.map(x=>x.id).join(', '));
