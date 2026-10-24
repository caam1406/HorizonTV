package br.com.suporte.horizontv;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

final class AppShortcut {
    final String packageName;
    final String label;
    final ComponentName component;
    final String launchCategory;
    final Drawable icon;

    AppShortcut(String packageName, String label, ComponentName component,
                String launchCategory, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.component = component;
        this.launchCategory = launchCategory;
        this.icon = icon;
    }
}
