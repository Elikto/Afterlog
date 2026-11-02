package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Rappel périodique Android, au plus tôt toutes les quinze minutes et sans heure exacte. */
final class AutoSyncScheduler {
    static final String JOB="afterlog-shotgun-auto-sync";
    private static final String PREFS="afterlog_auto_sync";

    static void enable(Context context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean("enabled",true).apply();
        schedule(context);
    }
    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean("enabled",false);
    }
    static void schedule(Context context) {
        if(!enabled(context))return;
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
            ShotgunScheduledWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            JOB,ExistingPeriodicWorkPolicy.KEEP,request);
    }
    static void disable(Context context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean("enabled",false).apply();
        WorkManager.getInstance(context).cancelUniqueWork(JOB);
    }
}
