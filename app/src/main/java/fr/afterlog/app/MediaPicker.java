package fr.afterlog.app;

import android.content.Intent;
import android.os.Build;
import android.provider.MediaStore;

/** Ouvre la photothèque Android, sans envoyer l'utilisateur dans Fichiers. */
final class MediaPicker {
    private MediaPicker(){}
    static Intent photosAndVideos() {
        if(Build.VERSION.SDK_INT>=33) {
            // Android Photo Picker : grille de photos et vidéos, albums et multi-sélection.
            Intent gallery=new Intent(MediaStore.ACTION_PICK_IMAGES);
            gallery.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
                Math.max(2,Math.min(50,MediaStore.getPickImagesMaxLimit())));
            return gallery;
        }
        // Compatible anciens Android : galerie système lorsqu'elle est disponible.
        Intent gallery=new Intent(Intent.ACTION_GET_CONTENT);
        gallery.setType("*/*");
        gallery.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        return Intent.createChooser(gallery,"Choisir dans la galerie");
    }
}
