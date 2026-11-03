package fr.afterlog.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * One consistent AfterLog dialog for every app-owned confirmation or input.
 * The Android AlertDialog is only a window host: all visible content and
 * buttons are drawn by AfterLog instead of the platform's grey dialog UI.
 */
final class NeonDialog {
    private static final int BG=Color.rgb(20,20,34);
    private static final int PANEL=Color.rgb(33,29,52);
    private static final int LILAC=Color.rgb(194,174,255);
    private static final int MUTED=Color.rgb(189,185,207);

    static Builder builder(Context context){return new Builder(context);}
    private static int dp(Context c,float value) {
        return Math.round(value*c.getResources().getDisplayMetrics().density);
    }
    private static GradientDrawable background(int fill,int stroke,int radius,Context ctx){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(ctx,radius));
        if(stroke!=Color.TRANSPARENT)drawable.setStroke(dp(ctx,1),stroke);
        return drawable;
    }

    static final class Builder extends AlertDialog.Builder {
        private final Context context;
        private CharSequence title="",message="";
        private CharSequence positive="",negative="",neutral="";
        private DialogInterface.OnClickListener positiveAction,negativeAction,neutralAction;
        private View content;
        private boolean cancelable=true;
        Builder(Context context){super(context);this.context=context;}
        @Override public Builder setTitle(CharSequence text){title=text;return this;}
        @Override public Builder setTitle(int id){title=context.getText(id);return this;}
        @Override public Builder setMessage(CharSequence text){message=text;return this;}
        @Override public Builder setMessage(int id){message=context.getText(id);return this;}
        @Override public Builder setView(View view){content=view;return this;}
        @Override public Builder setCancelable(boolean enabled){cancelable=enabled;return this;}
        @Override public Builder setPositiveButton(CharSequence text,
                       DialogInterface.OnClickListener action){
            positive=text;positiveAction=action;return this;
        }
        @Override public Builder setNegativeButton(CharSequence text,
                       DialogInterface.OnClickListener action){
            negative=text;negativeAction=action;return this;
        }
        @Override public Builder setNeutralButton(CharSequence text,
                       DialogInterface.OnClickListener action){
            neutral=text;neutralAction=action;return this;
        }
        @Override public Builder setPositiveButton(int id,
                       DialogInterface.OnClickListener action){
            return setPositiveButton(context.getText(id),action);
        }
        @Override public Builder setNegativeButton(int id,
                       DialogInterface.OnClickListener action){
            return setNegativeButton(context.getText(id),action);
        }
        @Override public Builder setNeutralButton(int id,
                       DialogInterface.OnClickListener action){
            return setNeutralButton(context.getText(id),action);
        }

        @Override public AlertDialog create(){
            LinearLayout panel=new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(context,22),dp(context,23),dp(context,22),dp(context,19));
            panel.setBackground(background(BG,Color.rgb(91,66,128),25,context));
            panel.setElevation(dp(context,18));

            TextView eyebrow=new TextView(context);
            eyebrow.setText("✦  AFTERLOG");
            eyebrow.setTextColor(LILAC);
            eyebrow.setTextSize(10);
            eyebrow.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            eyebrow.setLetterSpacing(.22f);
            LinearLayout.LayoutParams eyebrowLp=new LinearLayout.LayoutParams(-1,-2);
            eyebrowLp.bottomMargin=dp(context,12);
            panel.addView(eyebrow,eyebrowLp);

            if(title!=null && title.length()>0){
                TextView heading=new TextView(context);
                heading.setText(title);
                heading.setTextSize(22);
                heading.setTextColor(Color.WHITE);
                heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(-1,-2);
                headingLp.bottomMargin=dp(context,11);
                panel.addView(heading,headingLp);
            }

            if(message!=null && message.length()>0){
                ScrollView scroll=new ScrollView(context);
                scroll.setFillViewport(false);
                scroll.setVerticalScrollBarEnabled(false);
                TextView description=new TextView(context);
                description.setText(message);
                description.setTextColor(MUTED);
                description.setTextSize(14);
                description.setLineSpacing(dp(context,3),1.05f);
                scroll.addView(description,new ScrollView.LayoutParams(-1,-2));
                LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,-2);
                scrollLp.bottomMargin=dp(context,17);
                panel.addView(scroll,scrollLp);
            }

            if(content!=null){
                if(content.getParent() instanceof ViewGroup)
                    ((ViewGroup)content.getParent()).removeView(content);
                styleEditFields(content);
                LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,-2);
                bodyLp.bottomMargin=dp(context,16);
                panel.addView(content,bodyLp);
            }

            // Use a transparent window: no platform title, buttons or grey bezel.
            AlertDialog dialog=new AlertDialog.Builder(context)
                .setView(panel).setCancelable(cancelable).create();

            if(positive!=null&&positive.length()>0)
                addAction(panel,dialog,positive,positiveAction,true);
            if(neutral!=null&&neutral.length()>0)
                addAction(panel,dialog,neutral,neutralAction,false);
            if(negative!=null&&negative.length()>0)
                addAction(panel,dialog,negative,negativeAction,false);
            dialog.setOnShowListener(ignored->{
                Window win=dialog.getWindow();
                if(win==null)return;
                win.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams lp=win.getAttributes();
                lp.width=Math.min(context.getResources().getDisplayMetrics().widthPixels-
                    dp(context,32),dp(context,420));
                lp.height=WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity=Gravity.CENTER;
                lp.dimAmount=.70f;
                win.setAttributes(lp);
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                win.setNavigationBarColor(Color.rgb(12,14,24));
            });
            return dialog;
        }
        @Override public AlertDialog show(){
            AlertDialog dialog=create();
            dialog.show();
            return dialog;
        }
        private void addAction(LinearLayout panel,AlertDialog dialog,
                               CharSequence label,DialogInterface.OnClickListener callback,
                               boolean primary){
            TextView button=new TextView(context);
            button.setText(label);
            button.setTextSize(14);
            button.setTextColor(primary?Color.rgb(17,13,30):Color.WHITE);
            button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(context,12),dp(context,14),
                dp(context,12),dp(context,14));
            button.setMinHeight(dp(context,48));
            button.setBackground(background(primary?LILAC:PANEL,
                primary?Color.TRANSPARENT:Color.rgb(71,63,99),15,context));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=dp(context,8);
            panel.addView(button,lp);
            button.setOnClickListener(v->{
                dialog.dismiss();
                if(callback!=null)callback.onClick(dialog,primary?
                    DialogInterface.BUTTON_POSITIVE:
                    label==neutral?DialogInterface.BUTTON_NEUTRAL:
                    DialogInterface.BUTTON_NEGATIVE);
            });
        }
        private void styleEditFields(View view){
            if(view instanceof EditText){
                EditText field=(EditText)view;
                field.setTextColor(Color.WHITE);
                field.setHintTextColor(MUTED);
                field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(LILAC));
            }
            if(view instanceof ViewGroup){
                ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount();i++)
                    styleEditFields(group.getChildAt(i));
            }
        }
    }
}
