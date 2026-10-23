package br.com.suporte.horizontv;

final class WeatherSnapshot {
    final double temperature;
    final double feelsLike;
    final int humidity;
    final double windSpeed;
    final int code;

    WeatherSnapshot(double temperature, double feelsLike, int humidity, double windSpeed, int code) {
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
        this.code = code;
    }
}
