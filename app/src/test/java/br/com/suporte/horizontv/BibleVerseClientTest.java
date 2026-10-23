package br.com.suporte.horizontv;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class BibleVerseClientTest {
    @Test
    public void parsesPortugueseVerseAndBuildsReference() throws Exception {
        BibleVerse verse = BibleVerseClient.parse("""
                {"random_verse":{"book":"Salmos","chapter":23,"verse":1,
                "text":"O Senhor é o meu pastor.   "}}
                """);

        assertEquals("O Senhor é o meu pastor.", verse.text);
        assertEquals("Salmos 23:1", verse.reference);
    }

    @Test
    public void rejectsMissingVerseFields() throws Exception {
        try {
            BibleVerseClient.parse("{\"random_verse\":{\"book\":\"Salmos\"}}");
            fail("Expected malformed API response to be rejected");
        } catch (JSONException expected) {
            // The endpoint response must include verse text and coordinates.
        }
    }

    @Test
    public void rejectsEmptyVerseText() throws Exception {
        try {
            BibleVerseClient.parse("{\"random_verse\":{\"book\":\"Salmos\",\"chapter\":1,\"verse\":1,\"text\":\" \"}}");
            fail("Expected an empty verse to be rejected");
        } catch (JSONException expected) {
            // Empty content is never cached or shown as a verse.
        }
    }

    @Test
    public void rejectsInvalidVerseCoordinates() throws Exception {
        try {
            BibleVerseClient.parse("{\"random_verse\":{\"book\":\"Salmos\",\"chapter\":0,\"verse\":1,\"text\":\"Texto\"}}");
            fail("Expected invalid verse coordinates to be rejected");
        } catch (JSONException expected) {
            // Chapter and verse numbers must be positive.
        }
    }
}
