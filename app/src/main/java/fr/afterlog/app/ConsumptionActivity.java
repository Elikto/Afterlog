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
        build(event);
        if(entryId!=0)restoreSelection();
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
        add(details,measureText,23);

        add(details,text("HEURE DE LA PRISE",13,ACCENT,true),10);
        timeButton=action("🕒  Choisir l'heure",false,this::pickTime);
        add(details,timeButton,11);
        allEvening=new CheckBox(this);
        allEvening.setText("Pendant toute la soirée");
        allEvening.setTextColor(WHITE);
        allEvening.setTextSize(16);
        allEvening.setButtonTintList(ColorStateList.valueOf(ACCENT));
        allEvening.setPadding(dp(2),dp(8),0,dp(8));
        allEvening.setChecked(allNight);
        allEvening.setOnCheckedChangeListener((button,checked)->{
            allNight=checked;updateTime();
        });
        add(details,allEvening,3);
        add(details,text("Coche cette case si tu ne te souviens pas de l'heure précise.",13,SOFT,false),18);

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
        refreshCounter();
        scroll.post(()->scroll.smoothScrollTo(0,details.getTop()));
    }
    private void refreshCounter(){
        quantityText.setText(String.valueOf(count));
        if(selected!=null){
            String unit=selected.unit;
            measureText.setText(count+" "+unit+(count>1?"s":"")+" enregistré"+(count>1?"s":""));
        }
    }
    private void updateTime() {
        if(timeButton==null)return;
        if(allNight) {
            timeButton.setText("🌙  Pendant toute la soirée");
            timeButton.setAlpha(0.5f);
            timeButton.setEnabled(false);
        }else {
            timeButton.setText(time.isEmpty()?"🕒  Choisir l'heure de la prise":"🕒  "+time+" · Modifier");
            timeButton.setAlpha(1f);
            timeButton.setEnabled(true);
        }
    }
    private void pickTime() {
        LocalTime value;
        try {value=LocalTime.parse(time);}catch(Exception e){value=LocalTime.now();}
        new TimePickerDialog(this,(picker,hour,minute)->{
            time=String.format(Locale.ROOT,"%02d:%02d",hour,minute);
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
            Toast.makeText(this,"Choisis une heure ou coche « Pendant toute la soirée »",Toast.LENGTH_LONG).show();
            return;
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
            entry.put("quantity",count);
            entry.put("unit",selected.unit);
            entry.put("date",date);
            entry.put("time",allNight?"":time);
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
