(() => {
'use strict';

// Privacy-first: the journal and its photos stay in this browser's IndexedDB.
// No remote analytics, authentication, sync or upload is performed.
const $ = s => document.querySelector(s);
const screen = $('#screen');
const overlay = $('#overlay');
const toastBox = $('#toast');
const SOURCE = {
 alcohol:'https://www.santepubliquefrance.fr/alcool',
 mdma:'https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ecstasy-MDMA',
 ketamine:'https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ketamine',
 cocaine:'https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Cocaine',
 all:'https://www.drogues-info-service.fr/'
};
const TYPES = [
 ['🍺','Bière','Alcool','verre','#e1b06a'],
 ['🍷','Vin','Alcool','verre','#d77db7'],
 ['🍹','Vodka','Alcool','verre','#9acce9'],
 ['🥃','Whisky','Alcool','verre','#ca9372'],
 ['💊','Ecstasy','Autres substances','prise','#ff5ab8'],
 ['🧬','MDMA','Autres substances','prise','#31decf'],
 ['🧪','Kétamine','Autres substances','prise','#a48cff'],
 ['❄️','Cocaïne','Autres substances','prise','#58a8ff'],
 ['⚡','Speed','Autres substances','prise','#e2db59'],
 ['🌿','Cannabis','Autres substances','prise','#51cf83'],
 ['🌈','LSD','Autres substances','prise','#f9b85f'],
 ['🍄','Champignons','Autres substances','prise','#ca78d8'],
 ['💧','GHB / GBL','Autres substances','prise','#55c4ef'],
 ['💨','Poppers','Autres substances','prise','#ff8e80'],
 ['✨','Autre','Autres substances','prise','#bba7ff']
];
const state = {
  db:null,events:[],entries:[],route:'home',eventId:null,
  selected:new Set(),selectionMode:false,
  media:[],urls:[],posterUrls:new Map(),posterUploadEventId:null,rdrAddress:'',renderVersion:0,installPrompt:null,
  viewerUrl:null,toastTimer:null
};
const esc = value => String(value==null?'':value).replace(/[&<>"']/g,c=>({
 '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
}[c]));
const aesc = esc;
const formatDate = v => {
 if(!/^\d{4}-\d\d-\d\d$/.test(v||''))return v||'Date non renseignée';
 try{return new Date(v+'T12:00:00').toLocaleDateString('fr-FR',{day:'numeric',month:'long',year:'numeric'});}
 catch{return v;}
};
const day = () => {const d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');};
let idSequence=0;
const id = () => String(Date.now()*1000+(++idSequence%1000));
const byId = v => String(v);
const mediaKey = v => String(v);
const eventById = v => state.events.find(e=>byId(e.id)===byId(v));
const typeOf = e => TYPES.find(t=>t[1].toLowerCase()===(e.name||'').toLowerCase())||['✦',e.name||'Autre',e.category||'Autres substances',e.unit||'prise','#bba7ff'];
const rgbByName = e => {
 const t=typeOf(e);if(t[1]!=='Autre')return t[4];
 let h=0;for(const c of (e.name||''))h=(h*31+c.charCodeAt(0))>>>0;
 return 'hsl('+(h%360)+' 75% 73%)';
};
const safeImage = src => {
 try{const url=new URL(src);return url.protocol==='https:'?url.href:'';}
 catch{return '';}
};
const safeSpotify = src => {
 const url=safeImage(src);
 return /^https:\/\/open\.spotify\.com\/(?:intl-[a-z]{2}\/)?artist\/[a-zA-Z0-9]{22}(?:[/?#].*)?$/.test(url)?url:'';
};
const artistsOf = e => {
 const list = [];
 const seen = new Set();
 for(const profile of (Array.isArray(e.artistProfiles)?e.artistProfiles:[])){
  const name=String(profile.name||'').trim();
  if(name&&!seen.has(name.toLowerCase())){seen.add(name.toLowerCase());list.push(profile);}
 }
 for(const name of String(e.artists||'').split(/[,;\n]/).map(x=>x.trim())){
  if(name&&!seen.has(name.toLowerCase())){seen.add(name.toLowerCase());list.push({name});}
 }
 return list;
};
function tx(store,mode,job){
 return new Promise((resolve,reject)=>{
  const transaction=state.db.transaction(store,mode);
  const object=transaction.objectStore(store);
  let request;
  try{request=job(object);}catch(error){reject(error);return;}
  transaction.oncomplete=()=>resolve(request&&'result' in request?request.result:undefined);
  transaction.onerror=()=>reject(transaction.error||new Error('Erreur de stockage'));
  transaction.onabort=()=>reject(transaction.error||new Error('Opération annulée'));
 });
}
function openDb(){
 return new Promise((resolve,reject)=>{
  const request=indexedDB.open('noctaria-pwa-v1',1);
  request.onupgradeneeded=()=>{
   const db=request.result;
   db.createObjectStore('journal',{keyPath:'key'});
   const media=db.createObjectStore('media',{keyPath:'id'});
   media.createIndex('eventId','eventId',{unique:false});
  };
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(request.error);
 });
}
async function load(){
 const root=await tx('journal','readonly',obj=>obj.get('root'));
 state.events=Array.isArray(root&&root.events)?root.events:[];
 state.entries=Array.isArray(root&&root.entries)?root.entries:[];
}
async function persist(){
 await tx('journal','readwrite',obj=>obj.put({
  key:'root',events:state.events,entries:state.entries,format:'afterlog-v1'
 }));
}
async function eventMedia(eventId){
 return (await tx('media','readonly',obj=>obj.index('eventId').getAll(byId(eventId))))||[];
}
async function recordMedia(media){return tx('media','readwrite',obj=>obj.put(media));}
async function deleteMedia(mediaId){return tx('media','readwrite',obj=>obj.delete(mediaKey(mediaId)));}
function clearUrls(){
 for(const url of state.urls)URL.revokeObjectURL(url);
 state.urls=[];
}
function fileUrl(blob){
 const url=URL.createObjectURL(blob);state.urls.push(url);return url;
}
function notify(message){
 toastBox.textContent=message;
 toastBox.classList.add('active');
 clearTimeout(state.toastTimer);
 state.toastTimer=setTimeout(()=>toastBox.classList.remove('active'),3800);
}
function modal(title,html,onSubmit){
 overlay.hidden=false;
 overlay.innerHTML='<div class="modal-layer" role="presentation"><section class="modal-sheet" role="dialog" aria-modal="true" aria-label="'+aesc(title)+'"><div class="modal-head"><h2 class="modal-title">'+esc(title)+'</h2><button class="close-x" type="button" data-action="close-modal" aria-label="Fermer">×</button></div><form id="active-form">'+html+'</form></section></div>';
 const form=$('#active-form');
 if(onSubmit){
  form.addEventListener('submit',async event=>{
   event.preventDefault();
   const submit=form.querySelector('[type="submit"]');if(submit)submit.disabled=true;
   try{await onSubmit(new FormData(form));}
   catch(error){notify(error.message||'Enregistrement impossible');}
   finally{if(submit)submit.disabled=false;}
  });
 }
}
function closeModal(){overlay.hidden=true;overlay.innerHTML='';}
function field(name,label,value='',opts={}){
 const typ=opts.type||'text',required=opts.required?' required':'';
 if(typ==='textarea')return '<div class="field '+(opts.full?'full':'')+'"><label for="f-'+esc(name)+'">'+esc(label)+'</label><textarea name="'+esc(name)+'" id="f-'+esc(name)+'" placeholder="'+aesc(opts.placeholder||'')+'">'+esc(value)+'</textarea></div>';
 if(typ==='select')return '<div class="field '+(opts.full?'full':'')+'"><label for="f-'+esc(name)+'">'+esc(label)+'</label><select id="f-'+esc(name)+'" name="'+esc(name)+'">'+opts.options.map(v=>'<option value="'+aesc(v[0])+'" '+(String(v[0])===String(value)?'selected':'')+'>'+esc(v[1])+'</option>').join('')+'</select></div>';
 return '<div class="field '+(opts.full?'full':'')+'"><label for="f-'+esc(name)+'">'+esc(label)+'</label><input id="f-'+esc(name)+'" name="'+esc(name)+'" type="'+aesc(typ)+'" value="'+aesc(value)+'" placeholder="'+aesc(opts.placeholder||'')+'"'+required+'></div>';
}
function submitButtons(label){return '<div class="form-actions"><button type="button" class="btn ghost" data-action="close-modal">Annuler</button><button type="submit" class="btn primary">'+esc(label)+'</button></div>';}
function actionBtn(label,action,extra='',style=''){
 return '<button type="button" class="btn '+style+'" data-action="'+aesc(action)+'" '+extra+'>'+esc(label)+'</button>';
}
function posterHtml(e,large=false){
 const photo=state.posterUrls.get(byId(e.id))||safeImage(e.poster);
 const title=e.title||'Ma soirée';
 const style=photo?' style="background-image:url(&quot;'+aesc(photo)+'&quot;)"':'';
 return '<div class="poster '+(large?'large ':'')+(photo?'with-image':'')+'"'+style+'><span class="poster-brand">✦ NØCTARIA</span><div><div class="poster-title">'+esc(title)+'</div><div class="poster-date">'+esc(formatDate(e.date))+'</div></div></div>';
}
function setNav(active){
 for(const btn of document.querySelectorAll('.bottom-nav [data-tab]'))
  btn.classList.toggle('active',btn.dataset.tab===active);
}
function scrollTop(){window.scrollTo({top:0,behavior:'instant'});}
function parseRoute(){
 const raw=location.hash.replace(/^#/,'');
 if(raw.startsWith('event/')){state.route='event';state.eventId=decodeURIComponent(raw.slice(6));}
 else if(['tracking','settings','rdr'].includes(raw))state.route=raw;
 else state.route='home';
}
function navigate(route){
 location.hash=route==='home'?'#home':('#'+route);
 if((route==='home'&&location.hash==='#home')||location.hash==='#'+route)render();
 scrollTop();
}
async function render(){
 const seq=++state.renderVersion;
 parseRoute();clearUrls();
 state.posterUrls=new Map();
 // Poster files are kept independently of the gallery; original Shotgun
 // posters and generated NØCTARIA placeholders still work offline.
 for(const event of state.events){
  if(!event.posterLocal)continue;
  try{
   const record=await tx('media','readonly',obj=>obj.get(byId(event.posterLocal)));
   if(record&&record.blob)state.posterUrls.set(byId(event.id),fileUrl(record.blob));
  }catch(error){console.warn('Poster unavailable',error);}
 }
 if(seq!==state.renderVersion)return;
 if(state.route==='event'){
  const event=eventById(state.eventId);
  if(!event){navigate('home');return;}
  const media=await eventMedia(event.id);
  if(seq!==state.renderVersion)return;
  state.media=media.filter(m=>!m.isPoster);
  renderEvent(event);
 }else if(state.route==='tracking')renderTracking();
 else if(state.route==='settings')renderSettings();
 else if(state.route==='rdr')renderRdr();
 else renderHome();
 setNav(state.route==='event'?'home':state.route);
}
function renderHome(){
 const events=[...state.events].sort((a,b)=>String(b.date||'').localeCompare(String(a.date||'')));
 screen.innerHTML=
 '<div class="page-head"><div><p class="eyebrow">MES NUITS · MES SOUVENIRS</p><h1>NØCTARIA</h1>'+
 '<p class="kicker">Retrouve toutes tes soirées et ton journal personnel.</p></div></div>'+
 '<div class="button-row home-buttons">'+
 actionBtn('＋ Soirée','new-event','','primary')+
 actionBtn('🎟 Shotgun','shotgun')+
 '</div><div class="button-row home-buttons">'+
 actionBtn('♡ RDR','rdr')+actionBtn('Suivi','tracking')+'</div>'+
 '<div class="heading-row"><h2>MES SOIRÉES · '+events.length+'</h2></div>'+
 (events.length?events.map(event=>{
  const entries=state.entries.filter(e=>byId(e.eventId)===byId(event.id));
  const named=[...new Set(entries.map(e=>typeOf(e)[0]))].slice(0,8).join('  ');
  const risk=window.NoctariaParity?.warnings(entries,state.entries)||[];
  const high=risk.some(w=>w.severity===2);
  const place=event.venue||event.city||'';
  const poster=posterHtml(event);
  return '<button class="event-row card" data-action="open-event" data-id="'+aesc(event.id)+'" aria-label="Ouvrir '+aesc(event.title)+'">'+
   '<div class="event-row-art">'+poster+'</div>'+
   '<div class="event-row-text"><strong>'+esc(event.title||'Événement')+'</strong>'+
   '<span class="event-row-date">'+esc(event.date||'')+(event.start?' · '+esc(event.start):'')+'</span>'+
   (place?'<span class="muted small">'+esc(place)+'</span>':'')+
   ((named||risk.length)?'<span class="event-row-icons">'+esc(named)+(risk.length?' <b class="'+(high?'danger':'warn')+'">⚠</b>':'')+'</span>':'')+
   '<span class="muted small">Ouvrir ›</span></div></button>';
 }).join(''):
 '<div class="card"><h3>Aucune soirée pour l’instant</h3>'+
 '<p class="muted small" style="margin-top:8px">Ajoute une soirée, ou importe les événements d’une sauvegarde Android.</p>'+
 '<div style="margin-top:15px">'+actionBtn('＋ Ajouter une soirée','new-event','','primary')+'</div></div>')+
 '<div class="section"><div class="button-row">'+actionBtn('Importer le journal Android','import')+
 actionBtn('Sauvegarder mon journal','export')+'</div></div>';
}
function entryLabel(e){
 if(e.parts&&typeof e.parts==='object'){
  const p=e.parts;
  return [p.quart?String(p.quart)+' quart':'',p.demi?String(p.demi)+' demi':'',p.entier?String(p.entier)+' entier':''].filter(Boolean).join(' · ')||String(e.quantity||1)+' prise';
 }
 if(Array.isArray(e.portions)&&e.portions.length)return e.portions.map(p=>String(p.count||1)+' '+(p.size||'prise')).join(', ');
 return String(e.quantity||1)+' '+String(e.unit||'prise');
}
function risksHtml(entries){
 const warnings=window.NoctariaParity?
   window.NoctariaParity.warnings(entries,state.entries):[];
 if(!warnings.length)return '';
 const content=window.NoctariaParity?
   window.NoctariaParity.warningMarkup(warnings):
   warnings.map(w=>'<div class="notice '+(w.high?'high':'')+'"><strong>'+esc(w.title)+'</strong><p>'+esc(w.body)+'</p></div>').join('');
 return '<section class="section"><div class="heading-row"><h2>Vigilance RDR</h2>'+
 '<span class="pill warn">'+warnings.length+' alerte(s)</span></div>'+content+'</section>';
}
function renderEvent(event){
 const entries=state.entries.filter(e=>byId(e.eventId)===byId(event.id)).sort((a,b)=>String(a.date+' '+(a.time||'')).localeCompare(String(b.date+' '+(b.time||''))));
 const artists=artistsOf(event);
 const media=state.media;
 const hasSelection=state.selectionMode&&state.selected.size>0;
 const gallery=media.length?'<div class="media-grid">'+media.map(m=>{
  const url=fileUrl(m.blob);
  const selected=state.selected.has(mediaKey(m.id));
  return '<button class="media-tile '+(selected?'selected':'')+'" data-action="media-tap" data-id="'+aesc(m.id)+'" title="'+aesc(m.name)+'">'+
   (m.type.startsWith('image/')?'<img loading="lazy" src="'+aesc(url)+'" alt="'+aesc(m.name)+'">':'<video preload="metadata" src="'+aesc(url)+'" muted playsinline></video><span class="play">▶</span>')+
   (state.selectionMode?'<span class="check">'+(selected?'✓':'')+'</span>':'')+'</button>';
 }).join('')+'</div>':'<div class="empty"><p class="muted">Ajoute tes photos et vidéos de la soirée.</p></div>';
 const lineUp=artists.length?'<section class="section"><div class="heading-row"><h2>Line-up</h2><span class="pill">'+artists.length+' artistes</span></div>'+
 '<div class="artist-grid">'+artists.map(a=>{
  const photo=safeImage(a.spotifyPhoto||a.photo||'');
  const spotify=safeSpotify(a.spotify);
  return '<button type="button" class="artist-card" data-action="artist" data-name="'+aesc(a.name)+'" data-spotify="'+aesc(spotify)+'" title="'+aesc(a.name)+'">'+
   '<span class="artist-letter">'+esc((a.name||'?')[0].toUpperCase())+'</span>'+
   (photo?'<img class="artist-image" loading="lazy" src="'+aesc(photo)+'" alt="" onerror="this.remove()">':'')+
   '<span class="artist-shade"></span><span class="spotify-badge '+(spotify?'':'unlinked')+'">≋</span><span class="artist-name">'+esc(a.name)+'</span></button>';
 }).join('')+'</div></section>':'';
 screen.innerHTML='<button class="back" data-action="home">‹ Mes soirées</button>'+
 posterHtml(event,true)+
 '<div class="button-row" style="margin:-8px 0 19px">'+actionBtn('🖼 Modifier l’affiche','choose-poster','data-id="'+aesc(event.id)+'"','tiny')+
 (event.posterLocal?actionBtn('Retirer ma photo','reset-poster','data-id="'+aesc(event.id)+'"','tiny ghost'):'')+'</div>'+
 '<div class="page-head"><div class="header-sub"><p class="eyebrow">JOURNAL DE SOIRÉE</p><h1>'+esc(event.title||'Ma soirée')+'</h1><p class="kicker">'+esc(formatDate(event.date))+(event.start?' · '+esc(event.start):'')+(event.venue?' · '+esc(event.venue):'')+'</p></div>'+
 actionBtn('✎','edit-event','data-id="'+aesc(event.id)+'"')+'</div>'+
 (event.address||event.city?'<p class="kicker">📍 '+esc([event.address,event.city].filter(Boolean).join(' · '))+'</p>':'')+
 '<section class="section"><div class="heading-row"><h2>Mon journal</h2>'+actionBtn('＋ Prise','new-entry','','tiny primary')+'</div><div class="consumption-grid">'+entries.map(e=>{
  const t=typeOf(e);
  return '<button class="consume-bubble" data-action="edit-entry" data-id="'+aesc(e.id)+'" style="--tone:'+aesc(rgbByName(e))+'"><span class="emoji">'+esc(t[0])+'</span><span class="consume-name">'+esc(e.name)+'</span><span class="consume-quantity">'+esc(entryLabel(e))+'</span></button>';
 }).join('')+'<button class="consume-bubble add" data-action="new-entry" aria-label="Ajouter une prise">＋</button></div></section>'+
 risksHtml(entries)+
 '<section class="section"><div class="heading-row"><h2>Ma galerie · '+media.length+'</h2>'+actionBtn('＋ Médias','upload-media','','tiny')+'</div>'+
 '<div class="action-toolbar">'+actionBtn(state.selectionMode?'✕ Annuler':'✓ Sélectionner','selection-mode','','tiny')+
 (state.selectionMode?actionBtn('Tout','select-all','','tiny'):'')+
 (hasSelection?actionBtn('Partager '+state.selected.size,'share-selected','','tiny primary')+
 actionBtn('Story Instagram','share-story','','tiny')+
 actionBtn('Supprimer','delete-selected','','tiny danger'):'')+'</div>'+gallery+
 '<p class="muted small">Appuie sur un média pour l’ouvrir ou active « Sélectionner » pour un partage multiple.</p></section>'+
 (event.description?'<section class="section"><h2>À propos</h2><p class="detail-description">'+esc(event.description)+'</p></section>':'')+
 lineUp+
 (event.genres?'<section class="section"><h2>Vibe · Styles musicaux</h2><div class="button-row" style="margin-top:12px">'+
 String(event.genres).split(/[,;]+/).filter(Boolean).map(g=>'<span class="pill">♫ '+esc(g.trim())+'</span>').join('')+'</div></section>':'')+
 (event.address||event.venue||event.city?'<section class="section"><h2>Localisation</h2><div class="card"><p class="muted">'+esc([event.venue,event.address,event.city].filter(Boolean).join(' · '))+
 '</p><a class="btn block" style="margin-top:12px" target="_blank" rel="noopener noreferrer" href="https://www.google.com/maps/search/?api=1&query='+encodeURIComponent([event.address,event.venue,event.city].filter(Boolean).join(' '))+'">📍 Ouvrir sur la carte</a></div></section>':'')+
 (event.notes?'<section class="section"><h2>Mes notes</h2><div class="card"><p class="detail-description">'+esc(event.notes)+'</p></div></section>':'')+
 '<section class="section"><div class="heading-row"><h2>Informations</h2></div><div class="card">'+
 '<p class="muted">Date : '+esc(formatDate(event.date))+'</p>'+
 (event.start?'<p class="muted">Horaires : '+esc(event.start)+(event.end?' → '+esc(event.end):'')+'</p>':'')+
 (event.address?'<p class="muted">Adresse : '+esc(event.address)+'</p>':'')+
 (event.genres?'<p class="muted">Styles : '+esc(event.genres)+'</p>':'')+
 (event.url&&safeImage(event.url)?'<p><a target="_blank" rel="noopener noreferrer" href="'+aesc(event.url)+'">Voir l’événement Shotgun ↗</a></p>':'')+
 '</div><div style="margin-top:15px">'+actionBtn('Supprimer cette soirée','delete-event','data-id="'+aesc(event.id)+'"','danger block')+'</div></section>';
}
function openEventForm(existing){
 const e=existing||{};
 let html='<div class="fields">'+
 field('title','Nom de la soirée',e.title||'',{full:true,required:true,placeholder:'Ex. Soirée chez Camille'})+
 field('date','Date',e.date||day(),{type:'date'})+
 field('start','Début',e.start||'',{type:'time'})+
 field('end','Fin',e.end||'',{type:'time'})+
 field('venue','Lieu',e.venue||'',{placeholder:'Chez un ami, festival…'})+
 field('city','Ville',e.city||'')+
 field('address','Adresse (facultatif)',e.address||'',{full:true})+
 field('artists','Artistes (séparés par des virgules)',e.artists||'',{full:true,type:'textarea'})+
 field('genres','Styles musicaux',e.genres||'',{full:true})+
 field('poster','URL de l’affiche (facultatif)',e.poster||'',{full:true,type:'url',placeholder:'https://…'})+
 field('description','Souvenirs / description',e.description||'',{full:true,type:'textarea'})+
 field('organizer','Organisateur',e.organizer||'',{full:true})+
 field('url','Lien événement Shotgun',e.url||'',{full:true,type:'url'})+
 field('notes','Notes personnelles',e.notes||'',{full:true,type:'textarea'})+
 '</div><p class="muted small" style="margin-top:12px">Une affiche NØCTARIA sera créée automatiquement si tu ne mets pas de photo.</p>'+
 submitButtons(existing?'Enregistrer':'Créer la soirée');
 if(existing)html+='<div style="margin-top:13px">'+actionBtn('Supprimer cette soirée','delete-event','data-id="'+aesc(e.id)+'"','danger block')+'</div>';
 modal(existing?'Modifier la soirée':'Créer une soirée',html,async data=>{
  const next={...e};
  for(const [key,value] of data.entries())next[key]=String(value).trim();
  if(!next.title)throw new Error('Indique le nom de la soirée.');
  if(!next.id)next.id=id();
  if(!next.origin)next.origin='Manuel';
  const at=state.events.findIndex(x=>byId(x.id)===byId(next.id));
  if(at===-1)state.events.push(next);else state.events[at]=next;
  await persist();closeModal();navigate('event/'+encodeURIComponent(next.id));
  notify('Soirée enregistrée sur cet appareil');
 });
}
function openEntryForm(existing){
 const e=existing||{},event=eventById(state.eventId);
 if(!event)return;
 const parity=window.NoctariaParity;
 if(!parity){notify('Éditeur indisponible. Actualise la page.');return;}
 modal(existing?'Modifier une consommation':'Ajouter une consommation',
   parity.entryHtml(existing,event),async data=>{
   const form=$('#active-form');
   const next=parity.extractEntry(form,existing,event.id);
   next.id=e.id||id();
   const at=state.entries.findIndex(x=>byId(x.id)===byId(next.id));
   if(at===-1)state.entries.push(next);else state.entries[at]=next;
   await persist();closeModal();await render();
   notify('Prise enregistrée et avertissements actualisés');
 });
 parity.setupEntry($('#active-form'),existing);
}
function openConfirm(title,description,confirmLabel,callback){
 modal(title,'<p class="muted">'+esc(description)+'</p><div class="form-actions"><button type="button" class="btn ghost" data-action="close-modal">Annuler</button><button type="submit" class="btn danger">'+esc(confirmLabel)+'</button></div>',async()=>{await callback();closeModal();await render();});
}
function renderTracking(){
 if(window.NoctariaTracking){
  screen.innerHTML=window.NoctariaTracking.draw(
   state.entries,state.events,rgbByName);
  return;
 }
 screen.innerHTML='<div class="card"><h2>Mon suivi</h2><p>Actualise la page pour charger les graphiques.</p></div>';
}
function renderSettings(){
 const ios=/iphone|ipad|ipod/i.test(navigator.userAgent);
 const standalone=window.matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
 screen.innerHTML='<div class="page-head"><div><p class="eyebrow">CONFIDENTIALITÉ</p><h1>Mes réglages</h1><p class="kicker">Toutes tes données restent sur cet appareil.</p></div></div>'+
 '<div class="card"><h2>Installer sur iPhone</h2><p class="muted" style="margin:12px 0">'+(standalone?'NØCTARIA est déjà ouverte comme application.':ios?'Dans <b>Safari</b>, touche le bouton <b>Partager</b> puis <b>Sur l’écran d’accueil</b> et confirme « Ajouter ».':'Ouvre cette adresse dans Safari sur ton iPhone, puis Partager → Sur l’écran d’accueil.')+'</p>'+actionBtn('Instructions d’installation','install','','block')+'</div>'+
 '<div class="card"><h2>Sauvegarde & migration</h2><p class="muted small" style="margin:8px 0 15px">Importe une sauvegarde JSON Android « afterlog-v1 » pour retrouver tes soirées, artistes et prises. Les fichiers photo et vidéo Android ne sont pas contenus dans ce JSON et doivent être ajoutés à nouveau.</p>'+
 '<div class="button-row">'+actionBtn('Importer une sauvegarde Android','import','','primary')+actionBtn('Exporter mon journal JSON','export')+'</div>'+
 '<p class="muted small" style="margin-top:14px">Le stockage local d’une PWA peut être supprimé si tu effaces les données Safari. Exporte régulièrement ton journal. Ce JSON ne contient pas les médias binaires.</p></div>'+
 '<div class="card"><h2>Confidentialité</h2><p class="muted small" style="margin:12px 0">Aucun compte ni serveur NØCTARIA. Les prises, notes et fichiers sont enregistrés dans la base locale du navigateur (IndexedDB). Les liens Spotify/Shotgun s’ouvrent sur leurs propres sites, qui ont leurs politiques de confidentialité.</p></div>'+
 '<div class="card"><h2>Shotgun et Instagram</h2><p class="muted small" style="margin:8px 0 12px">La connexion Shotgun automatique d’Android n’est pas encore disponible sur iOS. Tu peux créer tes soirées manuellement et importer leurs données par JSON. Le partage multiple utilise la feuille de partage iOS : Instagram décide ensuite des options de story disponibles.</p>'+
 '<a class="btn block" href="https://shotgun.live/" target="_blank" rel="noopener noreferrer">Ouvrir Shotgun ↗</a></div>';
}
function renderRdr(){
 const rdr=window.NoctariaPlaces;
 screen.innerHTML='<button class="back" data-action="tracking">‹ Mon suivi</button>'+
 '<div class="page-head"><div><p class="eyebrow">PRÉVENTION</p><h1>Réduction des risques</h1>'+
 '<p class="kicker">Structures, analyses de produits et fiches de prévention.</p></div></div>'+
 '<div class="notice high"><strong>Urgence : 15 ou 112</strong><p>Perte de connaissance, respiration anormale, convulsions, douleur thoracique ou forte surchauffe : appelle immédiatement les secours.</p><a href="tel:15">Appeler le 15</a></div>'+
 '<div class="card"><h2>Drogues Info Service</h2><p class="muted" style="margin:10px 0">Écoute, informations et accompagnement anonyme. Vérifie les horaires sur le site officiel.</p>'+
 '<div class="button-row"><a class="btn" href="tel:0800231313">📞 Appeler le 0 800 23 13 13</a>'+
 '<a class="btn primary" target="_blank" rel="noopener noreferrer" href="https://www.drogues-info-service.fr/">Site et chat ↗</a></div></div>'+
 '<div class="card"><h2>📍 Structures et associations</h2>'+
 '<p class="muted small" style="margin:9px 0 14px">Recherche des CAARUD et CSAPA proches de l’adresse choisie, comme sur Android.</p>'+
 '<div class="fields">'+field('address','Ville, code postal ou adresse',state.rdrAddress||'',{full:true,placeholder:'Ex. Montpellier 34000'})+'</div>'+
 '<div class="button-row" style="margin-top:12px">'+actionBtn('Rechercher à cette adresse','rdr-places','','primary')+
 '<a class="btn" target="_blank" rel="noopener noreferrer" href="https://www.drogues-info-service.fr/Adresses-utiles">Annuaire national ↗</a></div>'+
 '<div id="rdr-results" aria-live="polite" style="margin-top:16px">'+
 '<p class="muted small">Saisis une adresse pour trouver les structures cartographiées à proximité. Les résultats peuvent être incomplets.</p></div></div>'+
 (rdr?rdr.guide():'');
}
async function searchRdrPlaces(query){
 const el=$('#rdr-results');
 if(!el)return;
 if(!query||query.trim().length<2){notify('Saisis une adresse ou une ville.');return;}
 if(!window.NoctariaPlaces){
  el.innerHTML='<p class="muted">Service indisponible. Consulte l’annuaire national.</p>';return;
 }
 state.rdrAddress=query.trim();
 el.innerHTML='<p class="muted">Recherche des structures autour de '+esc(query)+'…</p>';
 try{
  const result=await window.NoctariaPlaces.directory(query);
  if(!el.isConnected)return;
  el.innerHTML='<div class="pill">📍 '+esc(result.label)+'</div>'+
   '<p class="muted small" style="margin:13px 0 9px">'+result.items.length+' structure(s) cartographiée(s), triées par proximité. Distances à vol d’oiseau.</p>'+
   (result.items.length?result.items.map(window.NoctariaPlaces.card).join(''):
   '<div class="notice"><strong>Aucun lieu trouvé dans OpenStreetMap</strong><p>Cela ne signifie pas qu’il n’existe pas de CAARUD ou de CSAPA. Consulte l’annuaire officiel.</p></div>')+
   '<p class="muted small">Données OpenStreetMap non exhaustives. Vérifie les horaires et coordonnées avant de te déplacer.</p>';
 }catch(error){
  if(!el.isConnected)return;
  el.innerHTML='<div class="notice"><strong>Recherche momentanément indisponible</strong><p>'+esc(error.message||'Service externe inaccessible')+
  '</p><a href="https://www.drogues-info-service.fr/Adresses-utiles" target="_blank" rel="noopener noreferrer">Continuer sur l’annuaire officiel ↗</a></div>';
 }
}

function installGuide(){
 const standalone=window.matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
 if(standalone){notify('NØCTARIA est déjà installée sur cet appareil.');return;}
 if(state.installPrompt){state.installPrompt.prompt();return;}
 modal('Installer NØCTARIA','<p>Sur iPhone, ouvre NØCTARIA dans <b>Safari</b>, touche <b>Partager</b> (carré avec flèche), sélectionne <b>Sur l’écran d’accueil</b> puis <b>Ajouter</b>.</p><p class="muted small" style="margin-top:13px">L’icône NØCTARIA sera ajoutée à ton écran d’accueil. Les données resteront enregistrées sur ce téléphone.</p><div class="form-actions"><button class="btn primary block" type="button" data-action="close-modal">Compris</button></div>');
}
async function importBackup(file){
 if(!file||file.size>35_000_000)throw new Error('Fichier absent ou trop volumineux (35 Mo max).');
 let text=await file.text();
 // Android uses 17-digit numeric IDs, beyond JavaScript's exact integer
 // range. Quote them before parsing to prevent silent rounding.
 text=text.replace(/("(?:id|eventId)"\s*:\s*)(\d{16,20})(?=\s*[,}])/g,'$1"$2"');
 const data=JSON.parse(text);
 if(data.format!=='afterlog-v1'||!Array.isArray(data.events)||!Array.isArray(data.entries))
  throw new Error('Format incompatible : export Android afterlog-v1 attendu.');
 if(data.events.length>10000||data.entries.length>200000)
  throw new Error('Sauvegarde trop volumineuse.');
 const ids=new Set();
 for(const event of data.events){
  if(!event||!event.id||!event.title)throw new Error('Soirée invalide.');
  if(ids.has(String(event.id)))throw new Error('Identifiants de soirées dupliqués.');
  ids.add(String(event.id));
 }
 for(const entry of data.entries)
  if(!entry||!ids.has(String(entry.eventId)))throw new Error('Prise rattachée à une soirée inconnue.');
 openConfirm('Remplacer le journal ?','L’import remplace les soirées et prises actuelles sur cet appareil. Les médias déjà présents restent stockés localement. Exporte ton journal avant de poursuivre.','Importer',async()=>{
  state.events=data.events;
  state.entries=data.entries;
  state.selectionMode=false;state.selected.clear();
  await persist();navigate('home');notify('Sauvegarde importée : '+data.events.length+' soirées');
 });
}
function exportBackup(){
 const data={format:'afterlog-v1',events:state.events,entries:state.entries};
 const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});
 const url=URL.createObjectURL(blob);
 const a=document.createElement('a');a.href=url;a.download='noctaria-journal-'+day()+'.json';a.click();
 setTimeout(()=>URL.revokeObjectURL(url),2000);
 notify('Journal exporté — les fichiers médias ne sont pas inclus.');
}
async function uploadFiles(files){
 const event=eventById(state.eventId);
 if(!event)return;
 let imported=0;
 for(const file of files){
  if(!/^(image|video)\//.test(file.type))continue;
  if(file.size>160*1024*1024){notify('Un fichier dépasse 160 Mo et a été ignoré.');continue;}
  const media={id:id(),eventId:byId(event.id),name:file.name,type:file.type,blob:file,createdAt:Date.now()};
  try{
   await recordMedia(media);
   const field=file.type.startsWith('video/')?'videos':'photos';
   if(!Array.isArray(event[field]))event[field]=[];
   event[field].push(media.id);
   imported++;
  }catch(error){notify('Stockage saturé ou inaccessible. Exporte et libère de la place.');break;}
 }
 if(imported){await persist();state.selected.clear();state.selectionMode=false;await render();notify(imported+' média(s) ajouté(s)');}
}
async function shareSelected(){
 const chosen=state.media.filter(m=>state.selected.has(String(m.id)));
 if(!chosen.length){notify('Sélectionne au moins un média.');return;}
 const files=chosen.map(m=>new File([m.blob],m.name||'noctaria-'+m.id,
  {type:m.type||'application/octet-stream'}));
 if(navigator.share&&(!navigator.canShare||navigator.canShare({files}))){
  try{await navigator.share({files,title:'Souvenirs NØCTARIA'});}
  catch(error){if(error.name!=='AbortError')notify('Partage iOS non disponible pour cette sélection.');}
 }else{
  notify('Ce navigateur ne prend pas en charge le partage multiple. Tu peux ouvrir les médias individuellement.');
 }
}
async function viewMedia(mediaId){
 const media=state.media.find(x=>mediaKey(x.id)===mediaKey(mediaId));
 if(!media)return;
 if(state.viewerUrl)URL.revokeObjectURL(state.viewerUrl);
 state.viewerUrl=URL.createObjectURL(media.blob);
 const viewer=document.createElement('div');viewer.className='viewer';viewer.id='media-viewer';
 viewer.innerHTML='<button class="btn viewer-close" data-action="close-viewer">Fermer ×</button>'+
  '<div class="viewer-content">'+(media.type.startsWith('video/')?
   '<video controls playsinline src="'+aesc(state.viewerUrl)+'"></video>':
   '<img src="'+aesc(state.viewerUrl)+'" alt="'+aesc(media.name)+'">')+'</div>'+
  '<div class="viewer-actions">'+actionBtn('Partager','share-single','data-id="'+aesc(media.id)+'"')+
  actionBtn('Supprimer','delete-single','data-id="'+aesc(media.id)+'"','danger')+'</div>';
 document.body.appendChild(viewer);
}
function closeViewer(){
 const viewer=$('#media-viewer');if(viewer)viewer.remove();
 if(state.viewerUrl)URL.revokeObjectURL(state.viewerUrl);
 state.viewerUrl=null;
}
function selectedToggle(mediaId){
 const key=String(mediaId);
 if(state.selected.has(key))state.selected.delete(key);else state.selected.add(key);
 render();
}
function clearSelection(){state.selectionMode=false;state.selected.clear();render();}
function validatePhoto(eventName,profile,link){
 const artist=profile||{name:eventName};
 artist.spotify=link;
 return artist;
}
function askArtist(name){
 const match=state.events.flatMap(artistsOf).find(a=>a.name===name);
 modal('Profil Spotify · '+name,field('spotify','Lien du profil Spotify',match&&match.spotify||'',{type:'url',full:true,placeholder:'https://open.spotify.com/artist/…'})+
  '<p class="muted small" style="margin:12px 0">Associe uniquement le vrai compte de cet artiste. L’image Spotify ne peut pas toujours être extraite automatiquement depuis Safari.</p>'+submitButtons('Associer'),async form=>{
  const link=safeSpotify(form.get('spotify'));
  if(!link)throw new Error('Lien direct Spotify artiste invalide.');
  for(const event of state.events){
   const profiles=artistsOf(event);
   const target=profiles.find(p=>p.name.trim().toLowerCase()===name.trim().toLowerCase());
   if(!target)continue;
   target.spotify=link;
   event.artistProfiles=profiles;
  }
  await persist();closeModal();await render();notify('Profil Spotify associé');
 });
}
async function act(action,button){
 const idVal=button.dataset.id;
 switch(action){
  case 'home':navigate('home');break;
  case 'tracking':navigate('tracking');break;
  case 'tracking-period':
   window.NoctariaTracking?.mode(button.dataset.mode);
   render();break;
  case 'tracking-prev':
   window.NoctariaTracking?.move(-1);render();break;
  case 'tracking-next':
   window.NoctariaTracking?.move(1);render();break;
  case 'rdr':navigate('rdr');break;
  case 'settings':navigate('settings');break;
  case 'new-event':openEventForm(null);break;
  case 'open-event':state.selectionMode=false;state.selected.clear();navigate('event/'+encodeURIComponent(idVal));break;
  case 'edit-event':openEventForm(eventById(idVal));break;
  case 'delete-event':{
   const event=eventById(idVal);if(!event)return;
   openConfirm('Supprimer cette soirée ?', 'Les prises et médias associés seront supprimés de cet appareil.','Supprimer',async()=>{
    const media=await eventMedia(event.id);
    for(const m of media)await deleteMedia(m.id);
    state.events=state.events.filter(x=>byId(x.id)!==byId(idVal));
    state.entries=state.entries.filter(x=>byId(x.eventId)!==byId(idVal));
    await persist();navigate('home');notify('Soirée supprimée');
   });break;
  }
  case 'new-entry':openEntryForm(null);break;
  case 'edit-entry':openEntryForm(state.entries.find(e=>byId(e.id)===byId(idVal)));break;
  case 'delete-entry':{
   openConfirm('Supprimer cette prise ?','Elle sera retirée du journal personnel.','Supprimer',async()=>{
    state.entries=state.entries.filter(e=>byId(e.id)!==byId(idVal));
    await persist();notify('Prise supprimée');
   });break;
  }
  case 'upload-media':$('#media-file').click();break;
  case 'selection-mode':state.selectionMode=!state.selectionMode;state.selected.clear();render();break;
  case 'select-all':
   if(state.selected.size===state.media.length)state.selected.clear();
   else state.selected=new Set(state.media.map(x=>String(x.id)));
   render();break;
  case 'media-tap':
   if(state.selectionMode)selectedToggle(idVal);else viewMedia(idVal);
   break;
  case 'delete-selected':{
   const count=state.selected.size;
   openConfirm('Supprimer '+count+' média(s) ?',
    'Seules les copies de NØCTARIA seront supprimées. Les originaux dans Photos restent inchangés.',
    'Supprimer',async()=>{
    const event=eventById(state.eventId);
    for(const m of state.media.filter(m=>state.selected.has(String(m.id))))
     await deleteMedia(m.id);
    if(event){
     event.photos=(event.photos||[]).filter(x=>!state.selected.has(String(x)));
     event.videos=(event.videos||[]).filter(x=>!state.selected.has(String(x)));
    }
    await persist();state.selected.clear();state.selectionMode=false;notify(count+' média(s) supprimé(s)');
   });break;
  }
  case 'share-selected':await shareSelected();break;
  case 'share-story':
   await shareSelected();
   notify('Dans la feuille de partage iOS, choisis Instagram puis Story si proposé. Instagram peut refuser un lot mixte.');
   break;
  case 'choose-poster':
   state.posterUploadEventId=idVal||state.eventId;
   $('#poster-file').click();
   break;
  case 'reset-poster':{
   const target=eventById(idVal);
   if(!target)return;
   const old=target.posterLocal;
   delete target.posterLocal;
   await persist();
   if(old)await deleteMedia(byId(old));
   await render();notify('Affiche NØCTARIA rétablie');
   break;
  }
  case 'share-single':{
   const m=state.media.find(x=>byId(x.id)===byId(idVal));if(!m)return;
   if(navigator.share){try{await navigator.share({files:[new File([m.blob],m.name,{type:m.type})]});}
    catch(e){if(e.name!=='AbortError')notify('Partage non disponible');}}
   else notify('Partage non disponible dans ce navigateur.');
   break;
  }
  case 'delete-single':{
   closeViewer();
   state.selectionMode=true;state.selected=new Set([String(idVal)]);
   await render();
   await act('delete-selected',button);break;
  }
  case 'close-viewer':closeViewer();break;
  case 'artist':{
   const url=safeSpotify(button.dataset.spotify);
   if(url)window.open(url,'_blank','noopener,noreferrer');
   else askArtist(button.dataset.name||'Artiste');
   break;
  }
  case 'import':$('#import-file').click();break;
  case 'export':exportBackup();break;
  case 'install':installGuide();break;
  case 'rdr-places':{
   const value=$('#f-address')&&$('#f-address').value.trim();
   await searchRdrPlaces(value);
   break;
  }
  case 'close-modal':closeModal();break;
 }
}
// Long press on a gallery thumbnail enters multi-selection on touch devices.
let pressTimer=null;
document.addEventListener('pointerdown',event=>{
 const tile=event.target.closest('.media-tile');
 if(!tile||state.selectionMode)return;
 clearTimeout(pressTimer);
 const chosen=tile.dataset.id;
 pressTimer=setTimeout(()=>{
  state.selectionMode=true;
  state.selected.add(String(chosen));
  if(navigator.vibrate)navigator.vibrate(15);
  render();
 },550);
});
for(const name of ['pointerup','pointercancel','pointerleave','scroll'])
 document.addEventListener(name,()=>clearTimeout(pressTimer),{passive:true});

document.addEventListener('click',event=>{
 const button=event.target.closest('[data-action]');
 if(!button)return;
 event.preventDefault();
 Promise.resolve(act(button.dataset.action,button)).catch(error=>notify(error.message||'Erreur'));
});
overlay.addEventListener('click',event=>{
 if(event.target===overlay||event.target.classList.contains('modal-layer'))closeModal();
});
document.addEventListener('keydown',event=>{
 if(event.key==='Escape'){closeModal();closeViewer();}
});
$('#import-file').addEventListener('change',async e=>{
 const file=e.target.files[0];
 if(file)try{await importBackup(file);}catch(error){notify(error.message||'Import impossible');}
 e.target.value='';
});
$('#poster-file').addEventListener('change',async e=>{
 const file=e.target.files[0];
 const event=eventById(state.posterUploadEventId);
 e.target.value='';
 if(!file||!event)return;
 if(!file.type.startsWith('image/')||file.size>14*1024*1024){
  notify('Choisis une image de moins de 14 Mo.');return;
 }
 const old=event.posterLocal;
 const record={id:id(),eventId:byId(event.id),isPoster:true,
  name:file.name,type:file.type,blob:file,createdAt:Date.now()};
 try{
  await recordMedia(record);
  event.posterLocal=record.id;
  await persist();
  if(old)await deleteMedia(byId(old));
  await render();notify('Affiche personnelle enregistrée');
 }catch(error){notify('Impossible d’enregistrer cette affiche.');}
});
$('#media-file').addEventListener('change',async e=>{
 if(e.target.files.length)await uploadFiles([...e.target.files]);
 e.target.value='';
});
window.addEventListener('hashchange',render);
window.addEventListener('beforeinstallprompt',event=>{
 event.preventDefault();state.installPrompt=event;
});
(async()=>{
 try{
  state.db=await openDb();
  await load();
  await render();
  if('serviceWorker' in navigator)navigator.serviceWorker.register('./sw.js').catch(()=>{});
 }catch(error){
  console.error('NØCTARIA startup',error);
  screen.innerHTML='<div class="card"><h1>Stockage indisponible</h1><p class="muted">NØCTARIA a besoin du stockage local de Safari pour conserver ton journal. Désactive le mode navigation privée et vérifie l’espace disponible.</p></div>';
 }
})();
})();
