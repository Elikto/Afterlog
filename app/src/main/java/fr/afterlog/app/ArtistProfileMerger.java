package fr.afterlog.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.HashSet;

/** Enrichit les artistes sans effacer les photos/liens déjà présents. */
final class ArtistProfileMerger {
    static boolean merge(JSONObject original,JSONObject incoming) throws Exception {
        JSONArray additions=incoming.optJSONArray("artistProfiles");
        if(additions==null||additions.length()==0)return false;
        JSONArray existing=original.optJSONArray("artistProfiles");
        if(existing==null)existing=new JSONArray();
        JSONArray result=new JSONArray();
        HashSet<String> matched=new HashSet<>();
        boolean changed=false;
        for(int i=0;i<existing.length();i++){
            JSONObject old=existing.optJSONObject(i);
            if(old==null)continue;
            String name=key(old.optString("name"));
            JSONObject newer=null;
            for(int j=0;j<additions.length();j++){
                JSONObject candidate=additions.optJSONObject(j);
                if(candidate!=null&&name.equals(key(candidate.optString("name")))){
                    newer=candidate;matched.add(name);break;
                }
            }
            if(newer!=null){
                for(String field:new String[]{"photo","spotify","shotgunUrl"}){
                    if(old.optString(field).isEmpty() && !newer.optString(field).isEmpty()){
                        old.put(field,newer.optString(field));changed=true;
                    }
                }
            }
            result.put(old);
        }
        for(int j=0;j<additions.length();j++){
            JSONObject candidate=additions.optJSONObject(j);
            if(candidate==null)continue;
            String key=key(candidate.optString("name"));
            if(key.isEmpty()||matched.contains(key))continue;
            boolean duplicate=false;
            for(int i=0;i<result.length();i++){
                JSONObject p=result.optJSONObject(i);
                if(p!=null&&key.equals(key(p.optString("name")))){duplicate=true;break;}
            }
            if(!duplicate){result.put(candidate);changed=true;}
        }
        if(changed || original.optJSONArray("artistProfiles")==null)
            original.put("artistProfiles",result);
        return changed || original.optJSONArray("artistProfiles")==null;
    }
    private static String key(String name){return name.trim().toLowerCase(Locale.ROOT);}
}
