package br.com.suporte.horizontv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppShortcutDiscovery {
    private static final String HIDDEN_HOME_PACKAGE = "com.gathin.tvhome";

    private AppShortcutDiscovery() { }

    static Result discover(PackageManager packageManager, String ownPackage,
                           SharedPreferences preferences) {
        Set<String> packageNames = new HashSet<>();
        List<AppShortcut> shortcuts = new ArrayList<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo activity : packageManager.queryIntentActivities(launcherIntent, 0)) {
                String packageName = activity.activityInfo.packageName;
                if (ownPackage.equals(packageName) || HIDDEN_HOME_PACKAGE.equals(packageName)
                        || !packageNames.add(packageName)) continue;
                String label = activity.loadLabel(packageManager).toString().trim();
                shortcuts.add(new AppShortcut(packageName, label,
                        new ComponentName(packageName, activity.activityInfo.name), category,
                        activity.loadIcon(packageManager)));
            }
        }
        Collections.sort(shortcuts, (left, right) -> left.label.compareToIgnoreCase(right.label));
        String lastPackage = preferences.getString(HomeActivity.KEY_LAST_APP_PACKAGE, null);
        String lastLabel = preferences.getString(HomeActivity.KEY_LAST_APP_LABEL, null);
        if (lastPackage != null) {
            AppShortcut lastShortcut = find(shortcuts, lastPackage);
            if (lastShortcut == null) {
                preferences.edit().remove(HomeActivity.KEY_LAST_APP_PACKAGE)
                        .remove(HomeActivity.KEY_LAST_APP_LABEL).apply();
                lastPackage = null;
                lastLabel = null;
            } else if (!lastShortcut.label.equals(lastLabel)) {
                lastLabel = lastShortcut.label;
                preferences.edit().putString(HomeActivity.KEY_LAST_APP_LABEL, lastLabel).apply();
            }
        }
        return new Result(shortcuts, lastPackage, lastLabel);
    }

    private static AppShortcut find(List<AppShortcut> shortcuts, String packageName) {
        for (AppShortcut shortcut : shortcuts) {
            if (packageName.equals(shortcut.packageName)) return shortcut;
        }
        return null;
    }

    static final class Result {
        final List<AppShortcut> shortcuts;
        final String lastPackage;
        final String lastLabel;

        Result(List<AppShortcut> shortcuts, String lastPackage, String lastLabel) {
            this.shortcuts = shortcuts;
            this.lastPackage = lastPackage;
            this.lastLabel = lastLabel;
        }
    }
}
