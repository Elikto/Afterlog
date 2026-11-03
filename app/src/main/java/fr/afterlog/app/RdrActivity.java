package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class RdrActivity extends Activity {
    private static final int BG=Color.rgb(12,14,24),CARD=Color.rgb(25,28,44),
        ACCENT=Color.rgb(186,166,255),WHITE=Color.WHITE,SOFT=Color.rgb(183,189,208);
    private LinearLayout body;
    private String town;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(16));return g;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void add(LinearLayout p,View v,int gap){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(gap);p.addView(v,lp);
    }
    private TextView txt(String t,int size,boolean bold,int color){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private TextView btn(String value,Runnable click){
        TextView v=txt(value,14,true,WHITE);v.setGravity(Gravity.CENTER);
        v.setPadding(dp(13),dp(15),dp(13),dp(15));v.setBackground(bg(CARD));
        v.setOnClickListener(x->click.run());return v;
    }
    private LinearLayout card(){
        LinearLayout c=col();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(bg(CARD));return c;
    }
    private void open(String url) {
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(this,"Lien inaccessible",Toast.LENGTH_LONG).show();}
    }
    private void phone(String number) {open("tel:"+number.replace(" ",""));}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        town=getPreferences(0).getString("city","Arles, 13200");
        showPage();
    }
    private void showPage(){
        LinearLayout outer=col();outer.setBackgroundColor(BG);outer.setPadding(dp(16),dp(12),dp(16),0);
        setContentView(outer);
        add(outer,btn("← Retour",this::finish),11);
        add(outer,txt("RDR · Réduction des risques",25,true,WHITE),5);
        add(outer,txt("Prévenir, comprendre, trouver de l'aide.",14,false,SOFT),13);
        ScrollView scroll=new ScrollView(this);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=col();body.setPadding(0,dp(8),0,dp(24));scroll.addView(body);

        LinearLayout emergency=card();
        add(emergency,txt("Urgence : 15 ou 112",21,true,WHITE),8);
        add(emergency,txt("Perte de connaissance, respiration anormale, convulsions, forte douleur thoracique ou température très élevée : appelle immédiatement les secours.",14,false,SOFT),12);
        add(emergency,btn("📞 Appeler le 15",()->phone("15")),8);
        add(body,emergency,17);

        LinearLayout helpline=card();
        add(helpline,txt("Drogues Info Service",19,true,WHITE),6);
        add(helpline,txt("0 800 23 13 13 · anonyme et gratuit · 7j/7 de 8 h à 23 h (horaires annoncés depuis octobre 2026).",14,false,SOFT),11);
        add(helpline,btn("📞 Appeler gratuitement",()->phone("0800231313")),7);
        add(helpline,btn("🌐 Site et chat",()->open("https://www.drogues-info-service.fr/")),0);
        add(body,helpline,17);

        add(body,txt("📍 Structures et associations",20,true,WHITE),11);
        add(body,btn("Localisation choisie : "+town+"  ✎",this::chooseTown),13);
        if(town.toLowerCase(java.util.Locale.ROOT).contains("arles")){
            facility("CSAPA Camargue · La Maison Jaune","Consultation, accompagnement et prévention des addictions.",
                "10 rue Jean Saler, 13200 Arles","04 90 93 46 50","csapa.arles@groupe-sos.org",
                "https://www.drogues-info-service.fr/Adresses-utiles","10 rue Jean Saler Arles");
            facility("CAARUD Antenne mobile Camargue","Réduction des risques, matériel, accueil et orientation. Structure mobile : appeler pour connaître le lieu de permanence.",
                "13200 Arles · antenne mobile","07 87 69 85 44","caarud.mobile.camargue@groupe-sos.org",
                "https://drogues-info-service.fr/Adresses-utiles/101948","CAARUD mobile Camargue Arles");
            facility("CSAPA Camargue · Mas-Thibert","Centre de soins et d'accompagnement, notamment résidentiel. Contact préalable recommandé.",
                "Mas des Lauriers, route de Port-Saint-Louis-du-Rhône, 13104 Arles",
                "04 90 98 75 70","","https://www.drogues-info-service.fr/Adresses-utiles",
                "Mas des Lauriers Mas Thibert");
        }else {
            add(body,txt("L'adresse n'est pas envoyée à AfterLog ni utilisée pour te géolocaliser. Recherche les structures autour du lieu saisi via l'annuaire officiel ou une carte.",14,false,SOFT),12);
        }
        add(body,btn("🔎 Trouver des structures autour de "+town,()->open(
            "https://www.google.com/maps/search/?api=1&query="+Uri.encode("CAARUD CSAPA réduction des risques "+town))),10);
        add(body,btn("Annuaire officiel Drogues Info Service",()->
            open("https://www.drogues-info-service.fr/Adresses-utiles")),16);

        add(body,txt("🧪 Analyse de produits",20,true,WHITE),9);
        LinearLayout analysis=card();
        add(analysis,txt("Analyse ton prod",17,true,WHITE),6);
        add(analysis,txt("Recherche de dispositifs d'analyse des substances psychoactives. Contacte la structure avant de te déplacer : les modalités et disponibilités varient.",14,false,SOFT),9);
        add(analysis,btn("Consulter les points d'analyse",()->open("https://www.analysetonprod.fr/")),9);
        add(analysis,btn("Demander au CAARUD d'Arles",()->phone("0787698544")),0);
        add(body,analysis,19);

        add(body,txt("📚 Fiches de prévention",20,true,WHITE),6);
        add(body,txt("Informations synthétiques. Une prise ne peut pas être estimée à partir de « petit », « moyen » ou « grand ».",13,false,SOFT),12);
        String[][] sheets={
            {"🍺 Alcool","Risques de perte de vigilance, accidents et intoxication. Les mélanges avec des substances sédatives augmentent le risque de dépression respiratoire."},
            {"💊 Ecstasy / MDMA","La composition et la teneur d'un comprimé varient. Une forte chaleur, des troubles de conscience, une agitation extrême ou des convulsions nécessitent une aide médicale urgente."},
            {"🧪 Kétamine","Risque de désorientation, chutes, perte de connaissance et atteintes urinaires lors d'usages répétés. Mélanges sédatifs particulièrement dangereux."},
            {"❄️ Cocaïne","Risques cardiovasculaires, douleur thoracique, troubles neurologiques, agitation. Un produit vendu sous ce nom peut contenir des substances inattendues."},
            {"⚡ Speed / amphétamines","Risques de surchauffe, palpitations, anxiété et insomnie. La fatigue peut être masquée."},
            {"🌈 LSD","Les effets psychiques peuvent être intenses et prolongés ; attention aux accidents, à la confusion et au risque de produits vendus sous une fausse identité."},
            {"🌿 Cannabis","L'attention et la coordination peuvent être altérées ; anxiété et malaise possibles. Ne pas conduire après consommation."},
            {"🍄 Champignons hallucinogènes","Risques de panique et de désorientation. L'identification erronée d'un champignon peut entraîner une intoxication grave."},
            {"💧 GHB / GBL","Risque important de perte de conscience et de ralentissement respiratoire, surtout avec alcool ou autres dépresseurs."},
            {"💨 Poppers","Peuvent provoquer des vertiges ou malaises et une chute de tension. Les associations avec des médicaments vasodilatateurs sont dangereuses."},
            {"💊 Opioïdes","Surdose possible avec ralentissement ou arrêt respiratoire ; appeler le 15/112. La naloxone peut sauver une vie si elle est disponible."}
        };
        for(String[] sheet:sheets) {
            LinearLayout card=card();
            add(card,txt(sheet[0],17,true,WHITE),7);
            add(card,txt(sheet[1],14,false,SOFT),11);
            add(card,btn("Lire la fiche de référence",()->open(
                "https://www.drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues")),0);
            add(body,card,10);
        }
        add(body,txt("Sources : Drogues Info Service, dispositifs CAARUD et CSAPA. Les informations pratiques peuvent changer : vérifier avant déplacement.",12,false,SOFT),12);
    }
    private void facility(String name,String desc,String address,String number,String email,String site,String query) {
        LinearLayout c=card();
        add(c,txt(name,17,true,WHITE),6);
        add(c,txt(desc,13,false,SOFT),8);
        add(c,txt(address,14,false,WHITE),8);
        add(c,btn("📞 "+number,()->phone(number)),7);
        if(!email.isEmpty())add(c,btn("✉ "+email,()->
            open("mailto:"+email)),7);
        add(c,btn("Site ou fiche officielle",()->open(site)),7);
        add(c,btn("📍 Voir sur la carte",()->open("geo:0,0?q="+Uri.encode(query))),0);
        add(body,c,11);
    }
    private void chooseTown(){
        EditText city=new EditText(this);
        city.setSingleLine(true);city.setText(town);
        LinearLayout space=col();space.setPadding(dp(18),0,dp(18),0);space.addView(city);
        NeonDialog.builder(this).setTitle("Changer de ville ou d'adresse")
            .setMessage("Aucune autorisation GPS n'est nécessaire. L'adresse sert uniquement à ouvrir une recherche autour du lieu choisi.")
            .setView(space).setNegativeButton("Annuler",null)
            .setPositiveButton("Valider",(d,w)->{
                String value=city.getText().toString().trim();
                if(value.isEmpty())return;
                town=value;
                getPreferences(0).edit().putString("city",town).apply();
                showPage();
            }).show();
    }
}
