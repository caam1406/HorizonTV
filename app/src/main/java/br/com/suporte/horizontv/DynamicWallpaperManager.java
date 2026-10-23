package br.com.suporte.horizontv;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

final class DynamicWallpaperManager {
    interface Listener {
        void onWallpaperChanged(Bitmap bitmap, OpenverseImage image);
        void onStatusChanged(String message);
    }

    private static final long REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private final DynamicWallpaperStore store;
    private final OpenverseImageClient client = new OpenverseImageClient();
    private final ExecutorService network;
    private final Handler handler;
    private final Listener listener;
    private boolean requestInFlight;
    private boolean refreshPending;

    DynamicWallpaperManager(DynamicWallpaperStore store, ExecutorService network,
                            Handler handler, Listener listener) {
        this.store = store;
        this.network = network;
        this.handler = handler;
        this.listener = listener;
    }

    void restore() {
        if (!store.isEnabled()) return;
        Bitmap bitmap = store.loadImage();
        OpenverseImage image = store.metadata();
        if (bitmap != null && image != null) listener.onWallpaperChanged(bitmap, image);
        if (System.currentTimeMillis() - store.updatedAt() >= REFRESH_INTERVAL_MS) refresh();
    }

    boolean isEnabled() {
        return store.isEnabled();
    }

    String query() {
        return store.query();
    }

    OpenverseImage currentImage() {
        return store.metadata();
    }

    void setEnabled(boolean enabled) {
        store.setEnabled(enabled);
        if (!enabled) {
            listener.onWallpaperChanged(null, null);
            listener.onStatusChanged("Fundo original ativado");
            return;
        }
        Bitmap bitmap = store.loadImage();
        OpenverseImage image = store.metadata();
        if (bitmap != null && image != null) listener.onWallpaperChanged(bitmap, image);
        refresh();
    }

    void setQuery(String query) {
        store.setQuery(query);
        store.setEnabled(true);
        refresh();
    }

    void refresh() {
        if (!store.isEnabled()) return;
        if (requestInFlight) {
            refreshPending = true;
            return;
        }
        requestInFlight = true;
        String query = store.query();
        listener.onStatusChanged("Buscando novo fundo em Openverse…");
        network.execute(() -> {
            try {
                OpenverseImageClient.Wallpaper result = client.fetch(query);
                if (store.isEnabled() && query.equals(store.query())) store.save(result.metadata, result.bitmap);
                handler.post(() -> {
                    requestInFlight = false;
                    if (store.isEnabled() && query.equals(store.query())) {
                        listener.onWallpaperChanged(result.bitmap, result.metadata);
                        listener.onStatusChanged("Fundo dinâmico atualizado");
                    }
                    refreshPendingIfNeeded();
                });
            } catch (IOException | JSONException | IllegalArgumentException error) {
                Log.w("HorizonTV", "Wallpaper refresh failed: " + error.getClass().getSimpleName());
                handler.post(() -> {
                    requestInFlight = false;
                    if (store.isEnabled() && query.equals(store.query())) {
                        listener.onStatusChanged("Não foi possível atualizar o fundo; mantendo o salvo");
                    }
                    refreshPendingIfNeeded();
                });
            }
        });
    }

    private void refreshPendingIfNeeded() {
        if (!refreshPending) return;
        refreshPending = false;
        refresh();
    }
}
