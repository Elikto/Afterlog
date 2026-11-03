package fr.afterlog.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Vigilance documentaire et non calcul de seuil toxique personnalisé. */
final class RiskGuidance {
    static final class Warning {
        final String title,detail,reference;
        Warning(String title,String detail,String reference){
            this.title=title;this.detail=detail;this.reference=reference;
        }
    }
    static final String SOURCE_ALCOHOL=
        "https://www.santepubliquefrance.fr/alcool/comment-reduire-les-risques-de-la-consommation-dalcool";
    static final String SOURCE_MDMA=
        "https://drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ecstasy-MDMA";
    static final String SOURCE_KETAMINE=
        "https://drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ketamine";
    static final String SOURCE_DRUGS="https://www.drogues-info-service.fr/";
    static final String SOURCE_REPEATS=
        "https://www.drogues-info-service.fr/Vos-Questions-Nos-Reponses/Substance-chemsex";
    static final String SOURCE_COCAINE=
        "https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Cocaine";

    static boolean alcohol(JSONObject item){return "Alcool".equals(item.optString("category"));}
    static boolean mdma(JSONObject item) {
        String n=item.optString("name").toLowerCase(Locale.ROOT);
        return n.contains("mdma")||n.contains("ecstasy");
    }
    static boolean ketamine(JSONObject item){return item.optString("name").toLowerCase(Locale.ROOT).contains("kétamine");}
    static boolean cocaine(JSONObject item){return item.optString("name").toLowerCase(Locale.ROOT).contains("cocaïne");}
    static boolean ghb(JSONObject item){return item.optString("name").toLowerCase(Locale.ROOT).contains("ghb");}
    static LocalDate day(JSONObject item){
        try{return LocalDate.parse(item.optString("date"));}
        catch(Exception e){return null;}
    }
    static int quantity(JSONObject item) {
        if(item.optJSONObject("parts")!=null){
            JSONObject p=item.optJSONObject("parts");
            return p.optInt("quart")+p.optInt("demi")+p.optInt("entier");
        }
        JSONArray portions=item.optJSONArray("portions");
        if(portions!=null&&portions.length()>0){
            int total=0;
            for(int i=0;i<portions.length();i++){
                JSONObject p=portions.optJSONObject(i);
                if(p!=null)total+=Math.max(0,p.optInt("count"));
            }
            return total;
        }
        return Math.max(0,(int)Math.round(item.optDouble("quantity",0)));
    }
    static List<JSONObject> within(List<JSONObject> entries,LocalDate from,LocalDate untilExclusive){
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject e:entries){
            LocalDate d=day(e);
            if(d!=null && !d.isBefore(from) && d.isBefore(untilExclusive))result.add(e);
        }
        return result;
    }
    static List<Warning> forEvent(List<JSONObject> entries){
        List<Warning> warnings=new ArrayList<>();
        if(entries.isEmpty())return warnings;
        Map<LocalDate,Integer> glasses=new HashMap<>();
        Map<String,List<JSONObject>> bySubstance=new HashMap<>();
        HashMap<String,HashSet<String>> categories=new HashMap<>();
        for(JSONObject e:entries){
            LocalDate date=day(e);
            if(date==null)continue;
            String eventDay=e.optLong("eventId")+"|"+date;
            HashSet<String> group=categories.computeIfAbsent(eventDay,k->new HashSet<>());
            if(alcohol(e)){
                glasses.put(date,glasses.getOrDefault(date,0)+quantity(e));
                group.add("alcohol");
            }else{
                if(mdma(e))group.add("mdma");
                if(ketamine(e))group.add("ketamine");
                if(cocaine(e))group.add("cocaine");
                if(ghb(e))group.add("ghb");
                String substance=mdma(e)?"mdma / ecstasy":
                    e.optString("name").trim().toLowerCase(Locale.ROOT);
                // Regrouper aussi les entrées de minuit au sein d'une soirée.
                bySubstance.computeIfAbsent(e.optLong("eventId")+"|"+substance,
                    k->new ArrayList<>()).add(e);
            }
        }
        // Les nombres de verres saisis ne garantissent pas un verre standard.
        // Ne pas déclencher de bannière de risque sur 2 verres ou sur une
        // quantité sans volume et degré d'alcool connus.
        boolean mdmaMix=false,ketamineMix=false,cocaineMix=false,ghbMix=false;
        for(HashSet<String> group:categories.values()){
            if(!group.contains("alcohol"))continue;
            mdmaMix|=group.contains("mdma");
            ketamineMix|=group.contains("ketamine");
            cocaineMix|=group.contains("cocaine");
            ghbMix|=group.contains("ghb");
        }
        if(mdmaMix)warnings.add(new Warning("MDMA et alcool signalés le même jour",
            "Drogues Info Service déconseille l'association MDMA / alcool, notamment à cause des risques de surchauffe et de déshydratation. Les heures ne prouvent pas que les effets se sont superposés.",SOURCE_MDMA));
        if(ketamineMix)warnings.add(new Warning("Kétamine et alcool signalés le même jour",
            "Ce mélange peut accentuer les pertes de connaissance et les risques de coma et de complications respiratoires. Drogues Info Service déconseille fortement cette association.",SOURCE_KETAMINE));
        if(cocaineMix)warnings.add(new Warning("Cocaïne et alcool signalés le même jour",
            "L'association entraîne la formation de cocaéthylène, substance associée à des risques cardiovasculaires et hépatiques accrus.",SOURCE_COCAINE));
        if(ghbMix)warnings.add(new Warning("GHB / GBL et alcool signalés le même jour",
            "L'association de substances sédatives, notamment avec l'alcool, augmente le risque de perte de connaissance et de ralentissement respiratoire. Appeler le 15 / 112 face à une personne inconsciente ou respirant anormalement.",SOURCE_DRUGS));

        boolean repeated=false,closeMdma=false;
        for(List<JSONObject> sequence:bySubstance.values()){
            int total=0;
            for(JSONObject e:sequence)total+=quantity(e);
            if(sequence.size()>1)repeated=true;
            for(int i=0;i<sequence.size();i++)for(int j=i+1;j<sequence.size();j++){
                JSONObject a=sequence.get(i),b=sequence.get(j);
                if(!mdma(a)||!mdma(b) || a.optBoolean("allNight") ||
                    b.optBoolean("allNight"))continue;
                if(!"exact".equals(a.optString("timeMode","exact"))||
                    !"exact".equals(b.optString("timeMode","exact")))continue;
                try{
                    LocalTime t1=LocalTime.parse(a.optString("time"));
                    LocalTime t2=LocalTime.parse(b.optString("time"));
                    LocalDate d1=day(a),d2=day(b);
                    if(d1==null||d2==null)continue;
                    long minutes=Math.abs(ChronoUnit.MINUTES.between(
                        d1.atTime(t1),d2.atTime(t2)));
                    if(minutes<120)closeMdma=true;
                }catch(Exception ignored){}
            }
        }
        if(closeMdma)warnings.add(new Warning("Prises de MDMA rapprochées (heures déclarées)",
            "Deux saisies d'horaires précis se trouvent proches dans le journal. Drogues Info Service déconseille les prises successives de MDMA. Le délai affiché n'est ni une limite officielle ni une garantie de sécurité.",SOURCE_REPEATS));
        // Un seul enregistrement « 2 demi + 1 quart » n'est pas une
        // preuve de prises rapprochées : n'en déduire aucun délai.
        return warnings;
    }
    /**
     * 0 = aucun signal détectable, 1 = vigilance, 2 = association grave
     * avec horaires/intervalle explicitement compatibles.
     * Ne déduit PAS la toxicité d'un grammage inconnu ou de la taille d'une prise.
     */
    static int eventSeverity(List<JSONObject> entries) {
        if(forEvent(entries).isEmpty())return 0;
        for(int i=0;i<entries.size();i++){
            JSONObject a=entries.get(i);
            for(int j=i+1;j<entries.size();j++){
                JSONObject b=entries.get(j);
                boolean severePair=(alcohol(a)&&ghb(b))||(ghb(a)&&alcohol(b));
                if(!severePair)continue;
                if(overlappingDeclaredIntervals(a,b))return 2;
            }
        }
        return 1;
    }
    private static boolean overlappingDeclaredIntervals(JSONObject a,JSONObject b){
        // Deux prises signalées le même jour sans horaire ne suffisent pas à
        // affirmer une co-consommation. Pas de fenêtre de sécurité inventée.
        if(a.optBoolean("allNight")||b.optBoolean("allNight"))return false;
        LocalDate da=day(a),db=day(b);
        if(da==null||db==null)return false;
        try{
            String ma=a.optString("timeMode","exact");
            String mb=b.optString("timeMode","exact");
            if(!("exact".equals(ma)||"between".equals(ma))||
               !("exact".equals(mb)||"between".equals(mb)))return false;
            LocalDateTime startA=da.atTime(LocalTime.parse(a.optString("time")));
            LocalDateTime startB=db.atTime(LocalTime.parse(b.optString("time")));
            LocalDateTime endA="between".equals(ma)?
                da.atTime(LocalTime.parse(a.optString("timeEnd"))):startA;
            LocalDateTime endB="between".equals(mb)?
                db.atTime(LocalTime.parse(b.optString("timeEnd"))):startB;
            if(endA.isBefore(startA))endA=endA.plusDays(1);
            if(endB.isBefore(startB))endB=endB.plusDays(1);
            return !endA.isBefore(startB)&&!endB.isBefore(startA);
        }catch(Exception ignored){return false;}
    }
    static List<Warning> forPeriod(List<JSONObject> entries,LocalDate from,LocalDate end){
        List<Warning> warnings=forEvent(within(entries,from,end));
        // Le repère hebdomadaire concerne les verres standards, non une estimation
        // d'intoxication aiguë ou une dose pharmacologique.
        LocalDate startMonday=from.minusDays(from.getDayOfWeek().getValue()-1);
        for(LocalDate week=startMonday;week.isBefore(end);week=week.plusWeeks(1)){
            LocalDate until=week.plusWeeks(1);
            int glasses=0;
            for(JSONObject item:entries){
                LocalDate date=day(item);
                if(date!=null&&!date.isBefore(week)&&date.isBefore(until)&&alcohol(item))
                    glasses+=quantity(item);
            }
            if(glasses>10){
                warnings.add(new Warning("Alcool : plus de 10 verres consignés sur une semaine",
                    glasses+" verres ont été notés sur la semaine du "+week+
                    ". Le repère Santé publique France est de 10 verres standards maximum par semaine. La taille des verres consignés ici peut ne pas correspondre aux verres standards.",SOURCE_ALCOHOL));
                break;
            }
        }
        return warnings;
    }
}
