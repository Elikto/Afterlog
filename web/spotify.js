/* NØCTARIA browser-side Spotify matching.
   Queries only public artist names (never journal dates or consumption data).
   Wikidata P1902 must yield one unique candidate; Spotify oEmbed (if CORS
   permits) must confirm its title before recording its official thumbnail.
   When browser CORS blocks Spotify thumbnails, keep Shotgun artwork intact. */
(()=>{'use strict';
const NS='noctaria-spotify-v2';
const TRUSTED=window.NoctariaTrustedSpotify||{};
const MAX_NETWORK_LOOKUPS=12;
const RETRY_MS=6*3600*1000;
let running=false,queuedRescan=false;
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
// Never accept an automatically discovered Wikidata ID without checking the
// official Spotify oEmbed artist title; the thumbnail is optional.
async function spotifyMetadata(link,name){
 if(!isSpotify(link))return null;
 try{
  const data=await fetchJson('https://open.spotify.com/oembed?url='+encodeURIComponent(link),9500);
  const title=String(data.title||'').trim()
   .replace(/^Spotify Embed:\s*/i,'').replace(/^Listen to\s+/i,'')
   .replace(/\s+Songs\s*\|\s*Spotify\s*$/i,'')
   .replace(/\s*[|·–-]\s*Spotify\s*$/i,'')
   .replace(/\s*[|·–-]\s*(artist|artiste)\s*$/i,'')
   .replace(/\s*·\s*Top tracks\s*$/i,'');
  if(data.provider_name!=='Spotify'||norm(title)!==norm(name))return null;
  const src=data.thumbnail_url||'';
  let photo='';
  if(src){
   const u=new URL(src);
   if(u.protocol==='https:'&&(
    u.hostname==='i.scdn.co'||u.hostname==='mosaic.scdn.co'||
    u.hostname.endsWith('.spotifycdn.com')))photo=src;
  }
  return {photo};
 }catch{return null;} // Never guess when Spotify cannot be checked.
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
    let eventChanged=false;
    for(const artist of p){
     if(norm(artist.name)!==key)continue;
     if(artist.spotify&&!isSpotify(artist.spotify))continue;
     if(artist.spotify&&artist.spotify!==link)continue;
     if(!artist.spotify&&link){artist.spotify=link;eventChanged=true;}
     if(photo&&!artist.spotifyPhoto){artist.spotifyPhoto=photo;eventChanged=true;}
    }
    if(eventChanged){event.artistProfiles=p;changed=true;}
   }
   if(changed)store.put(root);
  };
  tx.oncomplete=()=>resolve(changed);
  tx.onerror=()=>reject(tx.error);
  tx.onabort=()=>reject(tx.error||Error('Sauvegarde Spotify annulée'));
 });
}
function eventFingerprint(events){
 return JSON.stringify(events.map(e=>[
  String(e.id||''),profiles(e).map(a=>norm(a.name)).filter(Boolean).sort()
 ]).sort((a,b)=>a[0].localeCompare(b[0])));
}
async function scan(force=false){
 if(running){queuedRescan=true;return status();}
 if(!navigator.onLine)return status();
 running=true;
 let db;
 try{
  db=await openDb();
  const root=await getRoot(db),events=root.events||[];
  const fingerprint=eventFingerprint(events);
  const prev=status();
  if(!force&&prev.state==='completed'&&prev.fingerprint===fingerprint&&
     Date.now()-Number(prev.lastRun||0)<24*3600e3)return prev;
  const artists=distinctArtistNames(events);
  const attempts={...(prev.attempts||{})};
  let checked=0,network=0,changed=false,pending=false;
  update({state:'running',total:artists.size,checked:0,fingerprint});
  for(const [key,a] of artists){
   if(a.ambiguous){checked++;continue;}
   checked++;
   // The same 15 explicitly confirmed artist profiles as the Android app.
   // Propagate already known URLs to EVERY occurrence across all events.
   let link=isSpotify(a.link)?a.link:(TRUSTED[key]||'');
   let photo=a.photo||'';
   if(isSpotify(link)){
    try{if(await saveArtist(db,a.name,link,photo))changed=true;}catch{}
   }
   if(link&&photo)continue;
   if(!force&&Number(attempts[key])>0&&Date.now()-attempts[key]<RETRY_MS)continue;
   if(network>=MAX_NETWORK_LOOKUPS){pending=true;continue;}
   network++;
   attempts[key]=Date.now();
   if(!link){
    // Wikidata alone does not prove the identity of an artist.
    let candidate='';
    try{candidate=await wiki(a.name);}catch{}
    if(!candidate)continue;
    const result=await spotifyMetadata(candidate,a.name);
    if(!result)continue;
    link=candidate;photo=result.photo;
   }else{
    // Preserve a previously confirmed/manual link when Spotify blocks CORS.
    const result=await spotifyMetadata(link,a.name);
    if(result&&result.photo)photo=result.photo;
   }
   try{if(await saveArtist(db,a.name,link,photo))changed=true;}catch{}
   if(checked%3===0)update({state:'running',total:artists.size,checked,fingerprint});
  }
  const latest=distinctArtistNames((await getRoot(db)).events||[]);
  let linked=0,photos=0;
  for(const a of latest.values()){
   if(isSpotify(a.link))linked++;
   if(a.photo)photos++;
  }
  const result={state:pending?'partial':'completed',total:artists.size,checked,
   linked,photos,fingerprint,attempts,lastRun:Date.now(),
   note:pending?'Recherche progressive des profils sur toutes les soirées.':
    'Les homonymes et profils non confirmés restent sans lien.'};
  update(result);
  if(changed)window.dispatchEvent(new Event('noctaria:spotify-updated'));
  if(pending){
   // iOS can suspend background JS: remaining names are also retried next time
   // the PWA is opened. Keep each foreground batch bounded and respectful.
   setTimeout(()=>scan(false).catch(()=>{}),20000);
  }
  return result;
 }catch(error){
  update({state:'partial',error:error.message||'Recherche temporairement indisponible',lastRun:Date.now()});
  return status();
 }finally{
  running=false;
  if(db)db.close();
  if(queuedRescan){
   queuedRescan=false;
   setTimeout(()=>scan(false).catch(()=>{}),300);
  }
 }
}
window.NoctariaSpotify={scan,status};
})();