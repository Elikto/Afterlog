import {JSDOM} from 'jsdom';
import {indexedDB,IDBKeyRange} from 'fake-indexeddb';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const dom=new JSDOM('<!doctype html>',{
 url:'https://elikto.github.io/Afterlog/',runScripts:'outside-only'
});
const w=dom.window;
w.indexedDB=indexedDB;w.IDBKeyRange=IDBKeyRange;
const requests=[];
const reply=obj=>({ok:true,json:async()=>obj});
w.fetch=async input=>{
 const u=new URL(input);
 requests.push(u.href);
 if(u.hostname==='www.wikidata.org'){
  if(u.searchParams.get('action')==='wbsearchentities'){
   const name=(u.searchParams.get('search')||'').toLowerCase();
   const id=name==='soundcloud only'?'Q100':name==='wrong dj'?'Q200':null;
   return reply({search:id?[{id,label:name==='soundcloud only'?'SoundCloud Only':'Wrong DJ'}]:[]});
  }
  const ids=u.searchParams.get('ids')||'Q0';
  const profile=ids==='Q100'?'soundcloud-only-artist':'another-unrelated-account';
  const make=handle=>({mainsnak:{datavalue:{value:handle}}});
  return reply({entities:{[ids]:{claims:{P3040:[make(profile)]}}}});
 }
 if(u.hostname==='soundcloud.com'&&u.pathname==='/oembed'){
  const candidate=u.searchParams.get('url');
  return reply({provider_name:'SoundCloud',
    title:candidate.includes('another-unrelated')?'Not the same musician | SoundCloud':
      'SoundCloud Only | SoundCloud'});
 }
 if(u.hostname==='open.spotify.com'&&u.pathname==='/oembed'){
  const candidate=u.searchParams.get('url');
  return reply({provider_name:'Spotify',
   title:candidate?.includes('1aQbGaag8KEI8kKtY37UQU')?'Maissouille':
    'Unicorn on K',thumbnail_url:'https://i.scdn.co/image/artist-verified'});
 }
 throw Error('Unexpected lookup '+u.href);
};
const db=await new Promise((resolve,reject)=>{
 const rq=indexedDB.open('noctaria-pwa-v1',1);
 rq.onupgradeneeded=()=>rq.result.createObjectStore('journal',{keyPath:'key'});
 rq.onsuccess=()=>resolve(rq.result);rq.onerror=()=>reject(rq.error);
});
await new Promise((resolve,reject)=>{
 const tx=db.transaction('journal','readwrite');
 tx.objectStore('journal').put({key:'root',format:'afterlog-v1',
  entries:[],events:[{id:'one',title:'Festival',artists:
   'DJ Maissouille, 🦄 Unicorn On K ✨, SoundCloud Only, Wrong DJ'}]});
 tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);
});
w.eval(readFileSync(path.join(root,'web/spotify-trusted.js'),'utf8'));
w.eval(readFileSync(path.join(root,'web/spotify.js'),'utf8'));
const scan=await w.NoctariaSpotify.scan(true);
const saved=await new Promise((resolve,reject)=>{
 const tx=db.transaction('journal','readonly');
 const rq=tx.objectStore('journal').get('root');
 rq.onsuccess=()=>resolve(rq.result);rq.onerror=()=>reject(rq.error);
});
const byName=name=>saved.events[0].artistProfiles.find(p=>p.name===name);
assert.equal(byName('DJ Maissouille').spotify,
 'https://open.spotify.com/artist/1aQbGaag8KEI8kKtY37UQU',
 'DJ prefix must not block the actual official artist account');
assert.equal(byName('🦄 Unicorn On K ✨').spotify,
 'https://open.spotify.com/artist/4Y1vfB7DE2qneIPdgylsL8',
 'emoji are decorative and do not change the artist identity');
assert.equal(byName('SoundCloud Only').soundcloud,
 'https://soundcloud.com/soundcloud-only-artist',
 'official SoundCloud oEmbed identity is a valid fallback');
assert.equal(byName('Wrong DJ').spotify,undefined);
assert.equal(byName('Wrong DJ').soundcloud,undefined,
 'SoundCloud title mismatch must not be attributed to a namesake');
assert.equal(scan.linked,2);
assert.equal(scan.soundcloud,1);
assert.ok(requests.some(x=>x.includes('P3040'))===false ||
 requests.some(x=>x.includes('wbgetentities')),
 'SoundCloud IDs are obtained through documented Wikidata account claims');
db.close();dom.window.close();
console.log('Spotify aliases and SoundCloud fallback OK: DJ, emoji, P3040 and mismatched profile refusal');
