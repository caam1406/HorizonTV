package br.com.suporte.horizontv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeActivity extends Activity {
    private static final int LOCATION_REQUEST = 42;
    private static final long NEWS_REFRESH_MS = 15 * 60 * 1000L;
    private static final long WEATHER_REFRESH_MS = 30 * 60 * 1000L;
    private static final long NEIGHBORHOOD_REFRESH_MS = 24 * 60 * 60 * 1000L;
    private static final double NEIGHBORHOOD_MOVE_THRESHOLD = 0.025;
    private static final long CLOCK_REFRESH_MS = 15 * 1000L;
    private static final String PREFS = "horizon_home";
    private static final String KEY_LATITUDE = "approx_latitude";
    private static final String KEY_LONGITUDE = "approx_longitude";
    private static final String KEY_WEATHER_TIME = "weather_updated_at";
    private static final String KEY_NEIGHBORHOOD = "neighborhood";
    private static final String KEY_NEIGHBORHOOD_LATITUDE = "neighborhood_latitude";
    private static final String KEY_NEIGHBORHOOD_LONGITUDE = "neighborhood_longitude";
    private static final String KEY_NEIGHBORHOOD_TIME = "neighborhood_updated_at";
    private static final String KEY_NEIGHBORHOOD_CONSENT = "neighborhood_lookup_allowed";
    private static final String KEY_LOCATION_PROMPT_SKIPPED = "location_prompt_skipped";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private long lastNewsRequest;
    private long lastWeatherRequest;
    private boolean foreground;
    private DashboardView dashboard;
    private SharedPreferences preferences;
    private WeatherSnapshot cachedWeather;

    private final Runnable refreshClock = new Runnable() {
        @Override
        public void run() {
            if (!foreground) return;
            dashboard.refreshClock();
            long now = System.currentTimeMillis();
            if (now - lastNewsRequest >= NEWS_REFRESH_MS) refreshNews(false);
            if (now - lastWeatherRequest >= WEATHER_REFRESH_MS && hasLocationPermission()) findLocation();
            handler.postDelayed(this, CLOCK_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        dashboard = new DashboardView(this, new DashboardView.Listener() {
            @Override public void launchPackage(String name) { HomeActivity.this.launchPackage(name); }
            @Override public void openNews(String url) { HomeActivity.this.openNews(url); }
            @Override public void showMessage(String message) { Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show(); }
            @Override public void refreshData() { refreshDataNow(); }
        });
        setContentView(dashboard);
        List<Headline> cached = NewsCache.load(preferences);
        dashboard.setHeadlines(cached, cached.isEmpty() ? "Conectando às fontes de notícias…" : "Exibindo as últimas manchetes salvas");
        restoreWeather();
        applyImmersiveMode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        foreground = true;
        refreshAppShortcuts();
        handler.removeCallbacks(refreshClock);
        refreshClock.run();
        refreshNews(false);
        if (hasLocationPermission()) findLocation();
        else explainLocationPermission(false);
    }

    @Override
    protected void onStop() {
        foreground = false;
        handler.removeCallbacks(refreshClock);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        network.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != LOCATION_REQUEST) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) findLocation();
        else {
            preferences.edit().putBoolean(KEY_LOCATION_PROMPT_SKIPPED, true).apply();
            dashboard.setWeather(null, neighborhoodLabel(), "Permita localização para ver o clima");
        }
    }

    private void refreshDataNow() {
        refreshNews(true);
        if (hasLocationPermission()) findLocation();
        else explainLocationPermission(true);
    }

    private void refreshNews(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNewsRequest < NEWS_REFRESH_MS) return;
        lastNewsRequest = now;
        network.execute(() -> {
            try {
                List<Headline> result = NewsFeedReader.fetch();
                handler.post(() -> {
                    if (isFinishing()) return;
                    if (!result.isEmpty()) {
                        NewsCache.save(preferences, result);
                        dashboard.setHeadlines(result, "Manchetes atualizadas agora");
                    } else {
                        dashboard.setHeadlines(NewsCache.load(preferences), "Nenhuma manchete disponível");
                    }
                });
            } catch (IOException | org.xml.sax.SAXException error) {
                Log.w("HorizonTV", "News refresh failed: " + error.getClass().getSimpleName()
                        + ": " + error.getMessage());
                handler.post(() -> {
                    if (isFinishing()) return;
                    List<Headline> cached = NewsCache.load(preferences);
                    dashboard.setHeadlines(cached, cached.isEmpty()
                            ? "Sem conexão com as fontes de notícias" : "Sem conexão · mostrando últimas manchetes salvas");
                });
            }
        });
    }

    private void explainLocationPermission(boolean force) {
        if (isFinishing() || hasLocationPermission()) return;
        if (!force && preferences.getBoolean(KEY_LOCATION_PROMPT_SKIPPED, false)) {
            dashboard.setWeather(null, neighborhoodLabel(), "Localização necessária para a previsão");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Clima da sua região")
                .setMessage("O Horizon usa a localização aproximada enquanto a tela inicial está aberta. As coordenadas arredondadas são enviadas por HTTPS à Open‑Meteo para o clima e, uma vez para identificar o bairro, ao OpenStreetMap. O bairro fica salvo na TV. Sem localização, o clima local não aparece.")
                .setPositiveButton("Permitir localização", (dialog, which) ->
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST))
                .setNegativeButton("Agora não", (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_LOCATION_PROMPT_SKIPPED, true).apply();
                    dashboard.setWeather(null, neighborhoodLabel(), "Localização necessária para a previsão");
                })
                .show();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void findLocation() {
        if (!hasLocationPermission()) return;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            dashboard.setWeather(null, neighborhoodLabel(), "Serviço de localização indisponível");
            return;
        }
        Location last = bestLastKnown(manager);
        if (last != null && System.currentTimeMillis() - last.getTime() < 12 * 60 * 60 * 1000L) {
            useLocation(last);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    if (!manager.isProviderEnabled(provider)) continue;
                    manager.getCurrentLocation(provider, new CancellationSignal(), command -> handler.post(command), location -> {
                        if (location != null && foreground) useLocation(location);
                        else if (last == null) dashboard.setWeather(null, neighborhoodLabel(), "Não foi possível obter a localização");
                    });
                    return;
                } catch (IllegalArgumentException | SecurityException ignored) {
                    // A provider may be absent or disabled on this TV; try the next system provider.
                }
            }
        } else {
            requestLegacyLocation(manager);
        }
        if (last == null) dashboard.setWeather(null, neighborhoodLabel(), "Ative a localização do Android TV");
    }

    @SuppressLint("MissingPermission")
    private Location bestLastKnown(LocationManager manager) {
        Location best = null;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Some TV firmware omits network or passive location providers.
            }
        }
        return best;
    }

    @SuppressLint("MissingPermission")
    private void requestLegacyLocation(LocationManager manager) {
        try {
            if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return;
            manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                @Override public void onLocationChanged(Location location) { if (foreground) useLocation(location); }
                @Override public void onProviderDisabled(String provider) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            }, Looper.getMainLooper());
        } catch (IllegalArgumentException | SecurityException ignored) {
            dashboard.setWeather(null, neighborhoodLabel(), "Localização de rede indisponível");
        }
    }

    private void useLocation(Location location) {
        double latitude = Math.rint(location.getLatitude() * 100) / 100.0;
        double longitude = Math.rint(location.getLongitude() * 100) / 100.0;
        preferences.edit().putFloat(KEY_LATITUDE, (float) latitude)
                .putFloat(KEY_LONGITUDE, (float) longitude).apply();
        maybeFetchNeighborhood(latitude, longitude);
        fetchWeather(latitude, longitude);
    }

    private void maybeFetchNeighborhood(double latitude, double longitude) {
        if (!preferences.contains(KEY_NEIGHBORHOOD_CONSENT)) {
            requestNeighborhoodConsent(latitude, longitude);
            return;
        }
        if (!preferences.getBoolean(KEY_NEIGHBORHOOD_CONSENT, false)) return;
        String label = neighborhoodLabel();
        long now = System.currentTimeMillis();
        double previousLatitude = preferences.getFloat(KEY_NEIGHBORHOOD_LATITUDE, Float.NaN);
        double previousLongitude = preferences.getFloat(KEY_NEIGHBORHOOD_LONGITUDE, Float.NaN);
        boolean hasPreviousCoordinates = Double.isFinite(previousLatitude) && Double.isFinite(previousLongitude);
        boolean moved = !hasPreviousCoordinates
                || Math.abs(latitude - previousLatitude) >= NEIGHBORHOOD_MOVE_THRESHOLD
                || Math.abs(longitude - previousLongitude) >= NEIGHBORHOOD_MOVE_THRESHOLD;
        long lastLookup = preferences.getLong(KEY_NEIGHBORHOOD_TIME, 0);
        if ((!label.isEmpty() && (!moved || now - lastLookup < NEIGHBORHOOD_REFRESH_MS))
                || (label.isEmpty() && now - lastLookup < NEIGHBORHOOD_REFRESH_MS)) return;

        preferences.edit().putLong(KEY_NEIGHBORHOOD_TIME, now)
                .putFloat(KEY_NEIGHBORHOOD_LATITUDE, (float) latitude)
                .putFloat(KEY_NEIGHBORHOOD_LONGITUDE, (float) longitude).apply();
        network.execute(() -> {
            try {
                String result = NeighborhoodClient.fetch(latitude, longitude);
                if (result.isEmpty()) return;
                preferences.edit().putString(KEY_NEIGHBORHOOD, result).apply();
                handler.post(() -> {
                    if (!isFinishing()) dashboard.setWeather(cachedWeather, result, "Bairro pela OpenStreetMap");
                });
            } catch (IOException | JSONException | IllegalArgumentException error) {
                Log.w("HorizonTV", "Neighborhood lookup failed: " + error.getClass().getSimpleName());
            }
        });
    }

    private void requestNeighborhoodConsent(double latitude, double longitude) {
        if (!foreground || isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Mostrar seu bairro no clima?")
                .setMessage("Para identificar o bairro aproximado, o Horizon enviará as coordenadas arredondadas desta TV ao OpenStreetMap (Nominatim). A consulta é feita na configuração inicial e só se repete se a TV mudar de região, no máximo uma vez por dia. O nome fica salvo nesta TV. Você pode continuar usando o clima sem compartilhar com esse serviço.")
                .setPositiveButton("Permitir bairro", (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_NEIGHBORHOOD_CONSENT, true).apply();
                    maybeFetchNeighborhood(latitude, longitude);
                })
                .setNegativeButton("Agora não", (dialog, which) ->
                        preferences.edit().putBoolean(KEY_NEIGHBORHOOD_CONSENT, false).apply())
                .show();
    }

    private String neighborhoodLabel() {
        String label = preferences.getString(KEY_NEIGHBORHOOD, "").trim();
        return label.isEmpty() ? "Sua região" : label;
    }

    private void restoreWeather() {
        if (!preferences.contains("weather_temperature")) {
            dashboard.setWeather(null, neighborhoodLabel(), "Permita localização para ver o clima");
            return;
        }
        WeatherSnapshot cached = new WeatherSnapshot(
                preferences.getFloat("weather_temperature", 0),
                preferences.getFloat("weather_feels_like", 0),
                preferences.getInt("weather_humidity", 0),
                preferences.getFloat("weather_wind_speed", 0),
                preferences.getInt("weather_code", -1));
        cachedWeather = cached;
        dashboard.setWeather(cached, neighborhoodLabel(), "Última atualização salva");
        lastWeatherRequest = preferences.getLong(KEY_WEATHER_TIME, 0);
    }

    private void fetchWeather(double latitude, double longitude) {
        long now = System.currentTimeMillis();
        if (now - lastWeatherRequest < WEATHER_REFRESH_MS) return;
        lastWeatherRequest = now;
        network.execute(() -> {
            try {
                WeatherSnapshot result = WeatherClient.fetch(latitude, longitude);
                cachedWeather = result;
                preferences.edit().putFloat("weather_temperature", (float) result.temperature)
                        .putFloat("weather_feels_like", (float) result.feelsLike)
                        .putInt("weather_humidity", result.humidity)
                        .putFloat("weather_wind_speed", (float) result.windSpeed)
                        .putInt("weather_code", result.code)
                        .putLong(KEY_WEATHER_TIME, System.currentTimeMillis()).apply();
                handler.post(() -> {
                    if (foreground && !isFinishing()) dashboard.setWeather(result, neighborhoodLabel(), "Previsão atualizada");
                });
            } catch (IOException | JSONException | IllegalArgumentException error) {
                handler.post(() -> {
                    if (!isFinishing()) dashboard.setWeather(cachedWeather, neighborhoodLabel(),
                            cachedWeather == null ? "Sem conexão para atualizar o clima" : "Sem conexão · último dado salvo");
                });
            }
        });
    }

    private void launchPackage(String packageName) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null && "com.tcl.tv".equals(packageName)) {
            launch = new Intent(Intent.ACTION_MAIN).setPackage(packageName)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        }
        if (launch == null) {
            Toast.makeText(this, "O app ainda não está instalado.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Não foi possível abrir este app.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAppShortcuts() {
        network.execute(() -> {
            PackageManager packageManager = getPackageManager();
            Set<String> packageNames = new HashSet<>();
            List<AppShortcut> shortcuts = new ArrayList<>();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo activity : packageManager.queryIntentActivities(launcherIntent, 0)) {
                    String packageName = activity.activityInfo.packageName;
                    if (getPackageName().equals(packageName) || !packageNames.add(packageName)) continue;
                    String label = activity.loadLabel(packageManager).toString().trim();
                    shortcuts.add(new AppShortcut(packageName, label, activity.loadIcon(packageManager)));
                }
            }
            Collections.sort(shortcuts, (left, right) -> left.label.compareToIgnoreCase(right.label));
            handler.post(() -> {
                if (foreground && !isFinishing()) dashboard.setAppShortcuts(shortcuts);
            });
        });
    }

    private void openNews(String url) {
        if (!NewsFeedReader.isAllowedArticleUrl(url)) {
            Toast.makeText(this, "Link de notícia inválido.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Instale um navegador para abrir a notícia.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
