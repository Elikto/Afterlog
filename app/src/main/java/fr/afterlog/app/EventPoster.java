package fr.afterlog.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Offline-first illustrated poster. Every private event gets a real visual
 * even with no Shotgun link or personal photo. Shotgun/local photos take priority.
 */
final class EventPoster extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String title="",date="";
    private static final int CYAN=Color.rgb(16,225,231);
    private static final int PINK=Color.rgb(247,57,177);
    private static final int LILAC=Color.rgb(196,174,255);

    private EventPoster(Context ctx,JSONObject event){
        super(ctx);
        title=event.optString("title","Ma soirée");
        date=event.optString("date","");
        setContentDescription("Affiche NØCTARIA par défaut pour "+
            (title.trim().isEmpty()?"cette soirée":title));
    }

    static FrameLayout create(Context ctx,JSONObject event){
        FrameLayout container=new FrameLayout(ctx);
        GradientDrawable background=new GradientDrawable();
        background.setColor(Color.rgb(17,15,29));
        background.setCornerRadius(dp(ctx,17));
        container.setBackground(background);
        container.setClipToOutline(true);
        EventPoster art=new EventPoster(ctx,event);
        container.addView(art,new FrameLayout.LayoutParams(-1,-1));

        String local=event.optString("posterLocal");
        Bitmap localImage=local.isEmpty()?null:EventPhotos.preview(ctx,local,900);
        if(localImage!=null){
            ImageView picture=new ImageView(ctx);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setImageBitmap(localImage);
            container.addView(picture,new FrameLayout.LayoutParams(-1,-1));
            return container;
        }
        String remote=event.optString("poster");
        if(remote.startsWith("https://")){
            ImageView picture=new ImageView(ctx);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            container.addView(picture,new FrameLayout.LayoutParams(-1,-1));
            new Thread(()->{
                HttpURLConnection c=null;
                try{
                    c=(HttpURLConnection)new URL(remote).openConnection();
                    c.setConnectTimeout(7000);c.setReadTimeout(9000);
                    c.setRequestProperty("Accept","image/*");
                    if(c.getResponseCode()!=200)return;
                    android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
                    opts.inSampleSize=2;
                    Bitmap bitmap;
                    try(InputStream stream=c.getInputStream()){
                        bitmap=android.graphics.BitmapFactory.decodeStream(stream,null,opts);
                    }
                    if(bitmap!=null)new Handler(Looper.getMainLooper()).post(()->{
                        if(container.isAttachedToWindow())picture.setImageBitmap(bitmap);
                    });
                }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
            },"NOCTARIA-poster").start();
        }
        return container;
    }
    private static int dp(Context context,int value){
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }
    private void fill(int color){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setStyle(Paint.Style.FILL);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float scale=Math.max(w/720f,h/900f);
        canvas.save();
        canvas.translate((w-720*scale)/2f,(h-900*scale)/2f);
        canvas.scale(scale,scale);
        fill(Color.rgb(10,12,28));
        paint.setShader(new LinearGradient(0,0,720,900,
            new int[]{Color.rgb(31,10,48),Color.rgb(9,14,30),Color.rgb(9,48,60)},
            null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,720,900,paint);paint.setShader(null);

        // Diffuse neon light beams above a nighttime club skyline.
        Path ray=new Path();ray.moveTo(360,310);ray.lineTo(-150,900);
        ray.lineTo(230,900);ray.close();
        fill(Color.argb(22,253,63,188));canvas.drawPath(ray,paint);
        ray.reset();ray.moveTo(370,310);ray.lineTo(490,900);ray.lineTo(940,900);ray.close();
        fill(Color.argb(19,24,230,240));canvas.drawPath(ray,paint);

        for(int i=0;i<24;i++){
            float x=(i*97+41)%720,y=(i*211+61)%570;
            fill(i%2==0?Color.argb(145,255,91,204):Color.argb(140,68,225,248));
            canvas.drawCircle(x,y,1.7f+(i%3),paint);
        }
        fill(Color.rgb(18,24,44));
        for(int i=0;i<24;i++){
            float x=i*36-8;
            float top=700-(i*71%125);
            canvas.drawRect(x,top,x+31,910,paint);
        }
        paint.setColor(Color.argb(130,15,226,235));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
        for(int y=760;y<900;y+=23)canvas.drawLine(0,y,720,y,paint);
        for(int x=0;x<=720;x+=65)canvas.drawLine(x,760,360+(x-360)*1.9f,900,paint);

        fill(PINK);paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(54);
        paint.setShadowLayer(18,0,0,Color.argb(135,255,35,163));
        canvas.drawText("NØCTARIA",360,155,paint);paint.clearShadowLayer();
        fill(LILAC);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        paint.setTextSize(24);
        canvas.drawText("MES NUITS • MES SOUVENIRS",360,202,paint);

        paint.setColor(PINK);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);paint.setStrokeCap(Paint.Cap.ROUND);
        Path signal=new Path();signal.moveTo(80,317);signal.lineTo(205,317);
        signal.lineTo(245,282);signal.lineTo(276,350);signal.lineTo(319,295);
        signal.lineTo(345,317);signal.lineTo(640,317);
        canvas.drawPath(signal,paint);
        fill(Color.argb(225,13,17,36));canvas.drawRoundRect(62,405,658,586,28,28,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);
        paint.setColor(CYAN);canvas.drawRoundRect(62,405,658,586,28,28,paint);
        String label=title==null||title.trim().isEmpty()?"MA SOIRÉE":title.trim();
        if(label.length()>42)label=label.substring(0,39)+"…";
        fill(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(45);
        while(paint.measureText(label)>520&&paint.getTextSize()>23)
            paint.setTextSize(paint.getTextSize()-2);
        canvas.drawText(label,360,504,paint);
        if(!date.isEmpty()){
            fill(LILAC);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setTextSize(25);canvas.drawText(date,360,553,paint);
        }
        fill(CYAN);
        for(int i=0;i<23;i++){
            int bar=(int)(10+47*Math.abs(Math.sin(i*1.32)));
            float x=125+i*21;
            canvas.drawRoundRect(x,680-bar,x+10,680+bar,5,5,paint);
        }
        fill(Color.rgb(211,197,237));paint.setTextSize(21);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        canvas.drawText("LIVE IT. REMEMBER IT.",360,823,paint);
        canvas.restore();
    }
}
