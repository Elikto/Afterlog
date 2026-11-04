import {JSDOM} from 'jsdom';
import {indexedDB,IDBKeyRange} from 'fake-indexeddb';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const dom=new JSDOM('<!doctype html><title>NØCTARIA</title>',{
 url:'https://elikto.github.io/Afterlog/',runScripts:'outside-only'
});
const {window}=dom;
window.indexedDB=indexedDB;
window.IDBKeyRange=IDBKeyRange;
const correctId='3'+'A'.repeat(21),wrongId='4'+'B'.repeat(21);
const urlCorrect='https://open.spotify.com/artist/'+correctId;
const urlWrong='https://open.spotify.com/artist/'+wrongId;
const called=[];
const response=data=>({ok:true,status:200,json:async()=>data});
window.fetch=async input=>{
 const url=new URL(input);
 called.push(url.href);
 if(url.hostname==='www.wikidata.org'){
  const action=url.searchParams.get('action');
  if(action==='wbsearchentities'){
   const term=url.searchParams.get('search');
   const names={'Verified Performer':['Verified Performer','Q123'],
    'Imposteur':['Imposteur','Q456']};
   const found=names[term];
   return response({search:found?[{label:found[0],id:found[1]}]:[]});
  }
  const id=url.searchParams.get('ids');
  const sid=id==='Q123'?correctId:wrongId;
  return response({entities:{[id]:{claims:{P1902:[{
   mainsnak:{datavalue:{value:sid}}
  }]}}}});
 }
 if(url.hostname==='open.spotify.com'&&url.pathname==='/oembed'){
  const artistUrl=url.searchParams.get('url');
  if(artistUrl===urlCorrect)return response({provider_name:'Spotify',
   title:'Verified Performer',thumbnail_url:'https://i.scdn.co/image/testphoto'});
  if(artistUrl===urlWrong)return response({provider_name:'Spotify',
   title:'Un autre musicien',thumbnail_url:'https://i.scdn.co/image/wrong'});
  return response({provider_name:'Spotify',title:'Perceval'});
 }
 throw Error('Unexpected public request: '+url);
};
const db=await new Promise((resolve,reject)=>{
 const req=indexedDB.open('noctaria-pwa-v1',1);
 req.onupgradeneeded=()=>{req.result.createObjectStore('journal',{keyPath:'key'});};
 req.onsuccess=()=>resolve(req.result);
 req.onerror=()=>reject(req.error);
});
async function put(root){
 await new Promise((resolve,reject)=>{
  const tx=db.transaction('journal','readwrite');
  tx.objectStore('journal').put(root);
  tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);
 });
}
async function get(){
 return await new Promise((resolve,reject)=>{
  const tx=db.transaction('journal','readonly');
  const req=tx.objectStore('journal').get('root');
  req.onsuccess=()=>resolve(req.result);
  req.onerror=()=>reject(req.error);
 });
}
const seed={key:'root',format:'afterlog-v1',entries:[],events:[
 {id:'1',title:'Festival A',artists:'Perceval, Verified Performer, Imposteur'},
 {id:'2',title:'Festival B',artists:'Perceval'}
]};
await put(seed);
window.eval(readFileSync(path.join(root,'web/spotify-trusted.js'),'utf8'));
window.eval(readFileSync(path.join(root,'web/spotify.js'),'utf8'));
let result=await window.NoctariaSpotify.scan(true);
let saved=await get();
const find=(event,name)=>event.artistProfiles?.find(a=>a.name===name);
assert.equal(saved.events.length,2);
for(const event of saved.events){
 assert.equal(find(event,'Perceval')?.spotify,window.NoctariaTrustedSpotify.perceval,
  'previously confirmed Android profile propagated to every event');
}
assert.equal(find(saved.events[0],'Verified Performer')?.spotify,urlCorrect,
 'unique Wikidata ID was verified by Spotify oEmbed title');
assert.equal(find(saved.events[0],'Verified Performer')?.spotifyPhoto,
 'https://i.scdn.co/image/testphoto');
assert.ok(!find(saved.events[0],'Imposteur')?.spotify,
 'wrong Spotify homonym was not linked');
assert.equal(result.total,3);
assert.equal(result.linked,2);
assert.ok(called.every(u=>!u.includes('Festival%20A')&&!u.includes('Festival%20B')),
 'private event titles are never sent to the lookup services');
// A newly imported event must receive the known link without waiting 24 hours.
const previousCalls=called.length;
saved.events.push({id:'3',title:'Nouvelle soirée',artists:'Perceval'});
await put(saved);
result=await window.NoctariaSpotify.scan(false);
saved=await get();
assert.equal(find(saved.events[2],'Perceval')?.spotify,
 window.NoctariaTrustedSpotify.perceval,'new event is rechecked immediately');
assert.equal(result.total,3);
assert.ok(called.length>=previousCalls);
db.close();window.close();
console.log('NØCTARIA Spotify parity test OK: all events, shared links, official title, no wrong homonym, new imports');
