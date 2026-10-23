package br.com.suporte.horizontv;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilmSuggestionClientTest {
    @Test
    public void choosesOneValidMovieDeterministicallyForTheDay() throws Exception {
        FilmSuggestion film = FilmSuggestionClient.choose(catalog(), 1);

        assertEquals("tt7654321", film.imdbId);
        assertEquals(321, film.tmdbId);
        assertEquals("Filme Dois", film.title);
        assertEquals("https://www.themoviedb.org/movie/321", film.detailsUrl());
    }

    @Test
    public void skipsInvalidTypesIdsAndImageHosts() throws Exception {
        FilmSuggestion film = FilmSuggestionClient.choose(catalog(), 0);

        assertEquals("tt1234567", film.imdbId);
        assertEquals("https://www.imdb.com/title/tt1234567", film.detailsUrl());
        assertTrue(FilmSuggestionClient.isAllowedBackground(
                "https://images.metahub.space/background/medium/tt1234567/img"));
        assertFalse(FilmSuggestionClient.isAllowedBackground(
                "https://images.metahub.space/background/medium/tt1234567/img", "tt7654321"));
        assertFalse(FilmSuggestionClient.isAllowedBackground(
                "https://images.metahub.space.evil.example/background/medium/tt1234567/img"));
        assertFalse(FilmSuggestionClient.isAllowedBackground(
                "http://images.metahub.space/background/medium/tt1234567/img"));
    }

    @Test(expected = JSONException.class)
    public void rejectsCatalogWithoutUsableMovie() throws Exception {
        FilmSuggestionClient.choose("{\"metas\":[{\"type\":\"series\"}]}", 0);
    }

    private static String catalog() {
        return "{\"metas\":["
                + "{\"imdb_id\":\"tt1234567\",\"type\":\"movie\",\"name\":\"Filme Um\","
                + "\"background\":\"https://images.metahub.space/background/medium/tt1234567/img\"},"
                + "{\"imdb_id\":\"tt8765432\",\"type\":\"series\",\"name\":\"Série\","
                + "\"background\":\"https://images.metahub.space/background/medium/tt8765432/img\"},"
                + "{\"imdb_id\":\"tt7654321\",\"type\":\"movie\",\"name\":\"Filme Dois\","
                + "\"moviedb_id\":321,\"description\":\"Sinopse\",\"year\":\"2024\","
                + "\"background\":\"https://images.metahub.space/background/medium/tt7654321/img\"},"
                + "{\"imdb_id\":\"tt1111111\",\"type\":\"movie\",\"name\":\"Malicioso\","
                + "\"background\":\"https://attacker.example/background/medium/tt1111111/img\"}]}";
    }
}
