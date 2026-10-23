package br.com.suporte.horizontv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class NewsImageLoader {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 8192;
    private static final int MAX_IMAGE_PIXELS = 32_000_000;
    private static final int TARGET_WIDTH = 600;
    private static final int TARGET_HEIGHT = 340;

    private NewsImageLoader() { }

    static Bitmap fetch(String imageUrl) throws IOException {
        if (!NewsFeedReader.isAllowedImageUrl(imageUrl)) return null;
        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/jpeg,image/png");
        try {
            int status = connection.getResponseCode();
            String contentType = connection.getContentType();
            if (status != HttpURLConnection.HTTP_OK || contentType == null
                    || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return null;
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_BYTES) return null;

            byte[] imageBytes;
            try (InputStream input = connection.getInputStream()) {
                imageBytes = readLimited(input);
            }
            return decodeSampled(imageBytes);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > MAX_IMAGE_BYTES) throw new IOException("News image exceeded the size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Bitmap decodeSampled(byte[] imageBytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > MAX_IMAGE_DIMENSION || bounds.outHeight > MAX_IMAGE_DIMENSION
                || (long) bounds.outWidth * bounds.outHeight > MAX_IMAGE_PIXELS) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) >= TARGET_WIDTH || height / (sample * 2) >= TARGET_HEIGHT) {
            sample *= 2;
        }
        return sample;
    }
}
