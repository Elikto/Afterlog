package fr.afterlog.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Offre seulement l'APK de mise à jour téléchargé par AfterLog à l'installateur Android. */
public final class UpdateFileProvider extends ContentProvider {
    public static final String FILE = "afterlog-update.apk";
    public static Uri uri(String packageName) {
        return Uri.parse("content://" + packageName + ".update/" + FILE);
    }
    private File file(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri == null
            || !uri.getAuthority().equals(getContext().getPackageName() + ".update")
            || !("/" + FILE).equals(uri.getPath())) {
            throw new FileNotFoundException("URI non autorisée");
        }
        File f = new File(getContext().getCacheDir(), FILE);
        if (!f.isFile() || f.length() < 10000) throw new FileNotFoundException("APK absent");
        return f;
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Lecture seule");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        try {
            File f = file(uri);
            String[] columns = projection != null ? projection
                : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cursor = new MatrixCursor(columns);
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("AfterLog-update.apk");
                else if (OpenableColumns.SIZE.equals(column)) row.add(f.length());
                else row.add(null);
            }
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
