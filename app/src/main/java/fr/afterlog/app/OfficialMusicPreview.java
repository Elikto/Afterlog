package fr.afterlog.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URL;
import java.util.regex.Pattern;

/** Find explicit playable tracks on the public Spotify artist/official embed.
 * Does not guess Spotify track IDs or copy any audio. */
final class OfficialMusicPreview {
    static String firstSpotifyTrack(String artistLink){
        if(!SpotifyArtistVerifier.isArtistUrl(artistLink))return "";
        String id=artistLink.replaceFirst(
            "^https://open\\.spotify\\.com/(?:intl-[a-z]{2}/)?artist/","")
            .replaceFirst("[/?#].*$","");
        if(!id.matches("[A-Za-z0-9]{22}"))return "";
        for(String source:new String[]{"https://open.spotify.com/embed/artist/"+id,
                "https://open.spotify.com/artist/"+id}){
            try{
                Document page=Jsoup.connect(source).timeout(9000)
                    .userAgent("Mozilla/5.0 (compatible; NOCTARIA/0.19)").get();
                for(Element a:page.select("a[href*='/track/']")){
                    String link=a.absUrl("href");
                    URL url=new URL(link);
                    if(url.getHost().equalsIgnoreCase("open.spotify.com") &&
                        url.getPath().matches("^/(?:intl-[a-z]{2}/)?track/[A-Za-z0-9]{22}/?$")){
                        String trackId=url.getPath().replaceFirst(".*/track/","");
                        if(Pattern.matches("[A-Za-z0-9]{22}",trackId))
                            return "https://open.spotify.com/track/"+trackId;
                    }
                }
            }catch(Exception ignored){}
        }
        return "";
    }
}
