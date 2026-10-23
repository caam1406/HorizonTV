package br.com.suporte.horizontv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WeatherTextTest {
    @Test
    public void describesCommonWeatherCodes() {
        assertEquals("Céu limpo", WeatherText.describe(0));
        assertEquals("Parcialmente nublado", WeatherText.describe(2));
        assertEquals("Chuva forte", WeatherText.describe(65));
        assertEquals("Trovoadas", WeatherText.describe(95));
    }

    @Test
    public void unknownCodesUseSafeFallback() {
        assertEquals("Condição indisponível", WeatherText.describe(-1));
    }
}
