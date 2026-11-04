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
assert.ok(result.shotgunAbout.includes('Le festival rassemble plusieurs scènes'),
 'event À propos must come from the actual Shotgun section');
assert.ok(result.shotgunAbout.includes('Ouverture des portes'),
 'all paragraphs from the source À propos must be retained');
assert.equal(result.description,'Un court résumé marketing.',
 'social meta is not the source of the displayed À propos section');
assert.equal(result.artistProfiles?.[0]?.name,'Perceval');
assert.equal(result.artistProfiles?.[0]?.previewUrl,music,
 'only an explicit real official track is retained');
const none=inspect(html.replaceAll(music,'https://example.com/not-music'));
assert.ok(!none.artistProfiles?.[0]?.previewUrl,
 'no invented previews are created without official playable link');
const inline=inspect('<!doctype html><meta property="og:description" content="Meta pas fiable">'+
 '<main><h1>Soirée</h1><section><div><span>À propos</span>'+
 '<div data-testid="event-description"><p>Vraie description complète provenant de Shotgun.</p>'+
 '<p>Deuxième paragraphe conservé.</p></div></div></section>'+
 '<h2>Line up</h2><div>DJ Test</div></main>');
assert.ok(inline.shotgunAbout.includes('Vraie description complète'),
 'find À propos even when React renders a span instead of a heading');
assert.ok(inline.shotgunAbout.includes('Deuxième paragraphe'),
 'retain multiple paragraphs of a React description');
assert.ok(!inline.shotgunAbout.includes('Line up'),
 'never copy unrelated sections into the event À propos');
const missing=inspect('<!doctype html><meta property="og:description" content="SEO sans À propos">'+
 '<main><h1>Ma soirée</h1><h2>Infos pratiques</h2><p>Adresse</p></main>'+
 '<footer><h2>À propos</h2><p>À propos du site</p></footer>');
assert.equal(missing.shotgunAbout,undefined,
 'never replace event À propos with SEO, practical info or site footer');
const short=inspect('<!doctype html><main><h1>Soirée</h1>'+
 '<h2>À propos</h2><p>Entrée gratuite.</p><h2>Line up</h2></main>');
assert.equal(short.shotgunAbout,'Entrée gratuite.',
 'short genuine Shotgun À propos is valid even under 20 characters');
const main=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/MainActivity.java'),'utf8');
assert.ok(main.includes('isOfficialArtistPreview(preview)'),'only safe music pages shown');
assert.ok(main.includes('private void showAllArtists()'),'all artist directory exists');
assert.ok(main.includes('artist.optString("spotifyPhoto")'),'artist photo included');
const svc=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/ShotgunSyncService.java'),'utf8');
assert.ok(svc.includes('private int artistEventsComplete'),'artist enrichment belongs to sync');
assert.ok(svc.includes('artistEventsComplete!=queue.size()'),
 'partial artist sync cannot report 100%');
const android=readFileSync(path.join(root,
 'app/src/main/java/fr/afterlog/app/MainActivity.java'),'utf8');
assert.ok(svc.includes('queue.size()+1'),
 '9 selected events mean 10 progress steps, including the final artist stage');
assert.ok(svc.includes('artistSnapshots.add(')&&svc.includes('startArtistStage();'),
 'all selected events are imported before any Spotify/SoundCloud work starts');
assert.ok(svc.includes('SoundCloudArtistFinder.find(')&&
 svc.includes('OfficialMusicPreview.firstSpotifyTrack('),
 'genuine SoundCloud and Spotify music lookups are attempted');
assert.ok(android.includes('SyncState.IDLE.equals(mode)||SyncState.DONE.equals(mode)'),
 'successful synchronization hides the banner automatically');
assert.ok(android.includes('button("Masquer"'),
 'failed or partial syncs offer a manual hide control');
assert.ok(android.includes('Temps total restant estimé'),
 'ETA includes the full final artists stage, not only current event');
assert.ok(android.includes('frenchDate(e.optString("date"))'),
 'home event dates use French long display, not ISO');

console.log('NØCTARIA event extraction OK: full À propos, explicit official preview, artist directory and strict completion');
