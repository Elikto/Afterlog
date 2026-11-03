package fr.afterlog.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Public, credential-free, structured backup for Spotify profiles.
 * Wikidata P1902 stores Spotify artist IDs; results are checked against
 * Spotify's own oEmbed title and accepted only if exactly one matches.
 */
final class WikidataSpotifyFinder {
    private static final String API="https://www.wikidata.org/w/api.php?";
    static String find(String artistName){
        if(artistName==null||artistName.trim().length()<2)return "";
        try{
            String name=artistName.trim();
            String query=API+"action=wbsearchentities&format=json&language=en&limit=12&search="+
                URLEncoder.encode(name,"UTF-8");
            JSONObject search=new JSONObject(get(query));
            JSONArray matches=search.optJSONArray("search");
            if(matches==null)return "";
            LinkedHashSet<String> ids=new LinkedHashSet<>();
            for(int i=0;i<matches.length();i++){
                JSONObject item=matches.optJSONObject(i);
                if(item==null)continue;
                if(!same(name,item.optString("label")))continue;
                String id=item.optString("id");
                if(id.matches("Q[0-9]{1,12}"))ids.add(id);
            }
            if(ids.isEmpty())return "";
            String idsParam=String.join("|",ids);
            String url=API+"action=wbgetentities&format=json&props=claims&ids="+
                URLEncoder.encode(idsParam,"UTF-8");
            JSONObject entities=new JSONObject(get(url)).optJSONObject("entities");
            if(entities==null)return "";
            LinkedHashSet<String> candidates=new LinkedHashSet<>();
            for(String id:ids){
                JSONObject entity=entities.optJSONObject(id);
                if(entity==null)continue;
                JSONObject claims=entity.optJSONObject("claims");
                JSONArray spotify=claims==null?null:claims.optJSONArray("P1902");
                if(spotify==null)continue;
                for(int j=0;j<spotify.length();j++){
                    JSONObject claim=spotify.optJSONObject(j);
                    JSONObject snak=claim==null?null:claim.optJSONObject("mainsnak");
                    JSONObject value=snak==null?null:snak.optJSONObject("datavalue");
                    String artistId=value==null?"":value.optString("value");
                    if(artistId.matches("[0-7][a-zA-Z0-9]{21}"))
                        candidates.add("https://open.spotify.com/artist/"+artistId);
                }
            }
            String verified="";
            for(String candidate:candidates){
                if(!SpotifyArtistVerifier.matchesName(candidate,name))continue;
                if(!verified.isEmpty()&&!verified.equals(candidate))
                    return ""; // Namesake: require manual confirmation.
                verified=candidate;
            }
            return verified;
        }catch(Exception ignored){return "";}
    }
    private static boolean same(String a,String b){return norm(a).equals(norm(b));}
    private static String norm(String value){
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace("ø","o"),
            Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replaceAll("[^a-z0-9]+"," ").trim();
    }
    private static String get(String address)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        try{
            connection.setConnectTimeout(6500);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent",
                "NOCTARIA/0.18.0 (https://github.com/Elikto/Afterlog; artist lookup)");
            connection.setRequestProperty("Accept","application/json");
            if(connection.getResponseCode()!=200)
                throw new Exception("Wikidata momentanément indisponible");
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){
                    if(result.size()+count>650_000)
                        throw new Exception("Données Wikidata trop volumineuses");
                    result.write(buffer,0,count);
                }
            }
            return result.toString("UTF-8");
        }finally{connection.disconnect();}
    }
}
