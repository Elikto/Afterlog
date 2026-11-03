package fr.afterlog.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;

/** Galerie privée plein écran : images avec zoom et vidéos Media3. */
public final class EventGalleryActivity extends Activity {
    public static final String EXTRA_EVENT_ID="gallery_event_id";
    public static final String EXTRA_EVENT_JSON="gallery_event_json";
    public static final String EXTRA_INDEX="gallery_index";
    private static final int BG=Color.rgb(7,8,15),ACCENT=Color.rgb(188,164,255);
    private static final int SOFT=Color.rgb(180,182,202);
    private static final class Item {
        final boolean video;final String name;
        Item(boolean video,String name){this.video=video;this.name=name;}
    }
    private final ArrayList<Item> items=new ArrayList<>();
    private int currentIndex=0;
    private FrameLayout root,stage;
    private LinearLayout bottom;
    private TextView position,type,external;
    private ExoPlayer player;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView control(String label,int size,Runnable action){
        TextView button=new TextView(this);
        button.setText(label);button.setTextSize(size);button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12),dp(11),dp(12),dp(11));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.argb(225,33,31,50));bg.setCornerRadius(dp(20));
        button.setBackground(bg);
        button.setOnClickListener(v->action.run());return button;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            JSONObject event;
            String json=getIntent().getStringExtra(EXTRA_EVENT_JSON);
            if(json!=null)event=new JSONObject(json);
            else event=new AppStore(this).event(getIntent().getLongExtra(EXTRA_EVENT_ID,0));
            if(event==null)throw new Exception("Soirée introuvable");
            JSONArray photos=EventPhotos.gallery(event);
            for(int i=0;i<photos.length();i++){
                String filename=photos.optString(i);
                if(EventPhotos.file(this,filename)!=null)items.add(new Item(false,filename));
            }
            JSONArray videos=EventVideos.list(event);
            for(int i=0;i<videos.length();i++){
                String filename=videos.optString(i);
                if(EventVideos.file(this,filename)!=null)items.add(new Item(true,filename));
            }
            if(items.isEmpty())throw new Exception("Aucun média dans la galerie");
            currentIndex=Math.max(0,Math.min(items.size()-1,
                getIntent().getIntExtra(EXTRA_INDEX,0)));
        }catch(Exception e){
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();finish();return;
        }
        build();showItem();
    }
    private void build(){
        root=new FrameLayout(this);root.setBackgroundColor(BG);
        setContentView(root);
        stage=new FrameLayout(this);
        root.addView(stage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(17),dp(37),dp(17),dp(10));
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topLp);
        top.addView(control("✕",22,this::finish),new LinearLayout.LayoutParams(dp(52),dp(52)));
        position=new TextView(this);position.setTextColor(Color.WHITE);
        position.setTextSize(16);position.setGravity(Gravity.CENTER);
        top.addView(position,new LinearLayout.LayoutParams(0,dp(52),1));
        top.addView(control("↗",21,this::shareCurrent),
            new LinearLayout.LayoutParams(dp(52),dp(52)));

        bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12),dp(12),dp(12),dp(35));
        GradientDrawable panel=new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.TRANSPARENT,Color.argb(210,14,15,26)});
        bottom.setBackground(panel);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        root.addView(bottom,lp);
        type=new TextView(this);type.setTextColor(SOFT);type.setTextSize(13);
        type.setGravity(Gravity.CENTER);
        bottom.addView(type,new LinearLayout.LayoutParams(-1,dp(25)));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);
        bottom.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        TextView prev=control("‹",34,()->navigate(-1));
        nav.addView(prev,new LinearLayout.LayoutParams(dp(65),dp(58)));
        TextView spacer=new TextView(this);nav.addView(spacer,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView next=control("›",34,()->navigate(1));
        nav.addView(next,new LinearLayout.LayoutParams(dp(65),dp(58)));
        external=control("Partager sur les réseaux / messages",13,this::shareCurrent);
        LinearLayout.LayoutParams extLp=new LinearLayout.LayoutParams(-1,dp(46));
        extLp.topMargin=dp(10);
        bottom.addView(external,extLp);
        LinearLayout extras=new LinearLayout(this);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams extraLp=new LinearLayout.LayoutParams(-1,dp(47));
        extraLp.topMargin=dp(7);
        bottom.addView(extras,extraLp);
        TextView story=control("Story Instagram",12,this::shareInstagramStory);
        extras.addView(story,new LinearLayout.LayoutParams(0,-1,1));
        TextView other=control("Lecteur / Cast externe",12,this::openExternally);
        LinearLayout.LayoutParams otherLp=new LinearLayout.LayoutParams(0,-1,1);
        otherLp.leftMargin=dp(7);extras.addView(other,otherLp);
    }
    private void navigate(int direction){
        int next=currentIndex+direction;
        if(next<0||next>=items.size())return;
        currentIndex=next;showItem();
    }
    private void releasePlayer(){
        if(player!=null){player.release();player=null;}
    }
    private void showItem(){
        releasePlayer();
        stage.removeAllViews();
        Item item=items.get(currentIndex);
        position.setText((currentIndex+1)+" / "+items.size());
        type.setText(item.video?"▶ VIDÉO":"PHOTO · pincer pour zoomer · glisser pour changer");
        if(!item.video){
            File photo=EventPhotos.file(this,item.name);
            if(photo==null||!photo.isFile()){
                showError("Cette photo est introuvable.");return;
            }
            Bitmap bitmap=EventPhotos.preview(this,item.name,2600);
            if(bitmap==null){showError("Ce format d'image n'est pas reconnu.");return;}
            ZoomPhotoView image=new ZoomPhotoView(this);
            image.setImageBitmap(bitmap);
            image.setNavigation(dx->navigate(dx<0?1:-1));
            stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        }else {
            File file=EventVideos.file(this,item.name);
            if(file==null||!file.isFile()){showError("Vidéo introuvable");return;}
            try{
                player=new ExoPlayer.Builder(this).build();
                PlayerView surface=new PlayerView(this);
                surface.setUseController(true);
                surface.setControllerAutoShow(true);
                surface.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                surface.setPlayer(player);
                stage.addView(surface,new FrameLayout.LayoutParams(-1,-1));
                player.addListener(new Player.Listener(){
                    @Override public void onPlayerError(PlaybackException error){
                        Toast.makeText(EventGalleryActivity.this,
                            "Format non décodé ici. Essaie « Ouvrir dans une autre application ».",
                            Toast.LENGTH_LONG).show();
                    }
                });
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
                player.prepare();player.play();
            }catch(Exception error){showError("Lecture impossible sur cet appareil.");}
        }
    }
    private void showError(String message) {
        TextView label=new TextView(this);
        label.setText(message);label.setTextColor(Color.WHITE);label.setGravity(Gravity.CENTER);
        stage.addView(label,new FrameLayout.LayoutParams(-1,-1));
    }
    private File currentFile(){
        if(items.isEmpty())return null;
        Item item=items.get(currentIndex);
        return item.video?EventVideos.file(this,item.name):
            EventPhotos.file(this,item.name);
    }
    private void shareCurrent(){
        Item item=items.get(currentIndex);
        File file=currentFile();
        if(file==null||!file.isFile()) {
            Toast.makeText(this,"Média introuvable",Toast.LENGTH_LONG).show();return;
        }
        shareFile(this,file,item.video,false);
    }
    private void shareInstagramStory(){
        Item item=items.get(currentIndex);
        File file=currentFile();
        if(file==null||!file.isFile()){
            Toast.makeText(this,"Média introuvable",Toast.LENGTH_LONG).show();return;
        }
        shareFile(this,file,item.video,true);
    }
    static void shareFile(Activity activity,File file,boolean video,boolean instagramStory){
        if(file==null||!file.isFile()){
            Toast.makeText(activity,"Média introuvable",Toast.LENGTH_LONG).show();return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".media",file);
            String mime=video?EventVideos.mimeFor(file.getName()):"image/jpeg";
            Intent share;
            if(instagramStory){
                share=new Intent("com.instagram.share.ADD_TO_STORY");
                share.setPackage("com.instagram.android");
                share.setDataAndType(uri,mime);
            }else{
                share=new Intent(Intent.ACTION_SEND);
                share.setType(mime);
                share.putExtra(Intent.EXTRA_STREAM,uri);
            }
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newUri(activity.getContentResolver(),
                "AfterLog — média personnel",uri));
            if(instagramStory)activity.startActivity(share);
            else activity.startActivity(Intent.createChooser(share,
                "Partager la photo ou la vidéo"));
        }catch(Exception e){
            Toast.makeText(activity,instagramStory?
                "Story Instagram indisponible pour ce média. Essaie le partage classique.":
                "Aucune application compatible avec ce média.",Toast.LENGTH_LONG).show();
        }
    }

    private void openExternally(){
        Item item=items.get(currentIndex);
        File file=item.video?EventVideos.file(this,item.name):EventPhotos.file(this,item.name);
        if(file==null||!file.isFile()){
            Toast.makeText(this,"Fichier introuvable",Toast.LENGTH_SHORT).show();return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".media",file);
            Intent view=new Intent(Intent.ACTION_VIEW);
            String mime=item.video?EventVideos.mimeFor(item.name):"image/jpeg";
            view.setDataAndType(uri,mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.setClipData(ClipData.newUri(getContentResolver(),"AfterLog",uri));
            startActivity(Intent.createChooser(view,"Ouvrir ou diffuser avec une application compatible"));
        }catch(Exception e){
            Toast.makeText(this,"Aucune application disponible pour ouvrir ce média.",
                Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onPause(){releasePlayer();super.onPause();}
    @Override protected void onResume(){
        super.onResume();
        if(root!=null && player==null && currentIndex<items.size() &&
           items.get(currentIndex).video)showItem();
    }
    @Override protected void onDestroy(){releasePlayer();super.onDestroy();}

    private static final class ZoomPhotoView extends ImageView {
        interface OnNavigate{void navigate(float movement);}
        private final Matrix matrix=new Matrix();
        private final ScaleGestureDetector scaler;
        private final GestureDetector detector;
        private float zoom=1f,beginX,beginY,lastX,lastY;
        private OnNavigate navigation;
        ZoomPhotoView(Activity context){
            super(context);
            setScaleType(ScaleType.MATRIX);
            scaler=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    if(getDrawable()==null)return false;
                    float desired=Math.max(1f,Math.min(5f,zoom*d.getScaleFactor()));
                    matrix.postScale(desired/zoom,desired/zoom,d.getFocusX(),d.getFocusY());
                    zoom=desired;bound();setImageMatrix(matrix);return true;
                }
            });
            detector=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDoubleTap(MotionEvent e){
                    if(getDrawable()==null)return false;
                    float desired=zoom>1.5f?1f:2.5f;
                    matrix.postScale(desired/zoom,desired/zoom,e.getX(),e.getY());
                    zoom=desired;bound();setImageMatrix(matrix);return true;
                }
            });
            setBackgroundColor(Color.BLACK);
        }
        void setNavigation(OnNavigate callback){navigation=callback;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            super.onSizeChanged(w,h,ow,oh);fit();
        }
        @Override public void setImageBitmap(Bitmap bitmap){super.setImageBitmap(bitmap);fit();}
        private void fit(){
            if(getDrawable()==null||getWidth()==0||getHeight()==0)return;
            float width=getDrawable().getIntrinsicWidth();
            float height=getDrawable().getIntrinsicHeight();
            if(width<=0||height<=0)return;
            float scale=Math.min(getWidth()/width,getHeight()/height);
            matrix.reset();matrix.postScale(scale,scale);
            matrix.postTranslate((getWidth()-width*scale)/2,(getHeight()-height*scale)/2);
            zoom=1f;setImageMatrix(matrix);
        }
        private void bound(){
            if(getDrawable()==null)return;
            RectF r=new RectF(0,0,getDrawable().getIntrinsicWidth(),
                getDrawable().getIntrinsicHeight());
            matrix.mapRect(r);
            float dx=r.width()<=getWidth()?(getWidth()-r.width())/2-r.left:
                r.left>0?-r.left:r.right<getWidth()?getWidth()-r.right:0;
            float dy=r.height()<=getHeight()?(getHeight()-r.height())/2-r.top:
                r.top>0?-r.top:r.bottom<getHeight()?getHeight()-r.bottom:0;
            matrix.postTranslate(dx,dy);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            scaler.onTouchEvent(e);detector.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    beginX=lastX=e.getX();beginY=lastY=e.getY();return true;
                case MotionEvent.ACTION_MOVE:
                    if(!scaler.isInProgress()&&zoom>1.01f&&e.getPointerCount()==1){
                        matrix.postTranslate(e.getX()-lastX,e.getY()-lastY);
                        bound();setImageMatrix(matrix);
                    }
                    lastX=e.getX();lastY=e.getY();return true;
                case MotionEvent.ACTION_UP:
                    float dx=e.getX()-beginX,dy=e.getY()-beginY;
                    if(zoom<=1.05f&&Math.abs(dx)>90&&Math.abs(dx)>Math.abs(dy)*1.5f&&
                        navigation!=null)navigation.navigate(dx);
                    return true;
                default:return true;
            }
        }
    }
}
