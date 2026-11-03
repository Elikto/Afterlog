package fr.afterlog.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Consistent decorative color for each recorded substance across input,
 * event bubbles and tracking. RDR risk color is ALWAYS calculated separately:
 * decorative pink/cyan/purple/green never indicates a safe or dangerous dose.
 */
final class ConsumptionPalette {
    private ConsumptionPalette(){}
    private static int rgb(String hex){return Color.parseColor(hex);}
    static int forEntry(String category,String name){
        String key=normalize(name);
        switch(key){
            case "ecstasy":case "taz":return rgb("#FF5AB8");
            case "mdma":return rgb("#31DECF");
            case "ketamine":return rgb("#A48CFF");
            case "cocaine":return rgb("#58A8FF");
            case "speed":case "amphetamine":return rgb("#E2DB59");
            case "poppers":return rgb("#FF8E80");
            case "cannabis":return rgb("#51CF83");
            case "lsd":return rgb("#F9B85F");
            case "champignons":case "psilocybine":return rgb("#CA78D8");
            case "ghb gbl":case "ghb":case "gbl":return rgb("#55C4EF");
            case "biere":return rgb("#E1B06A");
            case "vin":return rgb("#D77DB7");
            case "vodka":return rgb("#9ACCE9");
            case "whisky":return rgb("#CA9372");
        }
        if("Alcool".equalsIgnoreCase(category))return rgb("#D6AB87");
        if("Autres substances".equalsIgnoreCase(category)){
            // Custom entries remain visibly distinguished and stable on relaunch.
            int hash=key.hashCode()&0x7fffffff;
            float hue=(hash%360+11)%360;
            return Color.HSVToColor(new float[]{hue,.47f,.94f});
        }
        return rgb("#BBA6FF");
    }
    static int muted(int color,float amount){
        int base=Color.rgb(21,23,38);
        float a=Math.max(0,Math.min(1,amount));
        return Color.rgb(
            (int)(Color.red(base)*(1-a)+Color.red(color)*a),
            (int)(Color.green(base)*(1-a)+Color.green(color)*a),
            (int)(Color.blue(base)*(1-a)+Color.blue(color)*a));
    }
    static GradientDrawable tile(int accent,boolean active,int radius,float density){
        GradientDrawable background=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{muted(accent,active?.34f:.22f),muted(accent,.07f)});
        background.setCornerRadius(radius*density);
        background.setStroke(Math.max(1,Math.round((active?2:1)*density)),
            active?accent:muted(accent,.52f));
        return background;
    }
    static GradientDrawable circle(int accent,float density){
        GradientDrawable background=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{muted(accent,.38f),muted(accent,.09f)});
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(Math.max(1,Math.round(density)),accent);
        return background;
    }
    private static String normalize(String s){
        if(s==null)return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace("ø","o"),
            Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replaceAll("[^a-z0-9]+"," ").trim();
    }
}
