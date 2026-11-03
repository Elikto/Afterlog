package fr.afterlog.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Locale;

/** Détails et modification d'une prise dans le style AfterLog. */
final class EntryDetailsDialog {
    private final Activity activity;
    private final Dialog dialog;
    private final JSONObject entry;
    private final Runnable edit,delete;
    private static final int BG=Color.rgb(19,18,33);
    private static final int CARD=Color.rgb(32,29,49);
    private static final int SOFT=Color.rgb(177,175,200);
    private static final int ACCENT=Color.rgb(189,169,255);
    private static final int WHITE=Color.WHITE;
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(activity);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setLineSpacing(dp(2),1f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void add(LinearLayout parent,View child,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(bottom);parent.addView(child,p);
    }
    private TextView action(String name,boolean primary,Runnable onClick) {
        TextView t=text(name,14,primary?BG:WHITE,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9),dp(12),dp(9),dp(12));
        t.setBackground(bg(primary?ACCENT:CARD,16));
        t.setOnClickListener(v->onClick.run());
        return t;
    }
    EntryDetailsDialog(Activity activity,JSONObject entry,String quantity,String when,
                       Runnable edit,Runnable delete){
        this.activity=activity;
        this.entry=entry;
        this.edit=edit;this.delete=delete;
        dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),dp(13),dp(18),dp(16));
        panel.setBackground(bg(BG,26));
        View handle=new View(activity);
        handle.setBackground(bg(Color.rgb(105,96,130),6));
        LinearLayout.LayoutParams handleLp=new LinearLayout.LayoutParams(dp(40),dp(4));
        handleLp.gravity=Gravity.CENTER_HORIZONTAL;handleLp.bottomMargin=dp(14);
        panel.addView(handle,handleLp);
        add(panel,text("Mon journal · Détails",14,SOFT,false),13);
        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        add(panel,header,11);
        TextView glyph=text(ConsumptionActivity.iconFor(entry.optString("category"),
            entry.optString("name")),27,WHITE,true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(bg(CARD,21));
        header.addView(glyph,new LinearLayout.LayoutParams(dp(58),dp(58)));
        TextView title=text(entry.optString("name","Consommation"),24,WHITE,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);
        titleLp.leftMargin=dp(12);
        header.addView(title,titleLp);
        TextView summary=text(quantity,18,ACCENT,true);
        add(panel,summary,17);

        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,0,1f);
        panel.addView(scroll,scrollLp);

        LinearLayout info=new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14),dp(14),dp(14),dp(11));
        info.setBackground(bg(CARD,17));
        add(content,info,12);
        metadata(info,"CALENDRIER",entry.optString("date",""),0);
        if(!when.isEmpty())metadata(info,"HEURE",when,8);
        JSONArray portions=entry.optJSONArray("portions");
        if(portions!=null && portions.length()>0){
            for(int i=0;i<portions.length();i++){
                JSONObject portion=portions.optJSONObject(i);
                if(portion==null)continue;
                String size=portion.optString("size");
                String method=portion.optString("route");
                if("Autre".equals(method))method=portion.optString("routeOther","Autre");
                String label=portion.optInt("count",1)+" "+(size.isEmpty()?"prise(s)":size.toLowerCase(Locale.FRANCE));
                if(!method.isEmpty())label+=" · "+method;
                String grams=portion.optString("measuredAmount");
                if(!grams.isEmpty())label+=" · "+grams+" "+portion.optString("measuredUnit");
                metadata(info,"PRISE "+(i+1),label,8);
            }
        }else{
            String route=entry.optString("route");
            if("Autre".equals(route))route=entry.optString("routeOther","Autre");
            if(!route.isEmpty())metadata(info,"MODE",route,8);
        }
        if(entry.optDouble("cost",0)>0)
            metadata(info,"PRIX",String.format(Locale.FRANCE,"%.2f €",entry.optDouble("cost")),8);
        if(!entry.optString("notes").trim().isEmpty())
            metadata(info,"NOTES",entry.optString("notes"),8);

        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-1,dp(52));
        actionLp.topMargin=dp(13);
        panel.addView(actions,actionLp);
        TextView close=action("Fermer",false,dialog::dismiss);
        actions.addView(close,new LinearLayout.LayoutParams(0,-1,1));
        TextView modify=action("✎ Modifier",true,()->{dialog.dismiss();edit.run();});
        LinearLayout.LayoutParams editLp=new LinearLayout.LayoutParams(0,-1,1.2f);
        editLp.leftMargin=dp(8);actions.addView(modify,editLp);
        TextView remove=action("Supprimer la prise",false,()->confirmDeletion());
        remove.setTextColor(Color.rgb(255,149,158));
        add(panel,remove,0);
        LinearLayout.LayoutParams removeLp=(LinearLayout.LayoutParams)remove.getLayoutParams();
        removeLp.topMargin=dp(8);remove.setLayoutParams(removeLp);
        dialog.setContentView(panel);
    }
    private void metadata(LinearLayout target,String label,String value,int margin){
        if(value==null||value.trim().isEmpty())return;
        add(target,text(label,11,ACCENT,true),3);
        add(target,text(value,14,WHITE,false),margin);
    }
    private void configureWindow(Dialog target,float heightFraction){
        Window w=target.getWindow();
        if(w==null)return;
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp=w.getAttributes();
        lp.width=activity.getResources().getDisplayMetrics().widthPixels-dp(22);
        lp.height=Math.round(activity.getResources().getDisplayMetrics().heightPixels*heightFraction);
        lp.gravity=Gravity.BOTTOM;lp.dimAmount=.66f;
        w.setAttributes(lp);
    }
    private void confirmDeletion(){
        Dialog confirm=new Dialog(activity);
        confirm.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(23),dp(20),dp(17));
        layout.setBackground(bg(BG,24));
        add(layout,text("Supprimer cette prise ?",21,WHITE,true),11);
        add(layout,text("Cette suppression est définitive. Les autres éléments de la soirée seront conservés.",
            14,SOFT,false),19);
        LinearLayout row=new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        add(layout,row,0);
        row.addView(action("Annuler",false,confirm::dismiss),
            new LinearLayout.LayoutParams(0,dp(50),1));
        TextView remove=action("Supprimer",false,()->{
            confirm.dismiss();dialog.dismiss();delete.run();
        });
        remove.setTextColor(Color.rgb(255,142,153));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);
        p.leftMargin=dp(9);row.addView(remove,p);
        confirm.setContentView(layout);
        confirm.show();
        Window w=confirm.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(28),-2);
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.gravity=Gravity.CENTER;lp.dimAmount=.7f;w.setAttributes(lp);
        }
    }
    void show(){
        dialog.show();
        configureWindow(dialog,.60f);
    }
}
