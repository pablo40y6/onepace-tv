package com.pablo40y6.onepacetv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class UpdatePackageProvider extends ContentProvider {
    static final String MIME_TYPE = "application/vnd.android.package-archive";
    private static final String FILE_NAME = "OnePaceTV-update.apk";
    private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    static File file(Context context) {
        return new File(context.getCacheDir(), FILE_NAME);
    }

    static Uri address(Context context) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".updates")
                .appendPath(FILE_NAME).build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return address(getContext()).equals(uri) ? MIME_TYPE : null;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || getType(uri) == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (getType(uri) == null) return null;
        String[] columns = projection == null ? COLUMNS : projection;
        MatrixCursor result = new MatrixCursor(columns);
        File apk = file(getContext());
        if (!apk.isFile()) return result;
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = FILE_NAME;
            if (OpenableColumns.SIZE.equals(columns[i])) values[i] = apk.length();
        }
        result.addRow(values);
        return result;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
}
