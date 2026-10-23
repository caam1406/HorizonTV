package br.com.suporte.horizontv;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class BibleVerseClient {
    private static final String ENDPOINT = "https://bible-api.com/data/almeida/random";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_CHARS = 32 * 1024;

    private BibleVerseClient() { }

    static BibleVerse fetch() throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "HorizonTV/1.0");
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Bible API returned HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                return parse(readLimited(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    static BibleVerse parse(String response) throws JSONException {
        JSONObject verse = new JSONObject(response).getJSONObject("random_verse");
        String text = verse.getString("text").replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
        String book = verse.getString("book").trim();
        int chapter = verse.getInt("chapter");
        int verseNumber = verse.getInt("verse");
        String reference = book + " " + chapter + ":" + verseNumber;
        if (text.isEmpty() || text.length() > 4000 || book.isEmpty()
                || chapter < 1 || verseNumber < 1 || reference.length() > 100) {
            throw new JSONException("Bible API returned an invalid verse");
        }
        return new BibleVerse(text, reference);
    }

    private static String readLimited(InputStream input) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (response.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("Bible API response exceeded the size limit");
                }
                response.append(buffer, 0, count);
            }
        }
        return response.toString();
    }
}
