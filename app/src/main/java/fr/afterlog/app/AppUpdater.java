package fr.afterlog.app;

import android.app.Activity;
import android.content.ClipData;
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
    private static final String STABLE_METADATA=
        "https://github.com/Elikto/Afterlog/releases/download/apk-latest/update.json";
    private static final String ASSET_PREFIX="https://github.com/Elikto/Afterlog/releases/download/";
    private static final String MIME="application/vnd.android.package-archive";
    private static final int MAX_BYTES=150*1024*1024;
    private final Activity activity;
    private File pendingApk;
    private boolean waitingForPermission;
    private final AtomicBoolean canceled=new AtomicBoolean(false);

    AppUpdater(Activity activity) {this.activity=activity;}

    void check() {check(false);}
    void checkAutomatically() {
        android.content.SharedPreferences prefs=activity.getSharedPreferences("afterlog_updates",0);
        long now=System.currentTimeMillis();
        if(now-prefs.getLong("lastCheck",0)<12*60*60*1000L)return;
        prefs.edit().putLong("lastCheck",now).apply();
        check(true);
    }
    private void check(boolean automatic) {
        if(!automatic)showStatus("Recherche des mises à jour…");
        new Thread(()->{
            try {
                JSONObject release=latestRelease();
                if(release==null) {
                    if(automatic)return;
                    ui(()->NeonDialog.builder(activity)
                        .setTitle("NØCTARIA est à jour")
                        .setMessage("Tu utilises déjà la dernière version disponible sur GitHub.")
                        .setPositiveButton("OK",null).show());
                    return;
                }
                final String signer=release.optString("signerSha256");
                if(!signer.isEmpty() && !signer.equals(installedSignerSha256())){
                    ui(()->NeonDialog.builder(activity)
                        .setTitle("Migration de signature nécessaire")
                        .setMessage("NØCTARIA utilise maintenant une clé permanente comme CyberQuiz, mais cette installation utilise une ancienne clé de test. Android ne permet pas de la remplacer directement.\n\nN'efface pas cette application sans avoir sauvegardé tes consommations et tes médias. Cette transition est nécessaire une seule fois ; ensuite, le bouton MAJ fonctionnera sur les prochaines versions.")
                        .setNegativeButton("Fermer",null)
                        .setPositiveButton("Paramètres et sauvegarde",(d,w)->{
                            if(activity instanceof MainActivity)
                                ((MainActivity)activity).openBackupSettings();
                        }).show());
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
                        || name.matches("(?:AfterLog|NOCTIS|NOCTARIA)-v\\d+\\.\\d+\\.\\d+\\.apk")) {
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
                ui(()->NeonDialog.builder(activity)
                    .setTitle("Nouvelle version : "+tag)
                    .setMessage(details+"\n\nTélécharger l'APK depuis les Releases GitHub et lancer l'installation Android ?")
                    .setNegativeButton("Plus tard",null)
                    .setPositiveButton("Télécharger et installer",(d,w)->download(url,digest,size))
                    .show());
            }catch(Exception e) {
                if(automatic)return;
                ui(()->NeonDialog.builder(activity).setTitle("Vérification impossible")
                    .setMessage("Impossible de consulter les versions GitHub.\n\n"+e.getMessage())
                    .setPositiveButton("OK",null).show());
            }
        },"NOCTARIA-update-check").start();
    }

    private JSONObject latestRelease() throws Exception {
        // Même mécanisme que CyberQuiz : update.json stable, avec version,
        // somme SHA-256 et identité de signature du paquet.
        HttpURLConnection conn=null;
        try {
            URL url=new URL(STABLE_METADATA+"?ts="+System.currentTimeMillis());
            conn=(HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(12000);conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept","application/json");
            conn.setRequestProperty("Cache-Control","no-cache");
            conn.setRequestProperty("User-Agent","NOCTARIA-Android-updater");
            int status=conn.getResponseCode();
            if(status==404)
                throw new Exception("La distribution NØCTARIA avec signature permanente n'est pas encore configurée sur GitHub. Il faut d'abord enregistrer le secret privé de signature dans le dépôt.");
            if(status!=200)throw new Exception("GitHub HTTP "+status);
            JSONObject json;
            try(InputStream in=conn.getInputStream()) {
                json=new JSONObject(new String(readLimited(in,40000),
                    java.nio.charset.StandardCharsets.UTF_8));
            }
            if(!json.optBoolean("updateReady",false))
                throw new Exception("La mise à jour stable n'est pas encore publiée.");
            if(!activity.getPackageName().equals(json.optString("packageName")))
                throw new Exception("L'identité Android indiquée est incorrecte.");
            String version=json.optString("versionName");
            int code=json.optInt("versionCode");
            if(code<1 || !version.matches("\\d+\\.\\d+\\.\\d+"))
                throw new Exception("Numéro de version de mise à jour invalide.");
            String asset=json.optString("apkUrl");
            if(!asset.equals("https://github.com/Elikto/Afterlog/releases/download/apk-latest/AfterLog.apk"))
                throw new Exception("La source de l'APK stable est incorrecte.");
            String sha=json.optString("sha256").toLowerCase(Locale.ROOT);
            String signer=json.optString("signerSha256").toLowerCase(Locale.ROOT);
            if(!sha.matches("[a-f0-9]{64}") || !signer.matches("[a-f0-9]{64}"))
                throw new Exception("Empreintes de la mise à jour manquantes.");
            int size=json.optInt("size");
            if(size<10000 || size>MAX_BYTES)
                throw new Exception("Taille de la mise à jour incorrecte.");
            PackageInfo installed=activity.getPackageManager().getPackageInfo(
                activity.getPackageName(),0);
            if(code<=installed.versionCode)return null;
            JSONObject apk=new JSONObject();
            apk.put("name","NOCTARIA-v"+version+".apk");
            apk.put("browser_download_url",asset);
            apk.put("digest","sha256:"+sha);
            apk.put("size",size);
            JSONObject release=new JSONObject();
            release.put("tag_name","v"+version);
            release.put("body","Version stable signée pour les mises à jour NØCTARIA sans réinstallation.");
            release.put("signerSha256",signer);
            release.put("assets",new JSONArray().put(apk));
            return release;
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
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(194,174,255)));
        progress.setMax(100);
        holder.addView(progress);
        TextView label=new TextView(activity);
        label.setText("Téléchargement en cours…");
        label.setTextColor(Color.rgb(210,205,226));
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0,dp(12),0,0);
        holder.addView(label);
        AlertDialog dialog=NeonDialog.builder(activity)
            .setTitle("Mise à jour NØCTARIA")
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
                    if(!canceled.get()) NeonDialog.builder(activity)
                        .setTitle("Mise à jour non installée")
                        .setMessage(e.getMessage())
                        .setPositiveButton("OK",null).show();
                });
            }finally {if(conn!=null)conn.disconnect();}
        },"NOCTARIA-apk-download").start();
    }
    /** Ne lance pas d'installation de paquet différent ni de rétrogradation. */
    private String validate(File apk) {
        PackageManager pm=activity.getPackageManager();
        try {
            int flags=Build.VERSION.SDK_INT>=28 ?
                PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
            PackageInfo current=pm.getPackageInfo(activity.getPackageName(),flags);
            if(candidate==null)return "APK illisible.";
            if(!activity.getPackageName().equals(candidate.packageName))
                return "Cet APK n'appartient pas à NØCTARIA.";
            if(candidate.versionCode<=current.versionCode)
                return "La version téléchargée n'est pas plus récente.";
            Signature[] ours=signatures(current);
            Signature[] theirs=signatures(candidate);
            if(ours==null || theirs==null || ours.length==0 || theirs.length==0 ||
               !Arrays.equals(ours,theirs)) {
                return "Signature Android différente. Cette version ne peut pas remplacer directement l'application installée. "
                    +"Exporte d'abord ta sauvegarde JSON dans NØCTARIA → Réglages, puis installe la nouvelle version depuis GitHub. "
                    +"La mise à jour automatique nécessite une signature stable.";
            }
            return "";
        }catch(Exception e){return "Impossible de vérifier cet APK : "+e.getMessage();}
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo info){
        if(info==null)return null;
        if(Build.VERSION.SDK_INT>=28 && info.signingInfo!=null){
            return info.signingInfo.hasMultipleSigners()?
                info.signingInfo.getApkContentsSigners():
                info.signingInfo.getSigningCertificateHistory();
        }
        return info.signatures;
    }
    private String installedSignerSha256() {
        try {
            PackageManager pm=activity.getPackageManager();
            int flags=Build.VERSION.SDK_INT>=28 ?
                PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            Signature[] current=signatures(pm.getPackageInfo(activity.getPackageName(),flags));
            if(current==null || current.length==0)return "";
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(current[0].toByteArray());
            StringBuilder result=new StringBuilder();
            for(byte item:digest)result.append(String.format(Locale.ROOT,"%02x",item&255));
            return result.toString();
        }catch(Exception e){return "";}
    }
    private void installOrAskPermission(){
        if(pendingApk==null||!pendingApk.isFile()){showStatus("APK introuvable");return;}
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            NeonDialog.builder(activity).setTitle("Autoriser les mises à jour")
                .setMessage("Android doit autoriser NØCTARIA à proposer l'installation de son APK. Autorise « Installer des applications inconnues » pour NØCTARIA, puis reviens dans l'application.")
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
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(),
                "Mise à jour NØCTARIA",UpdateFileProvider.uri(activity.getPackageName())));
            if(intent.resolveActivity(activity.getPackageManager())==null)
                throw new Exception("Installateur Android introuvable");
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
