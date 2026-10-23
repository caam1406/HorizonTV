package br.com.suporte.horizontv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

final class AppShortcutDock {
    interface HitAreaRegistrar {
        void add(int id, float x, float y, float width, float height);
    }

    static final int SELECTION_BASE = 12;
    private static final int MAX_VISIBLE = 9;
    private static final int[] ICON_COLORS = {
            0xFFE83E68, 0xFF6946F5, 0xFFD331A6, 0xFF11B887, 0xFF347FF0,
            0xFFE87928, 0xFF7548E8, 0xFF12B9D2, 0xFF65758F
    };

    private final Path iconClip = new Path();
    private List<AppShortcut> shortcuts = new ArrayList<>();
    private int page;

    int update(List<AppShortcut> value, int selected) {
        shortcuts = new ArrayList<>(value);
        int pageCount = Math.max(1, (shortcuts.size() + MAX_VISIBLE - 1) / MAX_VISIBLE);
        page = Math.min(page, pageCount - 1);
        return handlesSelection(selected) && selected - SELECTION_BASE >= visibleCount()
                ? SELECTION_BASE : selected;
    }

    void draw(Canvas canvas, Paint paint, int selected, HitAreaRegistrar hitAreaRegistrar) {
        int startIndex = page * MAX_VISIBLE;
        int count = visibleCount();
        if (count == 0) {
            DashboardText.centerText(canvas, paint, "Nenhum aplicativo disponível", 640, 337,
                    0xFFE6EAF2, 12, Typeface.NORMAL);
            return;
        }

        float spacing = Math.min(113f, 1048f / count);
        float firstCenterX = (1280f - spacing * (count - 1)) / 2f;
        float centerY = 328f;
        for (int slot = 0; slot < count; slot++) {
            int selection = SELECTION_BASE + slot;
            float centerX = firstCenterX + spacing * slot;
            boolean focused = selected == selection;
            AppShortcut shortcut = shortcuts.get(startIndex + slot);
            paint.setColor(ICON_COLORS[slot]);
            canvas.drawCircle(centerX, centerY, focused ? 29 : 26, paint);
            if (focused) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(centerX, centerY, 32, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x3355DFFF);
                canvas.drawCircle(centerX, centerY, 38, paint);
            }
            drawAppIcon(canvas, shortcut.icon, centerX, centerY);
            String label = DashboardText.ellipsis(paint, shortcut.label, 104, 9);
            DashboardText.centerText(canvas, paint, label, centerX, 372,
                    focused ? 0xFFFFFFFF : 0xFFE6EAF2, 9, focused ? Typeface.BOLD : Typeface.NORMAL);
            hitAreaRegistrar.add(selection, centerX - 43, centerY - 42, 86, 57);
        }

        String range = (startIndex + 1) + "–" + (startIndex + count) + " de " + shortcuts.size();
        DashboardText.text(canvas, paint, "APPS  " + range, 1110, 294, 0xFFB9C7DA, 8, Typeface.BOLD);
    }

    boolean handlesSelection(int selection) {
        return selection >= SELECTION_BASE && selection < SELECTION_BASE + MAX_VISIBLE;
    }

    int moveFocus(int selected, int direction) {
        int count = visibleCount();
        if (count == 0) return selected;
        int slot = selected - SELECTION_BASE;
        int startIndex = page * MAX_VISIBLE;
        if (direction > 0) {
            if (slot + 1 < count) return selected + 1;
            if (startIndex + count < shortcuts.size()) {
                page++;
                return SELECTION_BASE;
            }
            page = 0;
            return SELECTION_BASE;
        }
        if (slot > 0) return selected - 1;
        if (page > 0) page--;
        else page = Math.max(0, (shortcuts.size() + MAX_VISIBLE - 1) / MAX_VISIBLE - 1);
        return SELECTION_BASE + visibleCount() - 1;
    }

    String labelForSelection(int selected) {
        int index = page * MAX_VISIBLE + selected - SELECTION_BASE;
        return index >= 0 && index < shortcuts.size() ? shortcuts.get(index).label : "Aplicativos instalados";
    }

    String packageForSelection(int selected) {
        int index = page * MAX_VISIBLE + selected - SELECTION_BASE;
        return index >= 0 && index < shortcuts.size() ? shortcuts.get(index).packageName : null;
    }

    private int visibleCount() {
        int startIndex = page * MAX_VISIBLE;
        return Math.max(0, Math.min(MAX_VISIBLE, shortcuts.size() - startIndex));
    }

    private void drawAppIcon(Canvas canvas, Drawable icon, float centerX, float centerY) {
        if (icon == null) return;
        Rect previousBounds = new Rect(icon.getBounds());
        iconClip.reset();
        iconClip.addCircle(centerX, centerY, 20, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(iconClip);
        icon.setBounds(Math.round(centerX - 20), Math.round(centerY - 20),
                Math.round(centerX + 20), Math.round(centerY + 20));
        icon.draw(canvas);
        canvas.restoreToCount(save);
        icon.setBounds(previousBounds);
    }
}
