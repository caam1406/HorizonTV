package br.com.suporte.horizontv;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

final class FilmSuggestionStore {
    private static final String KEY_SUGGESTION = "film_suggestion";
    private static final String KEY_UPDATED_AT = "film_suggestion_updated_at";

    private FilmSuggestionStore() { }

    static FilmSuggestion load(SharedPreferences preferences) {
        try {
            JSONObject value = new JSONObject(preferences.getString(KEY_SUGGESTION, "{}"));
            String id = value.optString("id", "");
            String title = value.optString("title", "");
            String image = value.optString("image", "");
            String year = value.optString("year", "");
            String description = value.optString("description", "");
            String genres = value.optString("genres", "");
            if (!id.matches("tt[0-9]{5,12}") || title.isEmpty() || title.length() > 160
                    || (!year.matches("[0-9]{4}") && !year.isEmpty())
                    || description.length() > 1200 || genres.length() > 80
                    || !FilmSuggestionClient.isAllowedBackground(image, id)) return null;
            return new FilmSuggestion(id, value.optInt("tmdbId", 0), title,
                    year, description, genres, image);
        } catch (JSONException error) {
            return null;
        }
    }

    static long updatedAt(SharedPreferences preferences) {
        return preferences.getLong(KEY_UPDATED_AT, 0);
    }

    static void save(SharedPreferences preferences, FilmSuggestion film) {
        JSONObject value = new JSONObject();
        try {
            value.put("id", film.imdbId);
            value.put("tmdbId", film.tmdbId);
            value.put("title", film.title);
            value.put("year", film.year);
            value.put("description", film.description);
            value.put("genres", film.genres);
            value.put("image", film.backgroundUrl);
            preferences.edit().putString(KEY_SUGGESTION, value.toString())
                    .putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply();
        } catch (JSONException ignored) {
            throw new IllegalStateException("Movie suggestion could not be cached", ignored);
        }
    }
}
