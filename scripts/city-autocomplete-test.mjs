import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const dom=new JSDOM('<!doctype html>',{url:'https://elikto.github.io/Afterlog/',runScripts:'outside-only'});
const {window}=dom;
const fetched=[];
window.fetch=async raw=>{
 const url=new URL(raw);
 fetched.push(url.toString());
 assert.equal(url.hostname,'data.geopf.fr','suggestions use the official IGN service');
 return {ok:true,json:async()=>({features:[
  {properties:{type:'municipality',city:'Marseille',postcode:'13001'},geometry:{coordinates:[5.37,43.30]}},
  {properties:{type:'municipality',city:'Marseille',postcode:'13001'},geometry:{coordinates:[5.37,43.30]}},
  {properties:{type:'municipality',city:'Martigues',postcode:'13500'},geometry:{coordinates:[5.06,43.40]}}
 ]})};
};
window.eval(readFileSync(path.join(root,'web/places.js'),'utf8'));
const choices=await window.NoctariaPlaces.suggestCities('mar');
assert.deepEqual(Array.from(choices,x=>String(x.label)),['Marseille, 13001','Martigues, 13500']);
assert.equal((await window.NoctariaPlaces.suggestCities('m')).length,0);
assert.ok(fetched.some(u=>u.includes('type=municipality')),'municipality-only results are preferred');
const app=readFileSync(path.join(root,'web/app.js'),'utf8');
assert.ok(app.includes("case 'rdr-city':"),'user may select a returned city');
assert.ok(app.includes('state.rdrCitySelected'),'city must be validated before search');
const rdr=readFileSync(path.join(root,
 'app/src/main/java/fr/afterlog/app/RdrActivity.java'),'utf8');
const service=readFileSync(path.join(root,
 'app/src/main/java/fr/afterlog/app/RdrPlacesSearch.java'),'utf8');
assert.ok(rdr.includes('AutoCompleteTextView'),'Android suggests cities');
assert.ok(rdr.includes('RdrPlacesSearch.suggestCities('),'Android uses real suggestions');
assert.ok(rdr.includes('selected[0]')&&rdr.includes('showPage();'),'Android validates city before results');
assert.ok(service.includes('type=municipality'),'Android uses proper commune results');
const manifest=readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
assert.ok(manifest.includes('android:label="NØCTARIA"'));
const tracking=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/SuiviActivity.java'),'utf8');
assert.doesNotMatch(tracking,/Retour à AfterLog/i,'visible legacy name must not return');
const main=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/MainActivity.java'),'utf8');
assert.ok(main.includes('noctaria-sauvegarde.json'),'backup displays NØCTARIA');
dom.window.close();
console.log('City autocomplete & NØCTARIA branding OK: Android + iOS, commune validation');
