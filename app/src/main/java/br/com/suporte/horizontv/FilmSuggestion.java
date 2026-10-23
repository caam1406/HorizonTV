package br.com.suporte.horizontv;

final class FilmSuggestion {
    final String imdbId;
    final int tmdbId;
    final String title;
    final String year;
    final String description;
    final String genres;
    final String backgroundUrl;

    FilmSuggestion(String imdbId, int tmdbId, String title, String year,
                   String description, String genres, String backgroundUrl) {
        this.imdbId = imdbId;
        this.tmdbId = tmdbId;
        this.title = title;
        this.year = year;
        this.description = description;
        this.genres = genres;
        this.backgroundUrl = backgroundUrl;
    }

    String detailsUrl() {
        return tmdbId > 0 ? "https://www.themoviedb.org/movie/" + tmdbId
                : "https://www.imdb.com/title/" + imdbId;
    }
}
