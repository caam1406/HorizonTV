package br.com.suporte.horizontv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

final class AppShortcutController {
    private final Activity activity;
    private final SharedPreferences preferences;
    private final DashboardView dashboard;
    private final Executor discoveryExecutor;
    private final Handler handler;
    private final BooleanSupplier isForeground;
    private final Map<String, AppShortcut> shortcutsByPackage = new HashMap<>();

    AppShortcutController(Activity activity, SharedPreferences preferences,
                          DashboardView dashboard, Executor discoveryExecutor, Handler handler,
                          BooleanSupplier isForeground) {
        this.activity = activity;
        this.preferences = preferences;
        this.dashboard = dashboard;
        this.discoveryExecutor = discoveryExecutor;
        this.handler = handler;
        this.isForeground = isForeground;
    }

    void refresh() {
        discoveryExecutor.execute(() -> {
            AppShortcutDiscovery.Result result = AppShortcutDiscovery.discover(
                    activity.getPackageManager(), activity.getPackageName(), preferences);
            handler.post(() -> {
                if (!isForeground.getAsBoolean() || activity.isFinishing() || activity.isDestroyed()) return;
                shortcutsByPackage.clear();
                for (AppShortcut shortcut : result.shortcuts) {
                    shortcutsByPackage.put(shortcut.packageName, shortcut);
                }
                dashboard.setAppShortcuts(result.shortcuts);
                dashboard.setLastApp(result.lastPackage, result.lastLabel);
            });
        });
    }

    void launch(String packageName) {
        AppShortcut shortcut = shortcutsByPackage.get(packageName);
        Intent launch = shortcut == null ? resolveLaunchIntent(packageName)
                : new Intent(Intent.ACTION_MAIN).setComponent(shortcut.component)
                        .addCategory(shortcut.launchCategory);
        if (launch == null && "com.tcl.tv".equals(packageName)) {
            launch = new Intent(Intent.ACTION_MAIN).setPackage(packageName)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        }
        if (launch == null) {
            clearLastAppIfMatching(packageName);
            Toast.makeText(activity, "O app ainda não está instalado.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            try {
                activity.startActivity(launch);
            } catch (ActivityNotFoundException error) {
                if (shortcut == null) throw error;
                Intent fallback = resolveLaunchIntent(packageName);
                if (fallback == null) throw error;
                activity.startActivity(fallback);
            }
            activity.overridePendingTransition(0, 0);
            rememberLastApp(packageName, shortcut);
        } catch (ActivityNotFoundException error) {
            clearLastAppIfMatching(packageName);
            Toast.makeText(activity, "Não foi possível abrir este app.", Toast.LENGTH_SHORT).show();
        }
    }

    private Intent resolveLaunchIntent(String packageName) {
        PackageManager packageManager = activity.getPackageManager();
        Intent launch = packageManager.getLeanbackLaunchIntentForPackage(packageName);
        return launch == null ? packageManager.getLaunchIntentForPackage(packageName) : launch;
    }

    private void rememberLastApp(String packageName, AppShortcut shortcut) {
        String label = shortcut == null ? packageName : shortcut.label;
        if (shortcut == null) {
            try {
                PackageManager packageManager = activity.getPackageManager();
                label = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)).toString().trim();
            } catch (PackageManager.NameNotFoundException ignored) {
                // Keep the package name as a fallback label when package metadata is unavailable.
            }
        }
        preferences.edit().putString(HomeActivity.KEY_LAST_APP_PACKAGE, packageName)
                .putString(HomeActivity.KEY_LAST_APP_LABEL, label).apply();
        dashboard.setLastApp(packageName, label);
    }

    private void clearLastAppIfMatching(String packageName) {
        if (!packageName.equals(preferences.getString(HomeActivity.KEY_LAST_APP_PACKAGE, null))) return;
        preferences.edit().remove(HomeActivity.KEY_LAST_APP_PACKAGE)
                .remove(HomeActivity.KEY_LAST_APP_LABEL).apply();
        dashboard.setLastApp(null, null);
    }
}
