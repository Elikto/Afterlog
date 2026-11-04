const CACHE='noctaria-pwa-v11';
const BASE=new URL('./',self.location.href).href;
const ASSETS=['./','./index.html','./styles.css','./app.js','./parity.js','./places.js','./tracking.js','./spotify-trusted.js','./spotify.js','./backup.js','./manifest.webmanifest','./icon.svg','./icons/icon-192.png','./icons/icon-512.png','./icons/apple-touch-icon.png'];
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)));
  self.skipWaiting();
});
self.addEventListener('activate',event=>{
  event.waitUntil(Promise.all([
    caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))),
    self.clients.claim()
  ]));
});
self.addEventListener('fetch',event=>{
  const req=event.request;
  if(req.method!=='GET')return;
  const url=new URL(req.url);
  if(url.origin!==self.location.origin||!url.href.startsWith(BASE))return;
  if(req.mode==='navigate'){
    event.respondWith(fetch(req).then(resp=>{
      if(resp.ok)caches.open(CACHE).then(c=>c.put('./index.html',resp.clone()));
      return resp;
    }).catch(()=>caches.match('./index.html')));
    return;
  }
  event.respondWith(caches.match(req).then(found=>found||fetch(req).then(resp=>{
    if(resp.ok)caches.open(CACHE).then(c=>c.put(req,resp.clone()));
    return resp;
  })));
});