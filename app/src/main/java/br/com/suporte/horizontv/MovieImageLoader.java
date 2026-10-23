package br.com.suporte.horizontv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class MovieImageLoader {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PIXELS = 32_000_000;

    private MovieImageLoader() { }

    static Bitmap fetch(String imageUrl) throws IOException {
        if (!FilmSuggestionClient.isAllowedBackground(imageUrl)) return null;
        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        try {
            String type = connection.getContentType();
            int length = connection.getContentLength();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK || type == null
                    || !type.toLowerCase(Locale.ROOT).startsWith("image/") || length > MAX_BYTES) return null;
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readLimited(input);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 8192
                    || bounds.outHeight > 8192 || (long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("Movie image exceeded the size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) >= 1280 || height / (sample * 2) >= 720) sample *= 2;
        return sample;
    }
}
