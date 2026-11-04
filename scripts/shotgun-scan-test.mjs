import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const src=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/ShotgunTicketDiscovery.java'),'utf8');
const urls=[
 'https://shotgun.live/fr/account/tickets',
 'https://shotgun.live/fr/account/tickets/pending',
 'https://shotgun.live/fr/account/tickets/past'
];
for(const url of urls)assert.ok(src.includes('"'+url+'"'),'Android visits '+url);
const match=src.match(/static final String SCAN_JS = """([\s\S]*?)""";/);
assert.ok(match,'Android ticket extractor present');
// Convert Java text-block escaped backslashes to JavaScript regex backslashes.
const js=match[1].replace(/\\\\/g,'\\').trim();
function scan(url,html){
 const dom=new JSDOM(html,{url,runScripts:'outside-only'});
 dom.window.scrollTo=()=>{};
 const first=JSON.parse(dom.window.eval(js));
 assert.equal(first.action,'scroll','scroll ticket lists before collection');
 const second=JSON.parse(dom.window.eval(js));
 dom.window.close();
 return second;
}
const sample=`<!doctype html><main>
<a href="/fr/account/tickets/past">Passés</a>
<a href="/fr/account/tickets/pending">À venir</a>
<article><a href="/fr/events/test-festival"><h3>Festival Test</h3>
<img src="https://example.org/affiche.jpg" alt="Festival Test">
<time datetime="2026-11-15T23:00:00">15 novembre</time></a></article>
<article><a href="/fr/account/tickets/abc12345"><h3>Ma soirée à Marseille</h3></a></article>
<div class="qr-code" data-ticket-secret="SECRET_QR_NOT_ALLOWED">SECRET_QR_NOT_ALLOWED</div>
</main>`;
for(const url of urls){
 const response=scan(url,sample);
 assert.equal(response.action,'ready');
 assert.equal(response.events.length,2);
 assert.ok(!JSON.stringify(response).includes('SECRET_QR_NOT_ALLOWED'),
   'no ticket or QR secret data in extracted event info');
 assert.ok(response.events.every(e=>!e.url.match(/\/tickets\/(?:past|pending)\/?$/)),
   'category links never become events');
}
const auth=new JSDOM('<input type="password">',{
 url:'https://shotgun.live/fr/account/tickets',runScripts:'outside-only'
});
assert.equal(JSON.parse(auth.window.eval(js)).action,'login');
auth.window.close();
console.log('Shotgun Android extractor OK: 3 categories, events, no nav links/QR data, login detection');
