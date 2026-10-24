package br.com.suporte.horizontv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

final class OpenverseImageClient {
    private static final String API_ROOT = "https://api.openverse.org/v1";
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 12_000;
    private static final long MAX_IMAGE_PIXELS = 80_000_000L;
    private static final int MAX_RESULTS = 20;
    private static final Set<String> ALLOWED_LICENSES;

    static {
        Set<String> licenses = new HashSet<>();
        Collections.addAll(licenses, "cc0", "by", "by-sa");
        ALLOWED_LICENSES = Collections.unmodifiableSet(licenses);
    }

    private final Random random = new Random();
    private final DisplayResolution resolution;

    OpenverseImageClient(DisplayResolution resolution) {
        this.resolution = resolution;
    }

    Wallpaper fetch(String query) throws IOException, JSONException {
        List<OpenverseImage> results = search(query);
        if (results.isEmpty()) throw new IOException("Nenhuma imagem compatível encontrada");
        Collections.shuffle(results, random);
        for (OpenverseImage image : results) {
            Bitmap bitmap = fetchImage(image.imageUrl);
            if (bitmap != null) return new Wallpaper(image, bitmap);
        }
        throw new IOException("As imagens encontradas não puderam ser carregadas");
    }

    static List<OpenverseImage> parseResults(String json) throws JSONException {
        JSONArray results = new JSONObject(json).optJSONArray("results");
        if (results == null) throw new JSONException("Resposta Openverse sem resultados");
        List<OpenverseImage> images = new ArrayList<>();
        for (int index = 0; index < Math.min(results.length(), MAX_RESULTS); index++) {
            JSONObject item = results.optJSONObject(index);
            OpenverseImage image = parseImage(item);
            if (image != null) images.add(image);
        }
        return images;
    }

    private List<OpenverseImage> search(String query) throws IOException, JSONException {
        Uri uri = Uri.parse(API_ROOT + "/images/").buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("license", "cc0,by,by-sa")
                .appendQueryParameter("license_type", "commercial,modification")
                .appendQueryParameter("aspect_ratio", "wide")
                .appendQueryParameter("mature", "false")
                .appendQueryParameter("page_size", Integer.toString(MAX_RESULTS))
                .build();
        HttpURLConnection connection = open(uri.toString());
        try {
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Openverse respondeu HTTP " + connection.getResponseCode());
            }
            return parseResults(new String(readLimited(connection.getInputStream(), MAX_JSON_BYTES),
                    java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private Bitmap fetchImage(String imageUrl) throws IOException {
        HttpURLConnection connection = open(imageUrl);
        try {
            connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) return null;
            int length = connection.getContentLength();
            if (length > MAX_IMAGE_BYTES) return null;
            byte[] bytes = readLimited(connection.getInputStream(), MAX_IMAGE_BYTES);
            return decodeSampled(bytes);
        } finally {
            connection.disconnect();
        }
    }

    private static OpenverseImage parseImage(JSONObject item) {
        if (item == null || item.optBoolean("mature", true)) return null;
        String id = item.optString("id", "");
        String license = item.optString("license", "").toLowerCase(Locale.ROOT);
        String title = clean(item.optString("title", ""), 140);
        String creator = clean(item.optString("creator", ""), 100);
        String licenseVersion = clean(item.optString("license_version", ""), 20);
        String licenseUrl = item.optString("license_url", "");
        String sourceUrl = item.optString("foreign_landing_url", "");
        String imageUrl = item.optString("url", "");
        String provider = item.optString("provider", "");
        String attribution = clean(item.optString("attribution", ""), 500);
        if (!isUuid(id) || !ALLOWED_LICENSES.contains(license) || title.isEmpty()
                || !isAllowedLicenseUrl(licenseUrl) || !isHttpsUrl(sourceUrl)
                || !"flickr".equalsIgnoreCase(provider) || !isAllowedImageUrl(imageUrl)) return null;
        return new OpenverseImage(id, title, creator, license, licenseVersion,
                licenseUrl, sourceUrl, imageUrl, attribution);
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean isAllowedLicenseUrl(String value) {
        if (!isHttpsUrl(value)) return false;
        return "creativecommons.org".equalsIgnoreCase(URI.create(value).getHost());
    }

    static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean isAllowedImageUrl(String value) {
        if (!isHttpsUrl(value)) return false;
        return "live.staticflickr.com".equalsIgnoreCase(URI.create(value).getHost());
    }

    private static String clean(String value, int maxLength) {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength).trim();
    }

    private static HttpURLConnection open(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(9000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "HorizonTV/1.0 (Android TV wallpaper)");
        return connection;
    }

    private static byte[] readLimited(InputStream input, int maximumBytes) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maximumBytes) throw new IOException("Resposta excede o limite permitido");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private Bitmap decodeSampled(byte[] imageBytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > MAX_IMAGE_DIMENSION || bounds.outHeight > MAX_IMAGE_DIMENSION
                || (long) bounds.outWidth * bounds.outHeight > MAX_IMAGE_PIXELS) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = resolution.sampleSizeFor(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
    }

    static final class Wallpaper {
        final OpenverseImage metadata;
        final Bitmap bitmap;

        Wallpaper(OpenverseImage metadata, Bitmap bitmap) {
            this.metadata = metadata;
            this.bitmap = bitmap;
        }
    }
}
