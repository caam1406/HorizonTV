package br.com.suporte.horizontv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class DynamicWallpaperStore {
    static final String DEFAULT_QUERY = "space nebula galaxy landscape";
    private static final String IMAGE_FILE = "dynamic-wallpaper.jpg";
    private static final String KEY_ENABLED = "dynamic_wallpaper_enabled";
    private static final String KEY_QUERY = "dynamic_wallpaper_query";
    private static final String KEY_UPDATED = "dynamic_wallpaper_updated_at";
    private static final String KEY_ID = "dynamic_wallpaper_id";
    private static final String KEY_TITLE = "dynamic_wallpaper_title";
    private static final String KEY_CREATOR = "dynamic_wallpaper_creator";
    private static final String KEY_LICENSE = "dynamic_wallpaper_license";
    private static final String KEY_LICENSE_VERSION = "dynamic_wallpaper_license_version";
    private static final String KEY_LICENSE_URL = "dynamic_wallpaper_license_url";
    private static final String KEY_SOURCE_URL = "dynamic_wallpaper_source_url";
    private static final String KEY_ATTRIBUTION = "dynamic_wallpaper_attribution";

    private final File imageFile;
    private final SharedPreferences preferences;
    private final DisplayResolution resolution;

    DynamicWallpaperStore(Context context, SharedPreferences preferences) {
        imageFile = new File(context.getFilesDir(), IMAGE_FILE);
        this.preferences = preferences;
        resolution = DisplayResolution.from(context);
    }

    boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, true);
    }

    void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    String query() {
        return preferences.getString(KEY_QUERY, DEFAULT_QUERY);
    }

    void setQuery(String query) {
        String safeQuery = query.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        if (safeQuery.length() > 80) safeQuery = safeQuery.substring(0, 80).trim();
        preferences.edit().putString(KEY_QUERY, safeQuery).apply();
    }

    long updatedAt() {
        return preferences.getLong(KEY_UPDATED, 0);
    }

    OpenverseImage metadata() {
        String id = preferences.getString(KEY_ID, "");
        if (id.isEmpty()) return null;
        return new OpenverseImage(id,
                preferences.getString(KEY_TITLE, "Imagem sem título"),
                preferences.getString(KEY_CREATOR, ""),
                preferences.getString(KEY_LICENSE, ""),
                preferences.getString(KEY_LICENSE_VERSION, ""),
                preferences.getString(KEY_LICENSE_URL, ""),
                preferences.getString(KEY_SOURCE_URL, ""),
                "",
                preferences.getString(KEY_ATTRIBUTION, ""));
    }

    Bitmap loadImage() {
        if (!imageFile.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = resolution.sampleSizeFor(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
    }

    void save(OpenverseImage metadata, Bitmap bitmap) throws IOException {
        File temporary = new File(imageFile.getParentFile(), IMAGE_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                throw new IOException("Não foi possível salvar o fundo dinâmico");
            }
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            temporary.delete();
            throw new IOException("Não foi possível finalizar o arquivo do fundo", error);
        }
        preferences.edit()
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .putString(KEY_ID, metadata.id)
                .putString(KEY_TITLE, metadata.title)
                .putString(KEY_CREATOR, metadata.creator)
                .putString(KEY_LICENSE, metadata.license)
                .putString(KEY_LICENSE_VERSION, metadata.licenseVersion)
                .putString(KEY_LICENSE_URL, metadata.licenseUrl)
                .putString(KEY_SOURCE_URL, metadata.sourceUrl)
                .putString(KEY_ATTRIBUTION, metadata.attribution)
                .apply();
    }
}
