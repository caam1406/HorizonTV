package br.com.suporte.horizontv;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class NewsCache {
    private static final String KEY_HEADLINES = "cached_headlines";
    private static final int MAX_CACHED_HEADLINES = 20;

    private NewsCache() { }

    static List<Headline> load(SharedPreferences preferences) {
        ArrayList<Headline> headlines = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(preferences.getString(KEY_HEADLINES, "[]"));
            for (int i = 0; i < Math.min(items.length(), MAX_CACHED_HEADLINES); i++) {
                JSONObject item = items.getJSONObject(i);
                String url = item.optString("url", "");
                String title = item.optString("title", "").trim();
                if (title.isEmpty() || !NewsFeedReader.isAllowedArticleUrl(url)) continue;
                String imageUrl = item.optString("imageUrl", "");
                if (!NewsFeedReader.isAllowedImageUrl(imageUrl)) imageUrl = "";
                headlines.add(new Headline(title, item.optString("summary", ""),
                        item.optString("source", "Notícias"), url, imageUrl));
            }
        } catch (Exception ignored) {
            return headlines;
        }
        return headlines;
    }

    static void save(SharedPreferences preferences, List<Headline> headlines) {
        JSONArray items = new JSONArray();
        for (int i = 0; i < Math.min(headlines.size(), MAX_CACHED_HEADLINES); i++) {
            Headline headline = headlines.get(i);
            if (!NewsFeedReader.isAllowedArticleUrl(headline.url)) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("title", headline.title);
                item.put("summary", headline.summary);
                item.put("source", headline.source);
                item.put("url", headline.url);
                if (NewsFeedReader.isAllowedImageUrl(headline.imageUrl)) item.put("imageUrl", headline.imageUrl);
                items.put(item);
            } catch (Exception ignored) {
                // A malformed item is skipped; the rest of the feed remains useful.
            }
        }
        preferences.edit().putString(KEY_HEADLINES, items.toString()).apply();
    }
}
