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

const browser=readFileSync(path.join(root,
 'app/src/main/java/fr/afterlog/app/ShotgunBrowserActivity.java'),'utf8');
// The personal Shotgun login only shows the official Shotgun page and a status.
// Manual import controls are still available in the separate manual-import flow.
assert.match(browser,/autoSyncMode=getIntent\(\).getBooleanExtra\("afterlog_auto_sync",false\)/);
for(const buttonSection of ['nav','actions','footer']){
 assert.match(browser,new RegExp('if\\(!autoSyncMode\\)\\s*\\{\\s*LinearLayout '+buttonSection+'\\s*='),
  buttonSection+' must only be constructed outside automatic login');
}
assert.match(browser,/if\(autoSyncMode\)\{finish\(\);return;\}/,
 'Android system back exits the login window, never navigates Shotgun');
assert.match(browser,/else if\(autoSyncMode\)\s*\{[\s\S]*?web\.loadUrl\(ShotgunTicketDiscovery\.ACCOUNT\)/,
 'personal login starts with only the official Shotgun account view');
assert.match(browser,/offerSyncChoice\(\)/,
 'the login callback hands off to silent discovery after authentication');
assert.match(browser,/web\.setVisibility\(View\.GONE\)/,
 'the Shotgun browser is hidden once the session is verified');
assert.match(browser,/startSilentSync\(true\)/,
 'all three ticket categories are found before showing the prechecked selection');
assert.match(browser,/ShotgunSyncService\.discoverAccount\(this,choose\)/,
 'authenticated user hands off silent navigation to background service');
assert.doesNotMatch(browser,/ShotgunSyncService\.importSelected\(this\)/,
 'login UI must not handle import events by itself');
const main=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/MainActivity.java'),'utf8');
assert.ok(main.includes('private void showTicketChoice()'),
 'user can choose which events to sync after silent discovery');
assert.match(main,/TicketSelectionStore\.chooseIndices\(this,selectedIds\)/,
 'per-event selection is saved atomically before background import');
assert.ok(main.includes('String poster=item.optString("poster")'),
 'prechecked event selection shows each available poster thumbnail');
assert.ok(main.includes('private void showAllArtists()'),
 'a single page lists all artists from every synced event');
const service=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/ShotgunSyncService.java'),'utf8');
assert.ok(service.includes('private void inspectTicketCategory(int nav)'),
 'event categories are inspected by the background service');
assert.ok(service.includes('private void nextTicketCategory(boolean successful)'));
assert.ok(service.includes('private void enrichArtistsDuringSync(JSONObject snapshot)'),
 'artist pages and Spotify accounts are resolved during Shotgun sync');
assert.ok(service.includes('artistsPending>0')&&service.includes('metadataDone'),
 'completion waits for in-flight artist enrichment');
assert.ok(service.includes('ShotgunTicketDiscovery.TICKET_PAGES[ticketPageIndex]'),
 'service reads tickets, pending and past without visible navigation');
assert.doesNotMatch(browser,/SyncState\.SELECTION/,
 'login itself cannot enter a ticket-selection state');

console.log('Shotgun Android extractor OK: 3 categories, events, no nav links/QR data, login detection');
