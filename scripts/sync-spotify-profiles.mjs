#!/usr/bin/env node
// Android's explicitly confirmed Spotify artist map is the source of truth.
// Keep the PWA in lockstep with Android without introducing false same-name links.
import {readFileSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const src=readFileSync(path.join(root,'app/src/main/java/fr/afterlog/app/SpotifyArtistFinder.java'),'utf8');
const rows=[...src.matchAll(/case "([^"]+)":return "(https:\/\/open\.spotify\.com\/artist\/[A-Za-z0-9]{22})";/g)];
if(rows.length<10)throw Error('Android verified artist source unexpectedly empty');
const dictionary=Object.fromEntries(rows.map(m=>[m[1],m[2]]));
if(Object.keys(dictionary).length!==rows.length)throw Error('Duplicate artist names in Android Spotify table');
const output='/* Generated from Android SpotifyArtistFinder.java — DO NOT EDIT. */\n'+
 'window.NoctariaTrustedSpotify=Object.freeze('+JSON.stringify(dictionary,null,2)+');\n';
const outputFile=path.join(root,'web/spotify-trusted.js');
if(process.argv.includes('--check')){
 if(readFileSync(outputFile,'utf8')!==output)throw Error('Spotify trusted list differs from Android: run node scripts/sync-spotify-profiles.mjs');
 console.log('NØCTARIA Spotify parity OK: '+rows.length+' known profiles on both platforms');
}else{
 writeFileSync(outputFile,output);
 console.log('Generated '+rows.length+' shared artist mappings');
}
