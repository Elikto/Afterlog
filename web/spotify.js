/* NØCTARIA browser-side Spotify matching.
   Queries only public artist names (never journal dates or consumption data).
   Wikidata P1902 must yield one unique candidate; Spotify oEmbed (if CORS
   permits) must confirm its title before recording its official thumbnail.
   When browser CORS blocks Spotify thumbnails, keep Shotgun artwork intact. */
(()=>{'use strict';
const NS='noctaria-spotify-v1';
let running=false;
const norm=s=>String(s||'').toLowerCase().replace(/ø/g,'o').normalize('NFD')
 .replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim();
const isSpotify=s=>/^https:\/\/open\.spotify\.com\/(?:intl-[a-z]{2}\/)?artist\/[a-zA-Z0-9]{22}(?:[/?#].*)?$/.test(s||'');
const idOf=s=>String(s).match(/\/artist\/([a-zA-Z0-9]{22})/);
const status=()=>{try{return JSON.parse(localStorage.getItem(NS)||'{}');}catch{return {};}};
const update=data=>{try{localStorage.setItem(NS,JSON.stringify({...status(),...data}));}catch{}};
async function fetchJson(url,ms=13000){
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),ms);
 try{
  const response=await fetch(url,{credentials:'omit',mode:'cors',referrerPolicy:'no-referrer',signal:controller.signal});
  if(!response.ok)throw Error('Service distant '+response.status);
  return await response.json();
 }finally{clearTimeout(timer);}
}
function profiles(event){
 const result=Array.isArray(event.artistProfiles)?event.artistProfiles.map(p=>({...p})):[];
 const seen=new Set(result.map(p=>norm(p.name)));
 for(const name of String(event.artists||'').split(/[,;\n]/).map(s=>s.trim()).filter(Boolean)){
  const key=norm(name);if(!key||seen.has(key))continue;
  result.push({name});seen.add(key);
 }
 return result;
}
function distinctArtistNames(events){
 const map=new Map();
 for(const event of events){
  for(const artist of profiles(event)){
   if(!artist.name)continue;
   const key=norm(artist.name);if(!key)continue;
   const existing=map.get(key);
   if(!existing)map.set(key,{name:artist.name,link:artist.spotify||'',photo:artist.spotifyPhoto||'',ambiguous:false});
   else {
    if(artist.spotify&&existing.link&&existing.link!==artist.spotify)existing.ambiguous=true;
    if(!existing.link&&artist.spotify)existing.link=artist.spotify;
    if(!existing.photo&&artist.spotifyPhoto)existing.photo=artist.spotifyPhoto;
   }
  }
 }
 return map;
}
async function wiki(name){
 const search=await fetchJson('https://www.wikidata.org/w/api.php?'+new URLSearchParams({
  action:'wbsearchentities',format:'json',language:'en',limit:'12',search:name,origin:'*'
 }));
 const ids=(search.search||[]).filter(x=>norm(x.label)===norm(name)&&/^Q\d+$/.test(x.id)).map(x=>x.id);
 if(ids.length===0)return '';
 const data=await fetchJson('https://www.wikidata.org/w/api.php?'+new URLSearchParams({
  action:'wbgetentities',format:'json',props:'claims',ids:ids.join('|'),origin:'*'
 }));
 const candidates=new Set();
 for(const id of ids){
  const claims=data.entities?.[id]?.claims?.P1902||[];
  for(const claim of claims){
   const spotifyId=claim?.mainsnak?.datavalue?.value;
   if(typeof spotifyId==='string'&&/^[0-7][A-Za-z0-9]{21}$/.test(spotifyId))
    candidates.add('https://open.spotify.com/artist/'+spotifyId);
  }
 }
 // Do not pick a profile when multiple distinct Spotify IDs fit the same name.
 return candidates.size===1?[...candidates][0]:'';
}
async function spotifyPortrait(link,name){
 if(!isSpotify(link))return '';
 try{
  const data=await fetchJson('https://open.spotify.com/oembed?url='+encodeURIComponent(link),9500);
  if(norm(data.title)!==norm(name))return '';
  const src=data.thumbnail_url||'';
  const u=new URL(src);
  if(u.protocol!=='https:'||!(
    u.hostname==='i.scdn.co'||u.hostname==='mosaic.scdn.co'||
    u.hostname.endsWith('.spotifycdn.com')))return '';
  return src;
 }catch{return '';} // CORS-blocked on many iOS browsers: no arbitrary photo.
}
function openDb(){
 return new Promise((resolve,reject)=>{
  const request=indexedDB.open('noctaria-pwa-v1',1);
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(request.error);
 });
}
function getRoot(db){
 return new Promise((resolve,reject)=>{
  const tx=db.transaction('journal','readonly');
  const rq=tx.objectStore('journal').get('root');
  rq.onsuccess=()=>resolve(rq.result||{key:'root',events:[],entries:[],format:'afterlog-v1'});
  rq.onerror=()=>reject(rq.error);
 });
}
async function saveArtist(db,name,link,photo){
 const key=norm(name);
 return new Promise((resolve,reject)=>{
  const tx=db.transaction('journal','readwrite');
  const store=tx.objectStore('journal');
  const get=store.get('root');
  let changed=false;
  get.onsuccess=()=>{
   const root=get.result;
   if(!root||!Array.isArray(root.events))return;
   for(const event of root.events){
    const p=profiles(event);
    for(const artist of p){
     if(norm(artist.name)!==key)continue;
     if(artist.spotify&&!isSpotify(artist.spotify))continue;
     if(artist.spotify&&artist.spotify!==link)continue;
     if(!artist.spotify&&link){artist.spotify=link;changed=true;}
     if(photo&&!artist.spotifyPhoto){artist.spotifyPhoto=photo;changed=true;}
    }
    if(changed)event.artistProfiles=p;
   }
   if(changed)store.put(root);
  };
  tx.oncomplete=()=>resolve(changed);
  tx.onerror=()=>reject(tx.error);
 });
}
async function scan(force=false){
 if(running||!navigator.onLine)return status();
 const prev=status();
 if(!force&&Date.now()-Number(prev.lastRun||0)<24*3600e3)return prev;
 running=true;
 const db=await openDb();
 let linked=0,photos=0,checked=0,changed=false;
 try{
  const root=await getRoot(db),artists=distinctArtistNames(root.events||[]);
  update({state:'running',total:artists.size,checked:0});
  for(const [key,a] of artists){
   if(a.ambiguous)continue;
   checked++;
   if(a.link&&a.photo){linked++;photos++;continue;}
   let link=isSpotify(a.link)?a.link:'';
   if(!link){
    try{link=await wiki(a.name);}catch{}
   }
   if(!link)continue;
   linked++;
   let photo=a.photo||'';
   if(!photo)photo=await spotifyPortrait(link,a.name);
   if(photo)photos++;
   try{if(await saveArtist(db,a.name,link,photo))changed=true;}catch{}
   if(checked%4===0)update({state:'running',checked,total:artists.size,linked,photos});
  }
  const result={state:'completed',total:artists.size,checked,linked,photos,
    lastRun:Date.now(),note:'Les portraits peuvent être indisponibles si Spotify refuse l’accès du navigateur.'};
  update(result);
  if(changed)window.dispatchEvent(new Event('noctaria:spotify-updated'));
  return result;
 }catch(error){
  update({state:'partial',error:error.message||'Recherche temporairement indisponible',lastRun:Date.now()});
  return status();
 }finally{running=false;db.close();}
}
window.NoctariaSpotify={scan,status};
})();