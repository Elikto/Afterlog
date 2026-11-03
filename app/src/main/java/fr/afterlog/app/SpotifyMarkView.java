package fr.afterlog.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Petit pictogramme Spotify : vert si correspondance vérifiée, violet si recherche. */
public final class SpotifyMarkView extends View {
    private static final int VERIFIED=Color.rgb(30,215,96);
    private static final int SEARCH=Color.rgb(186,166,255);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean verified;

    SpotifyMarkView(Context context){super(context);}
    void setVerified(boolean value){verified=value;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight(),size=Math.min(w,h);
        float cx=w/2,cy=h/2,r=size*.46f;
        paint.setColor(verified?VERIFIED:SEARCH);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx,cy,r,paint);
        paint.setColor(Color.rgb(15,18,26));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<3;i++){
            float ry=r*(.62f-i*.19f);
            paint.setStrokeWidth(Math.max(1.5f,size*.062f-i*.27f));
            RectF arc=new RectF(cx-r*.68f,cy-r*.3f+i*r*.29f,
                cx+r*.74f,cy+ry+i*r*.22f);
            canvas.drawArc(arc,207,124,false,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
