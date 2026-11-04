import {JSDOM} from 'jsdom';
import {TextEncoder, TextDecoder} from 'node:util';
import {indexedDB, IDBKeyRange} from 'fake-indexeddb';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const root=path.dirname(new URL(import.meta.url).pathname);
const html=readFileSync(path.join(root,'index.html'),'utf8')
  .replace('<script src="./app.js" defer></script>','');
const dom=new JSDOM(html,{
 url:'https://example.org/Afterlog/',
 runScripts:'outside-only',
 pretendToBeVisual:true
});
const {window}=dom;
const manifest=JSON.parse(readFileSync(path.join(root,'manifest.webmanifest'),'utf8'));
assert.equal(manifest.display,'standalone','installed iOS PWA opens without a browser frame');
assert.equal(manifest.short_name,'NØCTARIA');
assert.ok(manifest.icons.some(x=>x.sizes==='192x192'));
assert.ok(manifest.icons.some(x=>x.sizes==='512x512'));
assert.ok(window.document.querySelector('link[rel="apple-touch-icon"][href="./icons/apple-touch-icon.png"]'),'iOS home screen icon configured');
assert.equal(window.document.querySelector('meta[name="apple-mobile-web-app-capable"]')?.content,'yes');
window.indexedDB=indexedDB;
window.IDBKeyRange=IDBKeyRange;
// jsdom does not expose these browser globals by default on Node.js 22.
window.TextEncoder=TextEncoder;
window.TextDecoder=TextDecoder;
// Mirror browser File/Blob APIs so the binary archive can be round-tripped in jsdom.
window.Blob=globalThis.Blob;
window.File=globalThis.File;
window.scrollTo=()=>{};
window.matchMedia=()=>({matches:false,addListener(){},removeListener(){}});
window.navigator.vibrate=()=>{};
let exportedBlob=null;
window.URL.createObjectURL=blob=>{exportedBlob=blob;return 'blob:mock-export';};
window.URL.revokeObjectURL=()=>{};
window.eval(readFileSync(path.join(root,'parity.js'),'utf8'));
window.eval(readFileSync(path.join(root,'places.js'),'utf8'));
window.eval(readFileSync(path.join(root,'tracking.js'),'utf8'));
window.eval(readFileSync(path.join(root,'spotify-trusted.js'),'utf8'));
window.eval(readFileSync(path.join(root,'spotify.js'),'utf8'));
assert.ok(window.NoctariaTrustedSpotify.perceval,'Android confirmed artist links available on iPhone');
assert.equal(typeof window.NoctariaPlaces.suggestCities,'function','IGN city autocomplete is available on iPhone');
window.eval(readFileSync(path.join(root,'backup.js'),'utf8'));
window.eval(readFileSync(path.join(root,'app.js'),'utf8'));
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const click=selector=>{
 const node=window.document.querySelector(selector);
 assert.ok(node,'missing element '+selector);node.click();return node;
};
async function submit(formName){
 const form=window.document.querySelector('#active-form');
 assert.ok(form,'form missing '+formName);
 form.dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));
 await pause(80);
}
await pause(150);
assert.ok(window.document.querySelector('h1')?.textContent.includes('NØCTARIA'),'home page rendered');
click('[data-action="new-event"]');
assert.ok(window.document.querySelector('.modal-title').textContent.includes('Créer une soirée'));
window.document.querySelector('[name="title"]').value='Soirée chez Camille';
window.document.querySelector('[name="date"]').value='2026-11-01';
await submit('event');
assert.ok(window.document.querySelector('.poster.large'),'private event has a default poster');
assert.ok(window.document.body.textContent.includes('Soirée chez Camille'));
click('[data-action="new-entry"]');
const products=window.document.querySelector('[name="name"]');
products.value='MDMA';
window.document.querySelector('[name="quantity"]').value='1';
window.document.querySelector('[name="time"]').value='22:30';
await submit('entry');
assert.ok(window.document.querySelector('.consume-bubble .consume-name').textContent.includes('MDMA'),'entry created');
click('[data-action="tracking"]');
await pause(55);
assert.ok(window.document.body.textContent.includes('Produits consignés'),'tracking appears');
click('[data-action="tracking-prev"]');
await pause(60);
assert.ok(window.document.body.textContent.includes('MDMA'),'history navigation reveals the saved substance');
click('[data-action="settings"]');
await pause(45);
assert.ok(window.document.body.textContent.includes('Importer JSON ou archive complète'),'archive import offered');
assert.ok(window.document.body.textContent.includes('Sauvegarder avec photos et vidéos'),'complete backup action offered');
assert.ok(window.NoctariaBackup && typeof window.NoctariaBackup.exportFull==='function','full backup module loaded');
const rows=await new Promise((resolve,reject)=>{
 const req=indexedDB.open('noctaria-pwa-v1',1);
 req.onsuccess=()=>{
  const tx=req.result.transaction('journal','readonly');
  const read=tx.objectStore('journal').get('root');
  read.onsuccess=()=>resolve(read.result);
  read.onerror=()=>reject(read.error);
 };
 req.onerror=()=>reject(req.error);
});
assert.equal(rows.events.length,1);
assert.equal(rows.entries.length,1);
// Complete media archive must preserve the image/video bytes and the journal.
const mediaDb=await new Promise((resolve,reject)=>{
 const req=indexedDB.open('noctaria-pwa-v1',1);
 req.onsuccess=()=>resolve(req.result);
 req.onerror=()=>reject(req.error);
});
const mediaBytes=new Uint8Array([2,7,12,99,255]);
await new Promise((resolve,reject)=>{
 const tx=mediaDb.transaction('media','readwrite');
 tx.objectStore('media').put({id:'picture-1',eventId:String(rows.events[0].id),
  name:'image-test.png',type:'image/png',createdAt:Date.now(),
  blob:new globalThis.Blob([mediaBytes],{type:'image/png'})});
 tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);
});
const oldClick=window.HTMLAnchorElement.prototype.click;
window.HTMLAnchorElement.prototype.click=function(){};
const saved=await window.NoctariaBackup.exportFull();
window.HTMLAnchorElement.prototype.click=oldClick;
assert.equal(saved.count,1,'one image included in complete export');
assert.ok(exportedBlob && exportedBlob.size>0,'binary archive generated');
const archive=new globalThis.File([exportedBlob],'journal.noctaria');
const prepared=await window.NoctariaBackup.previewImport(archive);
assert.equal(prepared.header.journal.events.length,1);
assert.equal(prepared.files.length,1);
const restored=await window.NoctariaBackup.restore(prepared);
assert.equal(restored.media,1);
const fetched=await new Promise((resolve,reject)=>{
 const tx=mediaDb.transaction('media','readonly');
 const req=tx.objectStore('media').get('picture-1');
 req.onsuccess=()=>resolve(req.result);
 req.onerror=()=>reject(req.error);
});
assert.deepEqual(new Uint8Array(await fetched.blob.arrayBuffer()),mediaBytes,
 'complete archive preserves the original binary media');
mediaDb.close();
console.log('NØCTARIA PWA smoke test OK: events, consumption, tracking, settings, IndexedDB, full media archive round-trip');
dom.window.close();
