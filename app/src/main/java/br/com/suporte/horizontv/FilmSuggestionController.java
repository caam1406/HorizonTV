package br.com.suporte.horizontv;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

final class FilmSuggestionController {
    interface Listener {
        void onFilmLoaded(FilmSuggestion film, Bitmap image);
        void onCatalogUnavailable();
    }

    private static final long REFRESH_MS = 24 * 60 * 60 * 1000L;
    private static final long COOLDOWN_MS = 10 * 1000L;
    private static final String KEY_ROTATION = "film_suggestion_rotation";

    private final SharedPreferences preferences;
    private final ExecutorService network;
    private final ExecutorService images;
    private final Handler handler;
    private final Listener listener;
    private long lastRequest;

    FilmSuggestionController(SharedPreferences preferences, ExecutorService network,
                             ExecutorService images, Handler handler, Listener listener) {
        this.preferences = preferences;
        this.network = network;
        this.images = images;
        this.handler = handler;
        this.listener = listener;
    }

    void refresh(boolean force) {
        long now = System.currentTimeMillis();
        FilmSuggestion cached = FilmSuggestionStore.load(preferences);
        boolean cacheIsFresh = cached != null && now - FilmSuggestionStore.updatedAt(preferences) < REFRESH_MS;
        if (cacheIsFresh) loadCachedImage(cached);
        if (!force && cacheIsFresh) return;
        if (now - lastRequest < COOLDOWN_MS) return;
        lastRequest = now;
        long rotation = preferences.getLong(KEY_ROTATION, 0);
        if (force) {
            rotation = (rotation + 1) % 100;
            preferences.edit().putLong(KEY_ROTATION, rotation).apply();
        }
        long selection = rotation;
        network.execute(() -> fetchAndDisplay(selection, cached));
    }

    private void fetchAndDisplay(long rotation, FilmSuggestion cached) {
        try {
            FilmSuggestion film = FilmSuggestionClient.fetch(rotation);
            FilmSuggestionStore.save(preferences, film);
            Bitmap image = loadImageOrNull(film.backgroundUrl);
            handler.post(() -> listener.onFilmLoaded(film, image));
        } catch (IOException | JSONException error) {
            Log.w("HorizonTV", "Movie suggestion refresh failed: " + error.getClass().getSimpleName());
            if (cached == null) handler.post(listener::onCatalogUnavailable);
        }
    }

    private Bitmap loadImageOrNull(String url) {
        try {
            return MovieImageLoader.fetch(url);
        } catch (IOException error) {
            Log.w("HorizonTV", "Movie image load failed: " + error.getClass().getSimpleName());
            return null;
        }
    }

    private void loadCachedImage(FilmSuggestion film) {
        images.execute(() -> {
            Bitmap image = loadImageOrNull(film.backgroundUrl);
            if (image != null) handler.post(() -> listener.onFilmLoaded(film, image));
        });
    }
}
