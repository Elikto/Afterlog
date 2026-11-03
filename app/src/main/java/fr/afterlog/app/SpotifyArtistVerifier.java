package fr.afterlog.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Ne déduit JAMAIS une URL Spotify du nom de l'artiste.
 * Vérifie uniquement un lien /artist/ fourni explicitement par Shotgun :
 * le titre de l'oEmbed Spotify doit correspondre et les deux portraits doivent
 * présenter une forte similarité visuelle. Sinon la carte ouvre une recherche.
 */
final class SpotifyArtistVerifier {
    private static final int LIMIT=1_500_000;
    static boolean isArtistUrl(String url) {
        return url != null && url.matches(
            "^https://open\\.spotify\\.com/artist/[A-Za-z0-9]{22}/?(?:[?#].*)?$");
    }
    static boolean verify(String spotifyUrl,String expectedName,String shotgunPhoto) {
        if(!isArtistUrl(spotifyUrl) || !safeImageUrl(shotgunPhoto))return false;
        try {
            String endpoint="https://open.spotify.com/oembed?url="+
                URLEncoder.encode(spotifyUrl,StandardCharsets.UTF_8.name());
            JSONObject metadata=new JSONObject(new String(get(endpoint,180000),
                StandardCharsets.UTF_8));
            if(!"Spotify".equalsIgnoreCase(metadata.optString("provider_name")))return false;
            String officialName=metadata.optString("title").replaceAll(
                "(?i)\\s*[-|·]\\s*(artist|artiste)\\s*$","");
            if(!normalize(expectedName).equals(normalize(officialName)))return false;
            String spotifyPortrait=metadata.optString("thumbnail_url");
            if(!safeImageUrl(spotifyPortrait))return false;
            // Seuls les portraits proches déclenchent le lien direct.
            Long shotgunHash=hash(shotgunPhoto),spotifyHash=hash(spotifyPortrait);
            if(shotgunHash==null || spotifyHash==null)return false;
            return Long.bitCount(shotgunHash^spotifyHash)<=7;
        }catch(Exception ignored){return false;}
    }
    private static String normalize(String text){
        String normalized=Normalizer.normalize(text.toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return normalized.replaceAll("[^a-z0-9]+"," ").trim();
    }
    private static boolean safeImageUrl(String input){
        try {
            URL url=new URL(input);
            return "https".equalsIgnoreCase(url.getProtocol()) &&
                url.getHost()!=null && !url.getHost().isEmpty();
        }catch(Exception e){return false;}
    }
    private static byte[] get(String input,int limit) throws Exception {
        HttpURLConnection con=null;
        try{
            con=(HttpURLConnection)new URL(input).openConnection();
            con.setConnectTimeout(7000);con.setReadTimeout(8000);
            con.setRequestProperty("Accept","application/json,image/*");
            con.setRequestProperty("User-Agent","AfterLog/0.9");
            if(con.getResponseCode()!=200)throw new Exception("HTTP");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=con.getInputStream()){
                byte[] buffer=new byte[8192];int len;
                while((len=in.read(buffer))!=-1){
                    if(out.size()+len>limit)throw new Exception("Taille d'image");
                    out.write(buffer,0,len);
                }
            }
            return out.toByteArray();
        }finally{if(con!=null)con.disconnect();}
    }
    private static Long hash(String url) throws Exception {
        // Borne la taille et le décodage : uniquement des portraits de petite taille.
        byte[] content=get(url,LIMIT);
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(content,0,content.length,options);
        if(options.outWidth<8 || options.outHeight<8)return null;
        options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>400 ||
              options.outHeight/options.inSampleSize>400)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        Bitmap raw=BitmapFactory.decodeByteArray(content,0,content.length,options);
        if(raw==null)return null;
        Bitmap tiny=Bitmap.createScaledBitmap(raw,9,8,true);
        if(tiny!=raw)raw.recycle();
        long hash=0L;int bit=0;
        for(int y=0;y<8;y++){
            for(int x=0;x<8;x++){
                int a=tiny.getPixel(x,y),b=tiny.getPixel(x+1,y);
                int grayA=((a>>16)&255)*30+((a>>8)&255)*59+(a&255)*11;
                int grayB=((b>>16)&255)*30+((b>>8)&255)*59+(b&255)*11;
                if(grayA>grayB)hash|=1L<<bit;
                bit++;
            }
        }
        tiny.recycle();
        return hash;
    }
}
