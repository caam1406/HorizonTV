package br.com.suporte.horizontv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

final class ContinueCardRenderer {
    private static final float CENTER_X = 168f;
    private static final float CIRCLE_RADIUS = 51f;
    private static final float HIT_RADIUS = 58f;
    private final Path playSymbol = new Path();
    private float centerY;

    RectF draw(Canvas canvas, Paint paint, float top, float bottom, String appLabel, boolean focused) {
        centerY = top + 105f;
        DashboardText.centerText(canvas, paint, "CONTINUAR", CENTER_X, top + 21f,
                0xFFD7E0EE, 9, Typeface.BOLD);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(focused ? 0xFF1FAFE2 : 0xFF167BA8);
        canvas.drawCircle(CENTER_X, centerY, CIRCLE_RADIUS, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(focused ? 4f : 2f);
        paint.setColor(focused ? 0xFFE0FAFF : 0xFF82D8FF);
        canvas.drawCircle(CENTER_X, centerY, HIT_RADIUS - 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        playSymbol.reset();
        playSymbol.moveTo(CENTER_X - 13f, centerY - 22f);
        playSymbol.lineTo(CENTER_X + 20f, centerY);
        playSymbol.lineTo(CENTER_X - 13f, centerY + 22f);
        playSymbol.close();
        canvas.drawPath(playSymbol, paint);

        String title = appLabel == null ? "Nenhum app recente" : appLabel;
        DashboardText.centerText(canvas, paint, DashboardText.ellipsis(paint, title, 240, 13),
                CENTER_X, centerY + 77f, 0xFFFFFFFF, 13, Typeface.BOLD);
        DashboardText.centerText(canvas, paint,
                appLabel == null ? "Abra um app pela dock" : "Último aplicativo aberto",
                CENTER_X, centerY + 95f, 0xFFC2CEDF, 9, Typeface.NORMAL);
        if (appLabel != null) {
            DashboardText.centerText(canvas, paint, "Pressione OK para voltar", CENTER_X, bottom - 18f,
                    0xFFC2CEDF, 8, Typeface.NORMAL);
        }
        return new RectF(CENTER_X - HIT_RADIUS, centerY - HIT_RADIUS,
                CENTER_X + HIT_RADIUS, centerY + HIT_RADIUS);
    }

    boolean contains(float x, float y) {
        float deltaX = x - CENTER_X;
        float deltaY = y - centerY;
        return deltaX * deltaX + deltaY * deltaY <= HIT_RADIUS * HIT_RADIUS;
    }
}
