package fr.afterlog.app;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Search places near ANY user-entered French postal address or town.
 * IGN Géoplateforme geocodes the query, OpenStreetMap Overpass supplies
 * locally mapped services. No GPS permission or sharing with NOCTARIA servers.
 * OSM coverage is incomplete; always offer the official Drogues Info Service
 * directory as an independent fallback.
 */
final class RdrPlacesSearch {
    interface Listener { void onResult(Result result); }
    static final class Place {
        final String name,category,address,phone,email,website;
        final double lat,lon,distanceKm;
        Place(String name,String category,String address,String phone,
              String email,String website,double lat,double lon,double km){
            this.name=name;this.category=category;this.address=address;
            this.phone=phone;this.email=email;this.website=website;
            this.lat=lat;this.lon=lon;this.distanceKm=km;
        }
    }
    static final class Result {
        final String query,resolved;
        final List<Place> places;
        final String error;
        final double lat,lon;
        Result(String query,String resolved,List<Place> places,
               double lat,double lon,String error){
            this.query=query;this.resolved=resolved;this.places=places;
            this.lat=lat;this.lon=lon;this.error=error;
        }
        boolean hasCoordinates(){return Double.isFinite(lat)&&Double.isFinite(lon);}
        String officialDirectory(){
            if(!hasCoordinates())return "https://www.drogues-info-service.fr/Adresses-utiles";
            // The national directory accepts latitude/longitude in its public
            // Adresses-utiles route; never invent facilities from search text.
            return "https://www.drogues-info-service.fr/Adresses-utiles/"+
                "(offset)/0/(lng)/"+String.format(Locale.ROOT,"%.6f",lon)+
                "/(lat)/"+String.format(Locale.ROOT,"%.6f",lat)+
                "/(address)/"+urlPath(query)+"/(country)/FR";
        }
    }
    private static String urlPath(String value){
        try{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}
        catch(Exception ignored){return "";}
    }
    static void search(String raw,Listener callback){
        final String query=raw==null?"":raw.trim();
        new Thread(()->{
            Result response=find(query);
            new Handler(Looper.getMainLooper()).post(()->callback.onResult(response));
        },"NOCTARIA-RDR-address-search").start();
    }
    private static Result find(String query){
        if(query.length()<2)
            return new Result(query,"",new ArrayList<>(),Double.NaN,Double.NaN,
                "Indique une ville, un code postal ou une adresse.");
        double lat=Double.NaN,lon=Double.NaN;
        String location="";
        try{
            String address="https://data.geopf.fr/geocodage/search?index=address&limit=5&q="+
                URLEncoder.encode(query,"UTF-8");
            JSONObject data=new JSONObject(get(address,18000,600000));
            JSONArray features=data.optJSONArray("features");
            if(features==null||features.length()==0)
                return new Result(query,"",new ArrayList<>(),lat,lon,
                    "Adresse introuvable : précise la ville ou le code postal.");
            JSONObject feature=features.optJSONObject(0);
            if(feature==null)throw new Exception("Localisation introuvable");
            JSONArray coordinates=feature.getJSONObject("geometry").getJSONArray("coordinates");
            lon=coordinates.getDouble(0);lat=coordinates.getDouble(1);
            location=feature.optJSONObject("properties")==null?query:
                feature.getJSONObject("properties").optString("label",query);
            }catch(Exception error){
            return new Result(query,"",new ArrayList<>(),Double.NaN,Double.NaN,
                "Impossible de localiser cette adresse. Vérifie la connexion ou précise la ville.");
        }
        try{
            List<Place> places=lookup(lat,lon);
            return new Result(query,location,places,lat,lon,"");
        }catch(Exception error){
            return new Result(query,location,new ArrayList<>(),lat,lon,
                "L'annuaire cartographique est momentanément indisponible. "+
                "Utilise l'annuaire officiel ci-dessous ou réessaie.");
        }
    }
    private static List<Place> lookup(double lat,double lon)throws Exception{
        String center=String.format(Locale.ROOT,"%.6f,%.6f",lat,lon);
        // Search broadly enough for rural locations. No on-device static
        // results restricted to Arles. Only map entries actually returned.
        String around="(around:55000,"+center+")";
        String query="[out:json][timeout:23];("+
            "nwr"+around+"[\"name\"~\"CSAPA|CAARUD|addictolog|addiction\",i];"+
            "nwr"+around+"[\"type:FR:FINESS\"~\"^(178|197)$\"];"+
            "nwr"+around+"[\"social_facility:for\"~\"drug_addicted\"];"+
            ");out center 220;";
        String data=postOverpass(query);
        JSONObject root=new JSONObject(data);
        JSONArray elements=root.optJSONArray("elements");
        ArrayList<Place> result=new ArrayList<>();
        HashSet<String> distinct=new HashSet<>();
        if(elements==null)return result;
        for(int i=0;i<elements.length();i++){
            JSONObject entry=elements.optJSONObject(i);
            if(entry==null)continue;
            JSONObject tags=entry.optJSONObject("tags");
            if(tags==null)continue;
            String name=tags.optString("name").trim();
            if(name.isEmpty())continue;
            JSONObject centerJson=entry.optJSONObject("center");
            double y=centerJson!=null?centerJson.optDouble("lat",Double.NaN):
                entry.optDouble("lat",Double.NaN);
            double x=centerJson!=null?centerJson.optDouble("lon",Double.NaN):
                entry.optDouble("lon",Double.NaN);
            if(!Double.isFinite(x)||!Double.isFinite(y))continue;
            double distance=distance(lat,lon,y,x);
            String kind=category(name,tags);
            String key=name.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ")+
                "|"+Math.round(y*1000)+"|"+Math.round(x*1000);
            if(!distinct.add(key))continue;
            String address=join(tags.optString("addr:housenumber"),
                tags.optString("addr:street"));
            String postcode=tags.optString("addr:postcode"),city=tags.optString("addr:city");
            String locality=join(postcode,city);
            if(!locality.isEmpty())address=joinWithComma(address,locality);
            String phone=first(tags.optString("contact:phone"),tags.optString("phone"));
            String email=first(tags.optString("contact:email"),tags.optString("email"));
            String website=first(tags.optString("contact:website"),tags.optString("website"));
            result.add(new Place(name,kind,address,phone,email,website,y,x,distance));
        }
        result.sort(Comparator.comparingDouble(p->p.distanceKm));
        if(result.size()>18)return new ArrayList<>(result.subList(0,18));
        return result;
    }
    private static String category(String name,JSONObject tags){
        String text=name.toUpperCase(Locale.ROOT);
        String code=tags.optString("type:FR:FINESS");
        if(text.contains("CAARUD")||"178".equals(code))return "CAARUD · Réduction des risques";
        if(text.contains("CSAPA")||"197".equals(code))return "CSAPA · Addictologie";
        return "Structure d'accompagnement";
    }
    private static String join(String a,String b){
        if(a.trim().isEmpty())return b.trim();
        if(b.trim().isEmpty())return a.trim();
        return a.trim()+" "+b.trim();
    }
    private static String joinWithComma(String a,String b){
        return a.isEmpty()?b:(b.isEmpty()?a:a+", "+b);
    }
    private static String first(String a,String b){return a.isEmpty()?b:a;}
    private static double distance(double lat1,double lon1,double lat2,double lon2){
        double dLat=Math.toRadians(lat2-lat1);
        double dLon=Math.toRadians(lon2-lon1);
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)+
            Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
            Math.sin(dLon/2)*Math.sin(dLon/2);
        return 6371.0088*2*Math.asin(Math.min(1,Math.sqrt(a)));
    }
    private static String postOverpass(String query)throws Exception{
        Exception last=null;
        for(String endpoint:new String[]{
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"}){
            try{return post(endpoint,query);}
            catch(Exception error){last=error;}
        }
        throw last==null?new Exception("Service indisponible"):last;
    }
    private static String post(String endpoint,String query)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        try{
            c.setRequestMethod("POST");
            c.setConnectTimeout(11000);c.setReadTimeout(29000);
            c.setRequestProperty("User-Agent","NOCTARIA/0.18 (Android; public harm-reduction directory)");
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            c.setDoOutput(true);
            byte[] body=("data="+URLEncoder.encode(query,"UTF-8"))
                .getBytes(StandardCharsets.UTF_8);
            try(OutputStream stream=c.getOutputStream()){stream.write(body);}
            if(c.getResponseCode()!=200)throw new Exception("Overpass HTTP "+c.getResponseCode());
            return read(c.getInputStream(),1800000);
        }finally{c.disconnect();}
    }
    private static String get(String url,int timeout,int maxBytes)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setConnectTimeout(timeout);c.setReadTimeout(timeout);
            c.setRequestProperty("User-Agent","NOCTARIA/0.18 (Android; address lookup)");
            c.setRequestProperty("Accept","application/json");
            if(c.getResponseCode()!=200)throw new Exception("Adresse HTTP "+c.getResponseCode());
            return read(c.getInputStream(),maxBytes);
        }finally{c.disconnect();}
    }
    private static String read(InputStream stream,int limit)throws Exception{
        try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int size;
            while((size=input.read(buffer))!=-1){
                if(out.size()+size>limit)throw new Exception("Réponse trop volumineuse");
                out.write(buffer,0,size);
            }
            return out.toString("UTF-8");
        }
    }
}
