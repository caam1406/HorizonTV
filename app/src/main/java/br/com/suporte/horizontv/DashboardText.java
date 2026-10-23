package br.com.suporte.horizontv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

final class DashboardText {
    private DashboardText() { }

    static void drawProgress(Canvas canvas, Paint paint, RectF rect, float x, float y,
                             float width, float progress) {
        paint.setColor(0x557E8AA1);
        rect.set(x, y, x + width, y + 2);
        canvas.drawRoundRect(rect, 1, 1, paint);
        paint.setColor(0xFF89E5FF);
        rect.set(x, y, x + width * progress, y + 2);
        canvas.drawRoundRect(rect, 1, 1, paint);
    }

    static void drawWrapped(Canvas canvas, Paint paint, String value, float x, float y,
                            float width, float size, int maxLines, int color, int weight) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif", weight));
        paint.setColor(color);
        String remaining = value == null ? "" : value;
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            int end = Math.min(remaining.length(), fitCharacters(paint, remaining, width));
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) end = space;
            }
            canvas.drawText(remaining.substring(0, end).trim(), x, y + line * 12, paint);
            remaining = remaining.substring(end).trim();
        }
    }

    static String ellipsis(Paint paint, String value, float maxWidth, float size) {
        if (value == null) return "";
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(size);
        if (paint.measureText(value) <= maxWidth) return value;
        String tail = "…";
        int end = value.length();
        while (end > 0 && paint.measureText(value.substring(0, end) + tail) > maxWidth) end--;
        return value.substring(0, end) + tail;
    }

    private static int fitCharacters(Paint paint, String value, float width) {
        int end = value.length();
        while (end > 1 && paint.measureText(value.substring(0, end)) > width) end--;
        return end;
    }

    static void text(Canvas canvas, Paint paint, String value, float x, float y, int color,
                     float size, int weight) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif", weight));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        canvas.drawText(value == null ? "" : value, x, y, paint);
    }

    static void centerText(Canvas canvas, Paint paint, String value, float cx, float y,
                           int color, float size, int weight) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif", weight));
        text(canvas, paint, value, cx - paint.measureText(value) / 2f, y, color, size, weight);
    }

    static void letterSpaced(Canvas canvas, Paint paint, String value, float x, float y,
                             int color, float size, float spacing) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        float at = x;
        for (int i = 0; i < value.length(); i++) {
            String character = value.substring(i, i + 1);
            canvas.drawText(character, at, y, paint);
            at += paint.measureText(character) + spacing;
        }
    }
}
