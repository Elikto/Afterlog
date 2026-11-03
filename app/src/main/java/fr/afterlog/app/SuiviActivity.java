package fr.afterlog.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;

/** Suivi quotidien/hebdomadaire/mensuel/annuel, uniquement à partir du journal privé. */
public final class SuiviActivity extends Activity {
    private static final int BG=Color.rgb(12,14,24),TILE=Color.rgb(28,30,47),
        ACCENT=Color.rgb(186,166,255),WHITE=Color.WHITE,SOFT=Color.rgb(178,183,202);
    private final String[] modes={"Jour","Semaine","Mois","Année"};
    private int mode=1;
    private LocalDate cursor=LocalDate.now();
    private LinearLayout body;
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void add(LinearLayout parent,View child,int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(margin);parent.addView(child,p);
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c=col();c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.setBackground(background(TILE,19));add(parent,c,12);return c;
    }
    private TextView button(String title,Runnable task) {
        TextView t=text(title,14,WHITE,true);
        t.setPadding(dp(12),dp(14),dp(12),dp(14));t.setGravity(Gravity.CENTER);
        t.setBackground(background(TILE,14));t.setOnClickListener(v->task.run());return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);render();
    }
    private LocalDate periodStart(){
        switch(mode){
            case 0:return cursor;
            case 1:return cursor.with(DayOfWeek.MONDAY);
            case 2:return cursor.withDayOfMonth(1);
            default:return cursor.withDayOfYear(1);
        }
    }
    private LocalDate periodEnd(LocalDate start){
        switch(mode){
            case 0:return start.plusDays(1);
            case 1:return start.plusWeeks(1);
            case 2:return start.plusMonths(1);
            default:return start.plusYears(1);
        }
    }
    private String periodTitle(LocalDate start,LocalDate end){
        DateTimeFormatter full=DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.FRANCE);
        switch(mode){
            case 0:return start.format(full);
            case 1:return start.format(DateTimeFormatter.ofPattern("d MMM",Locale.FRANCE))+
                " – "+end.minusDays(1).format(full);
            case 2:return start.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.FRANCE));
            default:return String.valueOf(start.getYear());
        }
    }
    private void move(int direction){
        switch(mode){
            case 0:cursor=cursor.plusDays(direction);break;
            case 1:cursor=cursor.plusWeeks(direction);break;
            case 2:cursor=cursor.plusMonths(direction);break;
            default:cursor=cursor.plusYears(direction);
        }
        render();
    }
    private void render() {
        LinearLayout outer=col();outer.setPadding(dp(16),dp(12),dp(16),0);
        outer.setBackgroundColor(BG);setContentView(outer);
        add(outer,button("← Retour à AfterLog",this::finish),11);
        add(outer,text("Mon suivi",27,WHITE,true),5);
        add(outer,text("Visualiser les consommations consignées, sans estimation de dose cachée.",
            13,SOFT,false),16);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);add(outer,tabs,13);
        for(int i=0;i<modes.length;i++){
            int selected=i;
            TextView tab=button(modes[i],()->{mode=selected;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(53),1);
            p.setMargins(dp(2),0,dp(2),0);
            tab.setTextSize(13);
            tab.setBackground(background(i==mode?ACCENT:TILE,14));
            tab.setTextColor(i==mode?BG:WHITE);tabs.addView(tab,p);
        }
        LocalDate from=periodStart(),until=periodEnd(from);
        LinearLayout navigation=new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);add(outer,navigation,14);
        navigation.addView(button("‹",()->move(-1)),new LinearLayout.LayoutParams(dp(54),dp(50)));
        TextView heading=text(periodTitle(from,until),17,WHITE,true);
        heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);
        navigation.addView(heading,new LinearLayout.LayoutParams(0,dp(57),1));
        navigation.addView(button("›",()->move(1)),new LinearLayout.LayoutParams(dp(54),dp(50)));

        ScrollView scroll=new ScrollView(this);
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=col();body.setPadding(0,0,0,dp(30));scroll.addView(body);

        AppStore store=new AppStore(this);
        List<JSONObject> selected=RiskGuidance.within(store.allEntries(),from,until);
        int drinks=0,substanceUnits=0,other=0;
        HashSet<Long> evenings=new HashSet<>();
        Map<String,Integer> classes=new LinkedHashMap<>();
        for(JSONObject entry:selected) {
            evenings.add(entry.optLong("eventId"));
            int quantity=RiskGuidance.quantity(entry);
            if(RiskGuidance.alcohol(entry))drinks+=quantity;
            else if("Autres substances".equals(entry.optString("category")))substanceUnits+=quantity;
            else other+=quantity;
            classes.merge(entry.optString("name","Autre"),1,Integer::sum);
        }

        LinearLayout summary=card(body);
        add(summary,text("BILAN DE LA PÉRIODE",12,ACCENT,true),11);
        add(summary,text(selected.size()+" entrées · "+evenings.size()+" soirée(s)",22,WHITE,true),10);
        add(summary,text(drinks+" verre(s) d'alcool saisis",15,WHITE,false),6);
        add(summary,text(substanceUnits+" unité(s) / fraction(s) déclarée(s) pour les substances",
            14,SOFT,false),6);
        if(other>0)add(summary,text(other+" boisson(s) sans alcool",14,SOFT,false),6);
        add(summary,text("Les unités ne représentent pas une quantité de substance active. Une « prise » ou une fraction peut avoir une teneur très variable.",
            12,SOFT,false),0);

        showTrend(store.allEntries(),from,until);

        if(!classes.isEmpty()){
            LinearLayout breakdown=card(body);
            add(breakdown,text("SUBSTANCES ET BOISSONS NOTÉES",13,ACCENT,true),12);
            for(Map.Entry<String,Integer> item:classes.entrySet()){
                String substance=item.getKey();
                add(breakdown,text("● "+substance+" · "+item.getValue()+" entrée(s)",
                    15,ConsumptionPalette.forEntry("",substance),false),8);
            }
        }
        List<RiskGuidance.Warning> warnings=RiskGuidance.forPeriod(store.allEntries(),from,until);
        add(body,text("Vigilance et réduction des risques",21,WHITE,true),13);
        if(warnings.isEmpty()){
            LinearLayout empty=card(body);
            add(empty,text("Aucun motif d'alerte automatique détecté à partir des données renseignées.",
                15,WHITE,false),7);
            add(empty,text("Cela ne signifie pas qu'une consommation est sans danger. Les heures approximatives, la composition et les dosages réels ne sont pas connus.",
                13,SOFT,false),0);
        }else for(RiskGuidance.Warning warning:warnings)showWarning(warning);

        LinearLayout help=card(body);
        add(help,text("Besoin d'aide ?",18,WHITE,true),9);
        add(help,text("Perte de connaissance, respiration anormale, convulsions, douleur thoracique ou forte surchauffe : appelle immédiatement les secours.",
            13,SOFT,false),9);
        add(help,button("📞 Urgences · 15 ou 112",()->dial("15")),9);
        add(help,button("📞 Drogues Info Service · 0 800 23 13 13",()->
            dial("0800231313")),9);
        add(help,text("Écoute gratuite et anonyme, 7 j/7 de 8 h à 23 h (horaires actualisés en octobre 2026).",
            12,SOFT,false),9);
        add(help,button("♡ Annuaire et fiches RDR",()->
            startActivity(new Intent(this,RdrActivity.class))),0);
    }
    private void showTrend(List<JSONObject> entries,LocalDate start,LocalDate until) {
        LinearLayout chart=card(body);
        add(chart,text("ENTRÉES CONSIGNÉES · ÉVOLUTION",13,ACCENT,true),9);
        ArrayList<String> labels=new ArrayList<>();
        ArrayList<Integer> counts=new ArrayList<>();
        if(mode==0) {
            for(int h=0;h<24;h+=4){
                int count=0;
                for(JSONObject e:entries){
                    if(!start.equals(RiskGuidance.day(e))||e.optBoolean("allNight"))continue;
                    try{
                        int hour=Integer.parseInt(e.optString("time").substring(0,2));
                        if(hour>=h&&hour<h+4)count++;
                    }catch(Exception ignored){}
                }
                labels.add(String.format(Locale.FRANCE,"%02d–%02d h",h,h+4));counts.add(count);
            }
            add(chart,text("Heures exactes uniquement ; les prises « toute la soirée » n'apparaissent pas dans cette répartition horaire.",
                12,SOFT,false),8);
        }else if(mode==1){
            DateTimeFormatter fmt=DateTimeFormatter.ofPattern("EEE d",Locale.FRANCE);
            for(LocalDate day=start;day.isBefore(until);day=day.plusDays(1)){
                labels.add(day.format(fmt));
                counts.add(RiskGuidance.within(entries,day,day.plusDays(1)).size());
            }
        }else if(mode==2){
            for(LocalDate day=start;day.isBefore(until);day=day.plusWeeks(1)){
                LocalDate end=day.plusWeeks(1);
                if(end.isAfter(until))end=until;
                labels.add("Du "+day.getDayOfMonth()+" au "+end.minusDays(1).getDayOfMonth());
                counts.add(RiskGuidance.within(entries,day,end).size());
            }
        }else {
            DateTimeFormatter fmt=DateTimeFormatter.ofPattern("MMM",Locale.FRANCE);
            for(LocalDate day=start;day.isBefore(until);day=day.plusMonths(1)){
                labels.add(day.format(fmt));
                counts.add(RiskGuidance.within(entries,day,day.plusMonths(1)).size());
            }
        }
        // Courbes multi-séries, plutôt que des barres horizontales.
        int n=labels.size();
        LinkedHashMap<String,int[]> bySubstance=new LinkedHashMap<>();
        LinkedHashMap<String,Integer> colorBySubstance=new LinkedHashMap<>();
        LinkedHashMap<String,String> labelBySubstance=new LinkedHashMap<>();
        int[] alerts=new int[n];
        ArrayList<ArrayList<JSONObject>> grouped=new ArrayList<>();
        for(int i=0;i<n;i++)grouped.add(new ArrayList<>());
        for(JSONObject item:entries){
            LocalDate date=RiskGuidance.day(item);
            if(date==null||date.isBefore(start)||!date.isBefore(until))continue;
            int bucket=-1;
            if(mode==0){
                if(item.optBoolean("allNight") ||
                    !"exact".equals(item.optString("timeMode","exact")))continue;
                try{
                    int hour=Integer.parseInt(item.optString("time").substring(0,2));
                    bucket=Math.min(5,Math.max(0,hour/4));
                }catch(Exception ignored){}
            }else if(mode==1){
                bucket=(int)java.time.temporal.ChronoUnit.DAYS.between(start,date);
            }else if(mode==2){
                bucket=(int)java.time.temporal.ChronoUnit.DAYS.between(start,date)/7;
            }else{
                bucket=(int)java.time.temporal.ChronoUnit.MONTHS.between(
                    start.withDayOfMonth(1),date.withDayOfMonth(1));
            }
            if(bucket<0||bucket>=n)continue;
            String category=item.optString("category","");
            String name=item.optString("name","Autre");
            String seriesKey=category+"|"+name;
            if(!bySubstance.containsKey(seriesKey)){
                bySubstance.put(seriesKey,new int[n]);
                colorBySubstance.put(seriesKey,ConsumptionPalette.forEntry(category,name));
                labelBySubstance.put(seriesKey,name);
            }
            bySubstance.get(seriesKey)[bucket]++;
            grouped.get(bucket).add(item);
        }
        for(int i=0;i<n;i++){
            List<RiskGuidance.Warning> risk=RiskGuidance.forEvent(grouped.get(i));
            if(!risk.isEmpty()){
                alerts[i]=1;
                for(RiskGuidance.Warning w:risk)
                    if(w.title.contains("GHB")||w.title.contains("Kétamine et alcool"))
                        alerts[i]=2;
            }
        }
        int[][] series=bySubstance.values().toArray(new int[0][]);
        int[] seriesColors=new int[colorBySubstance.size()];
        String[] seriesNames=labelBySubstance.values().toArray(new String[0]);
        int ci=0;for(int color:colorBySubstance.values())seriesColors[ci++]=color;
        TrackingChartView drawing=new TrackingChartView(this);
        drawing.show(labels,series,alerts,seriesColors,seriesNames);
        add(chart,drawing,7);
        for(int offset=0;offset<seriesNames.length;offset+=2){
            LinearLayout legend=new LinearLayout(this);
            legend.setOrientation(LinearLayout.HORIZONTAL);
            add(chart,legend,5);
            for(int k=offset;k<Math.min(offset+2,seriesNames.length);k++){
                TextView item=text("● "+seriesNames[k],12,seriesColors[k],true);
                item.setMaxLines(1);
                item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(23),1);
                legend.addView(item,lp);
            }
        }
        add(chart,text("Points orange : vigilance. Rouge : interaction connue à risque ; ce n'est pas une mesure de toxicité. Les prises sans heure précise ne figurent pas dans le graphique horaire.",
            11,SOFT,false),0);

    }
    private void showWarning(RiskGuidance.Warning warning){
        LinearLayout card=card(body);
        add(card,text("⚠ "+warning.title,16,ACCENT,true),8);
        add(card,text(warning.detail,14,WHITE,false),12);
        add(card,button("Lire la source de prévention",()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(warning.reference)));}
            catch(Exception ignored){}
        }),0);
    }
    private void dial(String number){
        try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+number)));}
        catch(Exception ignored){}
    }
}
