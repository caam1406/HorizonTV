package br.com.suporte.horizontv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class FilmSuggestionClient {
    private static final String ENDPOINT = "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{5,12}");

    private FilmSuggestionClient() { }

    static FilmSuggestion fetch() throws IOException, JSONException {
        return fetch(0);
    }

    static FilmSuggestion fetch(long rotation) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "HorizonTV/1.0");
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Movie catalog returned HTTP " + status);
            int length = connection.getContentLength();
            if (length > MAX_RESPONSE_BYTES) throw new IOException("Movie catalog response exceeded the size limit");
            try (InputStream input = connection.getInputStream()) {
                long day = System.currentTimeMillis() / (24 * 60 * 60 * 1000L);
                return choose(readLimited(input), day + rotation);
            }
        } finally {
            connection.disconnect();
        }
    }

    static FilmSuggestion choose(String response, long day) throws JSONException {
        JSONArray metas = new JSONObject(response).optJSONArray("metas");
        if (metas == null || metas.length() == 0) throw new JSONException("Movie catalog has no titles");
        List<FilmSuggestion> candidates = new ArrayList<>();
        for (int index = 0; index < Math.min(metas.length(), 100); index++) {
            JSONObject item = metas.optJSONObject(index);
            if (item == null || !"movie".equals(item.optString("type"))) continue;
            String id = item.optString("imdb_id", "").trim();
            String title = item.optString("name", "").trim();
            String image = item.optString("background", "").trim();
            if (!IMDB_ID.matcher(id).matches() || title.isEmpty() || title.length() > 160
                    || !isAllowedBackground(image, id)) continue;
            String description = item.optString("description", "").trim();
            if (description.length() > 1200) description = description.substring(0, 1200).trim();
            JSONArray genres = item.optJSONArray("genre");
            String genreText = genres == null ? "" : genres.join(" · ").replace("\"", "");
            if (genreText.length() > 80) genreText = genreText.substring(0, 80).trim();
            String year = item.optString("year", "").trim();
            if (!year.matches("[0-9]{4}")) year = "";
            candidates.add(new FilmSuggestion(id, Math.max(0, item.optInt("moviedb_id", 0)),
                    title, year, description, genreText, image));
        }
        if (candidates.isEmpty()) throw new JSONException("Movie catalog has no usable titles");
        return candidates.get((int) Math.floorMod(day, candidates.size()));
    }

    static boolean isAllowedBackground(String value) {
        try {
            URL url = new URL(value);
            String path = url.getPath();
            return "https".equalsIgnoreCase(url.getProtocol())
                    && "images.metahub.space".equals(url.getHost().toLowerCase(Locale.ROOT))
                    && url.getUserInfo() == null && url.getPort() == -1
                    && path.matches("/background/(small|medium|large)/tt[0-9]{5,12}/img")
                    && url.getQuery() == null && url.getRef() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isAllowedBackground(String value, String imdbId) {
        return value != null && imdbId != null && IMDB_ID.matcher(imdbId).matches()
                && value.contains("/background/")
                && value.endsWith("/" + imdbId + "/img")
                && isAllowedBackground(value);
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_RESPONSE_BYTES) throw new IOException("Movie catalog response exceeded the size limit");
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
