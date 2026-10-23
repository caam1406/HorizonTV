package br.com.suporte.horizontv;

import android.content.SharedPreferences;
import android.os.Handler;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

final class BibleVerseRepository {
    interface Listener {
        void onVerseLoaded(BibleVerse verse);
    }

    private static final long REFRESH_MS = 24 * 60 * 60 * 1000L;
    private static final long REQUEST_COOLDOWN_MS = 30 * 1000L;
    private static final String KEY_TEXT = "bible_verse_text";
    private static final String KEY_REFERENCE = "bible_verse_reference";
    private static final String KEY_UPDATED_AT = "bible_verse_updated_at";

    private final SharedPreferences preferences;
    private final ExecutorService network;
    private final Handler handler;
    private long lastRequest;

    BibleVerseRepository(SharedPreferences preferences, ExecutorService network, Handler handler) {
        this.preferences = preferences;
        this.network = network;
        this.handler = handler;
    }

    BibleVerse restore() {
        String text = preferences.getString(KEY_TEXT, "");
        String reference = preferences.getString(KEY_REFERENCE, "");
        lastRequest = preferences.getLong(KEY_UPDATED_AT, 0);
        return text.isEmpty() || reference.isEmpty() ? null : new BibleVerse(text, reference);
    }

    void refresh(boolean force, Listener listener) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < REQUEST_COOLDOWN_MS
                || (!force && now - lastRequest < REFRESH_MS)) return;
        lastRequest = now;
        network.execute(() -> fetchAndCache(listener));
    }

    private void fetchAndCache(Listener listener) {
        BibleVerse verse = null;
        try {
            verse = BibleVerseClient.fetch();
            preferences.edit().putString(KEY_TEXT, verse.text)
                    .putString(KEY_REFERENCE, verse.reference)
                    .putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply();
        } catch (IOException | JSONException error) {
            android.util.Log.w("HorizonTV", "Bible verse refresh failed: " + error.getClass().getSimpleName());
            if (preferences.contains(KEY_TEXT)) return;
        }
        BibleVerse result = verse;
        handler.post(() -> listener.onVerseLoaded(result));
    }
}
