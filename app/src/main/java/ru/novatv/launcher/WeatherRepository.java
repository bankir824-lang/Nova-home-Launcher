package ru.novatv.launcher;

import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

final class WeatherRepository {
  private final String userAgent;
  private final SharedPreferences metCache;
  private static final Object MET_LOCK = new Object();

  static final class Forecast {
    final double temperature;
    final String diagnostics;

    Forecast(double temperature, String diagnostics) {
      this.temperature = temperature;
      this.diagnostics = diagnostics;
    }
  }

  WeatherRepository(String version, SharedPreferences metCache) {
    userAgent = "NovaHomeLauncher/" + version + " (https://github.com/bankir824-lang/Nova-home-Launcher)";
    this.metCache = metCache;
  }

  Forecast forecast(String latitude, String longitude) throws IOException {
    double lat = coordinate(latitude, 90);
    double lon = coordinate(longitude, 180);
    String point = "lat=" + metCoordinate(lat) + "&lon=" + metCoordinate(lon);
    String address = "https://api.met.no/weatherapi/locationforecast/2.0/compact?" + point;
    long start = SystemClock.elapsedRealtime();
    try {
      return metForecast(address, point, start);
    } catch (IOException | JSONException failure) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
      throw new IOException(diagnostic(address, start, describe(failure)), failure);
    }
  }

  private static String metCoordinate(double value) {
    double truncated = Math.copySign(Math.floor(Math.abs(value) * 10000) / 10000, value);
    return String.format(Locale.ROOT, "%.4f", truncated);
  }

  private Forecast metForecast(String address, String point, long start)
      throws IOException, JSONException {
    synchronized (MET_LOCK) {
      long now = System.currentTimeMillis();
      long saved = metCache.getLong(point + ".at", 0);
      long expiry = metCache.getLong(point + ".expires", 0);
      if (saved > 0 && now >= saved && now < expiry) {
        double temperature = Double.longBitsToDouble(metCache.getLong(point + ".temp", 0));
        return new Forecast(temperature, diagnostic(address, start, "кеш прогноза"));
      }
      long retryAfter = metCache.getLong(point + ".retry", 0);
      if (now < retryAfter && retryAfter - now <= 60_000L)
        throw new IOException("Повтор доступен после паузы");
      metCache.edit().putLong(point + ".retry", now + 60_000L).apply();
      Response response = request(new URL(address));
      double temperature = metTemperature(response.json);
      metCache.edit()
          .putLong(point + ".at", now)
          .putLong(point + ".expires", response.expiresAt)
          .putLong(point + ".temp", Double.doubleToLongBits(temperature))
          .remove(point + ".retry")
          .apply();
      return new Forecast(temperature, diagnostic(address, start, "HTTP 200"));
    }
  }

  static double metTemperature(JSONObject response) throws JSONException {
    double value = response.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0)
        .getJSONObject("data").getJSONObject("instant").getJSONObject("details")
        .getDouble("air_temperature");
    if (!Double.isFinite(value)) throw new JSONException("Некорректная температура");
    return value;
  }

  static double coordinate(String text, int limit) throws IOException {
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

  private static final class Response {
    final JSONObject json;
    final long expiresAt;

    Response(JSONObject json, long expiresAt) {
      this.json = json;
      this.expiresAt = expiresAt;
    }
  }

  private Response request(URL url) throws IOException, JSONException {
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
        JSONObject body = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        return new Response(body, connection.getHeaderFieldDate("Expires", 0));
      }
    } finally {
      connection.disconnect();
    }
  }
}
