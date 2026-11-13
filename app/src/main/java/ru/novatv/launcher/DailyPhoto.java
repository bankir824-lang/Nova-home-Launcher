package ru.novatv.launcher;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.text.Html;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.json.JSONObject;

final class DailyPhoto {
  static final String WALLPAPER = "wallpaper_daily_photo";
  private static final int IMAGE_LIMIT = 8 * 1024 * 1024;
  private static final String API = "https://commons.wikimedia.org/w/api.php?";
  private final AtomicFile cache;

  static final class Photo {
    final String date, title, author, license, licenseUrl, source;
    final Bitmap bitmap;

    Photo(String date, String title, String author, String license, String licenseUrl,
        String source, Bitmap bitmap) {
      this.date = date;
      this.title = title;
      this.author = author;
      this.license = license;
      this.licenseUrl = licenseUrl;
      this.source = source;
      this.bitmap = bitmap;
    }
  }

  DailyPhoto(File directory) {
    cache = new AtomicFile(new File(directory, "daily-photo"));
  }

  static String today() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  Photo cached() {
    try (DataInputStream in = new DataInputStream(cache.openRead())) {
      String date = in.readUTF();
      String title = in.readUTF();
      String author = in.readUTF();
      String license = in.readUTF();
      String licenseUrl = in.readUTF();
      String source = in.readUTF();
      return new Photo(date, title, author, license, licenseUrl, source, decode(read(in, IMAGE_LIMIT)));
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  Photo download(String date, boolean save) throws Exception {
    JSONObject template = json("action=expandtemplates&prop=wikitext&text="
        + Uri.encode("{{Potd/" + date + "}}"));
    String name = template.getJSONObject("expandtemplates").getString("wikitext").trim();
    if (name.isEmpty() || name.startsWith("[[") || name.startsWith("<")) {
      throw new IOException("No daily photo");
    }
    String file = name.startsWith("File:") ? name : "File:" + name;
    JSONObject page = json("action=query&prop=imageinfo&iiprop=url%7Cextmetadata"
        + "&iiurlwidth=1280&iiurlheight=1280"
        + "&iiextmetadatafilter=Artist%7CLicenseShortName%7CLicenseUrl%7CRestrictions"
        + "&titles=" + Uri.encode(file)).getJSONObject("query").getJSONArray("pages").getJSONObject(0);
    JSONObject info = page.getJSONArray("imageinfo").getJSONObject(0);
    JSONObject metadata = info.getJSONObject("extmetadata");
    String license = value(metadata, "LicenseShortName");
    if (license.isEmpty()) throw new IOException("Missing license");
    if (!value(metadata, "Restrictions").isEmpty()) throw new IOException("Restricted image");
    URL thumbnail = new URL(info.getString("thumburl"));
    if (!"https".equals(thumbnail.getProtocol())
        || !("upload.wikimedia.org".equals(thumbnail.getHost())
            || "thumb.wikimedia.org".equals(thumbnail.getHost()))) {
      throw new IOException("Unexpected image host");
    }
    byte[] bytes = fetch(thumbnail, IMAGE_LIMIT);
    Bitmap bitmap = decode(bytes);
    String title = page.getString("title").substring("File:".length());
    String author = Html.fromHtml(value(metadata, "Artist"), Html.FROM_HTML_MODE_COMPACT)
        .toString().trim();
    String source = "https://commons.wikimedia.org/wiki/" + Uri.encode(file.replace(' ', '_'), ":");
    Photo photo = new Photo(date, title, author, license, value(metadata, "LicenseUrl"), source, bitmap);
    if (!save) return photo;
    FileOutputStream stream = cache.startWrite();
    try {
      DataOutputStream out = new DataOutputStream(stream);
      out.writeUTF(photo.date);
      out.writeUTF(photo.title);
      out.writeUTF(photo.author);
      out.writeUTF(photo.license);
      out.writeUTF(photo.licenseUrl);
      out.writeUTF(photo.source);
      out.write(bytes);
      out.flush();
      cache.finishWrite(stream);
    } catch (IOException | RuntimeException failure) {
      cache.failWrite(stream);
      throw failure;
    }
    return photo;
  }

  private static String value(JSONObject metadata, String key) {
    JSONObject field = metadata.optJSONObject(key);
    return field == null ? "" : field.optString("value");
  }

  private static JSONObject json(String query) throws Exception {
    byte[] bytes = fetch(new URL(API + query + "&format=json&formatversion=2"), 512 * 1024);
    return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
  }

  private static Bitmap decode(byte[] bytes) throws IOException {
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
        (decoder, info, source) -> {
          int width = info.getSize().getWidth(), height = info.getSize().getHeight();
          float scale = Math.min(1f, 1280f / Math.max(width, height));
          decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
              Math.max(1, Math.round(height * scale)));
        });
  }

  private static byte[] fetch(URL url, int limit) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    try {
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "NovaHomeLauncher/" + BuildConfig.VERSION_NAME
          + " (https://github.com/bankir824-lang/Nova-home-Launcher)");
      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("Unexpected HTTP response");
      }
      try (InputStream in = connection.getInputStream()) {
        return read(in, limit);
      }
    } finally {
      connection.disconnect();
    }
  }

  private static byte[] read(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    for (int size; (size = in.read(buffer)) != -1; ) {
      if (bytes.size() + size > limit || Thread.currentThread().isInterrupted()) {
        throw new IOException("Photo download stopped");
      }
      bytes.write(buffer, 0, size);
    }
    return bytes.toByteArray();
  }
}
