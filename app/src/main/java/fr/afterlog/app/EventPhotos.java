package fr.afterlog.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** Photos utilisateur privées, compressées en JPEG et sauvegardées dans NØCTARIA. */
final class EventPhotos {
    private static final String DIR="event_photos";
    private static File folder(Context ctx) {
        File f=new File(ctx.getFilesDir(),DIR);
        if(!f.exists())f.mkdirs();
        return f;
    }
    static File file(Context ctx,String name) {
        if(name==null || !name.matches("[a-z0-9-]{36}\\.jpg"))return null;
        return new File(folder(ctx),name);
    }
    static String importUri(Context ctx,Uri uri) throws Exception {
        if(uri==null || !"content".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("Choisis une photo de ta galerie");
        String filename=UUID.randomUUID().toString()+".jpg";
        File output=file(ctx,filename);
        if(output==null)throw new Exception("Nom de fichier invalide");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("Image introuvable");
            BitmapFactory.decodeStream(in,null,options);
        }
        if(options.outWidth<1 || options.outHeight<1)throw new Exception("Format d'image non reconnu");
        int sample=1;
        while(options.outWidth/sample>1600 || options.outHeight/sample>1600)sample*=2;
        options.inJustDecodeBounds=false;
        options.inSampleSize=sample;
        Bitmap bitmap;
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("Impossible de lire la photo");
            bitmap=BitmapFactory.decodeStream(in,null,options);
        }
        if(bitmap==null)throw new Exception("Impossible de décoder la photo");
        int w=bitmap.getWidth(),h=bitmap.getHeight();
        if(Math.max(w,h)>1600) {
            float scale=1600f/Math.max(w,h);
            Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(w*scale)),
                    Math.max(1,(int)(h*scale)),true);
            bitmap.recycle();bitmap=scaled;
        }
        try(FileOutputStream out=new FileOutputStream(output)){
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,82,out))
                throw new Exception("Compression JPEG impossible");
            out.getFD().sync();
        }catch(Exception e){output.delete();throw e;}
        finally {bitmap.recycle();}
        return filename;
    }
    static JSONArray gallery(JSONObject event) {
        JSONArray pics=event.optJSONArray("photos");
        return pics==null?new JSONArray():pics;
    }
    static void append(JSONObject event,String filename) throws Exception {
        JSONArray pics=gallery(event);
        if(pics.length()>=40)throw new IllegalArgumentException("40 photos maximum par soirée");
        pics.put(filename);
        event.put("photos",pics);
    }
    static void deleteAt(Context ctx,JSONObject event,int index)throws Exception{
        JSONArray before=gallery(event),after=new JSONArray();
        String removed="";
        for(int i=0;i<before.length();i++){
            if(i==index)removed=before.optString(i);
            else after.put(before.optString(i));
        }
        event.put("photos",after);
        if(!removed.isEmpty()){File f=file(ctx,removed);if(f!=null)f.delete();}
    }
    static Bitmap preview(Context ctx,String name,int target) {
        File file=file(ctx,name);
        if(file==null || !file.isFile())return null;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        int sample=1;
        while(options.outWidth/sample>target*2 || options.outHeight/sample>target*2)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=Math.max(sample,1);
        return BitmapFactory.decodeFile(file.getAbsolutePath(),options);
    }
}
