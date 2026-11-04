/* Public geocoding and harm-reduction directories, called directly on-device. */
(()=>{'use strict';
const SOURCE='https://www.drogues-info-service.fr/Adresses-utiles';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
async function getJson(url,opts={},timeout=21000){
 const c=new AbortController(),timer=setTimeout(()=>c.abort(),timeout);
 try{
  const res=await fetch(url,{...opts,signal:c.signal,credentials:'omit',referrerPolicy:'no-referrer'});
  if(!res.ok)throw Error('Service temporairement indisponible ('+res.status+').');
  return await res.json();
 }finally{clearTimeout(timer);}
}
function distance(a,b,c,d){
 const R=6371.0088,p=Math.PI/180,dl=(c-a)*p,doLon=(d-b)*p;
 const aa=Math.sin(dl/2)**2+Math.cos(a*p)*Math.cos(c*p)*Math.sin(doLon/2)**2;
 return 2*R*Math.asin(Math.min(1,Math.sqrt(aa)));
}
/** IGN Géoplateforme municipality suggestions, no GPS or user account. */
async function suggestCities(query){
 const term=String(query||'').trim();
 if(term.length<2)return [];
 const params=new URLSearchParams({
  index:'address',type:'municipality',limit:'8',autocomplete:'1',q:term
 });
 const data=await getJson('https://data.geopf.fr/geocodage/search?'+params,{},14000);
 const found=new Set(),results=[];
 for(const feature of data.features||[]){
  const p=feature.properties||{};
  const city=String(p.city||p.name||'').trim(),postcode=String(p.postcode||'').trim();
  if(!city)continue;
  const label=city+(postcode?', '+postcode:'');
  if(found.has(label.toLocaleLowerCase('fr')))continue;
  found.add(label.toLocaleLowerCase('fr'));
  results.push({label,query:label});
 }
 return results;
}
async function geocode(address){
 const q=encodeURIComponent(address);
 for(const suffix of [
  '?index=address&limit=5&q='+q,
  '?limit=5&q='+q]){
  try{
   const data=await getJson('https://data.geopf.fr/geocodage/search'+suffix,{},14000);
   const feature=data.features?.find(f=>f.geometry?.coordinates?.length>=2);
   if(feature){
    const [lon,lat]=feature.geometry.coordinates.map(Number);
    if(Number.isFinite(lat)&&Number.isFinite(lon))
     return {lat,lon,label:feature.properties?.label||address};
   }
  }catch(error){if(suffix.startsWith('?limit='))throw error;}
 }
 throw Error('Adresse non reconnue. Ajoute la ville ou le code postal.');
}
async function directory(address){
 const location=await geocode(address);
 const around='(around:50000,'+location.lat.toFixed(6)+','+location.lon.toFixed(6)+')';
 const query='[out:json][timeout:24];('+
 'nwr'+around+'["name"~"CSAPA|CAARUD|addictolog|addiction",i];'+
 'nwr'+around+'["social_facility:for"~"drug_addicted"];'+
 ');out center 160;';
 let data,lastError;
 for(const endpoint of ['https://overpass-api.de/api/interpreter','https://overpass.kumi.systems/api/interpreter']){
  try{
   data=await getJson(endpoint,{
    method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
    body:'data='+encodeURIComponent(query)
   },32000);
   break;
  }catch(error){lastError=error;}
 }
 if(!data)throw lastError||Error('Annuaire momentanément indisponible.');
 const seen=new Set(),items=[];
 for(const entity of data.elements||[]){
  const tags=entity.tags||{},name=String(tags.name||'').trim();
  const lat=Number(entity.lat??entity.center?.lat);
  const lon=Number(entity.lon??entity.center?.lon);
  if(!name||!Number.isFinite(lat)||!Number.isFinite(lon))continue;
  const key=name.toLowerCase()+'|'+lat.toFixed(3)+'|'+lon.toFixed(3);
  if(seen.has(key))continue;seen.add(key);
  const addressLine=[
   [tags['addr:housenumber'],tags['addr:street']].filter(Boolean).join(' '),
   [tags['addr:postcode'],tags['addr:city']].filter(Boolean).join(' ')
  ].filter(Boolean).join(', ');
  items.push({
   name,lat,lon,distance:distance(location.lat,location.lon,lat,lon),
   type:/CAARUD/i.test(name)?'CAARUD · réduction des risques':
    /CSAPA/i.test(name)?'CSAPA · addictologie':'Structure d’accompagnement',
   address:addressLine,
   phone:tags['contact:phone']||tags.phone||'',
   website:tags['contact:website']||tags.website||'',
   email:tags['contact:email']||tags.email||''
  });
 }
 items.sort((a,b)=>a.distance-b.distance);
 return {...location,items:items.slice(0,22)};
}
function card(place){
 const route='https://www.google.com/maps/search/?api=1&query='+encodeURIComponent(place.lat.toFixed(6)+','+place.lon.toFixed(6));
 const phone=place.phone.replace(/[^0-9+]/g,'');
 let website='';
 try{const u=new URL(place.website);if(['http:','https:'].includes(u.protocol))website=u.href;}catch{}
 return '<article class="rdr-item"><h3>'+esc(place.name)+'</h3><p class="muted small">'+esc(place.type)+' · '+esc(place.distance.toFixed(1))+' km à vol d’oiseau</p>'+
 (place.address?'<p>'+esc(place.address)+'</p>':'')+
 '<div class="button-row">'+
 (phone?'<a class="btn tiny" href="tel:'+esc(phone)+'">📞 Téléphoner</a>':'')+
 (website?'<a class="btn tiny" target="_blank" rel="noopener noreferrer" href="'+esc(website)+'">Site officiel ↗</a>':'')+
 '<a class="btn tiny" target="_blank" rel="noopener noreferrer" href="'+esc(route)+'">📍 Itinéraire</a>'+
 '</div></article>';
}
const sheets=[
 ['🍺 Alcool','Altération de la vigilance, intoxication aiguë, accidents ; les mélanges avec dépresseurs accentuent les risques.'],
 ['💊 MDMA / ecstasy','Composition incertaine, hyperthermie, troubles de conscience. Les prises rapprochées et les mélanges augmentent les risques.'],
 ['🧪 Kétamine','Désorientation, blessures, perte de conscience et complications urinaires lors des usages répétés.'],
 ['❄️ Cocaïne','Risques cardiovasculaires, neurologiques et psychiatriques ; le mélange avec l’alcool est particulièrement préoccupant.'],
 ['⚡ Speed / amphétamines','Palpitations, insomnie, anxiété et surchauffe. La fatigue peut être masquée.'],
 ['🌈 LSD','Effets psychiques parfois intenses et prolongés ; risques de confusion et d’accident.'],
 ['🌿 Cannabis','Altération de l’attention, anxiété, malaise ; ne pas conduire après consommation.'],
 ['🍄 Champignons','Désorientation et réactions anxieuses ; l’identification erronée d’un champignon peut être grave.'],
 ['💧 GHB / GBL','Ralentissement respiratoire, coma ; association avec alcool ou dépresseurs particulièrement risquée.'],
 ['💨 Poppers','Vertiges et hypotension ; association dangereuse avec certains médicaments vasodilatateurs.'],
 ['💊 Opioïdes','Surdose avec dépression respiratoire ; demander les secours et utiliser la naloxone si disponible.']
];
function guide(){
 return '<section class="section"><h2>🧪 Analyse de produits</h2><div class="card"><p class="muted small">Les dispositifs peuvent changer : vérifie les conditions avant de te déplacer.</p><div class="button-row"><a class="btn primary" rel="noopener noreferrer" target="_blank" href="https://www.analysetonprod.fr/">Analysetonprod ↗</a><a class="btn" rel="noopener noreferrer" target="_blank" href="'+SOURCE+'">Trouver un CAARUD ↗</a></div></div></section>'+
 '<section class="section"><h2>Fiches de prévention</h2><p class="muted small" style="margin-bottom:12px">Informations générales, sans seuil de sécurité inventé.</p>'+
 sheets.map(([title,text])=>'<details class="rdr-item"><summary><b>'+esc(title)+'</b></summary><p class="muted">'+esc(text)+'</p><a class="btn tiny" target="_blank" rel="noopener noreferrer" href="https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues">Lire les fiches de référence ↗</a></details>').join('')+'</section>';
}
window.NoctariaPlaces={directory,suggestCities,card,guide,source:SOURCE};
})();