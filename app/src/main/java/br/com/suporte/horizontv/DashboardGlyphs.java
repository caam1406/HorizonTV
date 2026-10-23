package br.com.suporte.horizontv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class DashboardGlyphs {
    private DashboardGlyphs() { }

static void drawShortcutGlyph(Canvas canvas, Paint paint, RectF rect, int index, float cx, float cy) {
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();
        switch (index) {
            case 0:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(cx - 6, cy - 10); path.lineTo(cx + 10, cy); path.lineTo(cx - 6, cy + 10); path.close();
                canvas.drawPath(path, paint);
                break;
            case 1:
                rect.set(cx - 12, cy - 8, cx + 12, cy + 10); canvas.drawRoundRect(rect, 2, 2, paint);
                path.moveTo(cx - 12, cy - 9); path.lineTo(cx - 5, cy - 14); path.lineTo(cx + 1, cy - 9);
                path.lineTo(cx + 7, cy - 14); path.lineTo(cx + 12, cy - 10); canvas.drawPath(path, paint);
                break;
            case 2:
                path.moveTo(cx + 3, cy - 12); path.lineTo(cx + 3, cy + 5); path.cubicTo(cx - 7, cy + 2, cx - 12, cy + 9, cx - 7, cy + 12);
                path.cubicTo(cx - 2, cy + 15, cx + 4, cy + 11, cx + 4, cy + 6); path.lineTo(cx + 4, cy - 8); path.lineTo(cx + 11, cy - 6);
                canvas.drawPath(path, paint);
                break;
            case 3:
                rect.set(cx - 13, cy - 7, cx + 13, cy + 8); canvas.drawRoundRect(rect, 7, 7, paint);
                canvas.drawLine(cx - 8, cy, cx - 2, cy, paint); canvas.drawLine(cx - 5, cy - 3, cx - 5, cy + 3, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx + 6, cy - 2, 1.8f, paint); canvas.drawCircle(cx + 9, cy + 3, 1.8f, paint);
                break;
            case 4:
                rect.set(cx - 13, cy - 10, cx + 13, cy + 10); canvas.drawRoundRect(rect, 3, 3, paint);
                canvas.drawCircle(cx + 6, cy - 5, 2.2f, paint);
                path.moveTo(cx - 11, cy + 7); path.lineTo(cx - 4, cy - 1); path.lineTo(cx + 1, cy + 5); path.lineTo(cx + 5, cy + 1); path.lineTo(cx + 11, cy + 7); canvas.drawPath(path, paint);
                break;
            case 5:
                path.moveTo(cx - 8, cy - 11); path.lineTo(cx - 5, cy - 2); path.quadTo(cx, cy + 3, cx + 5, cy - 2); path.lineTo(cx + 8, cy - 11);
                canvas.drawPath(path, paint); canvas.drawLine(cx - 8, cy - 11, cx + 8, cy - 11, paint);
                canvas.drawLine(cx, cy + 3, cx, cy + 10, paint); canvas.drawLine(cx - 8, cy + 10, cx + 8, cy + 10, paint);
                canvas.drawArc(cx - 14, cy - 10, cx - 6, cy + 1, 285, 180, false, paint);
                canvas.drawArc(cx + 6, cy - 10, cx + 14, cy + 1, 75, 180, false, paint);
                break;
            case 6:
                rect.set(cx - 11, cy - 12, cx + 11, cy + 12); canvas.drawRoundRect(rect, 2, 2, paint);
                canvas.drawLine(cx - 6, cy - 6, cx + 6, cy - 6, paint); canvas.drawLine(cx - 6, cy - 1, cx + 6, cy - 1, paint);
                canvas.drawLine(cx - 6, cy + 4, cx + 3, cy + 4, paint);
                break;
            case 7:
                path.moveTo(cx - 13, cy - 1); path.lineTo(cx, cy - 13); path.lineTo(cx + 13, cy - 1);
                path.lineTo(cx + 10, cy - 1); path.lineTo(cx + 10, cy + 11); path.lineTo(cx - 10, cy + 11); path.lineTo(cx - 10, cy - 1); path.close();
                canvas.drawPath(path, paint); canvas.drawRect(cx - 3, cy + 3, cx + 3, cy + 11, paint);
                break;
            default:
                canvas.drawCircle(cx, cy, 5, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    float x1 = cx + (float) Math.cos(angle) * 8;
                    float y1 = cy + (float) Math.sin(angle) * 8;
                    float x2 = cx + (float) Math.cos(angle) * 12;
                    float y2 = cy + (float) Math.sin(angle) * 12;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

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
