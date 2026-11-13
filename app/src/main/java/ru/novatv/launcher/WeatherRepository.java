package ru.novatv.launcher;

import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

final class WeatherRepository {
  private final String userAgent;
  private final SharedPreferences metCache;
  private static final long MET_INTERVAL = 3L * 60 * 60 * 1000;
  private static final Object MET_LOCK = new Object();

  static final class Forecast {
    final double temperature;
    final int weatherCode;
    final String provider, diagnostics;
    final long fetchedAt;

    Forecast(double temperature, int weatherCode, String provider, String diagnostics,
        long fetchedAt) {
      this.temperature = temperature;
      this.weatherCode = weatherCode;
      this.provider = provider;
      this.diagnostics = diagnostics;
      this.fetchedAt = fetchedAt;
    }
  }

  WeatherRepository(String version, SharedPreferences metCache) {
    userAgent = "NovaHomeLauncher/" + version + " (https://github.com/bankir824-lang/Nova-home-Launcher)";
    this.metCache = metCache;
  }

  Forecast forecast(String latitude, String longitude) throws IOException {
    double lat = coordinate(latitude, 90);
    double lon = coordinate(longitude, 180);
    String primary = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
        + "&longitude=" + lon + "&current=temperature_2m,weather_code";
    String point = "lat=" + metCoordinate(lat) + "&lon=" + metCoordinate(lon);
    String backup = "https://api.met.no/weatherapi/locationforecast/2.0/compact?" + point;
    long start = SystemClock.elapsedRealtime();
    String primaryError;
    try {
      JSONObject data = json(primary).getJSONObject("current");
      return new Forecast(data.getDouble("temperature_2m"), data.getInt("weather_code"),
          "Open-Meteo", diagnostic(primary, start, "HTTP 200"), System.currentTimeMillis());
    } catch (IOException | JSONException failure) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
      primaryError = diagnostic(primary, start, describe(failure));
    }
    start = SystemClock.elapsedRealtime();
    try {
      Forecast met = metForecast(backup, point, start);
      return new Forecast(met.temperature, met.weatherCode, met.provider,
          primaryError + "\n" + met.diagnostics, met.fetchedAt);
    } catch (IOException | JSONException failure) {
      throw new IOException(primaryError + "\n" + diagnostic(backup, start, describe(failure)), failure);
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
      long expiry = metCache.getLong(point + ".until", 0);
      if (now >= saved && now < expiry) {
        double temperature = Double.longBitsToDouble(metCache.getLong(point + ".temp", 0));
        return new Forecast(temperature, -1, "MET Norway",
            diagnostic(address, start, "кеш прогноза"), saved);
      }
      long retryAfter = metCache.getLong(point + ".retry", 0);
      if (now < retryAfter && retryAfter - now <= 60_000L)
        throw new IOException("Повтор доступен после паузы");
      metCache.edit().putLong(point + ".retry", now + 60_000L).apply();
      Response response = request(new URL(address));
      double temperature = metTemperature(response.json);
      metCache.edit()
          .putLong(point + ".at", now)
          .putLong(point + ".until", Math.max(now + MET_INTERVAL, response.expiresAt))
          .putLong(point + ".temp", Double.doubleToLongBits(temperature))
          .remove(point + ".retry")
          .apply();
      return new Forecast(temperature, -1, "MET Norway", diagnostic(address, start, "HTTP 200"), now);
    }
  }

  static double metTemperature(JSONObject response) throws JSONException {
    double value = response.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0)
        .getJSONObject("data").getJSONObject("instant").getJSONObject("details")
        .getDouble("air_temperature");
    if (!Double.isFinite(value)) throw new JSONException("Некорректная температура");
    return value;
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
      return request(url).json;
    } catch (SocketTimeoutException failure) {
      return request(url).json;
    }
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
