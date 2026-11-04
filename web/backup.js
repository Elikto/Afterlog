/* NØCTARIA private, full-fidelity offline media archive.
 * A binary .noctaria bundle = magic + UInt32(JSON header length) + JSON header
 * + exact image/video Blob payloads. The media bytes are never base64 encoded,
 * uploaded to a server, or sent through external analytics.
 */
(()=>{'use strict';
const MAGIC='NOCTARIA-PACK-1\n',ENC=new TextEncoder(),MAX_HEAD=12*1024*1024;
const marker=ENC.encode(MAGIC);
function db(){return new Promise((resolve,reject)=>{
 const req=indexedDB.open('noctaria-pwa-v1',1);
 req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);
});}
function reqPromise(request){return new Promise((resolve,reject)=>{
 request.onsuccess=()=>resolve(request.result);
 request.onerror=()=>reject(request.error);
});}
async function snapshot(){
 const connection=await db();
 try{
  const journal=await reqPromise(connection.transaction('journal','readonly').objectStore('journal').get('root'));
  const media=await reqPromise(connection.transaction('media','readonly').objectStore('media').getAll());
  if(!journal)throw Error('Aucun journal à sauvegarder.');
  return {journal,media:media||[]};
 }finally{connection.close();}
}
function validate(root){
 if(!root||root.format!=='afterlog-v1'||!Array.isArray(root.events)||!Array.isArray(root.entries))
  throw Error('Format de journal non reconnu.');
 if(root.events.length>10000||root.entries.length>200000)
  throw Error('Journal trop volumineux.');
 const ids=new Set();
 for(const event of root.events){
  if(!event||!event.id||!event.title)throw Error('Soirée incorrecte dans la sauvegarde.');
  const id=String(event.id);
  if(ids.has(id))throw Error('Identifiants de soirées répétés.');
  ids.add(id);
 }
 for(const entry of root.entries)
  if(!entry||!ids.has(String(entry.eventId)))throw Error('Prise liée à une soirée inconnue.');
 return ids;
}
async function exportFull(){
 const {journal,media}=await snapshot();
 validate(journal);
 const manifest=media.filter(m=>m&&m.blob instanceof Blob).map(m=>({
  id:String(m.id),eventId:String(m.eventId),isPoster:!!m.isPoster,
  name:String(m.name||'media'),type:String(m.type||'application/octet-stream'),
  createdAt:Number(m.createdAt)||0,size:m.blob.size
 }));
 const header={version:1,format:'noctaria-full-v1',createdAt:new Date().toISOString(),
  journal:{...journal,key:'root'},files:manifest};
 const headerBytes=ENC.encode(JSON.stringify(header));
 if(headerBytes.length>MAX_HEAD)throw Error('En-tête trop volumineux.');
 const prefix=new Uint8Array(4),view=new DataView(prefix.buffer);
 view.setUint32(0,headerBytes.length,false);
 const pieces=[marker,prefix,headerBytes,...media.filter(m=>m&&m.blob instanceof Blob).map(m=>m.blob)];
 const output=new Blob(pieces,{type:'application/octet-stream'});
 const url=URL.createObjectURL(output);
 const link=document.createElement('a');
 link.href=url;link.download='noctaria-complet-'+new Date().toISOString().slice(0,10)+'.noctaria';
 document.body.appendChild(link);link.click();link.remove();
 setTimeout(()=>URL.revokeObjectURL(url),60000);
 return {count:manifest.length,bytes:output.size};
}
async function previewImport(file){
 const prefixLen=marker.length+4;
 if(!file||file.size<prefixLen)throw Error('Archive trop petite.');
 const begin=new Uint8Array(await file.slice(0,prefixLen).arrayBuffer());
 if(!marker.every((x,i)=>begin[i]===x))throw Error('Ce fichier n’est pas une archive NØCTARIA valide.');
 const length=new DataView(begin.buffer).getUint32(marker.length,false);
 if(!length||length>MAX_HEAD||prefixLen+length>file.size)
  throw Error('En-tête de sauvegarde invalide.');
 const head=JSON.parse(await file.slice(prefixLen,prefixLen+length).text());
 if(head.format!=='noctaria-full-v1'||head.version!==1)throw Error('Version d’archive inconnue.');
 validate(head.journal);
 const manifest=head.files;
 if(!Array.isArray(manifest)||manifest.length>15000)
  throw Error('Nombre de médias invalide.');
 const ids=new Set(),known=new Set(head.journal.events.map(e=>String(e.id)));
 let pos=prefixLen+length;
 const files=[];
 for(const m of manifest){
  if(!m||!m.id||!known.has(String(m.eventId))||ids.has(String(m.id)))
   throw Error('Média invalide ou en doublon.');
  ids.add(String(m.id));
  if(!Number.isSafeInteger(m.size)||m.size<0||m.size>2*1024*1024*1024)
   throw Error('Fichier média trop volumineux.');
  if(pos+m.size>file.size)throw Error('Archive tronquée : médias manquants.');
  files.push({...m,offset:pos});
  pos+=m.size;
 }
 if(pos!==file.size)throw Error('Archive contenant des données inattendues.');
 return {header:head,files,file};
}
async function restore(prepared){
 const {header,files,file}=prepared;
 validate(header.journal);
 const connection=await db();
 try{
  await new Promise((resolve,reject)=>{
   const transaction=connection.transaction(['journal','media'],'readwrite');
   const media=transaction.objectStore('media'),journal=transaction.objectStore('journal');
   media.clear();
   for(const m of files){
    const blob=file.slice(m.offset,m.offset+m.size,m.type);
    media.put({
     id:String(m.id),eventId:String(m.eventId),isPoster:!!m.isPoster,
     name:m.name,type:m.type,createdAt:m.createdAt,blob
    });
   }
   journal.put({...header.journal,key:'root'});
   transaction.oncomplete=()=>resolve();
   transaction.onerror=()=>reject(transaction.error||Error('Échec du stockage.'));
   transaction.onabort=()=>reject(transaction.error||Error('Import annulé.'));
  });
 }finally{connection.close();}
 return {events:header.journal.events.length,entries:header.journal.entries.length,
  media:files.length};
}
window.NoctariaBackup={exportFull,previewImport,restore};
})();