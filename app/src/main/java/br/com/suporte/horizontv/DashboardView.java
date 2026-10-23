package br.com.suporte.horizontv;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("ViewConstructor")
final class DashboardView extends View {
    interface Listener {
        void launchPackage(String packageName);
        void openNews(String url);
        void showMessage(String message);
        void openBackgroundSettings();
        void refreshData();
        void refreshBibleVerse();
        void openEmailClient();
    }

    private static final float DESIGN_WIDTH = 1280f;
    private static final float DESIGN_HEIGHT = 720f;
    private static final int TRENDING_STORY_COUNT = 3;
    private static final int HERO_WATCH_SELECTION = 30;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect imageSource = new Rect();
    private final Path imageClip = new Path();
    private final BackdropRenderer backdrop;
    private final DashboardNavigation navigation = new DashboardNavigation();
    private final AppShortcutDock appDock = new AppShortcutDock();
    private final Listener listener;
    private final ArrayList<RectF> hitAreas = new ArrayList<>();
    private List<Headline> headlines = new ArrayList<>();
    private final Bitmap[] trendingImages = new Bitmap[3];
    private WeatherSnapshot weather;
    private BibleVerse bibleVerse;
    private String bibleMessage = "Carregando versículo…";
    private String weatherLocation = "Sua região";
    private String newsMessage = "Buscando manchetes recentes…";
    private String weatherMessage = "Localização necessária para a previsão";
    private String lastAppPackage;
    private String lastAppLabel;
    private String wallpaperCredit;
    private int selected = 0;
    private int selectedNavigation = 0;
    private int tickerIndex;
    private long lastTickerChange;

    DashboardView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("Horizon TV, tela inicial");
        backdrop = new BackdropRenderer(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    void setHeadlines(List<Headline> value, String message) {
        headlines = new ArrayList<>(value);
        for (int index = 0; index < trendingImages.length; index++) trendingImages[index] = null;
        newsMessage = message;
        invalidate();
    }

    void setHeadlineImage(String articleUrl, Bitmap image) {
        for (int index = 0; index < trendingImages.length; index++) {
            Headline headline = getHeadline(index);
            if (headline != null && headline.url.equals(articleUrl)) {
                trendingImages[index] = image;
                invalidate();
                return;
            }
        }
    }

    void setAppShortcuts(List<AppShortcut> value) {
        selected = appDock.update(value, selected);
        invalidate();
    }

    void setLastApp(String packageName, String label) {
        lastAppPackage = packageName;
        lastAppLabel = label == null || label.isEmpty() ? null : label;
        invalidate();
    }

    void setWeather(WeatherSnapshot value, String location, String message) {
        weather = value;
        weatherLocation = location == null || location.isEmpty() ? "Sua região" : location;
        weatherMessage = message;
        invalidate();
    }

    void setBibleVerse(BibleVerse value, String message) {
        bibleVerse = value;
        bibleMessage = message == null ? "" : message;
        invalidate();
    }

    void setDynamicWallpaper(Bitmap image, OpenverseImage metadata) {
        backdrop.setDynamic(image);
        wallpaperCredit = metadata == null ? null : metadata.shortCredit();
        invalidate();
    }

    void refreshClock() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scaleX = getWidth() / DESIGN_WIDTH;
        float scaleY = getHeight() / DESIGN_HEIGHT;
        int save = canvas.save();
        canvas.scale(scaleX, scaleY);
        drawBackdrop(canvas);
        hitAreas.clear();
        hitAreas.addAll(navigation.draw(canvas, paint, selected, selectedNavigation));
        drawHero(canvas);
        drawWeather(canvas);
        drawShortcuts(canvas);
        float tickerTop = DESIGN_HEIGHT - 60f;
        drawBottomCards(canvas, 394f, tickerTop - 16f);
        drawTicker(canvas, tickerTop);
        canvas.restoreToCount(save);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveFocus(keyCode);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            activateSelection();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            listener.refreshData();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = event.getX() / (getWidth() / DESIGN_WIDTH);
        float y = event.getY() / (getHeight() / DESIGN_HEIGHT);
        int hit = hitTarget(x, y);
        if (hit >= 0) {
            selected = hit;
            invalidate();
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        activateSelection();
        return true;
    }

    private void drawBackdrop(Canvas canvas) {
        canvas.drawColor(0xFF060A17);
        rect.set(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
        backdrop.draw(canvas, paint, rect);
        paint.setShader(new LinearGradient(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT,
                new int[]{0xE8060A17, 0xA4060A17, 0x60060A17, 0xD0060A17},
                new float[]{0f, 0.42f, 0.76f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT, paint);
        paint.setShader(null);
        paint.setColor(0x77060A17);
        canvas.drawRect(0, 0, DESIGN_WIDTH, 88, paint);
        paint.setColor(0xAA060A17);
        canvas.drawRect(0, 630, DESIGN_WIDTH, DESIGN_HEIGHT, paint);
    }

    private void drawHero(Canvas canvas) {
        DashboardText.text(canvas, paint, "EM DESTAQUE", 116, 112, 0xFFB9C7DA, 11, Typeface.BOLD);
        DashboardText.letterSpaced(canvas, paint, "HORIZON", 116, 164, 0xFFFFFFFF, 38, 8f);
        DashboardText.letterSpaced(canvas, paint, "UM NOVO AMANHÃ", 119, 188, 0xFFF0F4FF, 8, 5f);
        DashboardText.text(canvas, paint, "Uma jornada extraordinária em um mundo", 118, 216, 0xFFE1E6EF, 12, Typeface.NORMAL);
        DashboardText.text(canvas, paint, "que acaba de ganhar um novo horizonte.", 118, 233, 0xFFE1E6EF, 12, Typeface.NORMAL);
        drawButton(canvas, 116, 250, 94, 31, "▶  Assistir", selected == HERO_WATCH_SELECTION, true);
        drawButton(canvas, 220, 250, 88, 31, "Mais info", selected == 9, false);
        addHit(HERO_WATCH_SELECTION, 116, 250, 94, 31);
        addHit(9, 220, 250, 88, 31);
    }

    private void drawWeather(Canvas canvas) {
        drawGlassCard(canvas, 934, 90, 333, 75, selected == 10);
        DashboardGlyphs.drawWeatherSymbol(canvas, paint, rect, 963, 124, 22, weather == null ? 2 : weather.code);
        String location = "CLIMA  ·  " + weatherLocation.toUpperCase(new Locale("pt", "BR"));
        DashboardText.text(canvas, paint, DashboardText.ellipsis(paint, location, 261, 9),
                999, 108, 0xFFE0E8F4, 9, Typeface.BOLD);
        if (weather != null) {
            DashboardText.text(canvas, paint, Math.round(weather.temperature) + "°C", 999, 137, 0xFFFFFFFF, 22, Typeface.NORMAL);
            DashboardText.text(canvas, paint, WeatherText.describe(weather.code), 1071, 136, 0xFFDDE5F1, 10, Typeface.NORMAL);
            DashboardText.text(canvas, paint, "Sensação " + Math.round(weather.feelsLike) + "°  ·  Umid. " + weather.humidity + "%",
                    999, 153, 0xFFAEBBD0, 8, Typeface.NORMAL);
        } else {
            DashboardText.text(canvas, paint, weatherMessage, 999, 137, 0xFFE1E7F0, 10, Typeface.NORMAL);
            DashboardText.text(canvas, paint, "Fonte meteorológica: Open-Meteo", 999, 153, 0xFFAEBBD0, 8, Typeface.NORMAL);
        }
        drawGlassCard(canvas, 934, 174, 333, 88, selected == 11);
        DashboardText.text(canvas, paint, "VERSÍCULO DO DIA", 952, 196, 0xFFBBDDF2, 9, Typeface.BOLD);
        if (bibleVerse == null) {
            DashboardText.drawWrapped(canvas, paint, bibleMessage, 952, 218, 294, 10, 2,
                    0xFFE6EDF7, Typeface.NORMAL);
        } else {
            DashboardText.drawWrapped(canvas, paint, "“" + bibleVerse.text + "”", 952, 216,
                    294, 10, 2, 0xFFFFFFFF, Typeface.NORMAL);
            DashboardText.text(canvas, paint, bibleVerse.reference + "  ·  Almeida", 952, 250,
                    0xFFBBDDF2, 8, Typeface.BOLD);
        }
        addHit(10, 934, 90, 333, 75);
        addHit(11, 934, 174, 333, 88);
    }

    private void drawShortcuts(Canvas canvas) {
        appDock.draw(canvas, paint, selected, this::addHit);
    }

    private void drawBottomCards(Canvas canvas, float top, float bottom) {
        float height = bottom - top;
        float contentTop = top + 14f;
        float contentHeight = height - 28f;
        drawGlassCard(canvas, 13, top, 310, height, selected == 21);
        addHit(21, 13, top, 310, height);
        drawPlayIcon(canvas, 61, contentTop + contentHeight / 2f);
        DashboardText.text(canvas, paint, "CONTINUAR", 107, contentTop + 12, 0xFFD7E0EE, 8, Typeface.BOLD);
        String appLabel = lastAppLabel == null ? "Nenhum app recente"
                : DashboardText.ellipsis(paint, lastAppLabel, 200, 13);
        DashboardText.text(canvas, paint, appLabel, 107, contentTop + 38, 0xFFFFFFFF, 13, Typeface.BOLD);
        DashboardText.text(canvas, paint,
                lastAppLabel == null ? "Abra um app pela dock" : "Último aplicativo aberto",
                107, contentTop + 56, 0xFFC2CEDF, 9, Typeface.NORMAL);
        DashboardText.text(canvas, paint,
                lastAppLabel == null ? "" : "Pressione OK para voltar",
                107, bottom - 18, 0xFFC2CEDF, 8, Typeface.NORMAL);

        drawGlassCard(canvas, 335, top, 587, height, false);
        DashboardText.text(canvas, paint, "EM ALTA AGORA  ›", 353, contentTop + 8, 0xFFEAF0F8, 9, Typeface.BOLD);
        float tileTop = contentTop + 17f;
        float tileHeight = contentHeight - 17f;
        for (int i = 0; i < 3; i++) drawTrendingCard(canvas, i, 352 + i * 184, tileTop, 174, tileHeight);

        drawGlassCard(canvas, 934, top, 333, height, selected >= 23 && selected <= 27);
        DashboardText.text(canvas, paint, "ÚLTIMAS NOTÍCIAS", 951, contentTop + 8, 0xFFEAF0F8, 9, Typeface.BOLD);
        DashboardText.text(canvas, paint, headlines.isEmpty() ? newsMessage : "BRASIL  ·  AGORA",
                1135, contentTop + 8, 0xFF9DB5D4, 7, Typeface.NORMAL);
        int rowCount = Math.min(5, Math.max(3, (int) ((height - 47f) / 37f)));
        float rowStep = (height - 48f) / rowCount;
        for (int i = 0; i < rowCount; i++) drawNewsRow(canvas, i, 951, contentTop + 34f + i * rowStep);
    }

    private void drawPlayIcon(Canvas canvas, float centerX, float centerY) {
        paint.setColor(0xFF1D9FE8);
        canvas.drawCircle(centerX, centerY, 27, paint);
        paint.setColor(0xFF82D8FF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(centerX, centerY, 30, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        imageClip.reset();
        imageClip.moveTo(centerX - 6, centerY - 11);
        imageClip.lineTo(centerX + 11, centerY);
        imageClip.lineTo(centerX - 6, centerY + 11);
        imageClip.close();
        canvas.drawPath(imageClip, paint);
    }

    private void drawTrendingCard(Canvas canvas, int index, float x, float y, float width, float height) {
        RectF card = new RectF(x, y, x + width, y + height);
        paint.setColor(new int[]{0xFF182D50, 0xFF30223F, 0xFF1D3240}[index]);
        canvas.drawRoundRect(card, 9, 9, paint);
        Bitmap image = trendingImages[index];
        if (image == null) {
            backdrop.drawCropped(canvas, paint, x, y, width, height);
        } else {
            drawHeadlineImage(canvas, image, card);
            paint.setColor(0x44050A14);
            canvas.drawRoundRect(card, 9, 9, paint);
        }
        paint.setColor(0x99050A14);
        rect.set(x, y + height - 39, x + width, y + height);
        canvas.drawRoundRect(rect, 0, 0, paint);
        Headline item = getHeadline(index);
        String title = item == null ? new String[]{"Descubra novas histórias", "O que acontece agora", "Ideias para hoje"}[index] : item.title;
        DashboardText.drawWrapped(canvas, paint, title, x + 8, y + height - 23, width - 16, 10, 2, 0xFFFFFFFF, Typeface.BOLD);
        addHit(23 + index, x, y, width, height);
    }

    private void drawHeadlineImage(Canvas canvas, Bitmap image, RectF card) {
        float cardRatio = card.width() / card.height();
        float imageRatio = (float) image.getWidth() / image.getHeight();
        if (imageRatio > cardRatio) {
            int sourceWidth = Math.round(image.getHeight() * cardRatio);
            int left = (image.getWidth() - sourceWidth) / 2;
            imageSource.set(left, 0, left + sourceWidth, image.getHeight());
        } else {
            int sourceHeight = Math.round(image.getWidth() / cardRatio);
            int top = (image.getHeight() - sourceHeight) / 2;
            imageSource.set(0, top, image.getWidth(), top + sourceHeight);
        }
        imageClip.reset();
        imageClip.addRoundRect(card, 9, 9, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(imageClip);
        canvas.drawBitmap(image, imageSource, card, paint);
        canvas.restoreToCount(save);
    }

    private void drawNewsRow(Canvas canvas, int index, float x, float y) {
        Headline item = getNewsRowHeadline(index);
        String title = item == null ? (index == 0 ? newsMessage : "Aguardando novas manchetes…") : item.title;
        String source = item == null ? "" : item.source.toUpperCase(new Locale("pt", "BR"));
        DashboardText.text(canvas, paint, DashboardText.ellipsis(paint, title, 298, 9), x, y, 0xFFF1F4F9, 9, Typeface.NORMAL);
        DashboardText.text(canvas, paint, source.isEmpty() ? "" : source + "  ·  há pouco", x, y + 12, 0xFF9EADC1, 7, Typeface.NORMAL);
        addHit(23 + index, x, y - 10, 298, 25);
    }

    private void drawTicker(Canvas canvas, float y) {
        paint.setColor(0xB5090E1B);
        rect.set(13, y, 1267, y + 38);
        canvas.drawRoundRect(rect, 14, 14, paint);
        paint.setColor(0xFFFF4B61);
        canvas.drawCircle(29, y + 19, 3, paint);
        DashboardText.text(canvas, paint, "AO VIVO", 39, y + 22, 0xFFFF6577, 8, Typeface.BOLD);
        Headline ticker = getHeadline(tickerIndex);
        String headline = ticker == null ? newsMessage : ticker.title;
        DashboardText.text(canvas, paint, DashboardText.ellipsis(paint, headline, 590, 8), 100, y + 22, 0xFFE1E7F0, 8, Typeface.NORMAL);
        DashboardText.text(canvas, paint, wallpaperCredit == null
                        ? "Clima: Open-Meteo  ·  Bairro: © OpenStreetMap contributors  ·  Notícias: G1 / Agência Brasil"
                        : "Fundo: " + wallpaperCredit,
                730, y + 22, 0xFF9BAAC0, 7, Typeface.NORMAL);
        long now = System.currentTimeMillis();
        if (now - lastTickerChange > 12000 && headlines.size() > 1) {
            tickerIndex = (tickerIndex + 1) % headlines.size();
            lastTickerChange = now;
        }
    }

    private void drawButton(Canvas canvas, float x, float y, float width, float height,
                            String label, boolean focused, boolean primary) {
        rect.set(x, y, x + width, y + height);
        paint.setColor(primary ? 0xFFF4F7FC : 0xB5222E42);
        if (focused) paint.setColor(0xFF6EDBFA);
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
        DashboardText.centerText(canvas, paint, label, x + width / 2f, y + 20, primary && !focused ? 0xFF08111D : 0xFFFFFFFF,
                9, Typeface.BOLD);
    }

    private void drawGlassCard(Canvas canvas, float x, float y, float width, float height, boolean focused) {
        paint.setColor(focused ? 0xBB347DA2 : 0xA51A2435);
        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, 15, 15, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(focused ? 0xFFD5F5FF : 0x55E5EEFF);
        canvas.drawRoundRect(rect, 15, 15, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void addHit(int id, float x, float y, float width, float height) {
        while (hitAreas.size() <= id) hitAreas.add(null);
        hitAreas.set(id, new RectF(x, y, x + width, y + height));
    }

    private int hitTarget(float x, float y) {
        for (int id = 0; id < hitAreas.size(); id++) {
            RectF area = hitAreas.get(id);
            if (area != null && area.contains(x, y)) return id;
        }
        return -1;
    }

    private void moveFocus(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (appDock.handlesSelection(selected)) selected = appDock.moveFocus(selected, -1);
            else if (selected == 10 || selected == 11) selected = selected == 10 ? 11 : 10;
            else if (selected == 21) selected = 27;
            else if (selected >= 23 && selected <= 27) selected = selected == 23 ? 21 : selected - 1;
            else if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) selected = selected == 0 ? DashboardNavigation.ITEM_COUNT - 1 : selected - 1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (appDock.handlesSelection(selected)) selected = appDock.moveFocus(selected, 1);
            else if (selected == 10 || selected == 11) selected = selected == 10 ? 11 : 10;
            else if (selected == 21) selected = 23;
            else if (selected >= 23 && selected <= 27) selected = selected == 27 ? 21 : selected + 1;
            else if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) selected = selected == DashboardNavigation.ITEM_COUNT - 1 ? 0 : selected + 1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selected < DashboardNavigation.ITEM_COUNT) selected = selected == 3 ? 10 : HERO_WATCH_SELECTION;
            else if (selected == HERO_WATCH_SELECTION || selected == 9) selected = AppShortcutDock.SELECTION_BASE;
            else if (appDock.handlesSelection(selected)) selected = 21;
            else if (selected >= 21 && selected <= 27) selected = AppShortcutDock.SELECTION_BASE;
            else if (selected == 10 || selected == 11) selected = 23;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (appDock.handlesSelection(selected)) selected = 0;
            else if (selected == 21) selected = AppShortcutDock.SELECTION_BASE;
            else if (selected >= 23 && selected <= 27) selected = 18;
            else if (selected == HERO_WATCH_SELECTION || selected == 9) selected = 0;
            else if (selected == 10 || selected == 11) selected = 3;
        }
        if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) selectedNavigation = selected;
        setContentDescription(descriptionForSelection());
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
        invalidate();
    }

    private void activateSelection() {
        if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) {
            selectedNavigation = selected;
            if (selected == 7) listener.openBackgroundSettings();
            else if (selected == 8) listener.openEmailClient();
            else if (selected == 3) listener.launchPackage("com.tcl.tv");
            else if (selected == 6) selected = AppShortcutDock.SELECTION_BASE;
            else listener.showMessage("Abra um aplicativo pela dock.");
        } else if (selected == HERO_WATCH_SELECTION || selected == 9) {
            listener.showMessage("Horizon — TV, filmes e séries em um só lugar.");
        } else if (selected == 10) {
            listener.refreshData();
        } else if (selected == 11) {
            listener.refreshBibleVerse();
        } else if (selected == 21) {
            if (lastAppPackage == null) listener.showMessage("Abra um aplicativo pela dock para continuar.");
            else listener.launchPackage(lastAppPackage);
        } else if (appDock.handlesSelection(selected)) {
            String packageName = appDock.packageForSelection(selected);
            if (packageName != null) listener.launchPackage(packageName);
        } else if (selected >= 23 && selected <= 27) {
            Headline item = getNewsRowHeadline(selected - 23);
            if (item != null) listener.openNews(item.url);
        }
        invalidate();
    }

    private String descriptionForSelection() {
        if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) {
            return navigation.labelFor(selected);
        }
        if (appDock.handlesSelection(selected)) return appDock.labelForSelection(selected);
        if (selected == 21 && lastAppLabel != null) return "Continuar " + lastAppLabel;
        if (selected >= 23 && selected <= 27) {
            Headline item = getNewsRowHeadline(selected - 23);
            return item == null ? "Notícias" : item.title;
        }
        if (selected == 10) return "Clima da sua região";
        if (selected == 11) return "Versículo bíblico do dia";
        return "Horizon TV";
    }

    private Headline getHeadline(int index) {
        return index >= 0 && index < headlines.size() ? headlines.get(index) : null;
    }

    private Headline getNewsRowHeadline(int index) {
        return getHeadline(index + TRENDING_STORY_COUNT);
    }
}
