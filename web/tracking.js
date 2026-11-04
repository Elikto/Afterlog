/* NØCTARIA tracking view with Android-style day/week/month/year history. */
(()=>{'use strict';
const state={mode:1,cursor:new Date()};
const fmtDate=d=>new Date(d).toLocaleDateString('fr-FR',{day:'numeric',month:'short',year:'numeric'});
const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const clone=d=>new Date(d.getFullYear(),d.getMonth(),d.getDate(),12);
const at=s=>new Date(String(s)+'T12:00:00');
function period(){
 const now=clone(state.cursor),start=clone(now);
 if(state.mode===1){start.setDate(start.getDate()-(start.getDay()+6)%7);}
 else if(state.mode===2){start.setDate(1);}
 else if(state.mode===3){start.setMonth(0,1);}
 const end=clone(start);
 if(state.mode===0)end.setDate(end.getDate()+1);
 if(state.mode===1)end.setDate(end.getDate()+7);
 if(state.mode===2)end.setMonth(end.getMonth()+1);
 if(state.mode===3)end.setFullYear(end.getFullYear()+1);
 return {start,end};
}
function move(delta){
 const d=clone(state.cursor);
 if(state.mode===0)d.setDate(d.getDate()+delta);
 else if(state.mode===1)d.setDate(d.getDate()+7*delta);
 else if(state.mode===2)d.setMonth(d.getMonth()+delta);
 else d.setFullYear(d.getFullYear()+delta);
 state.cursor=d;
}
function mode(index){
 const n=Number(index);
 if(Number.isInteger(n)&&n>=0&&n<=3)state.mode=n;
 state.cursor=new Date();
}
function getBuckets(start,end){
 const out=[];
 if(state.mode===0){
  for(let h=0;h<24;h+=4)out.push({label:String(h).padStart(2,'0')+'–'+String(h+4).padStart(2,'0')+' h',start:new Date(start.getFullYear(),start.getMonth(),start.getDate(),h),end:new Date(start.getFullYear(),start.getMonth(),start.getDate(),h+4)});
 }else{
  let cursor=clone(start);
  while(cursor<end){
   const until=clone(cursor);
   if(state.mode===1)until.setDate(until.getDate()+1);
   if(state.mode===2)until.setDate(until.getDate()+7);
   if(state.mode===3)until.setMonth(until.getMonth()+1);
   const stop=until>end?end:until;
   let label=state.mode===1?cursor.toLocaleDateString('fr-FR',{weekday:'short',day:'numeric'}):
     state.mode===2?'Du '+cursor.getDate()+' au '+new Date(stop-864e5).getDate():
     cursor.toLocaleDateString('fr-FR',{month:'short'});
   out.push({label,start:clone(cursor),end:clone(stop)});
   cursor=until;
  }
 }
 return out;
}
function plot(buckets,series,colors){
const n=buckets.length;
if(n<1||series.size===0)return '<div class="empty"><p class="muted">Aucune donnée pour ce créneau.</p></div>';
const left=32,right=570,top=14,bottom=195,plotH=bottom-top,range=right-left;
const max=Math.max(1,...[...series.values()].flat());
const point=(i,v)=>[left+(n===1?range/2:range*i/(n-1)),bottom-v/max*plotH];
let svg='<svg viewBox="0 0 600 250" role="img" aria-label="Évolution des prises enregistrées par produit" style="display:block;width:100%;height:auto">';
for(let i=0;i<=4;i++){
 const y=bottom-i*plotH/4;
 svg+='<line x1="'+left+'" y1="'+y+'" x2="'+right+'" y2="'+y+'" stroke="#504b64" stroke-dasharray="3 5" opacity=".65"/>';
 svg+='<text x="17" y="'+(y+4)+'" fill="#aaa3b9" font-size="12" text-anchor="end">'+Math.round(i*max/4)+'</text>';
}
for(const [key,values] of series){
 const color=colors.get(key);
 const coords=values.map((v,i)=>point(i,v));
 svg+='<path d="'+coords.map(([x,y],i)=>(i?'L':'M')+x.toFixed(1)+' '+y.toFixed(1)).join(' ')+'" fill="none" stroke="'+color+'" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>';
 for(const [x,y] of coords)svg+='<circle cx="'+x.toFixed(1)+'" cy="'+y.toFixed(1)+'" r="3.8" fill="'+color+'" stroke="#171b2f" stroke-width="1.5"/>';
}
for(let i=0;i<n;i++){
 if(n>13&&i%2!==0)continue;
 const x=left+(n===1?range/2:range*i/(n-1));
 svg+='<text x="'+x+'" y="219" text-anchor="middle" fill="#c6bfdb" font-size="'+(n>13?9:11)+'">'+esc(buckets[i].label)+'</text>';
}
return svg+'</svg>';
}
function draw(all,events,color){
const {start,end}=period(),entries=all.filter(e=>e.date&&at(e.date)>=start&&at(e.date)<end);
const startLabel=fmtDate(start),endLabel=fmtDate(new Date(end.getTime()-864e5));
const buckets=getBuckets(start,end);
const series=new Map(),colors=new Map(),shown=new Map();
for(const entry of entries){
 const name=String(entry.name||'Autre'),key=String(entry.category||'')+'|'+name;
 if(!series.has(key)){series.set(key,Array(buckets.length).fill(0));colors.set(key,color(entry));shown.set(key,name);}
 let t;
 if(state.mode===0){
  if(entry.allNight||entry.timeMode==='all'||!/^\d\d:\d\d$/.test(entry.time||''))continue;
  const h=Number(entry.time.slice(0,2));
  t=new Date(start.getFullYear(),start.getMonth(),start.getDate(),h);
 }else t=at(entry.date);
 const index=buckets.findIndex(b=>t>=b.start&&t<b.end);
 if(index>=0)series.get(key)[index]++;
}
const totals=new Map();
for(const e of entries){const name=e.name||'Autre';totals.set(name,(totals.get(name)||0)+1);}
const maximum=Math.max(1,...totals.values()),warnings=window.NoctariaParity?.warnings(entries,all)||[];
const totalCost=entries.reduce((n,e)=>n+(Number(e.cost)||0),0);
return '<div class="page-head"><div><p class="eyebrow">MES EXPÉRIENCES</p><h1>Mon suivi</h1><p class="kicker">Vue par date et par produit, comme sur Android.</p></div></div>'+
'<div class="segmented" id="tracking-periods" style="grid-template-columns:repeat(4,1fr)">'+
 ['Jour','Semaine','Mois','Année'].map((name,i)=>'<button class="btn tiny '+(i===state.mode?'primary':'')+'" data-action="tracking-period" data-mode="'+i+'">'+name+'</button>').join('')+
'</div><div class="button-row" style="justify-content:space-between;align-items:center;margin:14px 0 20px">'+
'<button class="btn tiny" data-action="tracking-prev">‹ Précédent</button><strong style="font-size:13px;text-align:center">'+esc(startLabel)+(startLabel!==endLabel?' – '+esc(endLabel):'')+'</strong>'+
'<button class="btn tiny" data-action="tracking-next">Suivant ›</button></div>'+
'<div class="stats"><div class="stat"><strong>'+entries.length+'</strong><span>prises notées</span></div><div class="stat"><strong>'+new Set(entries.map(e=>String(e.eventId))).size+'</strong><span>soirées</span></div><div class="stat"><strong>'+totalCost.toLocaleString('fr-FR',{maximumFractionDigits:2})+' €</strong><span>coût déclaré</span></div></div>'+
'<section class="card"><h2>Évolution des prises</h2><p class="muted small" style="margin:7px 0 15px">Chaque produit conserve sa couleur. Les prises sans heure précise sont exclues de la vue horaire.</p>'+
plot(buckets,series,colors)+
'<div class="button-row" style="margin-top:11px">'+[...shown].map(([key,name])=>'<span class="pill" style="color:'+colors.get(key)+';border-color:'+colors.get(key)+'">● '+esc(name)+'</span>').join('')+'</div></section>'+
'<section class="card"><h2>Produits consignés</h2>'+
 ([...totals].length?[...totals].sort((a,b)=>b[1]-a[1]).map(([name,total])=>{
 const item=entries.find(e=>e.name===name),shade=color(item);
 return '<div class="tracking-bar" style="--tone:'+shade+'"><span>'+esc(name)+'</span><div class="tracking-track"><div class="tracking-fill" style="width:'+(100*total/maximum)+'%"></div></div><b>'+total+'</b></div>';
 }).join(''):'<p class="muted" style="margin-top:12px">Aucune prise sur la période.</p>')+'</section>'+
(warnings.length?'<section class="section"><h2>⚠ Vigilances</h2><div style="margin-top:12px">'+window.NoctariaParity.warningMarkup(warnings)+'</div></section>':'')+
'<div class="card"><p class="muted small">Ce tableau résume ce que tu as déclaré. Il ne détermine pas la teneur réelle ni une dose sans danger.</p><div style="margin-top:12px"><button class="btn block" data-action="rdr">Réduction des risques et contacts utiles</button></div></div>';
}
window.NoctariaTracking={draw,move,mode,period};
})();