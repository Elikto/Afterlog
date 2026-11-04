import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const java=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/ShotgunBrowserActivity.java'),'utf8');
const block=java.match(/static final String CURRENT_EVENT_JS = """([\s\S]*?)""";/);
assert.ok(block,'Shotgun event extraction script exists');
const eventJs=block[1].replace(/\\\\/g,'\\').trim();
function inspect(html){
 const dom=new JSDOM(html,{url:'https://shotgun.live/fr/events/une-rave-test',runScripts:'outside-only'});
 const output=JSON.parse(dom.window.eval(eventJs));
 dom.window.close();
 return output;
}
const id='3'+'F'.repeat(21);
const music='https://open.spotify.com/track/'+id;
const html=[
 '<!doctype html><meta property="og:title" content="Soirée du test | Shotgun">',
 '<meta property="og:description" content="Un court résumé marketing.">',
 '<main><h1>Soirée du test</h1>',
 '<h2>À propos</h2><section class="about-text">',
 '<p>Le festival rassemble plusieurs scènes de musique électronique.</p>',
 '<p>Ouverture des portes à 20 heures et fin au petit matin.</p></section>',
 '<h2>Line-up</h2><div><article><h3>Perceval</h3>',
 '<img src="https://example.org/artist-photo.jpg" alt="Perceval">',
 '<a href="'+music+'">Écouter un morceau officiel</a>',
 '</article></div></main>',
 '<script type="application/ld+json">',
 '{"@type":"MusicEvent","name":"Soirée du test",',
 '"performer":[{"@type":"MusicGroup","name":"Perceval"}]}',
 '</script>'
].join('\n');
const result=inspect(html);
assert.ok(result.description.includes('Le festival rassemble plusieurs scènes'),
 'event À propos replaces short social metadata');
assert.ok(result.description.includes('Ouverture des portes'),
 'full About includes the practical second paragraph');
assert.equal(result.shotgunAbout,result.description,
 'Shotgun About is preserved without erasing user-edited notes on older events');
assert.equal(result.artistProfiles?.[0]?.name,'Perceval');
assert.equal(result.artistProfiles?.[0]?.previewUrl,music,
 'only an explicit real official track is retained');
const none=inspect(html.replaceAll(music,'https://example.com/not-music'));
assert.ok(!none.artistProfiles?.[0]?.previewUrl,
 'no invented previews are created without official playable link');
const main=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/MainActivity.java'),'utf8');
assert.ok(main.includes('isOfficialArtistPreview(preview)'),'only safe music pages shown');
assert.ok(main.includes('private void showAllArtists()'),'all artist directory exists');
assert.ok(main.includes('artist.optString("spotifyPhoto")'),'artist photo included');
const svc=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/ShotgunSyncService.java'),'utf8');
assert.ok(svc.includes('private int artistEventsComplete'),'artist enrichment belongs to sync');
assert.ok(svc.includes('artistEventsComplete!=queue.size()'),
 'partial artist sync cannot report 100%');
console.log('NØCTARIA event extraction OK: full À propos, explicit official preview, artist directory and strict completion');
