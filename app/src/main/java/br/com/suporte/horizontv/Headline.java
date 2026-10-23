package br.com.suporte.horizontv;

final class Headline {
    final String title;
    final String summary;
    final String source;
    final String url;
    final String imageUrl;

    Headline(String title, String summary, String source, String url) {
        this(title, summary, source, url, "");
    }

    Headline(String title, String summary, String source, String url, String imageUrl) {
        this.title = title;
        this.summary = summary;
        this.source = source;
        this.url = url;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }
}
