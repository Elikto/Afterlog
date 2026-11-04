import {JSDOM} from 'jsdom';
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
window.indexedDB=indexedDB;
window.IDBKeyRange=IDBKeyRange;
window.scrollTo=()=>{};
window.matchMedia=()=>({matches:false,addListener(){},removeListener(){}});
window.navigator.vibrate=()=>{};
window.URL.createObjectURL=blob=>'blob:mock-'+Math.random();
window.URL.revokeObjectURL=()=>{};
window.eval(readFileSync(path.join(root,'parity.js'),'utf8'));
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
assert.ok(window.document.querySelector('.hero'),'home page rendered');
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
assert.ok(window.document.body.textContent.includes('MDMA'),'tracking has custom product');
click('[data-action="settings"]');
await pause(45);
assert.ok(window.document.body.textContent.includes('Importer une sauvegarde Android'),'Android import offered');
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
console.log('NØCTARIA PWA smoke test OK: initial load, event, default poster, consumption, tracking, settings, IndexedDB');
dom.window.close();
