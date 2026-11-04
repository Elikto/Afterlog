package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Éditeur visuel avec rubriques et galerie photo, sans formulaire monolithique. */
public final class EventEditorActivity extends Activity {
    static final String EXTRA_EVENT="event_json", EXTRA_SAVED_ID="saved_id";
    private static final int PICK_IMAGES=704;
    private static final int PICK_VIDEOS=705;
    private static final int PICK_MEDIA=706;
    private static final int PICK_POSTER=707;
    private static final int BG=Color.rgb(12,14,24),CARD=Color.rgb(25,28,44);
    private static final int TILE=Color.rgb(38,41,64),ACCENT=Color.rgb(186,166,255);
    private static final int WHITE=Color.WHITE,SOFT=Color.rgb(185,190,205);
    private JSONObject event=new JSONObject();
    private AppStore store;
    private final LinkedHashMap<String,EditText> inputs=new LinkedHashMap<>();
    private LinearLayout images,videoItems,details,extra;
    private TextView videoCount;
    private FrameLayout posterPreview;
    private TextView dateButton,startButton,endButton,photoCount;
    private boolean advanced=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new AppStore(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try {
            String json=getIntent().getStringExtra(EXTRA_EVENT);
            if(json!=null)event=new JSONObject(json);
        }catch(Exception ignored){}
        render();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void add(LinearLayout group,View v,int bottom){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(bottom);group.addView(v,lp);
    }
    private TextView button(String label,boolean primary,Runnable click){
        TextView t=text(label,15,primary?BG:WHITE,true);
        t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(15),dp(12),dp(15));
        t.setBackground(background(primary?ACCENT:TILE,14));
        t.setOnClickListener(v->click.run());return t;
    }
    private LinearLayout group(String title,String subtitle,LinearLayout parent) {
        LinearLayout section=column();
        section.setPadding(dp(16),dp(16),dp(16),dp(16));
        section.setBackground(background(CARD,18));
        add(parent,section,15);
        add(section,text(title,19,WHITE,true),4);
        if(subtitle!=null && !subtitle.isEmpty())add(section,text(subtitle,13,SOFT,false),14);
        return section;
    }
    private EditText field(LinearLayout parent,String key,String label,String hint,boolean multiline) {
        add(parent,text(label,13,SOFT,true),6);
        EditText edit=new EditText(this);
        edit.setTextSize(16);edit.setTextColor(WHITE);edit.setHintTextColor(SOFT);edit.setHint(hint);
        edit.setBackground(background(TILE,12));
        edit.setPadding(dp(13),dp(13),dp(13),dp(13));
        edit.setSingleLine(!multiline);
        if(multiline){edit.setMinLines(4);edit.setGravity(Gravity.TOP);}
        edit.setText(event.optString(key));
        add(parent,edit,13);inputs.put(key,edit);return edit;
    }
    private void render() {
        LinearLayout root=column();root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12),dp(12),dp(16),dp(10));
        root.addView(top);
        TextView back=button("‹",false,this::finish);
        top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout heading=column();
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.leftMargin=dp(12);top.addView(heading,p);
        add(heading,text(event.optLong("id")==0?"Créer une soirée":"Modifier la soirée",22,WHITE,true),4);
        add(heading,text("Une fiche à ton image",13,SOFT,false),0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();
        content.setPadding(dp(16),dp(12),dp(16),dp(24));
        scroll.addView(content);
        inputs.clear();
        LinearLayout basic=group("✨ L'essentiel","Nom, date et horaires",content);
        EditText titleField=field(basic,"title","Nom de l'événement",
            "Ex. : Soirée chez Alex, festival, concert…",false);
        titleField.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                refreshPosterPreview();
            }
            @Override public void afterTextChanged(Editable e){}
        });
        dateButton=button("📅 Choisir une date",false,this::chooseDate);
        add(basic,dateButton,11);
        startButton=button("🕒 Heure de début",false,()->chooseTime("start"));
        add(basic,startButton,11);
        endButton=button("🕔 Heure de fin",false,()->chooseTime("end"));
        add(basic,endButton,0);
        refreshDate();

        LinearLayout posterSection=group("Affiche de ma soirée",
            "Une affiche NØCTARIA est fournie automatiquement, même pour une soirée chez un ami.",content);
        posterPreview=new FrameLayout(this);
        add(posterSection,posterPreview,11);
        posterPreview.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(210)));
        add(posterSection,button("Choisir ma propre photo",false,this::pickPoster),9);
        add(posterSection,button("Revenir à l'affiche NØCTARIA",false,()->{
            event.remove("posterLocal");
            try{event.put("poster","");}catch(Exception ignored){}
            EditText url=inputs.get("poster");
            if(url!=null)url.setText("");
            refreshPosterPreview();
        }),0);
        refreshPosterPreview();

        LinearLayout gallery=group("Galerie de la soirée",
            "Photos et vidéos, visibles dans une galerie plein écran.",content);
        add(gallery,button("＋ Ajouter des médias",true,this::pickMedia),12);
        photoCount=text("",13,SOFT,false);
        add(gallery,photoCount,8);
        images=column();add(gallery,images,8);
        videoCount=text("",13,SOFT,false);
        add(gallery,videoCount,8);
        videoItems=column();add(gallery,videoItems,0);
        showImages();showVideos();

        LinearLayout where=group("📍 Où ça se passe ?","Lieu et localisation",content);
        field(where,"venue","Nom du lieu","Salle, festival, club…",false);
        field(where,"city","Ville","Ex. Montpellier",false);
        field(where,"address","Adresse","Adresse complète (facultatif)",false);

        LinearLayout about=group("📝 À propos","Raconte ou importe l'histoire de cette soirée",content);
        field(about,"description","Description","Line-up, ambiance, programme, souvenirs…",true);

        LinearLayout artists=group("🎧 Musique & artistes","Personnalise la programmation",content);
        field(artists,"artists","Artistes","Noms séparés par une virgule",true);
        field(artists,"genres","Styles musicaux","Techno, hardcore, acid…",false);

        LinearLayout advancedSection=group("＋ Plus d'informations","Informations complémentaires",content);
        TextView show=button("Afficher les options",false,()->{
            advanced=!advanced;
            extra.setVisibility(advanced?View.VISIBLE:View.GONE);
        });
        add(advancedSection,show,9);
        extra=column();extra.setVisibility(View.GONE);add(advancedSection,extra,0);
        field(extra,"organizer","Organisateur","Organisateur de la soirée",false);
        field(extra,"poster","Lien de l'affiche Shotgun","https://…",false);
        field(extra,"url","Lien Shotgun","https://shotgun.live/…",false);
        field(extra,"notes","Mes notes personnelles","Notes privées",true);

        LinearLayout bottom=column();bottom.setPadding(dp(16),dp(8),dp(16),dp(15));
        root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        add(bottom,button("✓ Enregistrer la soirée",true,this::save),0);
    }
    private void refreshDate() {
        String date=event.optString("date");
        String label=date;
        try{label=LocalDate.parse(date).format(java.time.format.DateTimeFormatter
            .ofPattern("d MMMM yyyy",Locale.FRANCE));}
        catch(Exception ignored){}
        dateButton.setText(date.isEmpty()?"📅 Choisir une date":"📅 "+label);
        String start=event.optString("start"),end=event.optString("end");
        startButton.setText(start.isEmpty()?"🕒 Heure de début":"🕒 Début · "+start);
        endButton.setText(end.isEmpty()?"🕔 Heure de fin":"🕔 Fin · "+end);
    }
    private void chooseDate() {
        LocalDate date;
        try{date=LocalDate.parse(event.optString("date"));}
        catch(Exception e){date=LocalDate.now();}
        new DatePickerDialog(this,(v,y,m,d)->{
            try{event.put("date",String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d));}
            catch(Exception ignored){}
            refreshDate();
        },date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();
    }
    private void chooseTime(String key) {
        LocalTime time;
        try{time=LocalTime.parse(event.optString(key));}
        catch(Exception e){time=LocalTime.now();}
        new TimePickerDialog(this,(v,h,m)->{
            try{event.put(key,String.format(Locale.ROOT,"%02d:%02d",h,m));}
            catch(Exception ignored){}
            refreshDate();
        },time.getHour(),time.getMinute(),true).show();
    }
    private void refreshPosterPreview(){
        if(posterPreview==null)return;
        try{
            JSONObject preview=new JSONObject(event.toString());
            EditText input=inputs.get("title");
            if(input!=null)preview.put("title",input.getText().toString().trim());
            posterPreview.removeAllViews();
            posterPreview.addView(EventPoster.create(this,preview),
                new FrameLayout.LayoutParams(-1,-1));
        }catch(Exception ignored){}
    }
    private void pickPoster(){
        Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent,"Choisir une affiche"),
            PICK_POSTER);
    }
    private void pickMedia() {
        startActivityForResult(MediaPicker.photosAndVideos(),PICK_MEDIA);
    }
    private void previewMedia(int index) {
        Intent intent=new Intent(this,EventGalleryActivity.class);
        intent.putExtra(EventGalleryActivity.EXTRA_EVENT_JSON,event.toString());
        intent.putExtra(EventGalleryActivity.EXTRA_INDEX,index);
        startActivity(intent);
    }
    private void pickVideos() {
        Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(Intent.createChooser(intent,"Choisir des vidéos"),PICK_VIDEOS);
    }
    private void pickImages() {
        Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent,"Choisir des photos"),PICK_IMAGES);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null)return;
        if(request==PICK_POSTER){
            try{
                Uri uri=data.getData();
                if(uri==null)throw new Exception("Aucune image sélectionnée");
                String filename=EventPhotos.importUri(this,uri);
                event.put("posterLocal",filename);
                refreshPosterPreview();
            }catch(Exception error){
                Toast.makeText(this,"Impossible d'utiliser cette affiche : "+
                    error.getMessage(),Toast.LENGTH_LONG).show();
            }
            return;
        }
        if(request==PICK_MEDIA) {
            int success=0,errors=0;
            java.util.ArrayList<Uri> selected=new java.util.ArrayList<>();
            if(data.getClipData()!=null) {
                for(int i=0;i<data.getClipData().getItemCount();i++)
                    selected.add(data.getClipData().getItemAt(i).getUri());
            }else if(data.getData()!=null)selected.add(data.getData());
            for(Uri uri:selected){
                try{
                    String mime=getContentResolver().getType(uri);
                    if(mime!=null && mime.startsWith("image/")) {
                        if(EventPhotos.gallery(event).length()>=40)throw new Exception("Limite de photos");
                        EventPhotos.append(event,EventPhotos.importUri(this,uri));
                    }else{
                        if(EventVideos.list(event).length()>=EventVideos.MAX_COUNT)throw new Exception("Limite de vidéos");
                        EventVideos.append(event,EventVideos.importVideo(this,uri));
                    }
                    success++;
                }catch(Exception e){errors++;}
            }
            showImages();showVideos();
            Toast.makeText(this,success+" média(s) ajoutés"+(errors>0?" · "+errors+" non importés":""),
                Toast.LENGTH_LONG).show();
            return;
        }
        if(request==PICK_VIDEOS) {
            int added=0,errors=0;
            String lastError="";
            int count=data.getClipData()==null?1:data.getClipData().getItemCount();
            for(int i=0;i<count && i<12;i++){
                Uri uri=data.getClipData()==null?data.getData():
                    data.getClipData().getItemAt(i).getUri();
                try{
                    if(EventVideos.list(event).length()>=EventVideos.MAX_COUNT)break;
                    EventVideos.append(event,EventVideos.importVideo(this,uri));
                    added++;
                }catch(Exception e){errors++;lastError=e.getMessage();}
            }
            showVideos();
            Toast.makeText(this,added+" vidéo(s) ajoutée(s)"+
                (errors>0?" · "+errors+" erreur(s) : "+lastError:""),
                Toast.LENGTH_LONG).show();
            return;
        }
        if(request!=PICK_IMAGES)return;
        int added=0,errors=0;
        int n=data.getClipData()==null ? 1 : data.getClipData().getItemCount();
        for(int i=0;i<n && i<40;i++){
            Uri uri=data.getClipData()==null?data.getData():data.getClipData().getItemAt(i).getUri();
            try{
                if(EventPhotos.gallery(event).length()>=40)break;
                EventPhotos.append(event,EventPhotos.importUri(this,uri));
                added++;
            }catch(Exception e){errors++;}
        }
        showImages();
        Toast.makeText(this,added+" photo(s) ajoutée(s)"+
            (errors>0?" · "+errors+" non prises en charge":""),Toast.LENGTH_LONG).show();
    }
    private void showImages() {
        if(images==null)return;
        images.removeAllViews();
        JSONArray photos=EventPhotos.gallery(event);
        photoCount.setText(photos.length()+" / 40 photos");
        for(int i=0;i<photos.length();i+=3) {
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            add(images,row,8);
            for(int j=i;j<Math.min(photos.length(),i+3);j++){
                final int index=j;
                final String file=photos.optString(j);
                LinearLayout frame=column();
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
                lp.setMargins(dp(3),0,dp(3),0);row.addView(frame,lp);
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(background(TILE,10));
                frame.addView(image,new LinearLayout.LayoutParams(-1,dp(100)));
                Bitmap thumb=EventPhotos.preview(this,file,200);
                if(thumb!=null)image.setImageBitmap(thumb);
                image.setOnClickListener(v->previewMedia(index));
                add(frame,button("Retirer",false,()->NeonDialog.builder(this)
                    .setTitle("Retirer cette photo ?")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Retirer",(d,w)->{
                        try{EventPhotos.deleteAt(this,event,index);showImages();}
                        catch(Exception ignored){}
                    }).show()),0);
            }
        }
    }
    private void showVideos() {
        if(videoItems==null)return;
        videoItems.removeAllViews();
        JSONArray videos=EventVideos.list(event);
        videoCount.setText(videos.length()+" vidéo(s) · 1 Go max chacune");
        for(int index=0;index<videos.length();index++){
            int position=index;
            String video=videos.optString(index);
            LinearLayout item=column();
            item.setBackground(background(TILE,12));
            item.setPadding(dp(10),dp(11),dp(10),dp(11));
            add(videoItems,item,10);
            android.graphics.Bitmap preview=EventVideos.thumbnail(this,video);
            if(preview!=null) {
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(preview);
                item.addView(image,new LinearLayout.LayoutParams(-1,dp(135)));
            }
            add(item,button("▶ Lire dans la galerie "+(index+1),false,
                ()->previewMedia(EventPhotos.gallery(event).length()+position)),7);
            add(item,button("Retirer la vidéo",false,()->NeonDialog.builder(this)
                .setTitle("Supprimer cette vidéo ?")
                .setMessage("Elle sera supprimée du stockage de NØCTARIA.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Supprimer",(d,w)->{
                    try{EventVideos.remove(this,event,position);showVideos();}
                    catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}
                }).show()),0);
        }
    }
    private void save() {
        try{
            for(Map.Entry<String,EditText> field:inputs.entrySet())
                event.put(field.getKey(),field.getValue().getText().toString().trim());
            if(event.optString("title").isEmpty()) {
                Toast.makeText(this,"Donne un nom à la soirée",Toast.LENGTH_LONG).show();return;
            }
            if(event.optString("origin").isEmpty())event.put("origin","Manuel");
            JSONObject saved=store.saveEvent(event);
            Intent result=new Intent();result.putExtra(EXTRA_SAVED_ID,saved.getLong("id"));
            setResult(RESULT_OK,result);
            finish();
        }catch(Exception e){Toast.makeText(this,"Impossible d'enregistrer : "+e.getMessage(),
            Toast.LENGTH_LONG).show();}
    }
}
