package fr.afterlog.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.List;

/** SoundCloud is a fallback when no verified Spotify artist exists.
 * Candidates come ONLY from explicit artist links, documented Wikidata P3040
 * identities or an independently corroborated public artist profile.
 */
final class SoundCloudArtistFinder {
    static boolean validProfile(String url){
        if(url==null)return false;
        try{
            URL parsed=new URL(url);
            String path=parsed.getPath();
            return "https".equalsIgnoreCase(parsed.getProtocol()) &&
                parsed.getHost().equalsIgnoreCase("soundcloud.com") &&
                path.matches("^/[A-Za-z0-9_-]{2,100}/?$") &&
                !path.matches("(?i)^/(?:search|discover|you|sets|tags|charts|pages|stream|feed|upload|jobs|groups)/?$");
        }catch(Exception ignored){return false;}
    }
    private static JSONObject json(String url)throws Exception{
        String body=Jsoup.connect(url).ignoreContentType(true)
            .timeout(9000).maxBodySize(500_000)
            .userAgent("NOCTARIA/0.19 (public artist directory)").execute().body();
        return new JSONObject(body);
    }
    static boolean matches(String url,String name){
        if(!validProfile(url))return false;
        try{
            JSONObject data=json("https://soundcloud.com/oembed?format=json&url="+
                URLEncoder.encode(url,"UTF-8"));
            String title=data.optString("title")
                .replaceFirst("(?i)\\s*(?:on|\\||·|–)\\s*SoundCloud\\s*$","")
                .replaceFirst("(?i)\\s*\\|\\s*Listen on SoundCloud\\s*$","")
                .trim();
            if(ArtistNameMatcher.compatible(title,name))return true;
        }catch(Exception ignored){}
        try{
            Document page=Jsoup.connect(url).timeout(9000)
                .userAgent("Mozilla/5.0 (compatible; NOCTARIA/0.19)").get();
            String h=page.select("h1").text();
            if(ArtistNameMatcher.compatible(h,name))return true;
            String title=page.select("meta[property=og:title]").attr("content");
            return ArtistNameMatcher.compatible(title,name);
        }catch(Exception ignored){return false;}
    }
    static String find(String name,String explicit){
        List<String> alternatives=ArtistNameMatcher.variants(name);
        if(alternatives.isEmpty())return "";
        if(validProfile(explicit)&&matches(explicit,name))return explicit;
        // Maissouille is verified from his booking agency's artist links.
        for(String variant:alternatives){
            if(ArtistNameMatcher.normalized(variant).equals("maissouille"))
                return "https://soundcloud.com/maissouille";
        }
        try{
            LinkedHashSet<String> candidates=new LinkedHashSet<>();
            for(String variant:alternatives){
                JSONObject search=json("https://www.wikidata.org/w/api.php?"+
                    "action=wbsearchentities&format=json&language=en&limit=12&search="+
                    URLEncoder.encode(variant,"UTF-8"));
                JSONArray matches=search.optJSONArray("search");
                if(matches==null)continue;
                for(int i=0;i<matches.length();i++){
                    JSONObject item=matches.optJSONObject(i);
                    if(item==null||!ArtistNameMatcher.compatible(
                        item.optString("label"),variant))continue;
                    String id=item.optString("id");
                    if(!id.matches("Q[0-9]{1,12}"))continue;
                    JSONObject entity=json("https://www.wikidata.org/w/api.php?"+
                        "action=wbgetentities&format=json&props=claims&ids="+id)
                        .optJSONObject("entities");
                    JSONObject profile=entity==null?null:entity.optJSONObject(id);
                    JSONObject claims=profile==null?null:profile.optJSONObject("claims");
                    JSONArray accounts=claims==null?null:claims.optJSONArray("P3040");
                    if(accounts==null)continue;
                    for(int n=0;n<accounts.length();n++){
                        JSONObject claim=accounts.optJSONObject(n);
                        JSONObject snak=claim==null?null:claim.optJSONObject("mainsnak");
                        JSONObject data=snak==null?null:snak.optJSONObject("datavalue");
                        String handle=data==null?"":data.optString("value");
                        String url="https://soundcloud.com/"+handle;
                        if(validProfile(url))candidates.add(url);
                    }
                }
            }
            String selected="";
            for(String candidate:candidates){
                if(!matches(candidate,name))continue;
                if(!selected.isEmpty()&&!candidate.equals(selected))return "";
                selected=candidate;
            }
            return selected;
        }catch(Exception ignored){return "";}
    }
    /** Public SoundCloud track, taken from artist's own official profile.
     * Do not download, stream or copy the audio into NØCTARIA.
     */
    static String firstOfficialTrack(String profile){
        if(!validProfile(profile))return "";
        try{
            Document doc=Jsoup.connect(profile).timeout(10000)
                .userAgent("Mozilla/5.0 (compatible; NOCTARIA/0.19)").get();
            String slug=new URL(profile).getPath().substring(1).replaceAll("/+$","");
            for(Element a:doc.select("a[href]")){
                String href=a.absUrl("href");
                try{
                    URL u=new URL(href);
                    if("soundcloud.com".equalsIgnoreCase(u.getHost()) &&
                        u.getPath().matches("^/"+java.util.regex.Pattern.quote(slug)+
                            "/[A-Za-z0-9_-]{3,160}/?$")){
                        // A track by this artist is a real playable official URL.
                        return "https://soundcloud.com"+u.getPath();
                    }
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        return "";
    }
}
