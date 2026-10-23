package br.com.suporte.horizontv;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class NeighborhoodClient {
    private static final int MAX_RESPONSE_BYTES = 32 * 1024;

    private NeighborhoodClient() { }

    static String fetch(double latitude, double longitude) throws IOException, JSONException {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid approximate coordinates");
        }
        String query = String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?lat=%.2f&lon=%.2f"
                        + "&zoom=14&addressdetails=1&format=jsonv2",
                latitude, longitude);
        HttpURLConnection connection = (HttpURLConnection) new URL(query).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9");
        connection.setRequestProperty("User-Agent", "HorizonTV/1.0 (Android TV dashboard)");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Map service returned an error");
            try (InputStream input = connection.getInputStream()) {
                return parse(readBounded(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    static String parse(String body) throws JSONException {
        JSONObject address = new JSONObject(body).optJSONObject("address");
        if (address == null) return "";
        String[] neighborhoodFields = {"neighbourhood", "suburb", "quarter", "city_district", "borough"};
        for (String field : neighborhoodFields) {
            String value = address.optString(field, "").trim();
            if (!value.isEmpty()) return value;
        }
        String[] cityFields = {"city", "town", "municipality", "village"};
        for (String field : cityFields) {
            String value = address.optString(field, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new IOException("Map response exceeded the size limit");
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
