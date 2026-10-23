package br.com.suporte.horizontv;

import android.util.Xml;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParserFactory;

final class NewsFeedReader {
    private static final int MAX_FEED_BYTES = 512 * 1024;
    private static final int MAX_HEADLINES = 20;
    private static final Pattern HTML_IMAGE = Pattern.compile(
            "(?is)<img\\b[^>]*\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Feed[] FEEDS = {
            new Feed("G1", "https://g1.globo.com/rss/g1/"),
            new Feed("Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml")
    };

    private NewsFeedReader() { }

    static List<Headline> fetch() throws IOException, SAXException {
        List<List<Headline>> feedResults = new ArrayList<>();
        IOException ioFailure = null;
        SAXException parseFailure = null;
        for (Feed feed : FEEDS) {
            try {
                feedResults.add(fetchFeed(feed));
            } catch (IOException error) {
                ioFailure = error;
            } catch (SAXException error) {
                parseFailure = error;
            }
        }
        List<Headline> all = merge(feedResults);
        if (all.isEmpty()) {
            if (parseFailure != null) throw parseFailure;
            if (ioFailure != null) throw ioFailure;
        }
        return all;
    }

    static List<Headline> merge(List<List<Headline>> feeds) {
        List<Headline> merged = new ArrayList<>();
        Set<String> seenArticles = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        int[] positions = new int[feeds.size()];
        while (merged.size() < MAX_HEADLINES) {
            boolean addedInRound = false;
            for (int feedIndex = 0; feedIndex < feeds.size(); feedIndex++) {
                List<Headline> feed = feeds.get(feedIndex);
                while (positions[feedIndex] < feed.size()) {
                    Headline item = feed.get(positions[feedIndex]++);
                    if (!seenArticles.add(articleIdentity(item.url))
                            || !seenTitles.add(titleIdentity(item.title))) continue;
                    merged.add(item);
                    addedInRound = true;
                    break;
                }
                if (merged.size() == MAX_HEADLINES) break;
            }
            if (!addedInRound) break;
        }
        return Collections.unmodifiableList(merged);
    }

    private static String articleIdentity(String value) {
        try {
            URL url = new URL(value);
            String path = url.getPath().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
            return url.getHost().toLowerCase(Locale.ROOT) + path;
        } catch (Exception ignored) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    private static String titleIdentity(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static List<Headline> parse(InputStream stream, String source) throws IOException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        XMLReader reader;
        try {
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            disableExternalEntities(factory);
            reader = factory.newSAXParser().getXMLReader();
        } catch (Exception error) {
            return parseWithPlatformPullParser(stream, source, error);
        }
        Handler handler = new Handler(source);
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new LimitedInputStream(stream, MAX_FEED_BYTES)));
        return Collections.unmodifiableList(handler.items);
    }

    private static List<Headline> parseWithPlatformPullParser(InputStream stream, String source,
                                                               Exception saxConfigurationError)
            throws SAXException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            parser.setInput(new LimitedInputStream(stream, MAX_FEED_BYTES), null);
            List<Headline> items = new ArrayList<>();
            String title = "";
            String summary = "";
            String link = "";
            String imageUrl = "";
            String currentField = "";
            StringBuilder text = new StringBuilder();
            boolean inItem = false;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName().toLowerCase(Locale.ROOT);
                    if (tag.equals("item") || tag.equals("entry")) {
                        inItem = true;
                        title = "";
                        summary = "";
                        link = "";
                        imageUrl = "";
                        currentField = "";
                    } else if (inItem && tag.equals("link")) {
                        String href = parser.getAttributeValue(null, "href");
                        if (href != null) link = href;
                        else currentField = tag;
                        text.setLength(0);
                    } else if (inItem && (tag.equals("imagem-destaque") || tag.equals("thumbnail"))) {
                        currentField = tag;
                        text.setLength(0);
                    } else if (inItem && (tag.equals("content") || tag.equals("enclosure"))) {
                        String candidate = parser.getAttributeValue(null, "url");
                        if (candidate == null) candidate = parser.getAttributeValue(
                                "http://search.yahoo.com/mrss/", "url");
                        if (imageUrl.isEmpty() && isAllowedImageUrl(candidate)) imageUrl = candidate;
                    } else if (inItem && (tag.equals("title") || tag.equals("description")
                            || tag.equals("summary"))) {
                        currentField = tag;
                        text.setLength(0);
                    }
                } else if (inItem && currentField.length() > 0
                        && (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT
                        || event == XmlPullParser.ENTITY_REF) && text.length() < 4096) {
                    String value = parser.getText();
                    if (value != null) text.append(value, 0, Math.min(value.length(), 4096 - text.length()));
                } else if (event == XmlPullParser.END_TAG && inItem) {
                    String tag = parser.getName().toLowerCase(Locale.ROOT);
                    if (tag.equals(currentField)) {
                        String value = cleanText(text.toString());
                        if (tag.equals("title") && title.isEmpty()) title = value;
                        if ((tag.equals("description") || tag.equals("summary")) && summary.isEmpty()) summary = value;
                        if (tag.equals("link") && link.isEmpty()) link = value;
                        if ((tag.equals("imagem-destaque") || tag.equals("thumbnail") || tag.equals("image"))
                                && imageUrl.isEmpty()) imageUrl = safeImageUrl(value);
                        if (tag.equals("description") && imageUrl.isEmpty()) imageUrl = extractImageUrl(text.toString());
                        currentField = "";
                    }
                    if (tag.equals("item") || tag.equals("entry")) {
                        String safeTitle = truncate(title, 180);
                        if (!safeTitle.isEmpty() && isAllowedArticleUrl(link)) {
                            items.add(new Headline(safeTitle, truncate(summary, 220), source, link, imageUrl));
                        }
                        inItem = false;
                    }
                }
            }
            return Collections.unmodifiableList(items);
        } catch (XmlPullParserException error) {
            throw new SAXException("Could not parse RSS with DTD processing disabled", error);
        }
    }

    private static void disableExternalEntities(SAXParserFactory factory) {
        String[] features = {
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        };
        for (String feature : features) {
            try {
                factory.setFeature(feature, false);
            } catch (Exception ignored) {
                // Required DTD rejection below prevents any external entity declaration.
            }
        }
    }

    static boolean isAllowedArticleUrl(String value) {
        try {
            URL url = new URL(value);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && url.getUserInfo() == null
                    && (host.equals("g1.globo.com") || host.endsWith(".globo.com")
                    || host.equals("agenciabrasil.ebc.com.br"));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isAllowedImageUrl(String value) {
        try {
            URL url = new URL(value);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && url.getUserInfo() == null
                    && (host.endsWith(".glbimg.com")
                    || host.equals("imagens.ebc.com.br")
                    || host.equals("agenciabrasil.ebc.com.br"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractImageUrl(String html) {
        Matcher matcher = HTML_IMAGE.matcher(html);
        if (!matcher.find()) return "";
        String candidate = matcher.group(1) != null ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        return safeImageUrl(candidate == null ? "" : candidate.replace("&amp;", "&").trim());
    }

    private static String safeImageUrl(String value) {
        String candidate = value == null ? "" : value.trim();
        return isAllowedImageUrl(candidate) ? candidate : "";
    }

    private static List<Headline> fetchFeed(Feed feed) throws IOException, SAXException {
        HttpURLConnection connection = (HttpURLConnection) new URL(feed.url).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9");
        connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300 || !isAllowedArticleUrl(connection.getURL().toString())) {
                throw new IOException("News source returned an unexpected response");
            }
            try (InputStream input = connection.getInputStream()) {
                return parse(input, feed.name);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static final class Feed {
        final String name;
        final String url;

        Feed(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private static final class Handler extends DefaultHandler {
        final String source;
        final List<Headline> items = new ArrayList<>();
        String title;
        String summary;
        String link;
        String imageUrl;
        String currentField = "";
        StringBuilder text = new StringBuilder();
        boolean inItem;

        Handler(String source) {
            this.source = source;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tag = tag(localName, qName);
            if (tag.equals("item") || tag.equals("entry")) {
                inItem = true;
                title = "";
                summary = "";
                link = "";
                imageUrl = "";
                currentField = "";
                text.setLength(0);
            } else if (inItem && (tag.equals("imagem-destaque") || tag.equals("thumbnail"))) {
                currentField = tag;
                text.setLength(0);
            } else if (inItem && (tag.equals("content") || tag.equals("enclosure"))) {
                String candidate = attributes.getValue("url");
                if (candidate == null) candidate = attributes.getValue("http://search.yahoo.com/mrss/", "url");
                if (imageUrl.isEmpty() && isAllowedImageUrl(candidate)) imageUrl = candidate;
            } else if (inItem && tag.equals("link")) {
                String href = attributes.getValue("href");
                if (href != null) link = href;
                else {
                    currentField = tag;
                    text.setLength(0);
                }
            } else if (inItem && (tag.equals("title") || tag.equals("description") || tag.equals("summary"))) {
                currentField = tag;
                text.setLength(0);
            }
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            if (inItem && !currentField.isEmpty() && text.length() < 4096) {
                text.append(chars, start, Math.min(length, 4096 - text.length()));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String tag = tag(localName, qName);
            if (inItem) {
                if (tag.equals(currentField)) {
                    String value = cleanText(text.toString());
                    if (tag.equals("title") && title.isEmpty()) title = value;
                    if ((tag.equals("description") || tag.equals("summary")) && summary.isEmpty()) summary = value;
                    if (tag.equals("link") && link.isEmpty()) link = value;
                    if ((tag.equals("imagem-destaque") || tag.equals("thumbnail") || tag.equals("image"))
                            && imageUrl.isEmpty()) imageUrl = safeImageUrl(value);
                    if (tag.equals("description") && imageUrl.isEmpty()) {
                        imageUrl = extractImageUrl(text.toString());
                    }
                    currentField = "";
                }
                if (tag.equals("item") || tag.equals("entry")) finishItem();
            }
        }

        private void finishItem() {
            String safeTitle = truncate(title, 180);
            if (!safeTitle.isEmpty() && isAllowedArticleUrl(link)) {
                items.add(new Headline(safeTitle, truncate(summary, 220), source, link, imageUrl));
            }
            inItem = false;
        }
    }

    private static String tag(String localName, String qName) {
        String name = localName == null || localName.isEmpty() ? qName : localName;
        int prefix = name.indexOf(':');
        return (prefix >= 0 ? name.substring(prefix + 1) : name).toLowerCase(Locale.ROOT);
    }

    private static String cleanText(String value) {
        String noMarkup = value.replaceAll("(?is)<[^>]*>", " ");
        return noMarkup.replace("&nbsp;", " ").replace("&#160;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength - 1).trim() + "…";
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream input;
        private final int limit;
        private int count;

        LimitedInputStream(InputStream input, int limit) {
            this.input = input;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = input.read();
            if (value >= 0 && ++count > limit) throw new IOException("RSS feed exceeded the size limit");
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int remaining = limit - count + 1;
            int read = input.read(buffer, offset, Math.min(length, remaining));
            if (read > 0) {
                count += read;
                if (count > limit) throw new IOException("RSS feed exceeded the size limit");
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
