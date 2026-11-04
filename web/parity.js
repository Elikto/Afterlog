/* NØCTARIA parity helpers: standalone logic usable in Safari, no analytics. */
(()=>{'use strict';
const href={
 alcohol:'https://www.santepubliquefrance.fr/alcool',
 mdma:'https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ecstasy-MDMA',
 ketamine:'https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ketamine',
 cocaine:'https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Cocaine',
 ghb:'https://www.drogues-info-service.fr/',
 all:'https://www.drogues-info-service.fr/'
};
const ESC=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const choices=['Bière','Vin','Vodka','Whisky','Ecstasy','MDMA','Kétamine','Cocaïne','Speed','Cannabis','LSD','Champignons','GHB / GBL','Poppers','Autre'];
const routeMap={
 'Ecstasy':['Orale','Autre'],
 'MDMA':['Orale (para)','Nasale (trace)','Autre'],
 'Speed':['Orale (para)','Nasale (trace)','Autre'],
 'Kétamine':['Nasale (trace)','Autre'],
 'Cocaïne':['Nasale (trace)','Autre'],
 'LSD':['Buvard oral','Gouttes orales','Autre'],
 'Cannabis':['Fumée','Vaporisée','Orale','Autre'],
 'Champignons':['Orale','Autre'],
 'GHB / GBL':['Orale','Autre'],
 'Poppers':['Inhalée','Autre'],
 'Autre':['Orale','Nasale','Inhalée','Autre']
};
const unitMap={
 'MDMA':['mg','g','unité'],
 'Kétamine':['mg','g','unité'],
 'Cocaïne':['mg','g','unité'],
 'Speed':['mg','g','unité'],
 'LSD':['buvard(s)','goutte(s)','unité'],
 'Cannabis':['g','mg','unité'],
 'Champignons':['g','mg','unité'],
 'GHB / GBL':['mL','unité'],
 'Poppers':['inhalation(s)','unité'],
 'Autre':['unité','mg','g','mL']
};
const field=(name,label,value='',opts={})=>{
const type=opts.type||'text',full=opts.full?' full':'';
if(type==='select')return '<div class="field'+full+'"><label for="f-'+ESC(name)+'">'+ESC(label)+'</label><select id="f-'+ESC(name)+'" name="'+ESC(name)+'">'+opts.options.map(v=>'<option value="'+ESC(v)+'" '+(String(v)===String(value)?'selected':'')+'>'+ESC(v)+'</option>').join('')+'</select></div>';
if(type==='textarea')return '<div class="field'+full+'"><label for="f-'+ESC(name)+'">'+ESC(label)+'</label><textarea id="f-'+ESC(name)+'" name="'+ESC(name)+'">'+ESC(value)+'</textarea></div>';
return '<div class="field'+full+'"><label for="f-'+ESC(name)+'">'+ESC(label)+'</label><input id="f-'+ESC(name)+'" name="'+ESC(name)+'" type="'+ESC(type)+'" value="'+ESC(value)+'" '+(opts.min!=null?'min="'+ESC(opts.min)+'" ':'')+' '+(opts.step?'step="'+ESC(opts.step)+'"':'')+'></div>';
};
const fraction=(n,v)=>field('part_'+n,n==='quart'?'Quarts':n==='demi'?'Demis':'Entiers',v,{type:'number',min:0,step:1});
function entryHtml(existing,event){
const e=existing||{},name=e.name||'Bière',parts=e.parts||{},range=e.timeMode||'exact';
const drug=e.category==='Autres substances'||(!['Bière','Vin','Vodka','Whisky'].includes(name));
const portions=Array.isArray(e.portions)?e.portions:[];
const options=choices.includes(name)?choices:[name,...choices];
const namePicker=field('name','Produit ou boisson',name,{type:'select',full:true,options});
const custom=field('customName','Nom de la substance',!choices.includes(name)?name:'',{full:true});
const timeOptions=[['exact','🕒 Exacte'],['between','↔ Entre deux heures'],['all','🌙 Pendant la soirée']];
return '<section class="editor-group"><p class="eyebrow">QU’AS-TU CONSOMMÉ ?</p>'+
namePicker+'<div id="custom-name-wrap" style="display:none">'+custom+'</div></section>'+
'<section class="editor-group"><p class="eyebrow">QUANTITÉ</p><div class="fields">'+
field('quantity','Nombre de prises / verres',e.quantity||1,{type:'number',min:0,step:1})+
field('unit','Unité',e.unit||'prise',{type:'select',options:['verre','prise','unité','morceaux','mg','g','mL']})+'</div>'+
'<div id="ecstasy-panel" class="editor-subsection"><h3>Fractions d’ecstasy</h3><p class="muted small">Indique les morceaux consignés, sans supposer leur quantité de substance active.</p><div class="fields">'+
fraction('quart',parts.quart||0)+fraction('demi',parts.demi||0)+fraction('entier',parts.entier||0)+'</div></div>'+
'<div id="substance-panel" class="editor-subsection"><div class="fields">'+
field('measuredAmount','Quantité déclarée (optionnel)',e.measuredAmount||'',{type:'number',min:0,step:'.001'})+
field('measuredUnit','Unité déclarée',e.measuredUnit||'mg',{type:'select',options:['mg','g','mL','unité','buvard(s)','goutte(s)','inhalation(s)']})+
field('subjectiveSize','Taille apparente',e.subjectiveSize||'',{type:'select',options:['','Petit','Moyen','Grand']})+
field('route','Mode de prise',e.route||'',{type:'select',options:['',...new Set(Object.values(routeMap).flat())]})+
field('routeOther','Préciser le mode (si autre)',e.routeOther||'',{full:true})+
'</div><div class="editor-subsection" style="margin-top:12px"><h3>Prises détaillées</h3><p class="muted small">Pour noter différentes tailles ou différents modes dans une même soirée.</p>'+
'<div id="portions-list"></div><button type="button" class="btn tiny" id="add-portion">＋ Ajouter une prise détaillée</button></div></div></section>'+
'<section class="editor-group"><p class="eyebrow">QUAND ?</p><div class="segmented" id="time-modes">'+timeOptions.map(([k,label])=>
 '<label class="seg-option"><input type="radio" name="timeMode" value="'+k+'" '+(range===k?'checked':'')+'><span>'+label+'</span></label>').join('')+'</div>'+
'<div class="fields" style="margin-top:14px">'+field('date','Date',e.date||event.date||new Date().toISOString().slice(0,10),{type:'date'})+
'<div id="time-start">'+field('time','Heure / début',e.time||'',{type:'time'})+'</div>'+
'<div id="time-end">'+field('timeEnd','Fin de l’intervalle',e.timeEnd||'',{type:'time'})+'</div></div></section>'+
'<section class="editor-group"><p class="eyebrow">OPTIONS PERSONNELLES</p><div class="fields">'+
field('cost','Coût total (€)',e.cost||'',{type:'number',min:0,step:'.01'})+
field('notes','Ma note personnelle',e.notes||'',{type:'textarea',full:true})+'</div></section>'+
'<p class="muted small">Les tailles sont subjectives. L’application ne calcule pas la teneur, la toxicité ni une dose sans risque.</p>'+
'<div class="form-actions"><button type="button" class="btn ghost" data-action="close-modal">Annuler</button>'+
'<button type="submit" class="btn primary">'+(existing?'Enregistrer les modifications':'Enregistrer la prise')+'</button></div>'+
(existing?'<div style="margin-top:12px"><button type="button" class="btn danger block" data-action="delete-entry" data-id="'+ESC(e.id)+'">Supprimer cette prise</button></div>':'');
}
function portionLine(p={}){
const row=document.createElement('div');
row.className='portion-row';
row.innerHTML='<div class="fields">'+field('portionCount','Nombre',p.count||1,{type:'number',min:1,step:1})+
 field('portionSize','Taille apparente',p.size||'',{type:'select',options:['','Petit','Moyen','Grand']})+
 field('portionRoute','Mode de prise',p.route||'',{type:'select',options:['',...new Set(Object.values(routeMap).flat())]})+
 field('portionAmount','Quantité observée',p.measuredAmount||'',{type:'number',min:0,step:'.001'})+
 field('portionUnit','Unité',p.measuredUnit||'mg',{type:'select',options:['mg','g','mL','unité','buvard(s)','goutte(s)','inhalation(s)']})+'</div>'+
 '<button type="button" class="btn tiny danger remove-portion">Retirer cette prise</button>';
 row.querySelector('.remove-portion').addEventListener('click',()=>row.remove());
 return row;
}
function setupEntry(form,existing){
const e=existing||{};
const select=form.elements.namedItem('name');
const custom=form.querySelector('#custom-name-wrap');
const substance=form.querySelector('#substance-panel');
const ecstasy=form.querySelector('#ecstasy-panel');
const start=form.querySelector('#time-start');
const end=form.querySelector('#time-end');
const route=form.elements.namedItem('route');
const unit=form.elements.namedItem('measuredUnit');
const portions=form.querySelector('#portions-list');
function configure(){
 const name=select.value,drug=!['Bière','Vin','Vodka','Whisky'].includes(name);
 custom.style.display=name==='Autre'?'block':'none';
 substance.style.display=drug&&name!=='Ecstasy'?'block':'none';
 ecstasy.style.display=name==='Ecstasy'?'block':'none';
 const allowed=routeMap[name]||routeMap.Autre;
 if(route){const prev=route.value;route.replaceChildren(...['',...allowed].map(v=>{
  const opt=document.createElement('option');opt.value=v;opt.textContent=v;return opt;
 }));route.value=allowed.includes(prev)?prev:'';}
 const units=unitMap[name]||unitMap.Autre;
 if(unit){const prev=unit.value;unit.replaceChildren(...units.map(v=>{
  const opt=document.createElement('option');opt.value=v;opt.textContent=v;return opt;
 }));unit.value=units.includes(prev)?prev:units[0];}
 }
 function mode(){
  const radio=form.querySelector('[name=timeMode]:checked');
  const val=radio?radio.value:'exact';
  start.style.display=val==='all'?'none':'block';
  end.style.display=val==='between'?'block':'none';
 }
 select.addEventListener('change',configure);
 form.querySelectorAll('[name=timeMode]').forEach(r=>r.addEventListener('change',mode));
 const add=form.querySelector('#add-portion');
 add.addEventListener('click',()=>portions.appendChild(portionLine()));
 (Array.isArray(e.portions)?e.portions:[]).forEach(p=>portions.appendChild(portionLine(p)));
 configure();
 if(route&&e.route)route.value=e.route;
 if(unit&&e.measuredUnit)unit.value=e.measuredUnit;
 mode();
}
function extractEntry(form,existing,eventId){
const e={...(existing||{})};
const named=n=>form.elements.namedItem(n);
const val=n=>named(n)?String(named(n).value||'').trim():'';
let name=val('name');
if(name==='Autre')name=val('customName');
if(!name)throw new Error('Indique le nom du produit.');
e.id=e.id||undefined;
e.eventId=eventId;
e.name=name;
e.category=['Bière','Vin','Vodka','Whisky'].includes(name)?'Alcool':'Autres substances';
e.date=val('date');
if(!/^\d{4}-\d\d-\d\d$/.test(e.date))throw new Error('Date incorrecte.');
e.timeMode=val('timeMode')||'exact';
e.allNight=e.timeMode==='all';
e.time=e.allNight?'':val('time');
e.timeEnd=e.timeMode==='between'?val('timeEnd'):'';
if(!e.allNight&&!e.time)throw new Error('Indique une heure ou choisis « Pendant la soirée ».');
if(e.timeMode==='between'&&!e.timeEnd)throw new Error('Indique l’heure de fin.');
e.cost=val('cost')?Number(val('cost')):0;
if(!Number.isFinite(e.cost)||e.cost<0)throw new Error('Coût incorrect.');
e.notes=val('notes');
const qty=Number(val('quantity'));
if(!Number.isInteger(qty)||qty<0||qty>9999)throw new Error('Quantité incorrecte.');
e.quantity=qty;e.unit=val('unit')||'prise';
if(name==='Ecstasy'){
 const parts={};
 for(const key of ['quart','demi','entier']){
  parts[key]=Number(val('part_'+key))||0;
  if(!Number.isInteger(parts[key])||parts[key]<0||parts[key]>9999)
   throw new Error('Nombre de morceaux incorrect.');
 }
 const total=parts.quart+parts.demi+parts.entier;
 if(total===0)throw new Error('Ajoute au moins un morceau (quart, demi ou entier).');
 e.parts=parts;e.quantity=total;e.unit='morceaux';
 delete e.portions;delete e.measuredAmount;delete e.measuredUnit;delete e.subjectiveSize;
 e.route=val('route')||'Orale';
}else if(e.category==='Autres substances'){
 delete e.parts;
 e.route=val('route');
 e.routeOther=val('routeOther');
 e.subjectiveSize=val('subjectiveSize');
 const measured=val('measuredAmount');
 if(measured&&!Number.isFinite(Number(measured)))throw new Error('Quantité déclarée invalide.');
 e.measuredAmount=measured;e.measuredUnit=val('measuredUnit');
 const rows=[...form.querySelectorAll('.portion-row')];
 e.portions=rows.map(row=>{
  const get=x=>String(row.querySelector('[name="'+x+'"]')?.value||'').trim();
  const count=Number(get('portionCount'))||0;
  if(!Number.isInteger(count)||count<1)throw new Error('Nombre de prises détaillées incorrect.');
  return {count,size:get('portionSize'),route:get('portionRoute'),measuredAmount:get('portionAmount'),measuredUnit:get('portionUnit')};
 });
 if(e.portions.length)e.quantity=e.portions.reduce((n,p)=>n+p.count,0);
 else if(e.quantity>0)e.portions=[{count:e.quantity,size:e.subjectiveSize,route:e.route,measuredAmount:e.measuredAmount,measuredUnit:e.measuredUnit}];
 if(e.quantity<1)throw new Error('Ajoute au moins une prise.');
 e.unit='prise';
}else{
 delete e.parts;delete e.portions;delete e.route;delete e.routeOther;
 delete e.subjectiveSize;delete e.measuredAmount;delete e.measuredUnit;
 if(e.quantity<1)throw new Error('Indique au moins un verre.');
 e.unit='verre';
}
return e;
}
function count(e){
 if(e.parts)return ['quart','demi','entier'].reduce((n,k)=>n+(Number(e.parts[k])||0),0);
 if(Array.isArray(e.portions)&&e.portions.length)
 return e.portions.reduce((n,p)=>n+(Number(p.count)||0),0);
 return Number(e.quantity)||0;
}
function risk(entry,all){
const w=[],date=entry.date, substance=String(entry.name||'').toLowerCase();
if(!date)return w;
const same=all.filter(e=>e.date===date);
const has=p=>same.some(e=>p.test(String(e.name||'').toLowerCase()));
const alcohol=e=>e.category==='Alcool';
const contains=regex=>regex.test(substance);
const daily=same.filter(alcohol).reduce((n,e)=>n+count(e),0);
if(alcohol(entry)&&daily>2){
 w.push({severity:daily>=10?2:1,
 title:'Alcool : '+daily+' verres notés le '+date,
 detail:'Santé publique France recommande de ne pas dépasser 2 verres standards par jour, 10 par semaine, et de prévoir des jours sans alcool. Le nombre saisi ne prouve pas la quantité de verres standards.',link:href.alcohol});
}
if(alcohol(entry)){
 const now=new Date(date+'T12:00:00');
 const monday=new Date(now);monday.setDate(now.getDate()-(now.getDay()+6)%7);
 const d0=monday.getTime();
 const weekly=all.filter(e=>{
  if(!alcohol(e)||!e.date)return false;
  const t=new Date(e.date+'T12:00:00').getTime();return t>=d0&&t<d0+7*864e5;
 }).reduce((n,e)=>n+count(e),0);
 if(weekly>10)w.push({severity:1,title:'Alcool : '+weekly+' verres notés cette semaine',detail:'Au-dessus du repère hebdomadaire de 10 verres standards. Les volumes et degrés réels ne sont pas connus.',link:href.alcohol});
}
if((alcohol(entry)&&has(/ghb|gbl/))||(contains(/ghb|gbl/)&&same.some(alcohol))){
 w.push({severity:2,title:'Alcool et GHB / GBL le même jour',detail:'Mélange particulièrement dangereux : perte de connaissance et dépression respiratoire. Les heures exactes et les chevauchements d’effets sont inconnus.',link:href.ghb});
}
if((alcohol(entry)&&has(/k[ée]tamine/))||(contains(/k[ée]tamine/)&&same.some(alcohol))){
 w.push({severity:2,title:'Alcool et kétamine le même jour',detail:'Association déconseillée : sédation, perte de connaissance et accidents possibles. Le chevauchement des effets n’est pas établi.',link:href.ketamine});
}
if((alcohol(entry)&&has(/coca[iï]ne/))||(contains(/coca[iï]ne/)&&same.some(alcohol))){
 w.push({severity:2,title:'Alcool et cocaïne le même jour',detail:'La combinaison peut former du cocaéthylène, augmentant notamment les risques cardiovasculaires.',link:href.cocaine});
}
if((alcohol(entry)&&has(/mdma|ecstasy/))||(contains(/mdma|ecstasy/)&&same.some(alcohol))){
 w.push({severity:1,title:'Alcool et MDMA / ecstasy le même jour',detail:'Association déconseillée, notamment en raison des risques de surchauffe et de déshydratation.',link:href.mdma});
}
if(contains(/mdma|ecstasy/)){
 const at=new Date(date+'T12:00:00').getTime();
 for(const other of all){
  if(String(entry.id)===String(other.id)||!/mdma|ecstasy/i.test(other.name||'')||!other.date)continue;
  const diff=Math.abs((new Date(other.date+'T12:00:00').getTime()-at)/864e5);
  if(Number.isFinite(diff)&&diff>0&&diff<42){
   w.push({severity:1,title:'MDMA / ecstasy : prises rapprochées sur '+Math.round(diff)+' jours',
    detail:'Les recommandations de réduction des risques préconisent un espacement important entre les consommations. Un intervalle d’environ six semaines est souvent conseillé ; il n’existe pas de délai garantissant l’absence de risques.',link:href.mdma});
   break;
  }
 }
}
return w;
}
function warnings(entries,all){
 const result=[],seen=new Set();
 for(const e of entries)
 for(const w of risk(e,all)){
  const key=w.title;
  if(seen.has(key))continue;
  seen.add(key);result.push(w);
 }
 return result;
}
function warningMarkup(warnings){
 return warnings.map(w=>'<div class="notice '+(w.severity===2?'high':'')+'"><strong>⚠ '+ESC(w.title)+'</strong><p>'+ESC(w.detail)+'</p><a href="'+ESC(w.link)+'" target="_blank" rel="noopener noreferrer">Source et prévention ↗</a></div>').join('');
}
window.NoctariaParity={entryHtml,setupEntry,extractEntry,risk,warnings,warningMarkup,count};
})();
