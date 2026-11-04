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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.CheckBox;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
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
import java.util.ArrayList;
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
    private AppUpdater updater;
    private LinearLayout page;
    private long currentEventId = 0;
    private Runnable backAction;
    private ShotgunArtistAutoRefresh artistAutoRefresh;
    private boolean homeScreen=false;
    private boolean eventScreen=false;
    // Sélection persistante uniquement pendant l'affichage d'une même soirée.
    private long gallerySelectionEventId=0;
    private boolean gallerySelectionMode=false;
    private final java.util.LinkedHashSet<String> gallerySelected=new java.util.LinkedHashSet<>();
    private final java.util.ArrayList<GalleryMedia> galleryMedia=new java.util.ArrayList<>();
    private final java.util.LinkedHashMap<String,GalleryThumb> galleryThumbs=
        new java.util.LinkedHashMap<>();
    private LinearLayout gallerySelectRow,galleryActions;
    private TextView galleryToggle,gallerySelectAll,galleryCount,galleryHint;
    private TextView galleryDelete,galleryShare,galleryStory;
    private static final class GalleryMedia {
        final boolean video;
        final String name,key;
        GalleryMedia(boolean video,String name){
            this.video=video;
            this.name=name;
            this.key=(video?"video:":"photo:")+name;
        }
    }
    private static final class GalleryThumb {
        final FrameLayout frame;
        final TextView check;
        GalleryThumb(FrameLayout frame,TextView check){this.frame=frame;this.check=check;}
    }

    // Photos already loaded must survive the UI refresh triggered by each
    // newly resolved Spotify portrait (otherwise the display keeps showing initials).
    private final android.util.LruCache<String,Bitmap> imageMemory=
        new android.util.LruCache<String,Bitmap>(10*1024*1024){
            @Override protected int sizeOf(String key,Bitmap value){
                return value==null?0:value.getByteCount();
            }
        };
    private long renderedEventRevision=-1L;
    private long displayedEventsRevision=-1L;
    private final Handler syncHandler=new Handler(Looper.getMainLooper());
    private LinearLayout syncPanel;
    private String syncLastSignature="";
    private boolean syncActiveView=false;
    private long observedSyncEnd=0;
    private long selectionDialogStamp=0;
    private final Runnable syncTicker=new Runnable(){
        @Override public void run(){
            if(!syncActiveView)return;
            if(homeScreen && displayedEventsRevision!=AppStore.revision(MainActivity.this)){
                // Nouveaux événements enregistrés par le service : rafraîchir la
                // liste immédiatement, sans relancer ni redémarrer NØCTARIA.
                showHome();
            } else if(eventScreen && currentEventId>0 &&
                renderedEventRevision!=AppStore.revision(MainActivity.this)){
                showEvent(currentEventId);
            } else {
                renderSyncProgress();
            }
            syncHandler.postDelayed(this,1500);
        }
    };
    private static final int EXPORT = 51, IMPORT = 52, SHOTGUN_REQUEST = 53, CONSUMPTION_REQUEST = 54,
        EVENT_EDITOR_REQUEST=55,EVENT_IMAGES_REQUEST=56,EVENT_VIDEOS_REQUEST=57,
        SHOTGUN_EVENT_REFRESH=58,EVENT_MEDIA_REQUEST=59;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AppStore(this);
        updater = new AppUpdater(this);
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
        if(artistAutoRefresh!=null){artistAutoRefresh.stop();artistAutoRefresh=null;}
        homeScreen=back==null;
        eventScreen=false;
        backAction=back;
        LinearLayout outer = column(); outer.setBackgroundColor(BG);
        outer.setPadding(dp(16), dp(14), dp(16), 0);
        setContentView(outer);
        if(back==null){
            LinearLayout bar=new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(0,dp(2),0,dp(6));
            TextView update=text("↻ MAJ",12,ACCENT,true);
            update.setGravity(Gravity.CENTER);
            update.setPadding(dp(10),dp(12),dp(10),dp(12));
            update.setContentDescription("Rechercher une mise à jour");
            update.setOnClickListener(v->updater.check());
            bar.addView(update,new LinearLayout.LayoutParams(dp(64),dp(44)));
            LinearLayout branding=new LinearLayout(this);
            branding.setOrientation(LinearLayout.HORIZONTAL);
            branding.setGravity(Gravity.CENTER);
            ImageView logo=new ImageView(this);
            logo.setImageResource(R.drawable.ic_afterlog_foreground);
            logo.setContentDescription("Logo NØCTARIA");
            branding.addView(logo,new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView brand=text("NØCTARIA",26,Color.WHITE,true);
            brand.getPaint().setShader(new android.graphics.LinearGradient(0,0,dp(160),0,
                new int[]{Color.rgb(255,72,187),Color.rgb(203,177,255),Color.rgb(0,226,235)},
                null,android.graphics.Shader.TileMode.CLAMP));
            brand.setGravity(Gravity.CENTER_VERTICAL);
            branding.addView(brand,new LinearLayout.LayoutParams(-2,dp(48)));
            bar.addView(branding,new LinearLayout.LayoutParams(0,dp(48),1));
            TextView gear=text("⚙",28,Color.WHITE,true);
            gear.setGravity(Gravity.CENTER);
            gear.setContentDescription("Paramètres et sauvegarde");
            gear.setOnClickListener(v->showSettings());
            bar.addView(gear,new LinearLayout.LayoutParams(dp(55),dp(48)));
            add(outer,bar,6);
        }else{
            add(outer, button("← Retour", false, back), 12);
            add(outer, text(title, 24, Color.WHITE, true), 6);
        }
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

    /** Optional per-event selection: discovery is already finished in background. */
    private void showTicketChoice(){
        JSONArray found=TicketSelectionStore.list(this);
        if(found.length()==0){
            message("Aucune soirée Shotgun à sélectionner.");return;
        }
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13),dp(5),dp(13),dp(6));
        TextView desc=text("Décoche les soirées que tu ne souhaites pas importer. "+
            "Aucune page Shotgun ne s'ouvrira.",13,SOFT,false);
        add(content,desc,10);
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        add(content,toolbar,12);
        ScrollView scroll=new ScrollView(this);
        LinearLayout choices=new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(350)));
        java.util.ArrayList<CheckBox> boxes=new java.util.ArrayList<>();
        for(int i=0;i<found.length();i++){
            JSONObject item=found.optJSONObject(i);
            if(item==null)continue;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6),dp(7),dp(6),dp(7));
            String poster=item.optString("poster");
            ImageView thumb=new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(rounded(PANEL,9));
            LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(dp(46),dp(58));
            imageLp.rightMargin=dp(12);
            row.addView(thumb,imageLp);
            if(poster.startsWith("https://"))loadImage(thumb,poster);
            else thumb.setImageResource(R.drawable.ic_afterlog_foreground);
            CheckBox check=new CheckBox(this);
            check.setTextColor(Color.WHITE);
            check.setTextSize(14);
            check.setButtonTintList(ColorStateList.valueOf(ACCENT));
            check.setText(item.optString("title","Soirée")+
                (item.optString("date").isEmpty()?"":"\n"+item.optString("date")));
            check.setChecked(item.optBoolean("selected",true));
            check.setTag(i);
            check.setPadding(dp(3),dp(5),dp(3),dp(5));
            row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v->check.setChecked(!check.isChecked()));
            choices.addView(row,new LinearLayout.LayoutParams(-1,-2));
            boxes.add(check);
        }
        TextView all=button("Tout",false,()->{
            for(CheckBox b:boxes)b.setChecked(true);
        });
        TextView none=button("Aucun",false,()->{
            for(CheckBox b:boxes)b.setChecked(false);
        });
        toolbar.addView(all,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(none,new LinearLayout.LayoutParams(0,-2,1));
        NeonDialog.builder(this)
            .setTitle("Quelles soirées synchroniser ?")
            .setView(content)
            .setNegativeButton("Plus tard",null)
            .setPositiveButton("Synchroniser la sélection",(d,w)->{
                java.util.HashSet<Integer> selectedIds=new java.util.HashSet<>();
                for(CheckBox b:boxes)
                    if(b.isChecked())selectedIds.add((Integer)b.getTag());
                TicketSelectionStore.chooseIndices(this,selectedIds);
                int selected=TicketSelectionStore.selectedCount(this);
                if(selected==0){
                    message("Aucune soirée cochée : synchronisation non lancée.");
                    SyncState.save(this,SyncState.IDLE,"",0,0,-1,"");
                    syncLastSignature="";renderSyncProgress();return;
                }
                try{
                    AutoSyncScheduler.enable(this);
                    SyncState.save(this,SyncState.RUNNING,
                        "Import Shotgun en arrière-plan",0,selected,-1,
                        "Profils d'artistes vérifiés pendant la synchronisation.");
                    ShotgunSyncService.importSelected(this);
                    showHome(); // Show progress + remaining time immediately.
                }catch(Exception e){
                    SyncState.save(this,SyncState.ERROR,
                        "Import impossible : "+e.getMessage(),0,selected,-1,
                        "Réessaie depuis NØCTARIA.");
                    syncLastSignature="";renderSyncProgress();
                }
            }).show();
    }
    private boolean allSelectedEventsPresent() {
        JSONArray wanted=TicketSelectionStore.chosen(this);
        if(wanted.length()==0)return false;
        List<JSONObject> saved=new AppStore(this).events();
        for(int i=0;i<wanted.length();i++){
            JSONObject expected=wanted.optJSONObject(i);
            if(expected==null)continue;
            String ticket=TicketSelectionStore.canonical(expected.optString("url"));
            String title=expected.optString("title").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+"," ");
            String date=expected.optString("date");
            boolean present=false;
            for(JSONObject event:saved){
                String origin=TicketSelectionStore.canonical(event.optString("ticketUrl"));
                String eventUrl=TicketSelectionStore.canonical(event.optString("url"));
                if(!ticket.isEmpty() && (ticket.equals(origin)||ticket.equals(eventUrl))){
                    present=true;break;
                }
                String candidate=event.optString("title").trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{N}]+"," ");
                if(!date.isEmpty()&&date.equals(event.optString("date")) &&
                    !title.isEmpty() && (candidate.equals(title) ||
                        candidate.startsWith(title+" ") || title.startsWith(candidate+" "))){
                    present=true;break;
                }
            }
            if(!present)return false;
        }
        return true;
    }
    private int visibleFailureCount() {
        return SyncFailureStore.actionableCount(this);
    }
    private void renderSyncProgress() {
        if(syncPanel==null)return;
        JSONObject state=SyncState.read(this);
        String mode=state.optString("state",SyncState.IDLE);
        // Seules les erreurs concernant les billets cochés sont pertinentes.
        // Ne pas réafficher un ancien compteur de "fiche non récupérée".
        if(SyncState.IDLE.equals(mode)) {
            syncPanel.removeAllViews();
            syncPanel.setVisibility(View.GONE);
            syncLastSignature="";
            return;
        }
        syncPanel.setVisibility(View.VISIBLE);
        String signature=mode+"|"+state.optString("phase")+"|"+state.optInt("done")+
            "|"+state.optInt("total")+"|"+state.optString("detail")+
            "|"+state.optLong("eta");
        if(signature.equals(syncLastSignature))return;
        syncLastSignature=signature;
        syncPanel.removeAllViews();
        LinearLayout panel=card();
        if(SyncState.SELECTION.equals(mode)) {
            int count=TicketSelectionStore.list(this).length();
            add(panel,text("Billets Shotgun récupérés",18,Color.WHITE,true),8);
            add(panel,text(count+" soirée(s) détectée(s), toutes cochées par défaut. "+
                "Tu peux en retirer avant la synchronisation.",
                14,SOFT,false),10);
            add(panel,button("Choisir mes soirées",true,this::showTicketChoice),10);
            add(panel,button("Plus tard",false,()->{
                SyncState.save(this,SyncState.IDLE,"",0,0,-1,"");
                syncLastSignature="";renderSyncProgress();
            }),0);
            add(syncPanel,panel,16);
            if(homeScreen&&selectionDialogStamp!=state.optLong("updatedAt")) {
                selectionDialogStamp=state.optLong("updatedAt");
                syncHandler.post(this::showTicketChoice);
            }
            return;
        }
        if(SyncState.BLOCKED.equals(mode) || SyncState.ERROR.equals(mode) ||
            SyncState.CANCELED.equals(mode)) {
            add(panel,text("⚠  Import Shotgun à reprendre",17,Color.WHITE,true),9);
            add(panel,text(state.optString("phase"),14,SOFT,false),12);
            add(panel,button("Ouvrir mes billets sur Shotgun",false,this::connectShotgun),8);
            add(panel,button("Masquer cet avertissement",false,()->{
                SyncState.save(this,SyncState.IDLE,"",0,0,-1,"");
                syncLastSignature="";
                renderSyncProgress();
            }),0);
            renderFailedEvents(panel);
            add(syncPanel,panel,15);
            return;
        }
        String heading=SyncState.RUNNING.equals(mode)?"Synchronisation Shotgun":
            SyncState.DONE.equals(mode)?"Synchronisation complète":
            SyncState.PARTIAL.equals(mode)?"Synchronisation incomplète":
            SyncState.BLOCKED.equals(mode)?"Synchronisation à reprendre":
            "Synchronisation interrompue";
        add(panel,text("↻  "+heading,17,Color.WHITE,true),8);
        add(panel,text(state.optString("phase"),14,SOFT,false),9);
        int done=state.optInt("done"),total=state.optInt("total");
        int percent=total>0?(int)(100L*Math.max(0,done)/total):0;
        // Only a final verified DONE can display 100% — no invisible or
        // missing artist portrait is counted as a completed stage.
        if(!SyncState.DONE.equals(mode))percent=Math.min(99,percent);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(total==0&&SyncState.RUNNING.equals(mode));
        bar.setProgress(percent);
        add(panel,bar,7);
        add(panel,text(percent+" %",18,ACCENT,true),4);
        String amount=total>0?done+" / "+total+
            " étapes validées (soirées + profils d'artistes)":
            "Recherche de tes billets Shotgun…";
        add(panel,text(amount,13,ACCENT,true),5);
        long remaining=state.optLong("eta",-1);
        if(remaining>=0&&SyncState.RUNNING.equals(mode)&&total>0) {
            long min=remaining/60,sec=remaining%60;
            add(panel,text("Temps restant estimé : "+min+" min "+sec+" s",12,SOFT,false),7);
        } else if(SyncState.RUNNING.equals(mode)) {
            add(panel,text("Estimation disponible après les premières fiches.",12,SOFT,false),7);
        }
        if(!state.optString("detail").isEmpty())
            add(panel,text(state.optString("detail"),13,SOFT,false),12);
        android.content.SharedPreferences recent=getSharedPreferences("afterlog_auto_sync",0);
        long last=recent.getLong("lastCheckAt",0);
        if(last>0) {
            String when=new java.text.SimpleDateFormat("dd/MM · HH:mm",Locale.FRANCE)
                .format(new java.util.Date(last));
            add(panel,text("Dernière vérification : "+when,12,SOFT,false),7);
        }
        if(SyncState.RUNNING.equals(mode))
            add(panel,button("Arrêter la synchronisation",false,()->ShotgunSyncService.cancel(this)),0);
        else if(SyncState.PARTIAL.equals(mode)){
            add(panel,button("↻ Compléter les profils Spotify",false,()->{
                SpotifyArtistBackfillWorker.refreshAll(this);
                message("Recherche des artistes manquants lancée.");
            }),8);
            if(visibleFailureCount()>0)
                add(panel,button("Rechercher à nouveau les soirées",false,this::connectShotgun),8);
        }else if(!SyncState.DONE.equals(mode))
            add(panel,button("Reconnecter Shotgun",false,this::connectShotgun),0);
        if(!SyncState.RUNNING.equals(mode) && !SyncState.SELECTION.equals(mode))
            renderFailedEvents(panel);
        add(syncPanel,panel,18);
        if(!SyncState.RUNNING.equals(mode)) {
            long stamp=state.optLong("updatedAt");
            if(stamp>0 && observedSyncEnd!=stamp) {
                observedSyncEnd=stamp;
                store=new AppStore(this);
                // Mettre à jour la liste des événements sans reconstruire la vue en boucle.
                if(total>0 && SyncState.DONE.equals(mode))
                    syncHandler.post(()->{
                        if(syncActiveView&&syncPanel!=null)showHome();
                    });
            }
        }
    }
    private void showHome() {
        store=new AppStore(this);
        displayedEventsRevision=AppStore.revision(this);
        screen("NØCTARIA",null);
        syncPanel=column();
        syncLastSignature="";
        add(page,syncPanel,8);
        renderSyncProgress();

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        add(page,actions,12);
        TextView addEvent=button("＋ Soirée",true,()->editEvent(new JSONObject()));
        actions.addView(addEvent,new LinearLayout.LayoutParams(0,-2,1));
        TextView sync=button("🎟 Shotgun",false,this::connectShotgun);
        LinearLayout.LayoutParams syncParams=new LinearLayout.LayoutParams(0,-2,1);
        syncParams.leftMargin=dp(8);
        actions.addView(sync,syncParams);

        LinearLayout shortcuts=new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        add(page,shortcuts,18);
        TextView rdr=button("♡ RDR",false,()->startActivity(new Intent(this,RdrActivity.class)));
        shortcuts.addView(rdr,new LinearLayout.LayoutParams(0,-2,1));
        TextView stats=button("Suivi",false,()->
            startActivity(new Intent(this,SuiviActivity.class)));
        LinearLayout.LayoutParams statsParams=new LinearLayout.LayoutParams(0,-2,1);
        statsParams.leftMargin=dp(8);shortcuts.addView(stats,statsParams);

        List<JSONObject> events=store.events();
        add(page,text("MES SOIRÉES   ·   "+events.size(),15,ACCENT,true),11);
        if(events.isEmpty()){
            LinearLayout empty=card();
            add(empty,text("Aucune soirée pour l'instant",17,Color.WHITE,true),7);
            add(empty,text("Ajoute une soirée ou connecte Shotgun pour rechercher tes billets.",13,SOFT,false),0);
            add(page,empty,13);
        }
        for(JSONObject e:events) {
            LinearLayout card=card();
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            add(card,row,0);
            // Always show a poster, including hand-created private evenings.
            View posterThumb=EventPoster.create(this,e);
            LinearLayout.LayoutParams thumbLp=new LinearLayout.LayoutParams(
                dp(73),dp(92));
            thumbLp.rightMargin=dp(12);
            row.addView(posterThumb,thumbLp);
            LinearLayout details=column();
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView title=text(e.optString("title","Événement"),17,Color.WHITE,true);
            title.setMaxLines(2);add(details,title,7);
            add(details,text(e.optString("date")+"  "+e.optString("start"),13,ACCENT,false),5);
            String venue=e.optString("venue");
            if(venue.isEmpty())venue=e.optString("city");
            if(!venue.isEmpty())add(details,text(venue,13,SOFT,false),5);
            List<JSONObject> intakes=store.entries(e.optLong("id"));
            java.util.LinkedHashSet<String> icons=new java.util.LinkedHashSet<>();
            for(JSONObject intake:intakes){
                String emoji=ConsumptionActivity.iconFor(
                    intake.optString("category"),intake.optString("name"));
                if(!emoji.trim().isEmpty())icons.add(emoji);
            }
            int severity=RiskGuidance.eventSeverity(intakes);
            if(!icons.isEmpty()||severity>0){
                LinearLayout iconsRow=new LinearLayout(this);
                iconsRow.setGravity(Gravity.CENTER_VERTICAL);
                iconsRow.setOrientation(LinearLayout.HORIZONTAL);
                add(details,iconsRow,5);
                StringBuilder emojiLine=new StringBuilder();
                int shown=0;
                for(String icon:icons){
                    if(shown++>=8)break;
                    if(emojiLine.length()>0)emojiLine.append("  ");
                    emojiLine.append(icon);
                }
                TextView emojiText=text(emojiLine.toString(),19,Color.WHITE,false);
                emojiText.setContentDescription("Consommations notées dans cette soirée");
                iconsRow.addView(emojiText);
                if(severity>0){
                    TextView triangle=text("⚠",23,severity==2?
                        Color.rgb(251,113,119):Color.rgb(255,180,94),true);
                    triangle.setPadding(dp(8),0,dp(5),0);
                    triangle.setContentDescription(severity==2?
                        "Alerte rouge : association grave avec horaires compatibles"+
                        ". Ouvrir les sources RDR.":
                        "Alerte orange : vigilance RDR. Ouvrir les sources.");
                    iconsRow.addView(triangle);
                    triangle.setOnClickListener(v->new RdrWarningDialog(this,
                        RiskGuidance.forEvent(store.entries(e.optLong("id")))).show());
                }
            }
            add(details,text("Ouvrir  ›",12,SOFT,true),0);
            row.setOnClickListener(v->showEvent(e.optLong("id")));
            card.setOnClickListener(v->showEvent(e.optLong("id")));
            add(page,card,10);
        }
    }

    private void showEvent(long id) {
        // Une activité de saisie utilise une instance AppStore distincte.
        // Recharger depuis le disque avant de reconstruire le journal.
        store=new AppStore(this);
        JSONObject event=store.event(id);
        if(event==null){showHome();return;}
        currentEventId=id;
        screen("Événement",this::showHome);
        eventScreen=true;
        renderedEventRevision=AppStore.revision(this);
        // L'affiche et le titre apparaissent avant le journal personnel.
        View poster=EventPoster.create(this,event);
        poster.setContentDescription("Affiche de "+event.optString("title","Soirée"));
        LinearLayout.LayoutParams posterLp=new LinearLayout.LayoutParams(-1,dp(265));
        posterLp.bottomMargin=dp(17);
        page.addView(poster,posterLp);
        add(page,text(event.optString("title","Soirée"),24,Color.WHITE,true),8);
        String headlineDate=prettyDate(event.optString("date"));
        String startHour=event.optString("start");
        if(!headlineDate.isEmpty())
            add(page,text(headlineDate+(startHour.isEmpty()?"":" · "+startHour),
                13,ACCENT,false),5);
        String headlinePlace=event.optString("venue");
        if(headlinePlace.isEmpty())headlinePlace=event.optString("city");
        if(!headlinePlace.isEmpty())
            add(page,text("📍 "+headlinePlace,13,SOFT,false),15);
        renderJournalFirst(id);
        renderRdrAlerts(id);
        add(page,text("Informations de l'événement",21,Color.WHITE,true),12);
        renderEventPhotos(event);
        if(!event.optString("organizer").isEmpty())
            add(page,text("Par "+event.optString("organizer"),14,SOFT,false),14);

        LinearLayout summary=card();
        String date=prettyDate(event.optString("date"));
        String startTime=event.optString("start");
        String endTime=event.optString("end");
        String timeLabel=startTime.isEmpty()?"":startTime+
            (endTime.isEmpty()?"":" → "+endTime+
                (!event.optString("endDate").isEmpty() && !event.optString("endDate").equals(event.optString("date"))?
                  " (lendemain)":""));
        line(summary,"📅 Date",date);
        line(summary,"🕒 Horaires",timeLabel);
        line(summary,"📍 Lieu",event.optString("venue"));
        line(summary,"Adresse",event.optString("address")+
            (event.optString("city").isEmpty()?"":" · "+event.optString("city")));
        add(page,summary,17);

        add(page,button("Modifier les informations",false,()->editEvent(event)),24);

        String description=event.optString("description");
        if(!description.isEmpty()) {
            add(page,text("À propos",21,Color.WHITE,true),11);
            LinearLayout about=card();
            add(about,text(description,15,Color.WHITE,false),0);
            add(page,about,23);
        }
        if(!event.optString("artists").isEmpty() ||
           (event.optJSONArray("artistProfiles")!=null &&
            event.optJSONArray("artistProfiles").length()>0)) {
            add(page,text("Line-up",21,Color.WHITE,true),9);
            renderArtistProfiles(event);
        }
        if(!event.optString("genres").isEmpty()) {
            add(page,text("Vibe · Styles musicaux",21,Color.WHITE,true),9);
            addChips(event.optString("genres"),"♫");
        }
        if(!event.optString("venue").isEmpty()||!event.optString("address").isEmpty()) {
            add(page,text("Localisation",21,Color.WHITE,true),11);
            LinearLayout location=card();
            line(location,"📍 Lieu",event.optString("venue"));
            line(location,"Adresse",event.optString("address")+
                (event.optString("city").isEmpty()?"":", "+event.optString("city")));
            add(location,button("Ouvrir sur la carte",false,()->{
                String query=event.optString("address")+" "+event.optString("venue")+" "+event.optString("city");
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q="+Uri.encode(query))));
            }),0);
            add(page,location,22);
        }
        String link=event.optString("url");
        if(link.startsWith("https://"))add(page,button("Voir la fiche officielle sur Shotgun",false,()->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)))),18);

        if(!event.optString("notes").isEmpty()) {
            add(page,text("Mes notes",19,Color.WHITE,true),9);
            LinearLayout personal=card();
            add(personal,text(event.optString("notes"),15,Color.WHITE,false),0);
            add(page,personal,12);
        }
        add(page,button("Supprimer cette soirée",false,()->NeonDialog.builder(this)
            .setTitle("Supprimer la soirée ?")
            .setMessage("Les consommations associées seront également supprimées.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(d,w)->{store.deleteEvent(id);showHome();})
            .show()),0);
        tryAutoRefreshArtists(event);
    }
    private void tryAutoRefreshArtists(JSONObject event){
        String url=event.optString("url");
        if(!ShotgunArtistAutoRefresh.trusted(url))return;
        String names=event.optString("artists");
        if(names.trim().isEmpty())return;
        JSONArray artists=event.optJSONArray("artistProfiles");
        int withPhotos=0;
        if(artists!=null)for(int i=0;i<artists.length();i++){
            JSONObject p=artists.optJSONObject(i);
            if(p!=null && p.optString("photo").startsWith("https://"))withPhotos++;
        }
        if(withPhotos>=names.split(",").length)return;
        long eventId=event.optLong("id");
        String key="artist_refresh_v014_"+eventId;
        android.content.SharedPreferences prefs=getSharedPreferences(
            "afterlog_artists",MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(now-prefs.getLong(key,0)<12L*60*60*1000)return;
        prefs.edit().putLong(key,now).apply();
        artistAutoRefresh=new ShotgunArtistAutoRefresh(this,page,url,extracted->{
            if(!eventScreen || currentEventId!=eventId || isFinishing())return;
            try {
                AppStore currentStore=new AppStore(this);
                JSONObject saved=currentStore.event(eventId);
                if(saved!=null && mergeShotgunFields(saved,extracted)){
                    currentStore.saveEvent(saved);
                    store=currentStore;
                    showEvent(eventId);
                }
            }catch(Exception ignored){}
        });
    }

    private String primaryQuantity(JSONObject entry) {
        JSONObject parts=entry.optJSONObject("parts");
        if(parts!=null){
            ArrayList<String> fragments=new ArrayList<>();
            int whole=parts.optInt("entier"),half=parts.optInt("demi"),quarter=parts.optInt("quart");
            if(whole>0)fragments.add(whole+" entier"+(whole>1?"s":""));
            if(half>0)fragments.add(half+" demi");
            if(quarter>0)fragments.add(quarter+" quart"+(quarter>1?"s":""));
            if(!fragments.isEmpty())return android.text.TextUtils.join(" + ",fragments);
        }
        JSONArray portions=entry.optJSONArray("portions");
        if(portions!=null&&portions.length()>0){
            ArrayList<String> fragments=new ArrayList<>();
            for(int i=0;i<portions.length();i++){
                JSONObject p=portions.optJSONObject(i);
                if(p==null)continue;
                String size=p.optString("size");
                String route=p.optString("route");
                int count=p.optInt("count",1);
                String label=count+" "+(size.isEmpty()?"prise"+(count>1?"s":""):size.toLowerCase(Locale.FRANCE));
                if(!route.isEmpty())label+=" · "+route;
                String amount=p.optString("measuredAmount");
                if(!amount.isEmpty())label+=" ("+amount+" "+p.optString("measuredUnit")+")";
                fragments.add(label);
            }
            if(!fragments.isEmpty())return android.text.TextUtils.join(" + ",fragments);
        }
        String label=entry.optString("quantity","1")+" "+entry.optString("unit","prise");
        String route=entry.optString("route");
        if(!route.isEmpty())label+=" · "+("Autre".equals(route)?
            entry.optString("routeOther",route):route);
        String size=entry.optString("subjectiveSize");
        if(!size.isEmpty())label+=" · "+size.toLowerCase(Locale.FRANCE);
        String amount=entry.optString("measuredAmount");
        if(!amount.isEmpty())label+=" · "+amount+" "+entry.optString("measuredUnit");
        return label;
    }
    private String timeDescription(JSONObject entry) {
        String timing=entry.optString("timeMode",entry.optBoolean("allNight")?"all":"exact");
        if("all".equals(timing)||entry.optBoolean("allNight"))return "";
        if("between".equals(timing))
            return entry.optString("time")+" – "+entry.optString("timeEnd");
        return entry.optString("time");
    }
    private String compactQuantity(JSONObject entry){
        JSONObject parts=entry.optJSONObject("parts");
        if(parts!=null){
            int quarter=parts.optInt("quart"),half=parts.optInt("demi"),
                whole=parts.optInt("entier");
            StringBuilder quantity=new StringBuilder();
            if(whole>0)quantity.append(whole).append(" entier");
            if(half>0)quantity.append(quantity.length()>0?" + ":"").append(half).append(" demi");
            if(quarter>0)quantity.append(quantity.length()>0?" + ":"").append(quarter).append(" quart");
            if(quantity.length()>0)return quantity.toString();
        }
        JSONArray portions=entry.optJSONArray("portions");
        if(portions!=null&&portions.length()>0){
            int count=0;
            for(int i=0;i<portions.length();i++){
                JSONObject p=portions.optJSONObject(i);
                if(p!=null)count+=p.optInt("count",0);
            }
            JSONObject first=portions.optJSONObject(0);
            String size=first==null?"":first.optString("size");
            return count+" "+(size.isEmpty()?"prise"+(count>1?"s":""):size.toLowerCase(Locale.FRANCE));
        }
        return entry.optString("quantity","1")+" "+entry.optString("unit","prise");
    }
    private void renderJournalFirst(long id){
        List<JSONObject> entries=store.entries(id);
        add(page,text("Mon journal de soirée",21,Color.WHITE,true),12);
        { // La même bulle + existe même en l'absence de prises.
            // Cinq petites bulles rondes par rangée (~la moitié du diamètre initial).
            final int columns=5;
            int availableDp=getResources().getConfiguration().screenWidthDp-42;
            final int circleDp=Math.min(72,Math.max(56,(availableDp-20)/columns));
            final int count=entries.size()+1;
            for(int offset=0;offset<count;offset+=columns){
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                add(page,row,8);
                for(int slot=0;slot<columns;slot++){
                    int position=offset+slot;
                    LinearLayout.LayoutParams cellLayout=
                        new LinearLayout.LayoutParams(0,dp(circleDp),1f);
                    cellLayout.setMargins(dp(2),0,dp(2),0);
                    FrameLayout cell=new FrameLayout(this);
                    row.addView(cell,cellLayout);
                    if(position>=count)continue;
                    if(position==entries.size()){
                        TextView plus=text("+",27,BG,true);
                        plus.setGravity(Gravity.CENTER);
                        GradientDrawable circle=new GradientDrawable();
                        circle.setShape(GradientDrawable.OVAL);
                        circle.setColor(ACCENT);
                        plus.setBackground(circle);
                        plus.setContentDescription("Ajouter une consommation");
                        plus.setOnClickListener(v->editEntry(new JSONObject(),id));
                        cell.addView(plus,new FrameLayout.LayoutParams(
                            dp(circleDp),dp(circleDp),Gravity.CENTER));
                        continue;
                    }
                    JSONObject entry=entries.get(position);
                    LinearLayout bubble=column();
                    bubble.setGravity(Gravity.CENTER);
                    bubble.setPadding(dp(3),dp(5),dp(3),dp(5));
                    int substanceColor=ConsumptionPalette.forEntry(
                        entry.optString("category"),entry.optString("name"));
                    bubble.setBackground(ConsumptionPalette.circle(substanceColor,
                        getResources().getDisplayMetrics().density));
                    cell.addView(bubble,new FrameLayout.LayoutParams(
                        dp(circleDp),dp(circleDp),Gravity.CENTER));
                    String icon=ConsumptionActivity.iconFor(
                        entry.optString("category"),entry.optString("name"));
                    TextView picture=text(icon,21,Color.WHITE,false);
                    picture.setGravity(Gravity.CENTER);
                    add(bubble,picture,0);
                    TextView name=text(entry.optString("name","Prise"),10,Color.WHITE,true);
                    name.setGravity(Gravity.CENTER);
                    name.setSingleLine(true);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    add(bubble,name,0);
                    TextView quantity=text(compactQuantity(entry),9,substanceColor,true);
                    quantity.setGravity(Gravity.CENTER);
                    quantity.setSingleLine(true);
                    quantity.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    add(bubble,quantity,0);
                    bubble.setContentDescription("Détails "+entry.optString("name")+
                        " · "+primaryQuantity(entry)+
                        (timeDescription(entry).isEmpty()?"":" · "+timeDescription(entry)));
                    bubble.setOnClickListener(v->showEntryDetails(entry,id));
                }
            }
        }
    }

    private void renderRdrAlerts(long eventId) {
        List<RiskGuidance.Warning> risks=RiskGuidance.forEvent(store.entries(eventId));
        if(risks.isEmpty())return;
        boolean high=false;
        for(RiskGuidance.Warning item:risks)
            if(item.title.contains("GHB")||item.title.contains("Kétamine et alcool"))high=true;
        TextView chip=text("● "+risks.size()+" vigilance"+
            (risks.size()>1?"s":"")+" RDR  ›",13,
            high?Color.rgb(255,153,153):Color.rgb(255,190,127),true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(15),dp(10),dp(15),dp(10));
        GradientDrawable chipBg=new GradientDrawable();
        chipBg.setColor(Color.rgb(34,30,43));
        chipBg.setCornerRadius(dp(16));
        chipBg.setStroke(dp(1),high?Color.rgb(169,93,93):Color.rgb(137,103,74));
        chip.setBackground(chipBg);
        add(page,chip,12);
        chip.setOnClickListener(v->new RdrWarningDialog(this,risks).show());
    }

    private void showEntryDetails(JSONObject entry,long eventId){
        new EntryDetailsDialog(this,entry,primaryQuantity(entry),
            timeDescription(entry),()->editEntry(entry,eventId),()->{
                store.deleteEntry(entry.optLong("id"));
                showEvent(eventId);
            }).show();
    }

    private void renderArtistProfiles(JSONObject event) {
        JSONArray profiles=event.optJSONArray("artistProfiles");
        if(profiles==null)profiles=new JSONArray();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        JSONArray ordered=new JSONArray();
        for(int i=0;i<profiles.length();i++){
            JSONObject p=profiles.optJSONObject(i);
            if(p==null||p.optString("name").trim().isEmpty())continue;
            String key=p.optString("name").trim().toLowerCase(Locale.ROOT);
            if(seen.add(key))ordered.put(p);
        }
        for(String artist:event.optString("artists").split("[,;\\n]+")){
            String name=artist.trim();
            if(name.isEmpty()||!seen.add(name.toLowerCase(Locale.ROOT)))continue;
            JSONObject profile=new JSONObject();
            try{profile.put("name",name);}catch(Exception ignored){}
            ordered.put(profile);
        }
        if(ordered.length()==0)return;

        int linkedCount=0,photoCount=0;
        for(int k=0;k<ordered.length();k++){
            JSONObject item=ordered.optJSONObject(k);
            if(item==null)continue;
            if(SpotifyArtistVerifier.isArtistUrl(item.optString("spotify")))linkedCount++;
            if(item.optString("spotifyPhoto").startsWith("https://"))photoCount++;
        }
        add(page,text("Artistes · "+ordered.length(),14,SOFT,false),5);
        if(linkedCount<ordered.length()||photoCount<ordered.length()){
            add(page,text(linkedCount+" profils Spotify · "+photoCount+
                " photos · recherche sur toutes les soirées",12,SOFT,false),6);
            add(page,button("↻ Compléter les artistes",false,()->{
                SpotifyArtistBackfillWorker.refreshAll(this);
                message("Recherche des artistes relancée sur toutes les soirées.");
            }),11);
        }

        // Portrait plein cadre avec voile dégradé, nom lisible en bas et
        // petite pastille Spotify dans le coin. Aucun gros cercle vide.
        final int circleDp=Math.min(134,Math.max(96,
            (getResources().getConfiguration().screenWidthDp-48)/3));
        final int accent=Color.rgb(186,166,255);
        for(int index=0;index<ordered.length();index+=3){
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            add(page,row,9);
            for(int slot=0;slot<3;slot++){
                int i=index+slot;
                LinearLayout.LayoutParams columnLp=
                    new LinearLayout.LayoutParams(0,dp(circleDp),1f);
                columnLp.setMargins(dp(3),0,dp(3),0);
                FrameLayout holder=new FrameLayout(this);
                row.addView(holder,columnLp);
                if(i>=ordered.length())continue;
                JSONObject artist=ordered.optJSONObject(i);
                if(artist==null)continue;
                String name=artist.optString("name").trim();
                String photo=artist.optString("spotifyPhoto");
                if(photo.isEmpty())photo=artist.optString("photo");
                String candidate=artist.optString("spotify");
                String direct=SpotifyArtistFinder.savedProfile(this,name);
                if(direct.isEmpty())direct=SpotifyArtistFinder.trustedProfile(name);
                if(direct.isEmpty()&&SpotifyArtistVerifier.isArtistUrl(candidate))
                    direct=candidate;
                final String[] target={direct};

                FrameLayout tile=new FrameLayout(this);
                GradientDrawable base=new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(82,52,119),Color.rgb(27,34,64)});
                base.setShape(GradientDrawable.OVAL);
                tile.setBackground(base);
                tile.setClipToOutline(true);
                holder.addView(tile,new FrameLayout.LayoutParams(
                    dp(circleDp),dp(circleDp),Gravity.CENTER));

                TextView fallback=text(name.isEmpty()?"♪":
                    name.substring(0,1).toUpperCase(Locale.ROOT),
                    43,Color.rgb(227,205,255),true);
                fallback.setGravity(Gravity.CENTER);
                tile.addView(fallback,new FrameLayout.LayoutParams(-1,-1));

                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                tile.addView(image,new FrameLayout.LayoutParams(-1,-1));
                if(photo.startsWith("https://")){
                    String secondary=artist.optString("photo");
                    if(secondary.equals(photo))secondary="";
                    loadImage(image,photo,secondary,
                        ()->fallback.setVisibility(View.GONE));
                }else image.setVisibility(View.GONE);

                // Darken only the bottom of the portrait, not the full image.
                View gradient=new View(this);
                GradientDrawable shade=new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT,Color.argb(65,8,7,17),
                        Color.argb(238,9,8,21)});
                gradient.setBackground(shade);
                tile.addView(gradient,new FrameLayout.LayoutParams(-1,-1));

                TextView label=text(name,11,Color.WHITE,true);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setPadding(dp(7),0,dp(7),dp(4));
                label.setShadowLayer(dp(2),0,dp(1),Color.BLACK);
                FrameLayout.LayoutParams nameLp=new FrameLayout.LayoutParams(
                    -1,dp(37),Gravity.BOTTOM);
                nameLp.bottomMargin=dp(5);
                tile.addView(label,nameLp);

                SpotifyMarkView spotify=new SpotifyMarkView(this);
                spotify.setVerified(SpotifyArtistVerifier.isArtistUrl(target[0]));
                spotify.setContentDescription(spotify.isClickable()?
                    "Profil Spotify de "+name:"Compte Spotify de "+name);
                FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(
                    dp(27),dp(27),Gravity.RIGHT|Gravity.TOP);
                badgeLp.topMargin=dp(11);
                badgeLp.rightMargin=dp(11);
                tile.addView(spotify,badgeLp);

                // Subtle ring with no fill, so photos remain prominent.
                View border=new View(this);
                GradientDrawable ring=new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.TRANSPARENT);
                ring.setStroke(dp(1),Color.argb(170,204,178,255));
                border.setBackground(ring);
                tile.addView(border,new FrameLayout.LayoutParams(-1,-1));

                Runnable openSpotify=()->{
                    if(SpotifyArtistVerifier.isArtistUrl(target[0])){
                        try{
                            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(target[0])));
                        }catch(Exception ignored){
                            message("Impossible d'ouvrir ce profil Spotify.");
                        }
                    }else{
                        EditText input=new EditText(this);
                        input.setHint("https://open.spotify.com/artist/…");
                        input.setSingleLine(true);
                        input.setTextColor(Color.WHITE);
                        input.setInputType(InputType.TYPE_CLASS_TEXT|
                            InputType.TYPE_TEXT_VARIATION_URI);
                        NeonDialog.builder(this)
                            .setTitle("Spotify · "+name)
                            .setMessage("Profil non confirmé. Tu peux coller son lien officiel si tu le connais, ou relancer la recherche dans les paramètres.")
                            .setView(input)
                            .setNegativeButton("Annuler",null)
                            .setPositiveButton("Associer",(dialog,which)->{
                                String link=input.getText().toString().trim();
                                if(!SpotifyArtistVerifier.isArtistUrl(link)){
                                    message("Lien Spotify artiste invalide.");
                                    return;
                                }
                                SpotifyArtistFinder.saveProfile(this,name,link);
                                SpotifyArtistBackfillWorker.refreshAll(this);
                                target[0]=link;
                                spotify.setVerified(true);
                                message("Profil ajouté. Vérification du portrait en cours.");
                            }).show();
                    }
                };
                tile.setContentDescription(name+
                    (SpotifyArtistVerifier.isArtistUrl(target[0])?
                    " · ouvrir sur Spotify":" · associer un profil Spotify"));
                tile.setOnClickListener(v->openSpotify.run());
            }
        }

        String eventUrl=event.optString("url");
        if(eventUrl.startsWith("https://shotgun.live/")||
            eventUrl.startsWith("https://r.shotgun.live/")){
            int missing=0;
            for(int i=0;i<ordered.length();i++){
                JSONObject p=ordered.optJSONObject(i);
                if(p!=null&&p.optString("photo").isEmpty()&&
                    p.optString("spotifyPhoto").isEmpty())missing++;
            }
            if(missing>0)add(page,button("↻ Compléter depuis Shotgun",false,()->{
                Intent browser=new Intent(this,ShotgunBrowserActivity.class);
                browser.putExtra(ShotgunBrowserActivity.EXTRA_URL,eventUrl);
                browser.putExtra("afterlog_auto_extract",true);
                startActivityForResult(browser,SHOTGUN_EVENT_REFRESH);
            }),11);
        }
        add(page,text("Touche une photo pour ouvrir Spotify. Une pastille violette indique un profil non confirmé.",
            11,SOFT,false),13);
    }

    private void renderFailedEvents(LinearLayout panel) {
        JSONArray failures=SyncFailureStore.actionable(this);
        if(failures.length()==0)return;
        add(panel,text("Soirées non récupérées · "+failures.length(),18,Color.WHITE,true),8);
        add(panel,text("Voici les soirées que Shotgun n'a pas permis de compléter.",13,SOFT,false),12);
        for(int i=0;i<failures.length();i++){
            JSONObject item=failures.optJSONObject(i);
            if(item==null || TicketSelectionStore.isExcluded(this,item.optString("url")))continue;
            LinearLayout c=card();
            add(c,text(item.optString("title","Événement"),16,Color.WHITE,true),6);
            if(!item.optString("date").isEmpty())
                add(c,text(item.optString("date"),13,SOFT,false),5);
            add(c,text("Échec : "+item.optString("reason","Informations indisponibles"),13,SOFT,false),9);
            String url=item.optString("url");
            add(c,button("Réessayer cette soirée",false,()->{
                try{
                    ShotgunSyncService.retryFailed(this,url);
                    SyncState.save(this,SyncState.RUNNING,"Nouvelle tentative",
                        0,1,-1,item.optString("title"));
                    syncLastSignature="";
                    renderSyncProgress();
                }catch(Exception ex){message("Impossible de relancer : "+ex.getMessage());}
            }),8);
            add(c,button("Ajouter manuellement",false,()->{
                JSONObject draft=new JSONObject();
                try{
                    draft.put("title",item.optString("title"));
                    draft.put("date",item.optString("date"));
                    draft.put("poster",item.optString("poster"));
                    draft.put("url",item.optString("url"));
                    draft.put("origin","Shotgun (manuel)");
                }catch(Exception ignored){}
                editEvent(draft);
            }),8);
            if(url.startsWith("https://"))add(c,button("Ouvrir sur Shotgun",false,()->
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)))),0);
            add(panel,c,10);
        }
    }

    private void startGallery(long id,int mediaIndex){
        Intent intent=new Intent(this,EventGalleryActivity.class);
        intent.putExtra(EventGalleryActivity.EXTRA_EVENT_ID,id);
        intent.putExtra(EventGalleryActivity.EXTRA_INDEX,mediaIndex);
        startActivity(intent);
    }
    private void pickMedia(long id){
        currentEventId=id;
        startActivityForResult(MediaPicker.photosAndVideos(),EVENT_MEDIA_REQUEST);
    }
    private TextView galleryAction(String label,boolean primary,Runnable action){
        TextView control=text(label,13,primary?BG:Color.WHITE,true);
        control.setGravity(Gravity.CENTER);
        control.setMinHeight(dp(45));
        control.setPadding(dp(8),dp(10),dp(8),dp(10));
        control.setBackground(rounded(primary?ACCENT:PANEL,13));
        control.setOnClickListener(v->action.run());
        return control;
    }
    private void renderEventPhotos(JSONObject event) {
        final long eventId=event.optLong("id");
        JSONArray photos=EventPhotos.gallery(event),videos=EventVideos.list(event);
        if(gallerySelectionEventId!=eventId){
            gallerySelectionEventId=eventId;
            gallerySelectionMode=false;
            gallerySelected.clear();
        }
        galleryMedia.clear();
        galleryThumbs.clear();
        for(int i=0;i<photos.length();i++){
            String name=photos.optString(i);
            if(EventPhotos.file(this,name)!=null)galleryMedia.add(new GalleryMedia(false,name));
        }
        for(int i=0;i<videos.length();i++){
            String name=videos.optString(i);
            if(EventVideos.file(this,name)!=null)galleryMedia.add(new GalleryMedia(true,name));
        }
        java.util.HashSet<String> present=new java.util.HashSet<>();
        for(GalleryMedia item:galleryMedia)present.add(item.key);
        gallerySelected.retainAll(present);
        final int total=galleryMedia.size();
        if(total==0){gallerySelectionMode=false;gallerySelected.clear();}

        LinearLayout galleryHeader=new LinearLayout(this);
        galleryHeader.setOrientation(LinearLayout.HORIZONTAL);
        galleryHeader.setGravity(Gravity.CENTER_VERTICAL);
        add(page,galleryHeader,10);
        TextView title=text("Ma galerie"+(total>0?" · "+total:""),20,Color.WHITE,true);
        galleryHeader.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        TextView addMedia=button("＋ Médias",false,()->pickMedia(eventId));
        addMedia.setContentDescription("Ajouter des photos ou des vidéos");
        galleryHeader.addView(addMedia,new LinearLayout.LayoutParams(dp(136),dp(51)));
        if(total==0){
            add(page,text("Ajoute ici les photos et vidéos de ta soirée.",13,SOFT,false),12);
            return;
        }

        gallerySelectRow=new LinearLayout(this);
        gallerySelectRow.setOrientation(LinearLayout.HORIZONTAL);
        gallerySelectRow.setGravity(Gravity.CENTER_VERTICAL);
        add(page,gallerySelectRow,9);
        galleryToggle=galleryAction("✓ Sélectionner",false,()->{
            gallerySelectionMode=!gallerySelectionMode;
            if(!gallerySelectionMode)gallerySelected.clear();
            refreshGallerySelection();
        });
        gallerySelectRow.addView(galleryToggle,
            new LinearLayout.LayoutParams(0,dp(44),1));
        galleryCount=text("",13,ACCENT,true);
        galleryCount.setGravity(Gravity.CENTER);
        gallerySelectRow.addView(galleryCount,
            new LinearLayout.LayoutParams(0,dp(44),.88f));
        gallerySelectAll=galleryAction("Tout",false,()->{
            if(gallerySelected.size()==galleryMedia.size())gallerySelected.clear();
            else for(GalleryMedia item:galleryMedia)gallerySelected.add(item.key);
            refreshGallerySelection();
        });
        gallerySelectRow.addView(gallerySelectAll,
            new LinearLayout.LayoutParams(0,dp(44),.62f));

        galleryActions=new LinearLayout(this);
        galleryActions.setOrientation(LinearLayout.HORIZONTAL);
        galleryActions.setGravity(Gravity.CENTER_VERTICAL);
        add(page,galleryActions,11);
        galleryDelete=galleryAction("Supprimer",false,()->confirmDeleteGallery(eventId));
        galleryShare=galleryAction("Partager",false,()->shareGallery(eventId,false));
        galleryStory=galleryAction("Story Instagram",true,()->shareGallery(eventId,true));
        for(TextView action:new TextView[]{galleryDelete,galleryShare,galleryStory}){
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(48),1);
            layout.setMargins(dp(3),0,dp(3),0);
            galleryActions.addView(action,layout);
        }
        galleryStory.setTextSize(12);

        for(int offset=0;offset<total;offset+=3){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            add(page,row,8);
            for(int slot=0;slot<3;slot++){
                final int index=offset+slot;
                LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(108),1f);
                layout.setMargins(dp(3),0,dp(3),0);
                if(index>=total){row.addView(new View(this),layout);continue;}
                GalleryMedia media=galleryMedia.get(index);
                FrameLayout thumb=new FrameLayout(this);
                thumb.setBackground(rounded(Color.rgb(35,34,54),15));
                thumb.setClipToOutline(true);
                row.addView(thumb,layout);
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap picture=media.video?
                    EventVideos.thumbnail(this,media.name):
                    EventPhotos.preview(this,media.name,360);
                if(picture!=null)image.setImageBitmap(picture);
                thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
                if(media.video){
                    TextView play=text("▶",26,Color.WHITE,true);
                    play.setGravity(Gravity.CENTER);
                    GradientDrawable circle=new GradientDrawable();
                    circle.setColor(Color.argb(204,26,24,42));
                    circle.setShape(GradientDrawable.OVAL);
                    play.setBackground(circle);
                    thumb.addView(play,new FrameLayout.LayoutParams(
                        dp(48),dp(48),Gravity.CENTER));
                }
                TextView check=text("✓",17,BG,true);
                check.setGravity(Gravity.CENTER);
                FrameLayout.LayoutParams checkLp=new FrameLayout.LayoutParams(
                    dp(31),dp(31),Gravity.TOP|Gravity.RIGHT);
                checkLp.setMargins(0,dp(6),dp(6),0);
                thumb.addView(check,checkLp);
                galleryThumbs.put(media.key,new GalleryThumb(thumb,check));
                thumb.setContentDescription((media.video?"Vidéo ":"Photo ")+(index+1)
                    +". Appui long pour sélectionner.");
                thumb.setOnLongClickListener(v->{
                    if(!gallerySelectionMode){
                        gallerySelectionMode=true;
                        gallerySelected.clear();
                    }
                    gallerySelected.add(media.key);
                    refreshGallerySelection();
                    return true;
                });
                thumb.setOnClickListener(v->{
                    if(gallerySelectionMode){
                        if(!gallerySelected.add(media.key))gallerySelected.remove(media.key);
                        refreshGallerySelection();
                    }else startGallery(eventId,index);
                });
            }
        }
        galleryHint=text("",12,SOFT,false);
        add(page,galleryHint,15);
        refreshGallerySelection();
    }

    private void refreshGallerySelection(){
        if(gallerySelectRow==null||galleryToggle==null)return;
        int count=gallerySelected.size();
        galleryToggle.setText(gallerySelectionMode?"✕ Annuler":"✓ Sélectionner");
        galleryCount.setText(gallerySelectionMode?
            count+" / "+galleryMedia.size()+" choisi(s)":"");
        gallerySelectAll.setVisibility(gallerySelectionMode?View.VISIBLE:View.GONE);
        gallerySelectAll.setText(count==galleryMedia.size()?"Aucun":"Tout");
        galleryActions.setVisibility(gallerySelectionMode?View.VISIBLE:View.GONE);
        for(TextView action:new TextView[]{galleryDelete,galleryShare,galleryStory}){
            action.setEnabled(count>0);
            action.setAlpha(count>0?1f:.4f);
        }
        galleryHint.setText(gallerySelectionMode?
            "Sélectionne les photos et vidéos, puis choisis une action.":
            "Touche un média pour l'ouvrir · appui long pour sélectionner.");
        for(GalleryMedia media:galleryMedia){
            GalleryThumb tile=galleryThumbs.get(media.key);
            if(tile==null)continue;
            boolean selected=gallerySelected.contains(media.key);
            tile.check.setVisibility(gallerySelectionMode?View.VISIBLE:View.GONE);
            tile.check.setText(selected?"✓":"");
            GradientDrawable badge=new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(selected?ACCENT:Color.argb(210,16,15,28));
            badge.setStroke(dp(2),selected?Color.WHITE:ACCENT);
            tile.check.setBackground(badge);
            GradientDrawable outline=new GradientDrawable();
            outline.setColor(selected?Color.argb(55,186,166,255):Color.TRANSPARENT);
            outline.setCornerRadius(dp(15));
            if(selected)outline.setStroke(dp(3),ACCENT);
            tile.frame.setForeground(selected?outline:null);
            tile.frame.setContentDescription((media.video?"Vidéo ":"Photo ")+
                (selected?" sélectionnée":" non sélectionnée"));
        }
    }

    private void confirmDeleteGallery(long eventId){
        if(gallerySelected.isEmpty())return;
        final int count=gallerySelected.size();
        NeonDialog.builder(this)
            .setTitle(count==1?"Supprimer ce média ?":"Supprimer "+count+" médias ?")
            .setMessage("La sélection sera retirée de cette soirée et supprimée du stockage de NØCTARIA. Les originaux présents dans la galerie du téléphone ne seront pas supprimés.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(dialog,which)->deleteGallerySelection(eventId))
            .show();
    }

    private void deleteGallerySelection(long eventId){
        if(gallerySelectionEventId!=eventId||gallerySelected.isEmpty())return;
        try{
            AppStore latest=new AppStore(this);
            JSONObject event=latest.event(eventId);
            if(event==null)throw new IllegalStateException("Soirée introuvable");
            JSONArray keepPhotos=new JSONArray(),keepVideos=new JSONArray();
            java.util.ArrayList<java.io.File> files=new java.util.ArrayList<>();
            int removed=0;
            for(String name:toGalleryNames(EventPhotos.gallery(event))){
                if(gallerySelected.contains("photo:"+name)){
                    java.io.File file=EventPhotos.file(this,name);
                    if(file!=null)files.add(file);
                    removed++;
                }else keepPhotos.put(name);
            }
            for(String name:toGalleryNames(EventVideos.list(event))){
                if(gallerySelected.contains("video:"+name)){
                    java.io.File file=EventVideos.file(this,name);
                    if(file!=null)files.add(file);
                    removed++;
                }else keepVideos.put(name);
            }
            if(removed==0){message("Ces médias ne sont plus dans la soirée.");return;}
            event.put("photos",keepPhotos);
            event.put("videos",keepVideos);
            latest.saveEvent(event);
            // La sauvegarde est effectuée avant de retirer les copies privées.
            for(java.io.File file:files)if(file.exists())file.delete();
            gallerySelected.clear();
            gallerySelectionMode=false;
            store=new AppStore(this);
            showEvent(eventId);
            message(removed+" média(s) supprimé(s).");
        }catch(Exception error){
            message("Suppression impossible : "+error.getMessage());
        }
    }
    private java.util.ArrayList<String> toGalleryNames(JSONArray items){
        java.util.ArrayList<String> names=new java.util.ArrayList<>();
        for(int i=0;i<items.length();i++)names.add(items.optString(i));
        return names;
    }

    private void shareGallery(long eventId,boolean instagram){
        if(gallerySelectionEventId!=eventId||gallerySelected.isEmpty())return;
        JSONObject current=new AppStore(this).event(eventId);
        if(current==null){message("Soirée introuvable.");return;}
        java.util.HashSet<String> photos=new java.util.HashSet<>(
            toGalleryNames(EventPhotos.gallery(current)));
        java.util.HashSet<String> videos=new java.util.HashSet<>(
            toGalleryNames(EventVideos.list(current)));
        java.util.ArrayList<java.io.File> files=new java.util.ArrayList<>();
        java.util.ArrayList<Boolean> kinds=new java.util.ArrayList<>();
        for(GalleryMedia media:galleryMedia){
            if(!gallerySelected.contains(media.key))continue;
            if(media.video?!videos.contains(media.name):!photos.contains(media.name))continue;
            java.io.File file=media.video?
                EventVideos.file(this,media.name):EventPhotos.file(this,media.name);
            if(file==null||!file.isFile())continue;
            files.add(file);
            kinds.add(media.video);
        }
        if(files.isEmpty()){message("Aucun média sélectionné n'est disponible.");return;}
        EventGalleryActivity.shareMediaFiles(this,files,kinds,instagram);
    }

    private String prettyDate(String date) {
        try {
            return java.time.LocalDate.parse(date)
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.FRANCE));
        }catch(Exception ex){return date;}
    }
    private void addChips(String value,String symbol) {
        String[] parts=value.split(",");
        LinearLayout row=null;
        int count=0;
        for(String part:parts) {
            String word=part.trim();
            if(word.isEmpty())continue;
            if(count%2==0) {
                row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                add(page,row,9);
            }
            TextView chip=text(symbol+"  "+word,13,Color.WHITE,true);
            chip.setPadding(dp(11),dp(13),dp(11),dp(13));
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(rounded(PANEL,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.setMargins(dp(3),0,dp(3),0);
            row.addView(chip,lp);
            count++;
        }
        if(row!=null && count%2==1)row.addView(new View(this),
            new LinearLayout.LayoutParams(0,dp(1),1));
        add(page,new View(this),13);
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
        Intent intent=new Intent(this,EventEditorActivity.class);
        intent.putExtra(EventEditorActivity.EXTRA_EVENT,original.toString());
        startActivityForResult(intent,EVENT_EDITOR_REQUEST);
    }

    private void editEntry(JSONObject original,long eventId) {
        Intent intent=new Intent(this,ConsumptionActivity.class);
        intent.putExtra(ConsumptionActivity.EXTRA_EVENT_ID,eventId);
        intent.putExtra(ConsumptionActivity.EXTRA_ENTRY_ID,original.optLong("id"));
        startActivityForResult(intent,CONSUMPTION_REQUEST);
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
        browser.putExtra("afterlog_auto_sync",true);
        startActivityForResult(browser, SHOTGUN_REQUEST);
    }
    private void importShotgun() {
        EditText input=new EditText(this);
        input.setSingleLine(false);
        input.setTextColor(BG);
        input.setHint("https://shotgun.live/...");
        input.setPadding(dp(16),dp(10),dp(16),dp(10));
        NeonDialog.builder(this)
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
            if(records.length()==0){message("Aucun événement récupéré");return;}
            int added=0,updated=0,unchanged=0,ignored=0;
            for(int i=0;i<records.length();i++){
                JSONObject incoming=records.optJSONObject(i);
                if(incoming==null){ignored++;continue;}
                String canonical=normalizedEventUrl(incoming.optString("url"));
                if(canonical.isEmpty() || incoming.optString("title").trim().isEmpty()){
                    ignored++;continue;
                }
                incoming.put("url",canonical);
                JSONObject existing=findMatchingShotgunEvent(incoming);
                if(existing==null) {
                    store.saveEvent(incoming);
                    added++;
                    continue;
                }
                if(mergeShotgunFields(existing,incoming)) {
                    store.saveEvent(existing);
                    updated++;
                } else unchanged++;
            }
            showHome();
            SpotifyArtistBackfillWorker.schedule(this);
            NeonDialog.builder(this).setTitle("Import Shotgun terminé")
                .setMessage(added+" soirée(s) ajoutée(s)\n"+
                    updated+" soirée(s) complétée(s) avec leurs informations\n"+
                    unchanged+" déjà à jour, "+ignored+" ignorée(s).\n\n"+
                    "Les notes personnelles et toutes les consommations existantes sont conservées. "+
                    "Les valeurs que tu as déjà saisies ne sont pas remplacées.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){message("Import impossible : "+e.getMessage());}
    }
    private JSONObject findMatchingShotgunEvent(JSONObject incoming) {
        String url=incoming.optString("url");
        for(JSONObject existing:store.events()){
            String previous=normalizedEventUrl(existing.optString("url"));
            if(!previous.isEmpty() && previous.equalsIgnoreCase(url))return existing;
        }
        // Anciennes fiches importées sans lien exact : utiliser titre+date uniquement
        // en cas d'une correspondance unique (jamais fusionner deux soirées homonymes).
        String title=normalizeTitle(incoming.optString("title"));
        String date=incoming.optString("date");
        JSONObject match=null;
        for(JSONObject existing:store.events()){
            if(!existing.optString("origin").toLowerCase(java.util.Locale.ROOT).contains("shotgun"))
                continue;
            if(!normalizeTitle(existing.optString("title")).equals(title))continue;
            if(date.isEmpty() || existing.optString("date").isEmpty() ||
                !date.equals(existing.optString("date")))continue;
            if(match!=null)return null;
            match=existing;
        }
        return match;
    }
    private String normalizeTitle(String title) {
        return java.text.Normalizer.normalize(title.toLowerCase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim();
    }
    private boolean mergeShotgunFields(JSONObject existing,JSONObject incoming) throws Exception {
        // Ne pas écraser les champs personnalisés ni l'identifiant de la soirée.
        String[] fields={"poster","description","venue","city","address","organizer",
            "artists","genres","date","start","end","endDate","url"};
        boolean changed=false;
        for(String field:fields) {
            String newValue=incoming.optString(field).trim();
            String oldValue=existing.optString(field).trim();
            if(!newValue.isEmpty() && oldValue.isEmpty()) {
                existing.put(field,newValue);
                changed=true;
            }
        }
        if(ArtistProfileMerger.merge(existing,incoming))changed=true;
        return changed;
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

    @Override protected void onResume() {
        super.onResume();
        if(updater!=null){updater.onResume();updater.checkAutomatically();}
        AutoSyncScheduler.schedule(this);
        SpotifyArtistBackfillWorker.schedule(this);
        syncActiveView=true;
        syncHandler.removeCallbacks(syncTicker);
        syncHandler.post(syncTicker);
    }
    @Override protected void onPause() {
        syncActiveView=false;
        syncHandler.removeCallbacks(syncTicker);
        super.onPause();
    }
    // Ouvre la sauvegarde directement depuis le diagnostic du bouton MAJ.
    void openBackupSettings() { showSettings(); }

    private void showSettings() {
        screen("Confidentialité et sauvegarde",this::showHome);
        subtitle("Les consommations sont stockées localement sur cet appareil. Aucun compte Shotgun ou Gmail n'est nécessaire.");
        add(page,button("↻ Rechercher une mise à jour",false,()->updater.check()),10);
        add(page,button("Importer une soirée par lien Shotgun",false,this::importShotgun),10);
        add(page,text("ARTISTES ET SPOTIFY",13,ACCENT,true),7);
        add(page,text(SpotifyArtistBackfillWorker.status(this),13,SOFT,false),10);
        add(page,button("↻ Rechercher tous les artistes Spotify",false,()->{
            SpotifyArtistBackfillWorker.refreshAll(this);
            NeonDialog.builder(this)
                .setTitle("Recherche Spotify relancée")
                .setMessage("NØCTARIA parcourt tous les événements enregistrés, y compris les anciens. Les comptes et photos confirmés seront ajoutés progressivement et conservés. Les homonymes non fiables resteront à associer manuellement.")
                .setPositiveButton("Compris",null).show();
        }),13);
        add(page,button("Exporter toutes mes données (JSON)",true,()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"noctaria-sauvegarde.json");
            startActivityForResult(intent,EXPORT);
        }),10);
        add(page,button("Importer une sauvegarde JSON",false,()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent,IMPORT);
        }),14);
        subtitle("Attention : la sauvegarde JSON actuelle n'inclut pas les fichiers des photos personnelles. Conserve les photos originales sur ton téléphone. Le journal JSON n'est pas chiffré.");
        subtitle("Après connexion à Shotgun, NØCTARIA recherche « Mes billets → Passés » et affiche les événements à cocher avant de synchroniser. La disponibilité dépend du site et de ta session.");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==SHOTGUN_EVENT_REFRESH){
            if(resultCode==RESULT_OK && data!=null){
                try{
                    JSONArray found=new JSONArray(data.getStringExtra(ShotgunBrowserActivity.EXTRA_EVENTS));
                    JSONObject current=store.event(currentEventId);
                    if(current!=null && found.length()>0){
                        JSONObject incoming=found.optJSONObject(0);
                        if(incoming!=null && mergeShotgunFields(current,incoming)){
                            store.saveEvent(current);
                            message("Fiche et artistes complétés depuis Shotgun.");
                        }else message("Aucune photo supplémentaire détectée sur Shotgun.");
                    }
                }catch(Exception error){message("Extraction Shotgun incomplète.");}
            }
            showEvent(currentEventId);
            return;
        }
        if(requestCode==EVENT_EDITOR_REQUEST) {
            if(resultCode==RESULT_OK && data!=null) {
                SpotifyArtistBackfillWorker.schedule(this);
                long id=data.getLongExtra(EventEditorActivity.EXTRA_SAVED_ID,0);
                store=new AppStore(this);
                if(id>0)showEvent(id);else showHome();
            }else if(currentEventId>0)showEvent(currentEventId);
            else showHome();
            return;
        }
        if(requestCode==EVENT_MEDIA_REQUEST){
            if(resultCode!=RESULT_OK||data==null)return;
            ArrayList<Uri> sources=new ArrayList<>();
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount();i++)
                    sources.add(data.getClipData().getItemAt(i).getUri());
            }else if(data.getData()!=null)sources.add(data.getData());
            if(sources.isEmpty())return;
            long target=currentEventId;
            message("Importation de "+sources.size()+" média(s) dans NØCTARIA…");
            new Thread(()->{
                int added=0,errors=0;
                String failure="";
                try{
                    AppStore localStore=new AppStore(getApplicationContext());
                    JSONObject event=localStore.event(target);
                    if(event==null)throw new Exception("Soirée introuvable");
                    for(Uri uri:sources){
                        try {
                            String mime=getContentResolver().getType(uri);
                            if(mime!=null&&mime.startsWith("image/")){
                                if(EventPhotos.gallery(event).length()>=40)
                                    throw new Exception("40 photos maximum");
                                String savedName=EventPhotos.importUri(this,uri);
                                EventPhotos.append(event,savedName);
                            }else{
                                if(EventVideos.list(event).length()>=EventVideos.MAX_COUNT)
                                    throw new Exception("30 vidéos maximum");
                                String savedName=EventVideos.importVideo(this,uri);
                                EventVideos.append(event,savedName);
                            }
                            added++;
                        }catch(Exception e){
                            errors++;
                            failure=e.getMessage();
                        }
                    }
                    if(added>0)localStore.saveEvent(event);
                }catch(Exception e){errors++;failure=e.getMessage();}
                final int imported=added,rejected=errors;
                final String errorText=failure;
                runOnUiThread(()->{
                    if(isFinishing())return;
                    store=new AppStore(this);
                    if(eventScreen&&currentEventId==target)showEvent(target);
                    message(imported+" média(s) ajouté(s)"+
                        (rejected>0?" · "+rejected+" non importé(s) : "+errorText:
                         imported>0?" · utilise « Sélectionner » pour partager plusieurs médias.":""));

                });
            },"NØCTARIA-media-import").start();
            return;
        }
        if(requestCode==EVENT_VIDEOS_REQUEST){
            if(resultCode==RESULT_OK && data!=null){
                JSONObject current=store.event(currentEventId);
                if(current!=null){
                    int added=0,errors=0;
                    String errorText="";
                    int length=data.getClipData()==null?1:data.getClipData().getItemCount();
                    for(int i=0;i<length&&i<12;i++){
                        Uri uri=data.getClipData()==null?data.getData():
                            data.getClipData().getItemAt(i).getUri();
                        try{
                            if(EventVideos.list(current).length()>=EventVideos.MAX_COUNT)break;
                            EventVideos.append(current,EventVideos.importVideo(this,uri));
                            added++;
                        }catch(Exception ex){errors++;errorText=ex.getMessage();}
                    }
                    store.saveEvent(current);
                    message(added+" vidéo(s) ajoutée(s)"+
                        (errors>0?" · "+errors+" erreur(s) : "+errorText:""));
                    showEvent(currentEventId);
                }
            }
            return;
        }
        if(requestCode==EVENT_IMAGES_REQUEST){
            if(resultCode==RESULT_OK && data!=null){
                JSONObject current=store.event(currentEventId);
                if(current!=null){
                    int added=0,errors=0;
                    int length=data.getClipData()==null?1:data.getClipData().getItemCount();
                    for(int i=0;i<length&&i<40;i++){
                        Uri uri=data.getClipData()==null?data.getData():data.getClipData().getItemAt(i).getUri();
                        try{
                            if(EventPhotos.gallery(current).length()>=40)break;
                            EventPhotos.append(current,EventPhotos.importUri(this,uri));
                            added++;
                        }catch(Exception ex){errors++;}
                    }
                    store.saveEvent(current);
                    message(added+" photo(s) ajoutée(s)"+(errors>0?" · "+errors+" ignorée(s)":""));
                    showEvent(currentEventId);
                }
            }
            return;
        }
        if(requestCode==CONSUMPTION_REQUEST){
            if(resultCode==RESULT_OK) {
                store=new AppStore(this);
                if(currentEventId>0)showEvent(currentEventId);
            }
            return;
        }
        if(requestCode==SHOTGUN_REQUEST && resultCode==RESULT_OK && data==null){
            showHome();
            return;
        }
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
                NeonDialog.builder(this).setTitle("Remplacer les données ?")
                    .setMessage("Cette sauvegarde remplacera entièrement ton journal actuel.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Importer",(d,w)->{
                        try {
                            store.restore(content);
                            SpotifyArtistBackfillWorker.refreshAll(this);
                            message("Données restaurées · recherche Spotify sur toutes les soirées lancée");
                            showHome();
                        }
                        catch(Exception ex){message("Erreur de sauvegarde : "+ex.getMessage());}
                    }).show();
            }
        }catch(Exception ex){message("Erreur : "+ex.getMessage());}
    }
    @Override public void onBackPressed() {
        if(backAction!=null)backAction.run();
        else super.onBackPressed();
    }
    private void loadImage(ImageView image,String url) {
        loadImage(image,url,null,null);
    }
    private void loadImage(ImageView image,String url,Runnable onLoaded) {
        loadImage(image,url,null,onLoaded);
    }
    private void loadImage(ImageView image,String url,String backup,Runnable onLoaded){
        if(url==null||!url.startsWith("https://"))return;
        Bitmap cached=imageMemory.get(url);
        if(cached==null&&backup!=null)cached=imageMemory.get(backup);
        if(cached!=null){
            image.setVisibility(View.VISIBLE);
            image.setImageBitmap(cached);
            if(onLoaded!=null)onLoaded.run();
            return;
        }
        new Thread(()->{
            Bitmap bmp=fetchImage(url);
            String successful=url;
            if(bmp==null&&backup!=null&&backup.startsWith("https://")){
                bmp=fetchImage(backup);
                successful=backup;
            }
            if(bmp==null)return;
            final Bitmap portrait=bmp;
            final String cacheKey=successful;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                imageMemory.put(cacheKey,portrait);
                image.setVisibility(View.VISIBLE);
                image.setImageBitmap(portrait);
                if(onLoaded!=null)onLoaded.run();
            });
        },"NØCTARIA-image").start();
    }
    private Bitmap fetchImage(String url){
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(8500);
            connection.setReadTimeout(11000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept","image/jpeg,image/png,image/webp,image/*");
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android; NOCTARIA)");
            if(connection.getResponseCode()!=200)return null;
            // Avoid decoding unlimited third-party images on the UI thread.
            if(connection.getContentLengthLong()>5_000_000L)return null;
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                int count;
                while((count=in.read(buffer))!=-1){
                    if(out.size()+count>5_000_000)return null;
                    out.write(buffer,0,count);
                }
                byte[] bytes=out.toByteArray();
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<=0||options.outHeight<=0)return null;
                options.inSampleSize=1;
                while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>750)
                    options.inSampleSize*=2;
                options.inJustDecodeBounds=false;
                return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            }
        }catch(Exception ignored){return null;}
        finally{if(connection!=null)connection.disconnect();}
    }

}
