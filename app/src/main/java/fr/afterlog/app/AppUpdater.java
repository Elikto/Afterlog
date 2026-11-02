package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdater {
    private static final String API="https://api.github.com/repos/Elikto/Afterlog/releases?per_page=25";
    private static final String ASSET_PREFIX="https://github.com/Elikto/Afterlog/releases/download/";
    private static final String MIME="application/vnd.android.package-archive";
    private static final int MAX_BYTES=50*1024*1024;
    private final Activity activity;
    private File pendingApk;
    private boolean waitingForPermission;
    private final AtomicBoolean canceled=new AtomicBoolean(false);

    AppUpdater(Activity activity) {this.activity=activity;}

    void check() {
        showStatus("Recherche des mises à jour…");
        new Thread(()->{
            try {
                JSONObject release=latestRelease();
                if(release==null) {
                    ui(()->new AlertDialog.Builder(activity)
                        .setTitle("AfterLog est à jour")
                        .setMessage("Tu utilises déjà la dernière version disponible sur GitHub.")
                        .setPositiveButton("OK",null).show());
                    return;
                }
                final String tag=release.getString("tag_name");
                final JSONArray assets=release.getJSONArray("assets");
                JSONObject apk=null;
                for(int i=0;i<assets.length();i++){
                    JSONObject item=assets.optJSONObject(i);
                    if(item==null)continue;
                    String name=item.optString("name");
                    if(name.equals("AfterLog-"+tag.replaceFirst("-debug$","")+".apk")
                        || name.matches("AfterLog-v\\d+\\.\\d+\\.\\d+\\.apk")) {
                        apk=item; break;
                    }
                }
                if(apk==null)throw new Exception("La version "+tag+" ne contient pas d'APK.");
                final String url=apk.getString("browser_download_url");
                if(!url.startsWith(ASSET_PREFIX) || !url.toLowerCase(Locale.ROOT).endsWith(".apk"))
                    throw new Exception("Adresse de téléchargement inattendue.");
                final String digest=apk.optString("digest");
                final int size=apk.optInt("size");
                if(size<10000 || size>MAX_BYTES)throw new Exception("Taille d'APK invalide.");
                final String notes=release.optString("body","").trim();
                final String details=notes.isEmpty() ? "Une nouvelle version est disponible."
                    : notes.length()>600 ? notes.substring(0,600)+"…" : notes;
                ui(()->new AlertDialog.Builder(activity)
                    .setTitle("Nouvelle version : "+tag)
                    .setMessage(details+"\n\nTélécharger l'APK depuis les Releases GitHub et lancer l'installation Android ?")
                    .setNegativeButton("Plus tard",null)
                    .setPositiveButton("Télécharger et installer",(d,w)->download(url,digest,size))
                    .show());
            }catch(Exception e) {
                ui(()->new AlertDialog.Builder(activity).setTitle("Vérification impossible")
                    .setMessage("Impossible de consulter les versions GitHub.\n\n"+e.getMessage())
                    .setPositiveButton("OK",null).show());
            }
        },"AfterLog-update-check").start();
    }

    private JSONObject latestRelease() throws Exception {
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(API).openConnection();
            conn.setConnectTimeout(12000);conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept","application/vnd.github+json");
            conn.setRequestProperty("User-Agent","AfterLog-Android-updater");
            int status=conn.getResponseCode();
            if(status!=200)throw new Exception("GitHub HTTP "+status);
            byte[] response;
            try(InputStream in=conn.getInputStream()) {response=readLimited(in,1_500_000);}
            JSONArray list=new JSONArray(new String(response,java.nio.charset.StandardCharsets.UTF_8));
            long installed=semver(installedVersion());
            long highest=installed;
            JSONObject best=null;
            for(int i=0;i<list.length();i++) {
                JSONObject item=list.optJSONObject(i);
                if(item==null||item.optBoolean("draft"))continue;
                long number=semver(item.optString("tag_name"));
                if(number>highest){highest=number;best=item;}
            }
            return best;
        }finally {if(conn!=null)conn.disconnect();}
    }
    private String installedVersion() {
        try {
            PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            return info.versionName==null?"0.0.0":info.versionName;
        }catch(Exception e) {return "0.0.0";}
    }
    private static long semver(String input) {
        Matcher match=Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matcher(input);
        if(!match.matches())return -1;
        try {
            return Long.parseLong(match.group(1))*1_000_000_000L+
                   Long.parseLong(match.group(2))*1_000_000L+
                   Long.parseLong(match.group(3));
        }catch(Exception e){return -1;}
    }
    private void download(String url,String expectedSha,int expectedSize) {
        canceled.set(false);
        LinearLayout holder=new LinearLayout(activity);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(20),dp(18),dp(20),dp(8));
        ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        holder.addView(progress);
        TextView label=new TextView(activity);
        label.setText("Téléchargement en cours…");
        label.setTextColor(Color.DKGRAY);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0,dp(12),0,0);
        holder.addView(label);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Mise à jour AfterLog")
            .setView(holder)
            .setNegativeButton("Annuler",(d,w)->canceled.set(true))
            .create();
        dialog.show();
        new Thread(()->{
            HttpURLConnection conn=null;
            File part=new File(activity.getCacheDir(),"afterlog-update.part");
            File target=new File(activity.getCacheDir(),UpdateFileProvider.FILE);
            try {
                URL requested=new URL(url);
                conn=(HttpURLConnection)requested.openConnection();
                conn.setConnectTimeout(15000);conn.setReadTimeout(25000);
                conn.setRequestProperty("User-Agent","AfterLog-Android-updater");
                if(conn.getResponseCode()!=200)throw new Exception("Téléchargement HTTP "+conn.getResponseCode());
                String host=conn.getURL().getHost();
                if(!("github.com".equalsIgnoreCase(host)||
                    "objects.githubusercontent.com".equalsIgnoreCase(host)||
                    "release-assets.githubusercontent.com".equalsIgnoreCase(host)))
                    throw new Exception("Redirection vers un serveur inattendu.");
                int total=conn.getContentLength();
                if(total>MAX_BYTES)throw new Exception("APK trop volumineux");
                MessageDigest md=MessageDigest.getInstance("SHA-256");
                int count=0;
                try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(part)){
                    byte[] buffer=new byte[16384];int n;
                    while((n=in.read(buffer))!=-1){
                        if(canceled.get())throw new Exception("Téléchargement annulé");
                        count+=n;
                        if(count>MAX_BYTES)throw new Exception("APK trop volumineux");
                        md.update(buffer,0,n);
                        out.write(buffer,0,n);
                        int percent=(int)(100L*count/(total>0?total:Math.max(expectedSize,1)));
                        ui(()->{
                            progress.setProgress(Math.min(100,percent));
                            label.setText(Math.min(100,percent)+" %");
                        });
                    }
                    out.getFD().sync();
                }
                if(count<10000)throw new Exception("Fichier APK vide");
                if(expectedSize>0&&count!=expectedSize)throw new Exception("Taille APK incohérente");
                if(expectedSha.startsWith("sha256:")) {
                    byte[] hash=md.digest();
                    StringBuilder actual=new StringBuilder();
                    for(byte b:hash)actual.append(String.format(Locale.ROOT,"%02x",b&255));
                    if(!actual.toString().equalsIgnoreCase(expectedSha.substring(7)))
                        throw new Exception("Empreinte SHA-256 incorrecte");
                }
                if(target.exists()&&!target.delete())throw new Exception("Ancien téléchargement inaccessible");
                if(!part.renameTo(target))throw new Exception("Impossible de préparer l'installation");
                String validation=validate(target);
                if(!validation.isEmpty())throw new Exception(validation);
                pendingApk=target;
                ui(()->{dialog.dismiss();installOrAskPermission();});
            }catch(Exception e){
                part.delete();
                ui(()->{
                    dialog.dismiss();
                    if(!canceled.get()) new AlertDialog.Builder(activity)
                        .setTitle("Mise à jour non installée")
                        .setMessage(e.getMessage())
                        .setPositiveButton("OK",null).show();
                });
            }finally {if(conn!=null)conn.disconnect();}
        },"AfterLog-apk-download").start();
    }
    /** Ne lance pas d'installation de paquet différent ni de rétrogradation. */
    private String validate(File apk) {
        PackageManager pm=activity.getPackageManager();
        try {
            PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
            PackageInfo current=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
            if(candidate==null)return "APK illisible.";
            if(!activity.getPackageName().equals(candidate.packageName))
                return "Cet APK n'appartient pas à AfterLog.";
            if(candidate.versionCode<=current.versionCode)
                return "La version téléchargée n'est pas plus récente.";
            Signature[] ours=current.signatures;
            Signature[] theirs=candidate.signatures;
            if(ours==null || theirs==null || ours.length==0 || theirs.length==0 ||
               !Arrays.equals(ours,theirs)) {
                return "Signature Android différente. Cette version ne peut pas remplacer directement l'application installée. "
                    +"Exporte d'abord ta sauvegarde JSON dans AfterLog → Réglages, puis installe la nouvelle version depuis GitHub. "
                    +"La mise à jour automatique nécessite une signature stable.";
            }
            return "";
        }catch(Exception e){return "Impossible de vérifier cet APK : "+e.getMessage();}
    }

    private void installOrAskPermission(){
        if(pendingApk==null||!pendingApk.isFile()){showStatus("APK introuvable");return;}
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Autoriser les mises à jour")
                .setMessage("Android doit autoriser AfterLog à proposer l'installation de son APK. Autorise « Installer des applications inconnues » pour AfterLog, puis reviens dans l'application.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Ouvrir les paramètres",(d,w)->{
                    waitingForPermission=true;
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName())));
                    }catch(Exception e){showStatus("Impossible d'ouvrir les paramètres de sécurité.");}
                }).show();
            return;
        }
        try {
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(UpdateFileProvider.uri(activity.getPackageName()),MIME);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }catch(Exception e){showStatus("Impossible d'ouvrir l'installation Android : "+e.getMessage());}
    }
    void onResume() {
        if(waitingForPermission && pendingApk!=null) {
            waitingForPermission=false;
            if(Build.VERSION.SDK_INT<26 || activity.getPackageManager().canRequestPackageInstalls())
                installOrAskPermission();
        }
    }
    private static byte[] readLimited(InputStream in,int limit) throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        byte[] block=new byte[8192];int count,n;
        while((n=in.read(block))!=-1){
            if(bytes.size()+n>limit)throw new Exception("Réponse GitHub trop grande");
            bytes.write(block,0,n);
        }
        return bytes.toByteArray();
    }
    private int dp(int value){return Math.round(activity.getResources().getDisplayMetrics().density*value);}
    private void ui(Runnable runnable){activity.runOnUiThread(runnable);}
    private void showStatus(String text){
        ui(()->android.widget.Toast.makeText(activity,text,android.widget.Toast.LENGTH_LONG).show());
    }
}
