package br.com.suporte.horizontv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NeighborhoodClientTest {
    @Test
    public void prefersNeighborhoodOverCity() throws Exception {
        String json = "{\"address\":{\"suburb\":\"Glicério\",\"city\":\"São Paulo\"}}";
        assertEquals("Glicério", NeighborhoodClient.parse(json));
    }

    @Test
    public void fallsBackToCityWhenNoNeighborhoodIsMapped() throws Exception {
        String json = "{\"address\":{\"city\":\"Campinas\"}}";
        assertEquals("Campinas", NeighborhoodClient.parse(json));
    }
}
