package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Interface tactile : catégories, pictogrammes, compteur, heure ou soirée entière. */
public final class ConsumptionActivity extends Activity {
    static final String EXTRA_EVENT_ID="event_id";
    static final String EXTRA_ENTRY_ID="entry_id";

    private static final int BG=Color.rgb(12,14,24);
    private static final int CARD=Color.rgb(25,28,44);
    private static final int TILE=Color.rgb(35,39,60);
    private static final int ACCENT=Color.rgb(186,166,255);
    private static final int SOFT=Color.rgb(184,190,208);
    private static final int WHITE=Color.WHITE;

    private static final class Choice {
        final String icon,name,category,unit;
        Choice(String icon,String name,String category,String unit){
            this.icon=icon;this.name=name;this.category=category;this.unit=unit;
        }
    }
    // Les pictogrammes sont des repères visuels, pas des informations sur le dosage.
    private static final Choice[] ALCOHOL={
        new Choice("🍺","Bière","Alcool","verre"),
        new Choice("🍷","Vin","Alcool","verre"),
        new Choice("🍸","Vodka","Alcool","verre"),
        new Choice("🥃","Whisky","Alcool","verre"),
        new Choice("➕","Autre","Alcool","verre")
    };
    private static final Choice[] SUBSTANCES={
        new Choice("💊","Ecstasy","Autres substances","prise"),
        new Choice("🧬","MDMA","Autres substances","prise"),
        new Choice("🧪","Kétamine","Autres substances","prise"),
        new Choice("❄️","Cocaïne","Autres substances","prise"),
        new Choice("⚡","Speed","Autres substances","prise"),
        new Choice("💨","Poppers","Autres substances","prise"),
        new Choice("🌿","Cannabis","Autres substances","prise"),
        new Choice("🌈","LSD","Autres substances","prise"),
        new Choice("🍄","Champignons","Autres substances","prise"),
        new Choice("💧","GHB / GBL","Autres substances","prise"),
        new Choice("➕","Autre","Autres substances","prise")
    };
    private static final Choice[] NON_ALCOHOL={
        new Choice("💧","Eau","Sans alcool","verre"),
        new Choice("🥤","Soft","Sans alcool","verre"),
        new Choice("➕","Autre","Sans alcool","verre")
    };

    private AppStore store;
    private JSONObject original=new JSONObject();
    private long eventId,entryId;
    private Choice selected;
    private String customName="";
    private int count=1;
    private String date="";
    private String time="";
    private boolean allNight=false;
    private String timeMode="exact",timeEnd="";
    private int quarterCount=0,halfCount=0,wholeCount=0;
    private String lastPart="";
    private LinearLayout substanceOptions,measurementBlock,routeBlock;
    private EditText measuredQty,customRoute;
    private Spinner unitSelect;
    private String routeChoice="";
    private String subjectiveSize="";
    private LinearLayout sizeBlock;
    private final List<TextView> sizeButtons=new ArrayList<>();
    private final List<TextView> routeButtons=new ArrayList<>();
    private LinearLayout ecstasyParts;
    private TextView partsSummary,quarterBtn,halfBtn,wholeBtn;
    private TextView exactMode,rangeMode,wholeNightMode,timeEndButton;

    private final List<View> tiles=new ArrayList<>();
    private final List<Choice> tileChoices=new ArrayList<>();
    private LinearLayout details;
    private ScrollView scroll;
    private TextView heading,quantityText,timeButton,dateButton,measureText;
    private CheckBox allEvening;
    private EditText costField,noteField;
    private LinearLayout extras;
    private TextView extrasToggle;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store=new AppStore(this);
        eventId=getIntent().getLongExtra(EXTRA_EVENT_ID,0);
        entryId=getIntent().getLongExtra(EXTRA_ENTRY_ID,0);
        JSONObject event=store.event(eventId);
        if(event==null){Toast.makeText(this,"Soirée introuvable",Toast.LENGTH_LONG).show();finish();return;}
        if(entryId!=0) {
            for(JSONObject entry:store.entries(eventId))if(entry.optLong("id")==entryId) {
                original=entry;break;
            }
            if(original.optLong("id")!=entryId){finish();return;}
        }
        count=Math.max(1,(int)Math.round(original.optDouble("quantity",1)));
        date=original.optString("date",event.optString("date",LocalDate.now().toString()));
        if(!validDate(date))date=LocalDate.now().toString();
        time=original.optString("time","");
        allNight=original.optBoolean("allNight",false);
        timeMode=original.optString("timeMode",allNight?"all":"exact");
        if(!timeMode.equals("exact")&&!timeMode.equals("between")&&!timeMode.equals("all"))timeMode="exact";
        timeEnd=original.optString("timeEnd","");
        JSONObject parts=original.optJSONObject("parts");
        if(parts!=null){
            quarterCount=Math.max(0,parts.optInt("quart",0));
            halfCount=Math.max(0,parts.optInt("demi",0));
            wholeCount=Math.max(0,parts.optInt("entier",0));
        }
        build(event);
        if(entryId!=0)restoreSelection();
        else if(substanceOptions!=null)substanceOptions.setVisibility(View.GONE);
    }

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius,boolean selected){
        GradientDrawable result=new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        if(selected)result.setStroke(dp(2),ACCENT);
        return result;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setLineSpacing(dp(3),1f);
        return t;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private void add(LinearLayout holder,View child,int bottom){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(bottom);
        holder.addView(child,lp);
    }
    private TextView action(String label,boolean prominent,Runnable callback){
        TextView t=text(label,16,prominent?BG:WHITE,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16),dp(15),dp(16),dp(15));
        t.setBackground(shape(prominent?ACCENT:TILE,14,false));
        t.setOnClickListener(v->callback.run());
        return t;
    }
    private void build(JSONObject event) {
        LinearLayout root=column();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top=horizontal();
        top.setPadding(dp(16),dp(10),dp(16),dp(9));
        add(root,top,0);
        TextView back=text("‹",38,WHITE,false);
        back.setContentDescription("Retour à la soirée");
        back.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        top.addView(back,backLp);
        back.setOnClickListener(v->finish());
        LinearLayout titles=column();
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        add(titles,text(entryId==0?"Ajouter une consommation":"Modifier une consommation",21,WHITE,true),3);
        add(titles,text(event.optString("title","Ma soirée"),12,SOFT,false),0);

        scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();
        content.setPadding(dp(16),dp(12),dp(16),dp(22));
        scroll.addView(content);

        add(content,text("QU'AS-TU CONSOMMÉ ?",13,ACCENT,true),4);
        add(content,text("Choisis un pictogramme pour ouvrir le compteur.",14,SOFT,false),19);

        section(content,"🍹  Alcool",ALCOHOL);
        section(content,"💊  Autres substances",SUBSTANCES);
        section(content,"💧  Sans alcool",NON_ALCOHOL);

        details=column();
        details.setPadding(dp(17),dp(20),dp(17),dp(20));
        details.setBackground(shape(CARD,20,false));
        details.setVisibility(View.GONE);
        add(content,details,20);

        heading=text("Sélection",21,WHITE,true);
        add(details,heading,12);
        add(details,text("QUANTITÉ",12,ACCENT,true),9);
        LinearLayout counter=horizontal();
        add(details,counter,8);

        TextView minus=action("−",false,()->{if(count>1){count--;refreshCounter();}});
        minus.setTextSize(24);
        counter.addView(minus,new LinearLayout.LayoutParams(dp(63),dp(58)));
        quantityText=text("1",30,WHITE,true);
        quantityText.setGravity(Gravity.CENTER);
        counter.addView(quantityText,new LinearLayout.LayoutParams(0,-2,1));
        TextView plus=action("+",false,()->{if(count<999){count++;refreshCounter();}});
        plus.setTextSize(24);
        counter.addView(plus,new LinearLayout.LayoutParams(dp(63),dp(58)));

        measureText=text("",13,SOFT,false);
        measureText.setGravity(Gravity.CENTER);
        add(details,measureText,12);
        ecstasyParts=column();
        add(details,ecstasyParts,18);
        add(ecstasyParts,text("AJOUTER LES MORCEAUX",12,ACCENT,true),8);
        LinearLayout typeRow=horizontal();
        add(ecstasyParts,typeRow,9);
        quarterBtn=action("Quart",false,()->addEcstasyPart("quart"));
        halfBtn=action("Demi",false,()->addEcstasyPart("demi"));
        wholeBtn=action("Entier",false,()->addEcstasyPart("entier"));
        for(TextView part:new TextView[]{quarterBtn,halfBtn,wholeBtn}){
            part.setTextSize(13);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1);
            lp.setMargins(dp(2),0,dp(2),0);
            typeRow.addView(part,lp);
        }
        partsSummary=text("",15,WHITE,true);
        add(ecstasyParts,partsSummary,9);
        add(ecstasyParts,action("↶  Retirer le dernier choix",false,this::undoLastEcstasyPart),6);
        add(ecstasyParts,text("Le compteur revient à zéro après chaque choix. L'application enregistre les fractions déclarées, pas une quantité de substance active.",12,SOFT,false),10);
        substanceOptions=column();
        add(details,substanceOptions,16);
        measurementBlock=column();
        add(substanceOptions,measurementBlock,12);
        add(measurementBlock,text("QUANTITÉ DÉCLARÉE",12,ACCENT,true),5);
        add(measurementBlock,text("Indique une quantité observée, sans estimation de teneur.",12,SOFT,false),8);
        LinearLayout doseRow=horizontal();
        add(measurementBlock,doseRow,6);
        measuredQty=editText("Quantité (facultatif)",false);
        measuredQty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        doseRow.addView(measuredQty,new LinearLayout.LayoutParams(0,dp(52),2));
        unitSelect=new Spinner(this);
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(0,dp(52),1);
        ulp.leftMargin=dp(7);
        doseRow.addView(unitSelect,ulp);
        sizeBlock=column();
        add(substanceOptions,sizeBlock,13);
        add(sizeBlock,text("TAILLE APPARENTE (OPTIONNELLE)",12,ACCENT,true),9);
        LinearLayout sizes=horizontal();
        add(sizeBlock,sizes,7);
        for(String size:new String[]{"Petit","Moyen","Grand"}){
            TextView b=action(size,false,()->{
                subjectiveSize=subjectiveSize.equals(size)?"":size;
                updateSizeButtons();
            });
            b.setTag(size);
            b.setTextSize(13);
            LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(49),1);
            slp.setMargins(dp(2),0,dp(2),0);
            sizes.addView(b,slp);
            sizeButtons.add(b);
        }
        add(sizeBlock,text("Appréciation personnelle : aucune correspondance fiable en mg ou en g.",12,SOFT,false),5);
        add(substanceOptions,text("MODE DE PRISE",12,ACCENT,true),9);
        routeBlock=column();
        add(substanceOptions,routeBlock,8);
        customRoute=editText("Décris la méthode de prise",false);
        add(substanceOptions,customRoute,7);
        customRoute.setVisibility(View.GONE);
        add(substanceOptions,text("Les unités et modes indiqués servent uniquement à tenir ton journal, pas à conseiller une dose ou une méthode.",12,SOFT,false),12);

        add(details,text("HEURE DE LA PRISE",13,ACCENT,true),10);
        add(details,text("Choisis la précision dont tu te souviens.",13,SOFT,false),12);
        LinearLayout modes=horizontal();add(details,modes,11);
        exactMode=action("🕒 Exacte",false,()->setTimeMode("exact"));
        rangeMode=action("↔ Entre",false,()->setTimeMode("between"));
        wholeNightMode=action("🌙 Soirée",false,()->setTimeMode("all"));
        for(TextView option:new TextView[]{exactMode,rangeMode,wholeNightMode}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(55),1);
            lp.setMargins(dp(2),0,dp(2),0);option.setTextSize(13);modes.addView(option,lp);
        }
        timeButton=action("🕒 Heure exacte ou début",false,this::pickTime);add(details,timeButton,9);
        timeEndButton=action("🕓 Fin de l'intervalle",false,this::pickEndTime);add(details,timeEndButton,10);
        add(details,text("Exemple : entre 13 h et 14 h 30. Si l'heure de fin est plus petite, l'intervalle passe au lendemain.",13,SOFT,false),18);
        updateTime();

        add(details,text("DATE",12,ACCENT,true),9);
        dateButton=action("📅  "+date,false,this::pickDate);
        add(details,dateButton,19);

        extrasToggle=action("＋  Ajouter un prix ou une note (facultatif)",false,()->{
            extras.setVisibility(extras.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
            extrasToggle.setText(extras.getVisibility()==View.VISIBLE?
                "−  Masquer les options facultatives":"＋  Ajouter un prix ou une note (facultatif)");
        });
        add(details,extrasToggle,8);
        extras=column();
        extras.setVisibility(View.GONE);
        add(details,extras,0);
        add(extras,text("Prix total (€)",13,SOFT,false),4);
        costField=editText("",false);
        costField.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String previousCost=original.optString("cost","");
        costField.setText("0".equals(previousCost)||"0.0".equals(previousCost)?"":previousCost);
        add(extras,costField,13);
        add(extras,text("Note personnelle",13,SOFT,false),4);
        noteField=editText("Facultatif",true);
        noteField.setText(original.optString("notes"));
        add(extras,noteField,3);
        if(!noteField.getText().toString().isEmpty()||
            !costField.getText().toString().isEmpty()) {
            extras.setVisibility(View.VISIBLE);
            extrasToggle.setText("−  Masquer les options facultatives");
        }
        updateTime();

        LinearLayout footer=column();
        footer.setPadding(dp(16),dp(8),dp(16),dp(15));
        add(root,footer,0);
        add(footer,action(entryId==0?"Enregistrer la consommation":"Enregistrer les modifications",true,this::save),8);
        if(entryId!=0){
            TextView delete=action("Supprimer cette consommation",false,this::confirmDelete);
            add(footer,delete,0);
        }
    }
    private EditText editText(String placeholder,boolean multi) {
        EditText input=new EditText(this);
        input.setTextSize(16);input.setTextColor(WHITE);
        input.setHintTextColor(SOFT);input.setHint(placeholder);
        input.setSingleLine(!multi);
        if(multi){input.setMinLines(2);input.setGravity(Gravity.TOP);}
        input.setPadding(dp(12),dp(10),dp(12),dp(10));
        input.setBackground(shape(TILE,12,false));
        return input;
    }
    private void section(LinearLayout content,String title,Choice[] choices){
        add(content,text(title,19,WHITE,true),12);
        int cols=3;
        for(int start=0;start<choices.length;start+=cols) {
            LinearLayout row=horizontal();
            row.setGravity(Gravity.TOP);
            add(content,row,10);
            for(int x=0;x<cols;x++) {
                int index=start+x;
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(108),1);
                lp.setMargins(dp(3),0,dp(3),0);
                if(index>=choices.length){
                    row.addView(new View(this),lp);
                    continue;
                }
                Choice choice=choices[index];
                LinearLayout tile=column();
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(4),dp(8),dp(4),dp(6));
                tile.setBackground(shape(TILE,18,false));
                tile.setContentDescription("Choisir "+choice.name);
                TextView picture=text(choice.icon,29,WHITE,false);
                picture.setGravity(Gravity.CENTER);
                add(tile,picture,6);
                TextView name=text(choice.name,12,WHITE,true);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                add(tile,name,0);
                row.addView(tile,lp);
                tiles.add(tile);tileChoices.add(choice);
                tile.setOnClickListener(v->select(choice,true));
            }
        }
        View divider=new View(this);
        divider.setBackgroundColor(Color.rgb(39,43,62));
        LinearLayout.LayoutParams separator=new LinearLayout.LayoutParams(-1,dp(1));
        separator.topMargin=dp(9);separator.bottomMargin=dp(21);
        content.addView(divider,separator);
    }
    private void restoreSelection() {
        String category=original.optString("category");
        String name=original.optString("name");
        for(Choice c:tileChoices) {
            if(c.category.equalsIgnoreCase(category)&&c.name.equalsIgnoreCase(name)) {
                select(c,false);return;
            }
        }
        for(Choice c:tileChoices) {
            if(c.category.equalsIgnoreCase(category)&&"Autre".equals(c.name)){
                customName=name;
                select(c,false);return;
            }
        }
        // Les anciennes catégories personnalisées restent modifiables.
        customName=name;
        selected=new Choice("➕","Autre",category.isEmpty()?"Autres substances":category,
            original.optString("unit","prise"));
        renderSelection();
    }
    private void select(Choice choice,boolean fromTap) {
        boolean changed=selected==null || selected!=choice;
        if(changed && "Ecstasy".equals(choice.name))count=0;
        else if(changed && count==0)count=1;
        if(changed){routeChoice="";subjectiveSize="";}
        selected=choice;
        if(!"Autre".equals(choice.name))customName="";
        renderSelection();
        if(fromTap && "Autre".equals(choice.name))askCustomName();
    }
    private void askCustomName() {
        EditText name=new EditText(this);
        name.setSingleLine(true);
        name.setText(customName);
        name.setHint("Nom de la consommation");
        LinearLayout holder=column();
        holder.setPadding(dp(20),0,dp(20),0);
        holder.addView(name);
        new AlertDialog.Builder(this)
            .setTitle("Autre consommation")
            .setMessage("Indique son nom pour ton journal.")
            .setView(holder)
            .setPositiveButton("Valider",(d,w)->{
                String value=name.getText().toString().trim();
                if(!value.isEmpty())customName=value;
                renderSelection();
            })
            .setNegativeButton("Annuler",(d,w)->{})
            .show();
    }
    private void renderSelection() {
        for(int i=0;i<tiles.size();i++) {
            Choice c=tileChoices.get(i);
            boolean active=selected!=null && c==selected;
            tiles.get(i).setBackground(shape(active?Color.rgb(51,45,81):TILE,18,active));
        }
        if(selected==null){details.setVisibility(View.GONE);return;}
        details.setVisibility(View.VISIBLE);
        String label="Autre".equals(selected.name)&&!customName.isEmpty()?customName:selected.name;
        heading.setText(selected.icon+"  "+label);
        configureSubstanceOptions();
        refreshCounter();
        scroll.post(()->scroll.smoothScrollTo(0,details.getTop()));
    }

    private boolean isDrug() {
        return selected!=null && "Autres substances".equals(selected.category);
    }
    private String[] unitsFor(String substance) {
        switch(substance){
            case "MDMA":case "Kétamine":case "Cocaïne":case "Speed":
                return new String[]{"mg","g","unité"};
            case "LSD": return new String[]{"buvard(s)","goutte(s)","unité"};
            case "Champignons":case "Cannabis": return new String[]{"g","mg","unité"};
            case "GHB / GBL": return new String[]{"mL","unité"};
            case "Poppers": return new String[]{"inhalation(s)","unité"};
            default: return new String[]{"unité","mg","g","mL"};
        }
    }
    private String[] routesFor(String substance) {
        switch(substance) {
            case "MDMA":case "Speed":case "Kétamine":
                return new String[]{"Orale (para)","Nasale (trace)","Autre"};
            case "Cocaïne": return new String[]{"Nasale (trace)","Autre"};
            case "LSD": return new String[]{"Buvard oral","Gouttes orales","Autre"};
            case "Ecstasy": return new String[]{"Orale","Autre"};
            case "Cannabis": return new String[]{"Fumée","Vaporisée","Orale","Autre"};
            case "Champignons": return new String[]{"Orale","Autre"};
            case "Poppers": return new String[]{"Inhalée","Autre"};
            case "GHB / GBL": return new String[]{"Orale","Autre"};
            default: return new String[]{"Orale","Nasale","Inhalée","Autre"};
        }
    }
    private void configureSubstanceOptions() {
        if(substanceOptions==null)return;
        if(!isDrug()){
            substanceOptions.setVisibility(View.GONE);
            return;
        }
        substanceOptions.setVisibility(View.VISIBLE);
        measurementBlock.setVisibility(isEcstasy()?View.GONE:View.VISIBLE);
        sizeBlock.setVisibility(isEcstasy()?View.GONE:View.VISIBLE);
        if(entryId!=0 && subjectiveSize.isEmpty())
            subjectiveSize=original.optString("subjectiveSize");
        updateSizeButtons();
        if(!isEcstasy()){
            String[] units=unitsFor(selected.name);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,units);
            unitSelect.setAdapter(adapter);
            String previousUnit=original.optString("measuredUnit",original.optString("unit"));
            for(int i=0;i<units.length;i++) {
                if(units[i].equals(previousUnit))unitSelect.setSelection(i);
            }
            String value=original.has("measuredAmount")?
                original.optString("measuredAmount"):"";

            measuredQty.setText(value);
        }
        routeBlock.removeAllViews();
        routeButtons.clear();
        String storedRoute=original.optString("route");
        if(routeChoice.isEmpty() && !storedRoute.isEmpty())routeChoice=storedRoute;
        String[] routes=routesFor(selected.name);
        for(int i=0;i<routes.length;i+=2) {
            LinearLayout row=horizontal();
            add(routeBlock,row,8);
            for(int j=i;j<Math.min(i+2,routes.length);j++){
                String name=routes[j];
                TextView option=action(name,false,()->{
                    routeChoice=name;
                    updateRouteButtons();
                });
                option.setTag(name);
                option.setTextSize(13);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);
                lp.setMargins(dp(2),0,dp(2),0);
                row.addView(option,lp);
                routeButtons.add(option);
            }
            if(i+1>=routes.length)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        }
        if(entryId!=0)customRoute.setText(original.optString("routeOther"));
        updateRouteButtons();
    }
    private void updateSizeButtons() {
        for(TextView b:sizeButtons){
            boolean selectedSize=subjectiveSize.equals(b.getTag());
            b.setBackground(shape(selectedSize?ACCENT:TILE,12,selectedSize));
            b.setTextColor(selectedSize?BG:WHITE);
        }
    }
    private void updateRouteButtons() {
        for(TextView b:routeButtons) {
            boolean active=routeChoice.equals(b.getTag());
            b.setBackground(shape(active?ACCENT:TILE,12,active));
            b.setTextColor(active?BG:WHITE);
        }
        customRoute.setVisibility("Autre".equals(routeChoice)?View.VISIBLE:View.GONE);
    }

    private boolean isEcstasy() {
        return selected!=null && "Ecstasy".equalsIgnoreCase(selected.name);
    }
    private void addEcstasyPart(String part) {
        if(count<1){
            Toast.makeText(this,"Choisis d'abord un nombre avec +",Toast.LENGTH_SHORT).show();
            return;
        }
        if("quart".equals(part))quarterCount+=count;
        else if("demi".equals(part))halfCount+=count;
        else wholeCount+=count;
        lastPart=part;
        count=0;
        refreshCounter();
    }
    private void undoLastEcstasyPart() {
        if("quart".equals(lastPart)&&quarterCount>0)quarterCount--;
        else if("demi".equals(lastPart)&&halfCount>0)halfCount--;
        else if("entier".equals(lastPart)&&wholeCount>0)wholeCount--;
        else {
            if(wholeCount>0)wholeCount--;
            else if(halfCount>0)halfCount--;
            else if(quarterCount>0)quarterCount--;
        }
        lastPart="";
        refreshCounter();
    }
    private static String ecstasySummary(int whole,int half,int quarter) {
        java.util.ArrayList<String> parts=new java.util.ArrayList<>();
        if(whole>0)parts.add(whole+" entier"+(whole>1?"s":""));
        if(half>0)parts.add(half+" demi");
        if(quarter>0)parts.add(quarter+" quart"+(quarter>1?"s":""));
        return parts.isEmpty()?"Aucun morceau ajouté":android.text.TextUtils.join(" + ",parts);
    }
    private void refreshCounter(){
        quantityText.setText(String.valueOf(count));
        if(selected==null)return;
        if(isEcstasy()) {
            if(ecstasyParts!=null)ecstasyParts.setVisibility(View.VISIBLE);
            if(partsSummary!=null)partsSummary.setText(ecstasySummary(wholeCount,halfCount,quarterCount));
            if(quarterBtn!=null)quarterBtn.setText("Quart"+(quarterCount>0?" · "+quarterCount:""));
            if(halfBtn!=null)halfBtn.setText("Demi"+(halfCount>0?" · "+halfCount:""));
            if(wholeBtn!=null)wholeBtn.setText("Entier"+(wholeCount>0?" · "+wholeCount:""));
            measureText.setText("Choisis un nombre puis Quart, Demi ou Entier");
        }else {
            if(ecstasyParts!=null)ecstasyParts.setVisibility(View.GONE);
            String label=isDrug()?"prise":selected.unit;
            measureText.setText(count+" "+label+(count>1?"s":"")+" enregistré"+(count>1?"s":""));
        }
    }
    private void setTimeMode(String mode) {timeMode=mode;allNight="all".equals(mode);updateTime();}
    private void updateTime() {
        if(timeButton==null)return;
        boolean exact="exact".equals(timeMode),range="between".equals(timeMode);
        exactMode.setBackground(shape(exact?ACCENT:TILE,12,exact));
        rangeMode.setBackground(shape(range?ACCENT:TILE,12,range));
        wholeNightMode.setBackground(shape(allNight?ACCENT:TILE,12,allNight));
        exactMode.setTextColor(exact?BG:WHITE);
        rangeMode.setTextColor(range?BG:WHITE);
        wholeNightMode.setTextColor(allNight?BG:WHITE);
        timeButton.setVisibility(allNight?View.GONE:View.VISIBLE);
        timeEndButton.setVisibility(range?View.VISIBLE:View.GONE);
        timeButton.setText(time.isEmpty()?(range?"🕒 Heure de début":"🕒 Choisir l'heure exacte"):"🕒 "+time+" · Modifier");
        timeEndButton.setText(timeEnd.isEmpty()?"🕓 Heure de fin":"🕓 "+timeEnd+" · Modifier");
    }
    private void pickTime(){chooseTime(false);}
    private void pickEndTime(){chooseTime(true);}
    private void chooseTime(boolean ending) {
        LocalTime value;
        try{value=LocalTime.parse(ending?timeEnd:time);}catch(Exception e){value=LocalTime.now();}
        new TimePickerDialog(this,(picker,hour,minute)->{
            String t=String.format(Locale.ROOT,"%02d:%02d",hour,minute);
            if(ending)timeEnd=t;else time=t;
            updateTime();
        },value.getHour(),value.getMinute(),true).show();
    }
    private boolean validDate(String value) {
        try {LocalDate.parse(value);return true;}catch(Exception e){return false;}
    }
    private void pickDate() {
        LocalDate day=LocalDate.parse(date);
        DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,dayOfMonth)->{
            date=String.format(Locale.ROOT,"%04d-%02d-%02d",year,month+1,dayOfMonth);
            dateButton.setText("📅  "+date);
        },day.getYear(),day.getMonthValue()-1,day.getDayOfMonth());
        dialog.show();
    }
    private void save() {
        if(selected==null){Toast.makeText(this,"Choisis un pictogramme",Toast.LENGTH_LONG).show();return;}
        String name="Autre".equals(selected.name)?customName: selected.name;
        if(name.trim().isEmpty()){
            askCustomName();
            Toast.makeText(this,"Indique le nom de la consommation",Toast.LENGTH_LONG).show();
            return;
        }
        if(!allNight && time.isEmpty()) {
            Toast.makeText(this,"Choisis l'heure exacte ou le début",Toast.LENGTH_LONG).show();return;
        }
        if("between".equals(timeMode)&&timeEnd.isEmpty()){
            Toast.makeText(this,"Choisis l'heure de fin",Toast.LENGTH_LONG).show();return;
        }
        if(isDrug() && "Autre".equals(routeChoice) &&
            customRoute.getText().toString().trim().isEmpty()){
            Toast.makeText(this,"Décris la méthode de prise",Toast.LENGTH_LONG).show();
            return;
        }
        if(isEcstasy()) {
            if(quarterCount+halfCount+wholeCount==0) {
                Toast.makeText(this,"Ajoute au moins un quart, un demi ou un entier",Toast.LENGTH_LONG).show();
                return;
            }
            if(count>0) {
                Toast.makeText(this,"Il reste "+count+" au compteur : choisis Quart, Demi ou Entier avant d'enregistrer",Toast.LENGTH_LONG).show();
                return;
            }
        }
        try{
            String costInput=costField.getText().toString().trim().replace(",",".");
            double cost=costInput.isEmpty()?0:Double.parseDouble(costInput);
            if(!Double.isFinite(cost)||cost<0){
                Toast.makeText(this,"Prix invalide",Toast.LENGTH_LONG).show();return;
            }
            JSONObject entry=new JSONObject(original.toString());
            entry.put("eventId",eventId);
            entry.put("category",selected.category);
            entry.put("name",name);
            if(isEcstasy()) {
                JSONObject parts=new JSONObject();
                parts.put("quart",quarterCount);
                parts.put("demi",halfCount);
                parts.put("entier",wholeCount);
                entry.put("parts",parts);
                entry.put("quantity",quarterCount+halfCount+wholeCount);
                entry.put("unit","morceaux");
            } else {
                entry.remove("parts");
                if(!isDrug()){
                    entry.put("quantity",count);
                    entry.put("unit",selected.unit);
                }
            }
            if(isDrug()) {
                entry.put("route",routeChoice);
                entry.put("subjectiveSize",subjectiveSize);
                entry.put("routeOther","Autre".equals(routeChoice)?
                    customRoute.getText().toString().trim():"");
                if(!isEcstasy()) {
                    String typed=measuredQty.getText().toString().trim().replace(",",".");
                    double amount=typed.isEmpty()?0:Double.parseDouble(typed);
                    if(!Double.isFinite(amount)||amount<0||amount>100000)throw new Exception("Quantité invalide");
                    entry.put("quantity",count);
                    entry.put("unit","prise");
                    entry.put("measuredAmount",typed);
                    entry.put("measuredUnit",unitSelect.getSelectedItem().toString());
                } else {
                    entry.remove("measuredAmount");
                entry.remove("measuredUnit");
                entry.remove("subjectiveSize");
                    entry.remove("measuredUnit");
                }
            } else {
                entry.remove("route");
                entry.remove("routeOther");
                entry.remove("measuredAmount");
            }
            entry.put("date",date);
            entry.put("time",allNight?"":time);
            entry.put("timeMode",timeMode);
            entry.put("timeEnd","between".equals(timeMode)?timeEnd:"");
            entry.put("allNight",allNight);
            entry.put("cost",cost);
            entry.put("notes",noteField.getText().toString().trim());
            store.saveEntry(entry);
            setResult(RESULT_OK);finish();
        }catch(Exception error){
            Toast.makeText(this,"Impossible d'enregistrer : vérifie les champs",Toast.LENGTH_LONG).show();
        }
    }
    private void confirmDelete(){
        new AlertDialog.Builder(this)
            .setTitle("Supprimer cette consommation ?")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(dialog,which)->{
                store.deleteEntry(entryId);
                setResult(RESULT_OK);
                finish();
            }).show();
    }
    public static String iconFor(String category,String name) {
        for(Choice[] group:new Choice[][]{ALCOHOL,SUBSTANCES,NON_ALCOHOL}){
            for(Choice c:group)
                if(c.category.equalsIgnoreCase(category)&&c.name.equalsIgnoreCase(name))return c.icon;
        }
        if("Alcool".equalsIgnoreCase(category))return "🍹";
        if("Sans alcool".equalsIgnoreCase(category))return "🥤";
        return "🧾";
    }
}
