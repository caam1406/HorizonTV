package br.com.suporte.horizontv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DashboardNavigation {
    static final int ITEM_COUNT = 9;
    private static final String[] ITEMS = {
            "Início", "Filmes", "Séries", "TV ao vivo", "Música", "Jogos", "Apps", "Personalizar", "E-mail"
    };

    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
    private final DateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", new Locale("pt", "BR"));

    List<RectF> draw(Canvas canvas, Paint paint, int selected, int selectedNavigation) {
        ArrayList<RectF> hitAreas = new ArrayList<>();
        DashboardGlyphs.drawSearch(canvas, paint, 85, 39);
        float[] centers = {158, 222, 296, 375, 455, 520, 590, 685, 779};
        for (int index = 0; index < ITEMS.length; index++) {
            float width = index == 3 ? 78 : (index == 7 ? 90 : (index == 8 ? 78 : (index == 0 ? 58 : 64)));
            float x = centers[index] - width / 2f;
            float y = 34;
            boolean focused = selected == index;
            if (focused || selectedNavigation == index) {
                paint.setColor(focused ? 0x5536BDF2 : 0x302E94D8);
                RectF highlight = new RectF(x - 8, y - 7, x + width + 8, y + 30);
                canvas.drawRoundRect(highlight, 18, 18, paint);
                paint.setColor(0xFF73DDFC);
                RectF underline = new RectF(x + 4, y + 27, x + width - 4, y + 29);
                canvas.drawRoundRect(underline, 2, 2, paint);
            }
            DashboardText.text(canvas, paint, ITEMS[index], x, y + 16,
                    focused ? 0xFFFFFFFF : 0xFFD4D9E5, 12, focused ? Typeface.BOLD : Typeface.NORMAL);
            hitAreas.add(new RectF(x - 10, 22, x + width + 10, 66));
        }
        DashboardGlyphs.drawTopIcon(canvas, paint, new RectF(), 868, 46, 0);
        DashboardGlyphs.drawTopIcon(canvas, paint, new RectF(), 903, 46, 1);
        DashboardGlyphs.drawAvatar(canvas, paint, new RectF(), 939, 46);
        DashboardText.text(canvas, paint, timeFormat.format(new Date()), 1046, 39, 0xFFFFFFFF, 16, Typeface.NORMAL);
        DashboardText.text(canvas, paint, dateFormat.format(new Date()).toUpperCase(new Locale("pt", "BR")),
                1046, 57, 0xFFCFD5E2, 9, Typeface.NORMAL);
        return hitAreas;
    }

    String labelFor(int index) {
        return ITEMS[index];
    }
}
