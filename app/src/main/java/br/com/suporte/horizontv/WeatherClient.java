package br.com.suporte.horizontv;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class WeatherClient {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private WeatherClient() { }

    static WeatherSnapshot fetch(double latitude, double longitude) throws IOException, JSONException {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid approximate coordinates");
        }
        String query = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                        + "&timezone=auto&forecast_days=1", latitude, longitude);
        HttpURLConnection connection = (HttpURLConnection) new URL(query).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Weather service returned an error");
            String body;
            try (InputStream input = connection.getInputStream()) {
                body = readBounded(input);
            }
            return parse(body);
        } finally {
            connection.disconnect();
        }
    }

    static WeatherSnapshot parse(String body) throws JSONException {
        JSONObject current = new JSONObject(body).getJSONObject("current");
        return new WeatherSnapshot(
                current.getDouble("temperature_2m"),
                current.getDouble("apparent_temperature"),
                current.getInt("relative_humidity_2m"),
                current.getDouble("wind_speed_10m"),
                current.getInt("weather_code"));
    }

    private static String readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new IOException("Weather response exceeded the size limit");
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }
}
