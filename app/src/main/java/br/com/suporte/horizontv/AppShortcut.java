package br.com.suporte.horizontv;

import android.graphics.drawable.Drawable;

final class AppShortcut {
    final String packageName;
    final String label;
    final Drawable icon;

    AppShortcut(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }
}
