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
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.text.TextWatcher;
import android.text.Editable;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class RdrActivity extends Activity {
    private static final int BG=Color.rgb(12,14,24),CARD=Color.rgb(25,28,44),
        ACCENT=Color.rgb(186,166,255),WHITE=Color.WHITE,SOFT=Color.rgb(183,189,208);
    private LinearLayout body,placesContainer;
    private String town;
    private long searchVersion=0;
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
        add(helpline,txt("0 800 23 13 13 · anonyme et gratuit · 7j/7 de 8 h à 2 h, 7j/7 (vérifier les horaires avant d’appeler).",14,false,SOFT),11);
        add(helpline,btn("📞 Appeler gratuitement",()->phone("0800231313")),7);
        add(helpline,btn("🌐 Site et chat",()->open("https://www.drogues-info-service.fr/")),0);
        add(body,helpline,17);

        add(body,txt("📍 Structures et associations",20,true,WHITE),9);
        add(body,txt("CAARUD, CSAPA et structures d'addictologie proches de l'adresse de ton choix.",
            13,false,SOFT),12);
        add(body,btn("📍 "+town+"  ✎ Changer",this::chooseTown),8);
        add(body,btn("↻ Actualiser les résultats",()->searchPlaces(town)),11);
        placesContainer=col();
        add(body,placesContainer,13);
        // Unlike the old Arles-only hardcoded cards, results are looked up
        // around the address currently selected on every page visit.
        searchPlaces(town);

        add(body,btn("🔎 Annuaire officiel · Drogues Info Service",()->
            open("https://www.drogues-info-service.fr/Adresses-utiles")),8);
        add(body,btn("📍 Rechercher sur une carte",()->open(
            "https://www.google.com/maps/search/?api=1&query="+
                Uri.encode("CAARUD CSAPA réduction des risques "+town))),16);

        add(body,txt("🧪 Analyse de produits",20,true,WHITE),9);
        LinearLayout analysis=card();
        add(analysis,txt("Analyse ton prod",17,true,WHITE),6);
        add(analysis,txt("Recherche de dispositifs d'analyse des substances psychoactives. Contacte la structure avant de te déplacer : les modalités et disponibilités varient.",14,false,SOFT),9);
        add(analysis,btn("Consulter les points d'analyse",()->open("https://www.analysetonprod.fr/")),9);
        add(analysis,btn("Trouver un CAARUD proche",()->open(
            "https://www.drogues-info-service.fr/Adresses-utiles")),0);
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
    private void searchPlaces(String address){
        if(placesContainer==null)return;
        long version=++searchVersion;
        placesContainer.removeAllViews();
        LinearLayout loading=card();
        add(loading,txt("Recherche autour de "+address+"…",15,true,ACCENT),7);
        add(loading,txt("Recherche de structures proches à partir de l'adresse renseignée.",
            12,false,SOFT),0);
        add(placesContainer,loading,9);
        RdrPlacesSearch.search(address,result->{
            if(version!=searchVersion||isFinishing()||placesContainer==null)return;
            placesContainer.removeAllViews();
            if(!result.resolved.isEmpty()){
                add(placesContainer,txt("Adresse reconnue : "+result.resolved,
                    13,false,SOFT),9);
            }
            if(!result.error.isEmpty()){
                LinearLayout warning=card();
                add(warning,txt(result.error,14,false,SOFT),10);
                add(warning,btn("Réessayer",()->searchPlaces(town)),0);
                add(placesContainer,warning,11);
            }
            if(result.places.isEmpty()){
                LinearLayout empty=card();
                add(empty,txt("Aucune structure trouvée dans la base cartographique.",
                    15,true,WHITE),7);
                add(empty,txt("La couverture d'OpenStreetMap est incomplète. Cela ne signifie pas qu'aucun CAARUD ou CSAPA n'existe dans cette zone. Consulte l'annuaire national.",
                    13,false,SOFT),11);
                add(empty,btn("Consulter l'annuaire officiel",()->
                    open(result.officialDirectory())),0);
                add(placesContainer,empty,12);
            }else{
                add(placesContainer,txt(result.places.size()+
                    " structure(s) cartographiée(s), par proximité",14,true,ACCENT),11);
                for(RdrPlacesSearch.Place place:result.places)
                    facility(place);
                add(placesContainer,txt("Distances à vol d'oiseau. Informations OpenStreetMap à vérifier avant le déplacement ; la liste n'est pas exhaustive.",
                    12,false,SOFT),10);
                add(placesContainer,btn("Compléter avec l'annuaire national",()->
                    open(result.officialDirectory())),11);
            }
        });
    }
    private void facility(RdrPlacesSearch.Place place){
        LinearLayout c=card();
        add(c,txt(place.name,17,true,WHITE),6);
        add(c,txt(place.category+" · "+
            String.format(java.util.Locale.FRANCE,"%.1f km",place.distanceKm)+
            " à vol d'oiseau",13,true,ACCENT),7);
        if(!place.address.isEmpty())add(c,txt(place.address,14,false,WHITE),9);
        if(!place.phone.isEmpty())
            add(c,btn("📞 "+place.phone,()->phone(place.phone)),7);
        if(!place.email.isEmpty())
            add(c,btn("✉ "+place.email,()->open(
                "mailto:"+Uri.encode(place.email))),7);
        if(place.website.startsWith("https://")||place.website.startsWith("http://"))
            add(c,btn("🌐 Site de la structure",()->open(place.website)),7);
        add(c,btn("📍 Itinéraire",()->open(
            "https://www.google.com/maps/search/?api=1&query="+
            Uri.encode(String.format(java.util.Locale.ROOT,"%.6f,%.6f",place.lat,place.lon)))),0);
        add(placesContainer,c,10);
    }
    private void chooseTown(){
        AutoCompleteTextView city=new AutoCompleteTextView(this);
        city.setSingleLine(true);
        city.setThreshold(2);
        city.setTextColor(WHITE);
        city.setHintTextColor(SOFT);
        city.setHint("Ex. Toulouse, Arles, Marseille…");
        city.setText(town);
        city.setSelection(city.length());
        LinearLayout space=col();
        space.setPadding(dp(18),0,dp(18),0);
        add(space,txt("Entre le début d'une ville, puis sélectionne la proposition.",
            13,false,SOFT),8);
        space.addView(city);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(
            this,android.R.layout.simple_dropdown_item_1line,new ArrayList<>());
        city.setAdapter(adapter);
        city.setDropDownBackgroundDrawable(bg(CARD));
        final ArrayList<RdrPlacesSearch.City> suggestions=new ArrayList<>();
        final String[] selected={town};
        final int[] querySequence={0};
        final boolean[] filling={false};
        Handler debounce=new Handler(Looper.getMainLooper());
        city.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                if(filling[0])return;
                selected[0]="";
                final int seq=++querySequence[0];
                String input=text.toString().trim();
                if(input.length()<2){adapter.clear();return;}
                debounce.postDelayed(()->{
                    if(seq!=querySequence[0]||isFinishing())return;
                    RdrPlacesSearch.suggestCities(input,results->{
                        if(seq!=querySequence[0]||isFinishing())return;
                        suggestions.clear();suggestions.addAll(results);
                        adapter.clear();
                        for(RdrPlacesSearch.City option:results)adapter.add(option.label);
                        adapter.notifyDataSetChanged();
                        if(!results.isEmpty())city.showDropDown();
                    });
                },350);
            }
            @Override public void afterTextChanged(Editable text){}
        });
        city.setOnItemClickListener((parent,view,position,id)->{
            // AutoCompleteTextView filters the adapter: position in the popup
            // is not necessarily the original IGN suggestion index.
            String displayed=String.valueOf(parent.getItemAtPosition(position));
            RdrPlacesSearch.City option=null;
            for(RdrPlacesSearch.City candidate:suggestions){
                if(candidate.label.equals(displayed)){option=candidate;break;}
            }
            if(option==null)return;
            filling[0]=true;
            city.setText(option.label);
            city.setSelection(city.length());
            filling[0]=false;
            selected[0]=option.query;
            city.dismissDropDown();
        });
        NeonDialog.builder(this).setTitle("Choisir une ville")
            .setMessage("Les associations seront recherchées autour de la ville validée, pas autour de l'ancienne adresse.")
            .setView(space)
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Valider la ville",(d,w)->{
                String value=selected[0].trim();
                if(value.isEmpty()){
                    Toast.makeText(this,
                        "Sélectionne une ville dans les suggestions avant de valider.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                town=value;
                getPreferences(0).edit().putString("city",town).apply();
                showPage();
            }).show();
    }

}
