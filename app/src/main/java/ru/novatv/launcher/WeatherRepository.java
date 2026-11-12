package ru.novatv.launcher;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

final class WeatherRepository {
  private final String userAgent;

  static final class Forecast {
    final double temperature;
    final int weatherCode;
    final String provider, diagnostics;

    Forecast(double temperature, int weatherCode, String provider, String diagnostics) {
      this.temperature = temperature;
      this.weatherCode = weatherCode;
      this.provider = provider;
      this.diagnostics = diagnostics;
    }
  }

  WeatherRepository(String version) {
    userAgent = "NovaTVLauncher/" + version + " AndroidTV";
  }

  Forecast forecast(String latitude, String longitude) throws IOException {
    double lat = coordinate(latitude, 90);
    double lon = coordinate(longitude, 180);
    String primary = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
        + "&longitude=" + lon + "&current=temperature_2m,weather_code";
    String backup = "https://wttr.in/" + lat + "," + lon + "?format=j1";
    long start = SystemClock.elapsedRealtime();
    String primaryError;
    try {
      JSONObject data = json(primary).getJSONObject("current");
      return new Forecast(data.getDouble("temperature_2m"), data.getInt("weather_code"),
          "Open-Meteo", diagnostic(primary, start, "HTTP 200"));
    } catch (IOException | JSONException failure) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
      primaryError = diagnostic(primary, start, describe(failure));
    }
    start = SystemClock.elapsedRealtime();
    try {
      JSONObject data = json(backup).getJSONArray("current_condition").getJSONObject(0);
      return new Forecast(data.getDouble("temp_C"), -1, "wttr.in",
          primaryError + "\n" + diagnostic(backup, start, "HTTP 200"));
    } catch (IOException | JSONException failure) {
      throw new IOException(primaryError + "\n" + diagnostic(backup, start, describe(failure)), failure);
    }
  }

  private static double coordinate(String text, int limit) throws IOException {
    try {
      double value = Double.parseDouble(text);
      if (Double.isFinite(value) && Math.abs(value) <= limit) return value;
    } catch (NumberFormatException ignored) {
      // Invalid data from the geocoding service.
    }
    throw new IOException("Некорректные координаты города");
  }

  static String describe(Exception failure) {
    return failure instanceof JSONException ? "Некорректный ответ сервиса"
        : WeatherErrors.describe(failure);
  }

  private static String diagnostic(String url, long start, String result) throws IOException {
    return new URL(url).getHost() + " · " + result + " · "
        + (SystemClock.elapsedRealtime() - start) + " мс";
  }

  JSONObject json(String address) throws IOException, JSONException {
    URL url = new URL(address);
    if (!"https".equals(url.getProtocol())) throw new IOException("Требуется HTTPS");
    try {
      return request(url);
    } catch (SocketTimeoutException failure) {
      return request(url);
    }
  }

  private JSONObject request(URL url) throws IOException, JSONException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(4500);
    connection.setReadTimeout(4500);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("User-Agent", userAgent);
    try {
      int status = connection.getResponseCode();
      if (status != 200) throw new IOException("HTTP " + status);
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] bytes = new byte[4096];
        int count;
        while ((count = input.read(bytes)) != -1) {
          if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
          if (output.size() + count > 1048576) throw new IOException("Ответ слишком большой");
          output.write(bytes, 0, count);
        }
        return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
      }
    } finally {
      connection.disconnect();
    }
  }
}
