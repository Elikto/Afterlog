package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/** Résolution prudente du véritable profil Spotify, sans deviner un ID. */
final class SpotifyArtistFinder {
    private static long lastMusicBrainzCall=0L;
    private static final String PREFS="afterlog_artist_lookup_v2";

    // Profils exacts croisés avec la discographie publique Spotify.
    // Pas de génération d'ID à partir du nom (source d'erreurs d'homonymie).
    static String trustedProfile(String artist){
        switch(normalize(artist)){
            case "perceval":return "https://open.spotify.com/artist/3dJ5aHKA9m7zKAr0lX6rtb";
            case "acidpach":return "https://open.spotify.com/artist/0Z1E5Ebqj4e5CYElhs1lwC";
            case "lekkerfaces":return "https://open.spotify.com/artist/45Meh6B3L1TNh03On3y349";
            case "zapravka":return "https://open.spotify.com/artist/4oCaLmRVMM4Ck4VGrmh4VC";
            case "amygdala":return "https://open.spotify.com/artist/7BGTl5Swxc7pcXnSgqzSSM";
            case "a5km":return "https://open.spotify.com/artist/3SIWzGY4GAe5R18PTHOK1v";
            case "hortense de beauharnais":return "https://open.spotify.com/artist/6dwoHBXfXApma5YaoM2vE3";
            default:return "";
        }
    }
    static String savedProfile(Context context,String name){
        String profile=context.getSharedPreferences("afterlog_spotify_links",Context.MODE_PRIVATE)
            .getString(normalize(name),"");
        return SpotifyArtistVerifier.isArtistUrl(profile)?profile:"";
    }
    static void saveProfile(Context context,String name,String link){
        if(SpotifyArtistVerifier.isArtistUrl(link))
            context.getSharedPreferences("afterlog_spotify_links",Context.MODE_PRIVATE)
                .edit().putString(normalize(name),link).apply();
    }


    /**
     * Un candidat explicite ou une relation officielle MusicBrainz peut être
     * validé, mais seulement si nom ET vignette sont compatibles avec Shotgun.
     * Sinon renvoie la chaîne vide et l'interface reste sur "Recherche Spotify".
     */
    static String resolve(Context ctx,String artistName,String shotgunPortrait,String explicitLink){
        if(artistName==null||artistName.trim().isEmpty())return "";
        String local=savedProfile(ctx,artistName);
        if(!local.isEmpty())return local;
        String documented=trustedProfile(artistName);
        if(!documented.isEmpty())return documented;
        // Le profil officiel Shotgun peut pointer vers Spotify : vérifier le nom
        // affiché par Spotify, sans imposer que les deux portraits soient identiques.
        if(SpotifyArtistVerifier.isArtistUrl(explicitLink) &&
            SpotifyArtistVerifier.matchesName(explicitLink,artistName))
            return explicitLink;
        String cacheKey=normalize(artistName)+"|"+
            Integer.toHexString((shotgunPortrait==null?"":shotgunPortrait).hashCode());
        SharedPreferences cache=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        String answer=cache.getString(cacheKey,"");
        long stamp=cache.getLong(cacheKey+"_at",0);
        if(now-stamp<12L*60*60*1000)
            return SpotifyArtistVerifier.isArtistUrl(answer)?answer:"";
        String verified="";
        try {
            String canonical=normalize(artistName).equals("rdo")?"RDØ":artistName;
            String query="artist:\""+canonical.replace("\"","").trim()+"\"";
            String path="https://musicbrainz.org/ws/2/artist/?query="+
                URLEncoder.encode(query,"UTF-8")+"&fmt=json&limit=12";
            JSONObject response=new JSONObject(musicBrainzGet(path));
            JSONArray artists=response.optJSONArray("artists");
            java.util.ArrayList<String> mbids=new java.util.ArrayList<>();
            if(artists!=null)for(int i=0;i<artists.length();i++){
                JSONObject artist=artists.optJSONObject(i);
                if(artist==null)continue;
                if(!normalize(artistName).equals(normalize(artist.optString("name"))))continue;
                String mbid=artist.optString("id");
                if(mbid.matches("[a-f0-9-]{36}"))mbids.add(mbid);
            }
            if(!mbids.isEmpty()&&mbids.size()<=4){
                java.util.ArrayList<String> candidates=new java.util.ArrayList<>();
                for(String mbid:mbids){
                    JSONObject relations=new JSONObject(musicBrainzGet(
                        "https://musicbrainz.org/ws/2/artist/"+mbid+"?inc=url-rels&fmt=json"));
                    JSONArray urls=relations.optJSONArray("relations");
                    if(urls==null)continue;
                    for(int j=0;j<urls.length();j++){
                        JSONObject rel=urls.optJSONObject(j);
                        JSONObject urlObj=rel==null?null:rel.optJSONObject("url");
                        String candidate=urlObj==null?"":urlObj.optString("resource");
                        if(SpotifyArtistVerifier.isArtistUrl(candidate)&&
                            !candidates.contains(candidate)&&
                            SpotifyArtistVerifier.matchesName(candidate,artistName))
                            candidates.add(candidate);
                    }
                }
                if(candidates.size()==1){
                    // Une seule identité officielle liée depuis MusicBrainz,
                    // avec même nom confirmé par Spotify : profil direct.
                    verified=candidates.get(0);
                }else if(candidates.size()>1 && shotgunPortrait!=null &&
                    shotgunPortrait.startsWith("https://")){
                    // Si plusieurs homonymes, ne retenir que la photo vérifiée.
                    for(String candidate:candidates){
                        if(SpotifyArtistVerifier.verify(candidate,artistName,shotgunPortrait)){
                            if(!verified.isEmpty()){verified="";break;}
                            verified=candidate;
                        }
                    }
                }
            }
        }catch(Exception ignored){}
        cache.edit().putString(cacheKey,verified).putLong(cacheKey+"_at",now).apply();
        return verified;
    }
    private static synchronized String musicBrainzGet(String url)throws Exception{
        long wait=1150-(System.currentTimeMillis()-lastMusicBrainzCall);
        if(wait>0)Thread.sleep(wait);
        lastMusicBrainzCall=System.currentTimeMillis();
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        try {
            connection.setConnectTimeout(6500);connection.setReadTimeout(6500);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent",
                "AfterLog/0.14 (https://github.com/Elikto/Afterlog)");
            if(connection.getResponseCode()!=200)throw new Exception("MusicBrainz indisponible");
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    if(result.size()+n>600000)throw new Exception("Réponse trop volumineuse");
                    result.write(buffer,0,n);
                }
            }
            return result.toString("UTF-8");
        }finally{connection.disconnect();}
    }
    private static String normalize(String input){
        return Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT).replace("ø","o").replace("Ø","o"),
            Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim();
    }
}
