package br.com.suporte.horizontv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

final class DashboardGlyphs {
    private DashboardGlyphs() { }

static void drawWeatherSymbol(Canvas canvas, Paint paint, RectF rect, float cx, float cy, float radius, int code) {
        paint.setColor(code >= 51 ? 0xFF83D8FF : 0xFFFFD275);
        canvas.drawCircle(cx, cy, radius * 0.62f, paint);
        paint.setColor(0xFFE7F3FF);
        canvas.drawCircle(cx - radius * 0.15f, cy + radius * 0.15f, radius * 0.4f, paint);
        canvas.drawCircle(cx + radius * 0.27f, cy + radius * 0.15f, radius * 0.31f, paint);
        rect.set(cx - radius * 0.5f, cy + radius * 0.08f, cx + radius * 0.5f, cy + radius * 0.43f);
        canvas.drawRoundRect(rect, radius * 0.18f, radius * 0.18f, paint);
    }

static void drawTopIcon(Canvas canvas, Paint paint, RectF rect, float cx, float cy, int kind) {
        paint.setColor(0xAA202B3E);
        canvas.drawCircle(cx, cy, 13, paint);
        paint.setColor(0xFFEDF4FC);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f);
        if (kind == 0) {
            rect.set(cx - 5, cy - 7, cx + 5, cy + 6); canvas.drawRoundRect(rect, 4, 4, paint);
            canvas.drawLine(cx - 4, cy - 8, cx + 4, cy - 8, paint);
            canvas.drawLine(cx - 2, cy + 8, cx + 2, cy + 8, paint);
        } else {
            for (int i = 0; i < 3; i++) {
                rect.set(cx - 8 + i * 4, cy - 5 + i * 3, cx + 8 - i * 4, cy + 5 - i * 3);
                canvas.drawArc(rect, 220, 100, false, paint);
            }
            canvas.drawCircle(cx, cy + 6, 1.4f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

static void drawAvatar(Canvas canvas, Paint paint, RectF rect, float cx, float cy) {
        paint.setColor(0xFF8B70CE);
        canvas.drawCircle(cx, cy, 13, paint);
        paint.setColor(0xFFE4D9FF);
        canvas.drawCircle(cx, cy - 3, 4, paint);
        rect.set(cx - 7, cy + 2, cx + 7, cy + 11);
        canvas.drawRoundRect(rect, 7, 7, paint);
    }

static void drawSearch(Canvas canvas, Paint paint, float cx, float cy) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setColor(0xFFEAF2FC);
        canvas.drawCircle(cx - 2, cy - 2, 5, paint);
        canvas.drawLine(cx + 2, cy + 2, cx + 8, cy + 8, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
