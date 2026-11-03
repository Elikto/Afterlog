package fr.afterlog.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Courbes par type de consommation, avec marqueurs de vigilance sourcés. */
final class TrackingChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> labels=new ArrayList<>();
    private int[][] values=new int[3][0];
    private int[] alerts=new int[0]; // 0 rien, 1 vigilance, 2 risque documenté
    private final int[] colors={Color.rgb(183,164,255),Color.rgb(74,213,187),
        Color.rgb(255,188,129)};
    private float density;
    TrackingChartView(Context context){
        super(context);
        density=getResources().getDisplayMetrics().density;
        setMinimumHeight((int)(252*density));
    }
    void show(List<String> labels,int[][] values,int[] alerts){
        this.labels.clear();this.labels.addAll(labels);
        this.values=values;this.alerts=alerts;
        setContentDescription("Graphique d'évolution : alcool violet, MDMA turquoise, autres substances orange. Points de vigilance indiqués en orange et risques documentés en rouge.");
        invalidate();
    }
    private float dp(float value){return density*value;}
    private void color(int c,float width){
        paint.setColor(c);paint.setStrokeWidth(dp(width));paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
    }
    @Override protected void onMeasure(int wSpec,int hSpec){
        super.onMeasure(wSpec,MeasureSpec.makeMeasureSpec((int)dp(252),MeasureSpec.EXACTLY));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int n=labels.size();
        if(n==0)return;
        float left=dp(28),right=getWidth()-dp(15),top=dp(22),bottom=getHeight()-dp(46);
        int maximum=1;
        for(int[] series:values)for(int v:series)maximum=Math.max(maximum,v);
        float dy=(bottom-top)/4f;
        for(int y=0;y<=4;y++){
            float py=bottom-y*dy;
            color(Color.rgb(58,55,78),.75f);
            canvas.drawLine(left,py,right,py,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(10));paint.setColor(Color.rgb(175,176,191));
            canvas.drawText(Integer.toString(Math.round(maximum*y/4f)),dp(2),py+dp(4),paint);
        }
        float spacing=n>1?(right-left)/(n-1):0;
        // Evite la surcharge des étiquettes sur les vues mensuelles.
        int step=n>12?Math.max(2,(int)Math.ceil(n/7.0)):n>8?2:1;
        for(int i=0;i<n;i+=step){
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(10));paint.setColor(Color.rgb(187,188,203));
            paint.setTextAlign(Paint.Align.CENTER);
            String label=labels.get(i);
            if(label.length()>11)label=label.substring(0,10)+"…";
            canvas.drawText(label,left+i*spacing,bottom+dp(21),paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        for(int series=0;series<values.length;series++){
            int[] nums=values[series];
            if(nums==null||nums.length!=n)continue;
            color(colors[Math.min(series,colors.length-1)],2.5f);
            Path line=new Path();
            for(int i=0;i<n;i++){
                float x=left+i*spacing;
                float y=bottom-nums[i]/(float)maximum*(bottom-top);
                if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
            }
            canvas.drawPath(line,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colors[Math.min(series,colors.length-1)]);
            for(int i=0;i<n;i++)if(nums[i]>0)
                canvas.drawCircle(left+i*spacing,bottom-nums[i]/(float)maximum*(bottom-top),
                    dp(3.2f),paint);
        }
        for(int i=0;i<Math.min(alerts.length,n);i++){
            if(alerts[i]==0)continue;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alerts[i]==2?Color.rgb(239,107,110):Color.rgb(255,181,98));
            canvas.drawCircle(left+i*spacing,top-dp(10),dp(4),paint);
        }
    }
}
