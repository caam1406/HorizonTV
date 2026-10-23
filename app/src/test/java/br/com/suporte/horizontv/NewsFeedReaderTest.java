package br.com.suporte.horizontv;

import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NewsFeedReaderTest {
    @Test
    public void parsesHeadlineAndRemovesMarkup() throws Exception {
        String xml = "<rss><channel><item><title>Hoje <b>agora</b></title>"
                + "<description><![CDATA[<p>Resumo <strong>curto</strong></p>]]></description>"
                + "<link>https://g1.globo.com/brasil/noticia</link></item></channel></rss>";

        List<Headline> items = NewsFeedReader.parse(stream(xml), "G1");

        assertEquals(1, items.size());
        assertEquals("Hoje agora", items.get(0).title);
        assertEquals("Resumo curto", items.get(0).summary);
        assertEquals("G1", items.get(0).source);
    }

    @Test
    public void rejectsUnsafeArticleUrls() {
        assertFalse(NewsFeedReader.isAllowedArticleUrl("http://g1.globo.com/news"));
        assertFalse(NewsFeedReader.isAllowedArticleUrl("https://example.com/news"));
        assertFalse(NewsFeedReader.isAllowedArticleUrl("javascript:alert(1)"));
        assertTrue(NewsFeedReader.isAllowedArticleUrl("https://g1.globo.com/news"));
    }

    @Test
    public void rejectsExternalEntityDeclarations() throws Exception {
        String xml = "<!DOCTYPE rss [<!ENTITY leak SYSTEM 'file:///private-data'>]>"
                + "<rss><channel><item><title>&leak;</title>"
                + "<link>https://g1.globo.com/news</link></item></channel></rss>";
        try {
            NewsFeedReader.parse(stream(xml), "G1");
            fail("DOCTYPE must be rejected");
        } catch (SAXException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    public void keepsBothNewsSourcesInTheMergedResults() {
        List<Headline> g1 = Arrays.asList(
                new Headline("Manchete repetida", "", "G1", "https://g1.globo.com/a"),
                new Headline("Notícia do G1", "", "G1", "https://g1.globo.com/b"));
        List<Headline> agencyBrazil = Arrays.asList(
                new Headline("Manchete repetida", "", "Agência Brasil", "https://agenciabrasil.ebc.com.br/a"),
                new Headline("Notícia da Agência", "", "Agência Brasil", "https://agenciabrasil.ebc.com.br/b"));

        List<Headline> merged = NewsFeedReader.merge(Arrays.asList(g1, agencyBrazil));

        assertEquals(3, merged.size());
        assertEquals("G1", merged.get(0).source);
        assertEquals("Agência Brasil", merged.get(1).source);
        assertEquals("Notícia do G1", merged.get(2).title);
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
