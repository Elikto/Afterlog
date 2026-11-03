package fr.afterlog.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Fiche RDR personnalisée, sans pop-up Android grise. */
final class RdrWarningDialog {
    private static final int BG=Color.rgb(24,22,37);
    private static final int SECONDARY=Color.rgb(36,33,51);
    private static final int WHITE=Color.rgb(247,243,255);
    private static final int MUTED=Color.rgb(188,183,206);
    private static final int ACCENT=Color.rgb(189,162,255);
    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout content;
    private int dp(float value) {
        return Math.round(value*activity.getResources().getDisplayMetrics().density);
    }
    private GradientDrawable shape(int color,int radius){
        GradientDrawable s=new GradientDrawable();
        s.setColor(color);s.setCornerRadius(dp(radius));return s;
    }
    private TextView label(String value,int size,int color,boolean bold) {
        TextView view=new TextView(activity);view.setText(value);
        view.setTextSize(size);view.setTextColor(color);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }
    private void add(LinearLayout column,android.view.View child,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(bottom);
        column.addView(child,p);
    }
    private TextView action(String name,boolean primary,Runnable onClick){
        TextView t=label(name,14,primary?Color.rgb(22,19,35):WHITE,true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));t.setPadding(dp(14),dp(11),dp(14),dp(11));
        t.setBackground(shape(primary?ACCENT:SECONDARY,16));
        t.setOnClickListener(v->onClick.run());
        return t;
    }
    RdrWarningDialog(Activity activity,List<RiskGuidance.Warning> warnings){
        this.activity=activity;
        dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),dp(13),dp(18),dp(16));
        panel.setBackground(shape(BG,26));

        android.view.View handle=new android.view.View(activity);
        handle.setBackground(shape(Color.rgb(106,97,132),5));
        LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(42),dp(4));
        handleParams.gravity=Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin=dp(16);
        panel.addView(handle,handleParams);

        add(panel,label("♡  Réduction des risques",20,WHITE,true),6);
        add(panel,label("Informations basées sur ton journal, pas sur un diagnostic.",12,MUTED,false),17);

        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams=
            new LinearLayout.LayoutParams(-1,0,1);
        panel.addView(scroll,scrollParams);

        for(RiskGuidance.Warning warning:warnings){
            LinearLayout item=new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(13),dp(13),dp(13),dp(13));
            item.setBackground(shape(SECONDARY,18));
            add(content,item,11);
            add(item,label("●  "+warning.title,15,Color.rgb(255,189,128),true),9);
            add(item,label(warning.detail,14,WHITE,false),11);
            if(warning.reference!=null && warning.reference.startsWith("https://")){
                TextView link=label("Lire la source de prévention  ↗",13,ACCENT,true);
                link.setPadding(dp(2),dp(6),dp(2),dp(6));
                add(item,link,0);
                link.setOnClickListener(v->{
                    try{activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(warning.reference)));}catch(Exception ignored){}
                });
            }
        }
        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-1,-2);
        actionLp.topMargin=dp(10);
        panel.addView(actions,actionLp);
        TextView close=action("Fermer",false,dialog::dismiss);
        actions.addView(close,new LinearLayout.LayoutParams(0,dp(51),1));
        TextView suivi=action("Voir le suivi",true,()->{
            dialog.dismiss();
            activity.startActivity(new Intent(activity,SuiviActivity.class));
        });
        LinearLayout.LayoutParams suiviLp=new LinearLayout.LayoutParams(0,dp(51),1);
        suiviLp.leftMargin=dp(8);
        actions.addView(suivi,suiviLp);

        add(panel,label("En urgence : 15 / 112 · Drogues Info Service : 0 800 23 13 13",11,MUTED,false),0);

        dialog.setContentView(panel);
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params=window.getAttributes();
            params.width=activity.getResources().getDisplayMetrics().widthPixels-dp(24);
            params.height=Math.round(activity.getResources().getDisplayMetrics().heightPixels*.74f);
            params.gravity=Gravity.BOTTOM;
            params.dimAmount=.67f;
            window.setAttributes(params);
        }
    }
    void show(){dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(24),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels*.74f));
        }
    }
}
