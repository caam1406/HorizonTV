package br.com.suporte.horizontv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

final class BackdropRenderer {
    private final Bitmap original;
    private final Rect source = new Rect();
    private final RectF clip = new RectF();
    private Bitmap dynamic;

    BackdropRenderer(Context context) {
        original = BitmapFactory.decodeResource(context.getResources(), R.drawable.horizon_backdrop);
    }

    void setDynamic(Bitmap bitmap) {
        dynamic = bitmap;
    }

    void draw(Canvas canvas, Paint paint, RectF destination) {
        Bitmap image = current();
        if (image == null) return;
        source.set(0, 0, image.getWidth(), image.getHeight());
        paint.setAlpha(255);
        canvas.drawBitmap(image, source, destination, paint);
    }

    void drawCropped(Canvas canvas, Paint paint, float x, float y, float width, float height) {
        Bitmap image = current();
        if (image == null) return;
        int save = canvas.save();
        clip.set(x, y, x + width, y + height);
        canvas.clipRect(clip);
        int left = (int) (image.getWidth() * 0.43f);
        int top = (int) (image.getHeight() * 0.35f);
        source.set(left, top, Math.min(image.getWidth(), left + image.getWidth() / 3),
                Math.min(image.getHeight(), top + image.getHeight() / 2));
        clip.set(x, y, x + width, y + height);
        paint.setAlpha(145);
        canvas.drawBitmap(image, source, clip, paint);
        paint.setAlpha(255);
        canvas.restoreToCount(save);
    }

    private Bitmap current() {
        return dynamic == null ? original : dynamic;
    }
}
