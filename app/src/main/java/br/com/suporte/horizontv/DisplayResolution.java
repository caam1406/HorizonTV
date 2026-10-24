package br.com.suporte.horizontv;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

final class DisplayResolution {
    final int width;
    final int height;

    private DisplayResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    static DisplayResolution from(Context context) {
        Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? context.getDisplay() : null;
        if (display == null) {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            display = windowManager == null ? null : windowManager.getDefaultDisplay();
        }
        if (display != null) {
            Display.Mode mode = display.getMode();
            int width = mode.getPhysicalWidth();
            int height = mode.getPhysicalHeight();
            if (width > 0 && height > 0) return landscape(width, height);
        }
        Point size = new Point();
        if (display != null) display.getRealSize(size);
        if (size.x > 0 && size.y > 0) return landscape(size.x, size.y);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return landscape(metrics.widthPixels, metrics.heightPixels);
    }

    private static DisplayResolution landscape(int width, int height) {
        return width >= height ? new DisplayResolution(width, height)
                : new DisplayResolution(height, width);
    }

    int sampleSizeFor(int sourceWidth, int sourceHeight) {
        return sampleSizeFor(sourceWidth, sourceHeight, width, height);
    }

    int sampleSizeFor(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int sample = 1;
        while (sourceWidth / (sample * 2) >= targetWidth
                || sourceHeight / (sample * 2) >= targetHeight) sample *= 2;
        return sample;
    }

    int designWidth(float width, float designWidth) {
        return Math.max(1, Math.round(this.width * width / designWidth));
    }

    int designHeight(float height, float designHeight) {
        return Math.max(1, Math.round(this.height * height / designHeight));
    }
}
