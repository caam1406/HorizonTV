package br.com.suporte.horizontv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OpenverseImageClientTest {
    @Test
    public void parsesAttributionLicensedLandscapeImage() throws Exception {
        List<OpenverseImage> images = OpenverseImageClient.parseResults(
                new JSONObject().put("results", new JSONArray().put(image("by-sa", false))).toString());

        assertEquals(1, images.size());
        assertEquals("Aurora sobre montanhas", images.get(0).title);
        assertEquals("Fotógrafo", images.get(0).creator);
        assertEquals("by-sa", images.get(0).license);
    }

    @Test
    public void rejectsMatureImagesAndLicensesThatDoNotAllowModification() throws Exception {
        JSONObject mature = image("cc0", true);
        JSONObject nonCommercial = image("by-nc", false);
        JSONObject noDerivatives = image("by-nd", false);
        List<OpenverseImage> images = OpenverseImageClient.parseResults(new JSONObject()
                .put("results", new JSONArray().put(mature).put(nonCommercial).put(noDerivatives)).toString());

        assertTrue(images.isEmpty());
    }

    @Test
    public void rejectsUntrustedLicenseOrSourceAddresses() throws Exception {
        JSONObject badLicense = image("by", false).put("license_url", "https://evil.example/license");
        JSONObject badSource = image("by", false).put("foreign_landing_url", "http://images.example/photo");
        JSONObject badImageHost = image("by", false).put("url", "https://127.0.0.1/private.jpg");
        List<OpenverseImage> images = OpenverseImageClient.parseResults(new JSONObject()
                .put("results", new JSONArray().put(badLicense).put(badSource).put(badImageHost)).toString());

        assertTrue(images.isEmpty());
        assertFalse(OpenverseImageClient.isHttpsUrl("https://user:pass@example.org/photo"));
    }

    private static JSONObject image(String license, boolean mature) throws Exception {
        return new JSONObject()
                .put("id", "4bc43a04-ef46-4544-a0c1-63c63f56e276")
                .put("title", "Aurora sobre montanhas")
                .put("creator", "Fotógrafo")
                .put("license", license)
                .put("license_version", "4.0")
                .put("license_url", "https://creativecommons.org/licenses/by/4.0/")
                .put("foreign_landing_url", "https://example.org/photo")
                .put("provider", "flickr")
                .put("url", "https://live.staticflickr.com/1234/photo.jpg")
                .put("attribution", "Foto de Fotógrafo, CC BY 4.0")
                .put("mature", mature);
    }
}
