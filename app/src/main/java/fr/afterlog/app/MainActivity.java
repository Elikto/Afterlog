package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 14, 24);
    private static final int PANEL = Color.rgb(25, 28, 44);
    private static final int ACCENT = Color.rgb(186, 166, 255);
    private static final int SOFT = Color.rgb(182, 188, 205);
    private static final String[] CATEGORIES = {
        "Alcool", "Autres substances", "Sans alcool", "Médicaments", "Autre"
    };
    private AppStore store;
    private LinearLayout page;
    private long currentEventId = 0;
    private static final int EXPORT = 51, IMPORT = 52, SHOTGUN_REQUEST = 53;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AppStore(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(radius));
        return bg;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private void add(LinearLayout parent, View child, int marginBottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(marginBottom);
        parent.addView(child, lp);
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }
    private TextView button(String label, boolean primary, Runnable click) {
        TextView b = text(label, 15, primary ? BG : Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(15), dp(18), dp(15));
        b.setBackground(rounded(primary ? ACCENT : PANEL, 14));
        b.setOnClickListener(v -> click.run());
        return b;
    }
    private LinearLayout card() {
        LinearLayout l = column();
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(rounded(PANEL,18));
        return l;
    }
    private void screen(String title, Runnable back) {
        LinearLayout outer = column(); outer.setBackgroundColor(BG);
        outer.setPadding(dp(16), dp(14), dp(16), 0);
        setContentView(outer);
        if (back != null) add(outer, button("← Retour", false, back), 12);
        add(outer, text(title, 26, Color.WHITE, true), 6);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        page = column();
        page.setPadding(0,dp(12),0,dp(26));
        scroll.addView(page);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
    }
    private void subtitle(String s) { add(page, text(s,14,SOFT,false),16); }
    private void line(LinearLayout where, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        add(where,text(label.toUpperCase(Locale.ROOT),11,ACCENT,true),4);
        add(where,text(value,15,Color.WHITE,false),13);
    }
    private void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private String dateNow() { return LocalDate.now().toString(); }
    private String timeNow() { return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")); }

    private void showHome() {
        screen("AfterLog", null);
        subtitle("Ton journal personnel de soirées. Données conservées sur ton téléphone.");
        add(page, button("＋  Ajouter une soirée", true, () -> editEvent(new JSONObject())),10);
        add(page, button("🔐  Connexion Shotgun · Mes billets", true, this::connectShotgun),10);
        add(page, button("↗  Importer un lien Shotgun", false, this::importShotgun),10);
        add(page, button("Statistiques", false, this::showStats),10);
        add(page, button("Réglages et sauvegarde", false, this::showSettings),24);
        List<JSONObject> events = store.events();
        add(page,text("MES SOIRÉES  •  "+events.size(),15,ACCENT,true),13);
        if(events.isEmpty()) {
            LinearLayout c=card();
            add(c,text("Aucune soirée enregistrée",18,Color.WHITE,true),8);
            add(c,text("Ajoute une soirée manuellement ou colle son lien Shotgun.",14,SOFT,false),0);
            add(page,c,12);
        }
        for(JSONObject e:events) {
            LinearLayout c=card();
            String url=e.optString("poster");
            if(!url.isEmpty()) {
                ImageView cover=new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackground(rounded(Color.rgb(40,40,60),12));
                LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(-1,dp(155));
                imageLp.bottomMargin=dp(12); c.addView(cover,imageLp);
                loadImage(cover,url);
            }
            add(c,text(e.optString("title","Soirée"),19,Color.WHITE,true),7);
            add(c,text(e.optString("date")+"   "+e.optString("start"),14,ACCENT,false),6);
            add(c,text((e.optString("venue")+" · "+e.optString("city")).replaceAll("^ · | · $",""),14,SOFT,false),7);
            add(c,text("Voir la soirée et mes consommations ›",13,Color.WHITE,true),0);
            c.setOnClickListener(v -> showEvent(e.optLong("id")));
            add(page,c,12);
        }
    }

    private void showEvent(long id) {
        JSONObject event=store.event(id);
        if(event==null) { showHome(); return; }
        currentEventId=id;
        screen("Ma soirée", this::showHome);
        String poster=event.optString("poster");
        if(!poster.isEmpty()) {
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(PANEL,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(200));
            lp.bottomMargin=dp(16); page.addView(image,lp);
            loadImage(image,poster);
        }
        add(page,text(event.optString("title","Soirée"),24,Color.WHITE,true),10);
        add(page,text(event.optString("date")+"  "+event.optString("start")+
            (event.optString("end").isEmpty() ? "" : " → "+event.optString("end")),15,ACCENT,false),18);
        add(page,button("＋  Ajouter une consommation",true,()->editEntry(new JSONObject(),id)),10);
        add(page,button("Modifier les informations",false,()->editEvent(event)),14);
        LinearLayout info=card();
        line(info,"Lieu",event.optString("venue"));
        line(info,"Ville",event.optString("city"));
        line(info,"Adresse",event.optString("address"));
        line(info,"Organisateur",event.optString("organizer"));
        line(info,"Artistes",event.optString("artists"));
        line(info,"Genres",event.optString("genres"));
        line(info,"Description",event.optString("description"));
        line(info,"Mes notes",event.optString("notes"));
        line(info,"Source",event.optString("origin"));
        String url=event.optString("url");
        if(!url.isEmpty()) add(info,button("Ouvrir sur Shotgun",false,()->{
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        }),0);
        add(page,info,22);
        List<JSONObject> entries=store.entries(id);
        add(page,text("MES CONSOMMATIONS  •  "+entries.size(),16,ACCENT,true),13);
        if(entries.isEmpty()) add(page,text("Aucune consommation enregistrée pour cette soirée.",14,SOFT,false),14);
        for(JSONObject entry:entries) {
            LinearLayout c=card();
            add(c,text(entry.optString("name","Consommation"),18,Color.WHITE,true),6);
            add(c,text(entry.optString("category")+" · "+
                entry.optString("quantity","1")+" "+entry.optString("unit"),14,ACCENT,false),6);
            add(c,text(entry.optString("date")+"  "+entry.optString("time"),13,SOFT,false),6);
            if(entry.optDouble("cost",0)!=0) add(c,text(String.format(Locale.FRANCE,"%.2f €",entry.optDouble("cost")),14,SOFT,false),6);
            if(!entry.optString("notes").isEmpty()) add(c,text(entry.optString("notes"),14,SOFT,false),7);
            add(c,text("Modifier ›",13,Color.WHITE,true),0);
            c.setOnClickListener(v->editEntry(entry,id));
            add(page,c,10);
        }
        add(page,button("Supprimer cette soirée",false,()->new AlertDialog.Builder(this)
            .setTitle("Supprimer la soirée ?")
            .setMessage("Les consommations associées seront aussi supprimées.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(d,w)->{store.deleteEvent(id);showHome();})
            .show()),0);
    }

    private EditText field(LinearLayout holder,String label,String value,boolean multiline) {
        add(holder,text(label,12,SOFT,true),5);
        EditText input=new EditText(this);
        input.setSingleLine(!multiline);
        if(multiline) {input.setMinLines(3);input.setGravity(Gravity.TOP);}
        input.setText(value);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(SOFT);
        input.setTextSize(15);
        input.setPadding(dp(12),dp(12),dp(12),dp(12));
        input.setBackground(rounded(PANEL,10));
        add(holder,input,12);
        return input;
    }
    private void editEvent(JSONObject original) {
        long id=original.optLong("id");
        screen(id==0?"Nouvelle soirée":"Modifier la soirée",()->{
            if(id==0)showHome();else showEvent(id);
        });
        LinkedHashMap<String,EditText> fields=new LinkedHashMap<>();
        String[][] definitions={
            {"title","Nom de l'événement"},{"date","Date (AAAA-MM-JJ)"},
            {"start","Heure de début (HH:mm)"},{"end","Heure de fin (HH:mm)"},
            {"venue","Lieu"},{"city","Ville"},{"address","Adresse"},
            {"artists","Artistes / line-up"},{"organizer","Organisateur"},
            {"genres","Genres musicaux"},{"poster","Lien de l'affiche"},
            {"url","Lien Shotgun"},{"description","Description"},
            {"notes","Mes notes personnelles"}
        };
        for(String[] def:definitions) {
            boolean multi=def[0].equals("description")||def[0].equals("notes");
            String defaultValue=def[0].equals("date") && id==0 ? dateNow() : "";
            fields.put(def[0],field(page,def[1],original.optString(def[0],defaultValue),multi));
        }
        add(page,button("Enregistrer la soirée",true,()->{
            try {
                JSONObject e=new JSONObject(original.toString());
                for(Map.Entry<String,EditText> item:fields.entrySet())
                    e.put(item.getKey(),item.getValue().getText().toString().trim());
                if(e.optString("title").isEmpty()){message("Indique un nom");return;}
                if(e.optString("origin").isEmpty())e.put("origin","Manuel");
                JSONObject saved=store.saveEvent(e);
                showEvent(saved.getLong("id"));
            } catch(Exception ex){message("Enregistrement impossible : "+ex.getMessage());}
        }),0);
    }

    private void editEntry(JSONObject original,long eventId) {
        long entryId=original.optLong("id");
        screen(entryId==0?"Nouvelle consommation":"Modifier la consommation",()->showEvent(eventId));
        add(page,text("Catégorie",12,SOFT,true),6);
        Spinner select=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,CATEGORIES);
        select.setAdapter(adapter);
        for(int i=0;i<CATEGORIES.length;i++) if(CATEGORIES[i].equals(original.optString("category"))) select.setSelection(i);
        add(page,select,14);
        EditText name=field(page,"Nom / produit",original.optString("name"),false);
        EditText qty=field(page,"Quantité",original.optString("quantity","1"),false);
        qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText unit=field(page,"Unité (verre, cl, mg, etc.)",original.optString("unit","verre"),false);
        EditText date=field(page,"Date (AAAA-MM-JJ)",original.optString("date",store.event(eventId).optString("date",dateNow())),false);
        EditText time=field(page,"Heure (HH:mm)",original.optString("time",timeNow()),false);
        EditText cost=field(page,"Coût (€)",original.optString("cost","0"),false);
        cost.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText notes=field(page,"Notes",original.optString("notes"),true);
        add(page,button("Enregistrer",true,()->{
            try {
                if(name.getText().toString().trim().isEmpty()){message("Indique un nom");return;}
                JSONObject e=new JSONObject(original.toString());
                e.put("eventId",eventId);
                e.put("category",select.getSelectedItem().toString());
                e.put("name",name.getText().toString().trim());
                e.put("quantity",Double.parseDouble(qty.getText().toString().trim().replace(",",".")));
                e.put("unit",unit.getText().toString().trim());
                e.put("date",date.getText().toString().trim());
                e.put("time",time.getText().toString().trim());
                e.put("cost",Double.parseDouble(cost.getText().toString().trim().replace(",",".")));
                e.put("notes",notes.getText().toString().trim());
                if(e.optDouble("quantity")<0 || e.optDouble("cost")<0) {message("Valeurs négatives non autorisées");return;}
                store.saveEntry(e);showEvent(eventId);
            } catch(Exception ex){message("Vérifie les quantités et les champs numériques");}
        }),12);
        if(entryId!=0)add(page,button("Supprimer cette consommation",false,()->
            new AlertDialog.Builder(this).setTitle("Supprimer cette entrée ?")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Supprimer",(d,w)->{store.deleteEntry(entryId);showEvent(eventId);}).show()),0);
    }

    private void showStats() {
        screen("Statistiques",this::showHome);
        List<JSONObject> all=store.allEntries();
        List<JSONObject> events=store.events();
        double cost=0;
        LinkedHashMap<String,Integer> count=new LinkedHashMap<>();
        for(JSONObject e:all) {
            cost+=e.optDouble("cost");
            String c=e.optString("category","Autre");
            count.put(c,count.containsKey(c)?count.get(c)+1:1);
        }
        LinearLayout info=card();
        line(info,"Soirées",String.valueOf(events.size()));
        line(info,"Consommations consignées",String.valueOf(all.size()));
        line(info,"Total dépensé",String.format(Locale.FRANCE,"%.2f €",cost));
        add(page,info,16);
        add(page,text("Par catégorie",18,ACCENT,true),12);
        for(Map.Entry<String,Integer> entry:count.entrySet()) {
            LinearLayout c=card();
            add(c,text(entry.getKey()+"  ·  "+entry.getValue()+" entrée(s)",16,Color.WHITE,true),0);
            add(page,c,10);
        }
        subtitle("Les catégories utilisent des unités différentes : leurs quantités ne sont pas additionnées.");
    }

    private void connectShotgun() {
        Intent browser = new Intent(this, ShotgunBrowserActivity.class);
        startActivityForResult(browser, SHOTGUN_REQUEST);
    }
    private void importShotgun() {
        EditText input=new EditText(this);
        input.setSingleLine(false);
        input.setTextColor(BG);
        input.setHint("https://shotgun.live/...");
        input.setPadding(dp(16),dp(10),dp(16),dp(10));
        new AlertDialog.Builder(this)
            .setTitle("Importer via le site Shotgun")
            .setMessage("L'ancien téléchargement direct est supprimé (erreur HTTP 429). Le lien s'ouvre maintenant dans le navigateur Shotgun intégré.")
            .setView(input)
            .setNegativeButton("Annuler",null)
            .setNeutralButton("Saisie manuelle",(d,w)->editEvent(new JSONObject()))
            .setPositiveButton("Ouvrir sur Shotgun",(d,w)->{
                String url=input.getText().toString().trim();
                try {
                    Uri uri=Uri.parse(url);
                    String host=uri.getHost();
                    if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||
                        !(host.equalsIgnoreCase("shotgun.live") ||
                          host.equalsIgnoreCase("www.shotgun.live"))) {
                        message("Entre un lien HTTPS shotgun.live");return;
                    }
                    Intent browser=new Intent(this,ShotgunBrowserActivity.class);
                    browser.putExtra(ShotgunBrowserActivity.EXTRA_URL,url);
                    startActivityForResult(browser,SHOTGUN_REQUEST);
                }catch(Exception ex){message("Lien Shotgun invalide");}
            }).show();
    }
    private void receiveShotgunEvents(Intent data) {
        try {
            JSONArray records=new JSONArray(data.getStringExtra(ShotgunBrowserActivity.EXTRA_EVENTS));
            if(records.length()==0){message("Aucun événement trouvé");return;}
            if(records.length()==1){
                JSONObject item=records.getJSONObject(0);
                String canonical=normalizedEventUrl(item.optString("url"));
                item.put("url",canonical);
                JSONObject existing=store.eventByUrl(canonical);
                if(existing!=null){message("Cette soirée figure déjà dans ton journal");showEvent(existing.optLong("id"));return;}
                editEvent(item);
                message("Vérifie les informations et enregistre la soirée");
                return;
            }
            int added=0,skipped=0;
            for(int i=0;i<records.length();i++){
                JSONObject item=records.getJSONObject(i);
                String canonical=normalizedEventUrl(item.optString("url"));
                if(canonical.isEmpty()||item.optString("title").trim().isEmpty()){skipped++;continue;}
                if(store.eventByUrl(canonical)!=null){skipped++;continue;}
                item.put("url",canonical);
                store.saveEvent(item);added++;
            }
            showHome();
            new AlertDialog.Builder(this).setTitle("Import terminé")
                .setMessage(added+" soirée(s) ajoutée(s), "+skipped+" déjà présente(s) ou ignorée(s).\n\nSeuls les liens visibles ont été importés. Les détails manquants peuvent être complétés sur chaque fiche.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){message("Import impossible : "+e.getMessage());}
    }
    private String normalizedEventUrl(String url) {
        try {
            Uri u=Uri.parse(url);
            String host=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme()) || host==null ||
                !(host.equalsIgnoreCase("shotgun.live") || host.equalsIgnoreCase("www.shotgun.live")))
                return "";
            return u.buildUpon().clearQuery().fragment(null).build().toString();
        }catch(Exception e){return "";}
    }

    private void showSettings() {
        screen("Confidentialité et sauvegarde",this::showHome);
        subtitle("Les consommations sont stockées localement sur cet appareil. Aucun compte Shotgun ou Gmail n'est nécessaire.");
        add(page,button("Exporter toutes mes données (JSON)",true,()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"afterlog-sauvegarde.json");
            startActivityForResult(intent,EXPORT);
        }),10);
        add(page,button("Importer une sauvegarde JSON",false,()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent,IMPORT);
        }),14);
        subtitle("Attention : la sauvegarde JSON exportée contient ton journal en clair. Garde-la dans un emplacement privé. L'import remplace toutes les données actuelles.");
        subtitle("Connecte-toi à Shotgun dans le navigateur intégré. Ouvre « Mes billets → Passés », puis importe les liens affichés. Une synchronisation complète automatique n’est pas encore disponible.");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null)return;
        if(requestCode==SHOTGUN_REQUEST){receiveShotgunEvents(data);return;}
        if(data.getData()==null)return;
        Uri uri=data.getData();
        try {
            if(requestCode==EXPORT) {
                try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                    if(out==null)throw new IllegalStateException("Fichier inaccessible");
                    out.write(store.backup().getBytes(StandardCharsets.UTF_8));
                }
                message("Sauvegarde exportée");
            } else if(requestCode==IMPORT) {
                StringBuilder json=new StringBuilder();
                try(InputStream in=getContentResolver().openInputStream(uri);
                    BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
                    char[] buf=new char[4096]; int n;
                    while((n=reader.read(buf))!=-1) {
                        json.append(buf,0,n);
                        if(json.length()>20_000_000)throw new IllegalArgumentException("Fichier trop grand");
                    }
                }
                String content=json.toString();
                new AlertDialog.Builder(this).setTitle("Remplacer les données ?")
                    .setMessage("Cette sauvegarde remplacera entièrement ton journal actuel.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Importer",(d,w)->{
                        try {store.restore(content);message("Données restaurées");showHome();}
                        catch(Exception ex){message("Erreur de sauvegarde : "+ex.getMessage());}
                    }).show();
            }
        }catch(Exception ex){message("Erreur : "+ex.getMessage());}
    }
    private void loadImage(ImageView image,String url) {
        if(!url.startsWith("https://"))return;
        new Thread(()->{
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                try(InputStream in=connection.getInputStream()) {
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    opts.inSampleSize=4;
                    Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);
                    if(bmp!=null)runOnUiThread(()->image.setImageBitmap(bmp));
                }
            }catch(Exception ignored){}
            finally {if(connection!=null)connection.disconnect();}
        }).start();
    }
}
