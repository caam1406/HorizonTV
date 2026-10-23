package br.com.suporte.horizontv;

final class OpenverseImage {
    final String id;
    final String title;
    final String creator;
    final String license;
    final String licenseVersion;
    final String licenseUrl;
    final String sourceUrl;
    final String imageUrl;
    final String attribution;

    OpenverseImage(String id, String title, String creator, String license,
                   String licenseVersion, String licenseUrl, String sourceUrl,
                   String imageUrl, String attribution) {
        this.id = id;
        this.title = title;
        this.creator = creator;
        this.license = license;
        this.licenseVersion = licenseVersion;
        this.licenseUrl = licenseUrl;
        this.sourceUrl = sourceUrl;
        this.imageUrl = imageUrl;
        this.attribution = attribution;
    }

    String shortCredit() {
        String creatorLabel = creator.isEmpty() ? "autor não informado" : creator;
        String version = licenseVersion.isEmpty() ? "" : " " + licenseVersion;
        return title + " — " + creatorLabel + " · " + license.toUpperCase() + version;
    }
}
