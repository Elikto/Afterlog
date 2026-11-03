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
    private static final String PREFS="afterlog_artist_lookup";

    /**
     * Un candidat explicite ou une relation officielle MusicBrainz peut être
     * validé, mais seulement si nom ET vignette sont compatibles avec Shotgun.
     * Sinon renvoie la chaîne vide et l'interface reste sur "Recherche Spotify".
     */
    static String resolve(Context ctx,String artistName,String shotgunPortrait,String explicitLink){
        if(artistName==null||artistName.trim().isEmpty() ||
            shotgunPortrait==null||!shotgunPortrait.startsWith("https://"))
            return "";
        if(SpotifyArtistVerifier.isArtistUrl(explicitLink) &&
            SpotifyArtistVerifier.verify(explicitLink,artistName,shotgunPortrait))
            return explicitLink;
        String cacheKey=normalize(artistName)+"|"+
            Integer.toHexString(shotgunPortrait.hashCode());
        SharedPreferences cache=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        String answer=cache.getString(cacheKey,"");
        long stamp=cache.getLong(cacheKey+"_at",0);
        if(now-stamp<3L*24*60*60*1000){
            return SpotifyArtistVerifier.isArtistUrl(answer)?answer:"";
        }
        String verified="";
        try {
            String query="artist:\""+artistName.replace("\"","").trim()+"\"";
            String path="https://musicbrainz.org/ws/2/artist/?query="+
                URLEncoder.encode(query,"UTF-8")+"&fmt=json&limit=8";
            JSONObject response=new JSONObject(musicBrainzGet(path));
            JSONArray artists=response.optJSONArray("artists");
            int attempted=0;
            if(artists!=null)for(int i=0;i<artists.length()&&attempted<3;i++){
                JSONObject artist=artists.optJSONObject(i);
                if(artist==null)continue;
                if(!normalize(artistName).equals(normalize(artist.optString("name"))))continue;
                String mbid=artist.optString("id");
                if(!mbid.matches("[a-f0-9-]{36}"))continue;
                attempted++;
                JSONObject relations=new JSONObject(musicBrainzGet(
                    "https://musicbrainz.org/ws/2/artist/"+mbid+"?inc=url-rels&fmt=json"));
                JSONArray urls=relations.optJSONArray("relations");
                if(urls==null)continue;
                for(int j=0;j<urls.length();j++){
                    JSONObject rel=urls.optJSONObject(j);
                    if(rel==null)continue;
                    JSONObject urlObj=rel.optJSONObject("url");
                    String candidate=urlObj==null?"":urlObj.optString("resource");
                    if(SpotifyArtistVerifier.isArtistUrl(candidate) &&
                        SpotifyArtistVerifier.verify(candidate,artistName,shotgunPortrait)){
                        verified=candidate;break;
                    }
                }
                if(!verified.isEmpty())break;
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
                "AfterLog/0.10 (https://github.com/Elikto/Afterlog)");
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
        return Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim();
    }
}
