package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** Vidéos privées de la soirée, accessibles et lisibles hors ligne dans AfterLog. */
final class EventVideos {
    static final long MAX_BYTES=200L*1024*1024;
    static final int MAX_COUNT=12;
    private static final String DIR="event_videos";

    private static File folder(Context context) {
        File folder=new File(context.getFilesDir(),DIR);
        if(!folder.exists())folder.mkdirs();
        return folder;
    }
    static File file(Context context,String name) {
        if(name==null || !name.matches("[0-9a-f-]{36}\\.(mp4|webm|3gp|mkv)"))return null;
        return new File(folder(context),name);
    }
    static JSONArray list(JSONObject event) {
        JSONArray videos=event.optJSONArray("videos");
        return videos==null?new JSONArray():videos;
    }
    static String importVideo(Context context,Uri uri)throws Exception {
        if(uri==null||!"content".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("Choisis une vidéo de la galerie.");
        String mime=context.getContentResolver().getType(uri);
        if(mime==null||!mime.startsWith("video/"))
            throw new IllegalArgumentException("Le fichier sélectionné n'est pas une vidéo.");
        String extension="mp4";
        if(mime.contains("webm"))extension="webm";
        else if(mime.contains("3gpp"))extension="3gp";
        else if(mime.contains("matroska"))extension="mkv";
        String filename=UUID.randomUUID().toString()+"."+extension;
        File destination=file(context,filename);
        if(destination==null)throw new Exception("Nom de fichier invalide.");
        long bytes=0;
        try(InputStream in=context.getContentResolver().openInputStream(uri);
            FileOutputStream out=new FileOutputStream(destination)){
            if(in==null)throw new Exception("Impossible de lire la vidéo.");
            byte[] block=new byte[32768];int length;
            while((length=in.read(block))!=-1){
                bytes+=length;
                if(bytes>MAX_BYTES)throw new Exception("Vidéo trop grande (200 Mo maximum).");
                out.write(block,0,length);
            }
            out.getFD().sync();
        }catch(Exception error){
            destination.delete();
            throw error;
        }
        if(bytes<1024){destination.delete();throw new Exception("Vidéo vide ou non reconnue.");}
        return filename;
    }
    static void append(JSONObject event,String filename)throws Exception {
        JSONArray videos=list(event);
        if(videos.length()>=MAX_COUNT)
            throw new IllegalArgumentException("12 vidéos maximum par soirée.");
        videos.put(filename);
        event.put("videos",videos);
    }
    static void remove(Context context,JSONObject event,int index)throws Exception{
        JSONArray old=list(event),updated=new JSONArray();
        String toDelete="";
        for(int i=0;i<old.length();i++){
            if(i==index)toDelete=old.optString(i);
            else updated.put(old.optString(i));
        }
        event.put("videos",updated);
        File f=file(context,toDelete);
        if(f!=null)f.delete();
    }
    static Bitmap thumbnail(Context context,String name) {
        File file=file(context,name);
        if(file==null||!file.isFile())return null;
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            Bitmap frame=retriever.getFrameAtTime();
            if(frame==null)return null;
            int max=Math.max(frame.getWidth(),frame.getHeight());
            if(max>400){
                float ratio=400f/max;
                Bitmap reduced=Bitmap.createScaledBitmap(frame,
                    Math.max(1,(int)(frame.getWidth()*ratio)),
                    Math.max(1,(int)(frame.getHeight()*ratio)),true);
                if(reduced!=frame)frame.recycle();
                return reduced;
            }
            return frame;
        }catch(Exception ignored){return null;}
        finally{try{retriever.release();}catch(Exception ignored){}}
    }
    static void play(Activity activity,String name) {
        File video=file(activity,name);
        if(video==null||!video.isFile()) {
            Toast.makeText(activity,"Vidéo introuvable sur le téléphone.",Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout frame=new LinearLayout(activity);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(12,12,12,12);
        VideoView player=new VideoView(activity);
        player.setBackgroundColor(android.graphics.Color.BLACK);
        frame.addView(player,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,Math.round(activity.getResources()
                .getDisplayMetrics().heightPixels*.43f)));
        TextView caption=new TextView(activity);
        caption.setText("Lecture locale · les formats pris en charge dépendent d'Android.");
        caption.setTextColor(android.graphics.Color.GRAY);
        caption.setPadding(10,12,10,0);
        frame.addView(caption);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Vidéo de la soirée")
            .setView(frame)
            .setPositiveButton("Fermer",(d,w)->{})
            .create();
        dialog.setOnDismissListener(d->player.stopPlayback());
        dialog.show();
        MediaController controls=new MediaController(activity);
        controls.setAnchorView(player);
        player.setMediaController(controls);
        player.setOnPreparedListener(p->{p.setLooping(false);player.start();});
        player.setOnErrorListener((p,what,extra)->{
            Toast.makeText(activity,"Ce format vidéo n'est pas pris en charge par le lecteur Android.",
                Toast.LENGTH_LONG).show();
            return true;
        });
        player.setVideoPath(video.getAbsolutePath());
        player.requestFocus();
    }
}
