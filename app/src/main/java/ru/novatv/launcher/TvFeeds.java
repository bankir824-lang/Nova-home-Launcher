package ru.novatv.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.*;

/** Reads only rows the TV provider grants to an ordinary installed launcher. */
final class TvFeeds {
  static final class Program {
    final String title, artwork, intent, pkg;

    Program(String title, String artwork, String intent, String pkg) {
      this.title = title;
      this.artwork = artwork;
      this.intent = intent;
      this.pkg = pkg;
    }
  }

  static final class Channel {
    final String id, title, pkg, source;
    final List<Program> programs = new ArrayList<>();

    Channel(String id, String title, String pkg, String source) {
      this.id = id;
      this.title = title;
      this.pkg = pkg;
      this.source = source;
    }
  }

  static final class Result {
    final List<Channel> channels = new ArrayList<>();
    final List<String> details = new ArrayList<>();
    String message = "";
    int discovered, displayedPrograms, skippedWithoutLink;

    String diagnostics() {
      return "Preview Channels: " + discovered + "\nПоказано карточек: " + displayedPrograms
          + "\nПропущено без ссылки: " + skippedWithoutLink + "\n" + message + "\n"
          + android.text.TextUtils.join("\n", details);
    }
  }

  static Result read(Context context, boolean readPreview) {
    Result result = new Result();
    if (readPreview) readPreviewChannels(context, result);
    else result.message = "Для каналов приложений требуется разрешение.";
    try {
      Channel next = new Channel("watch_next", "Смотреть далее", "", "Android TV");
      readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, next, result);
      if (!next.programs.isEmpty()) result.channels.add(0, next);
      result.details.add("Смотреть далее: " + next.programs.size());
    } catch (RuntimeException e) {
      result.details.add("Смотреть далее: " + failure(e));
    }
    return result;
  }

  private static void readPreviewChannels(Context context, Result result) {
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                TvContract.Channels.CONTENT_URI,
                new String[] {"_id", "display_name", "package_name", "type", "browsable"},
                null,
                null,
                null)) {
      if (c == null) {
        result.message = "TV Provider не вернул список каналов.";
        return;
      }
      while (c.moveToNext() && result.discovered < 40) {
        if (!TvContract.Channels.TYPE_PREVIEW.equals(c.getString(3))) continue;
        result.discovered++;
        String pkg = c.getString(2);
        String source = sourceName(context, pkg);
        String title = c.getString(1);
        Channel channel = new Channel(c.getString(0),
            title == null || title.trim().isEmpty() ? source : title, pkg, source);
        String label = channel.title + " [" + pkg + "]";
        if (c.getInt(4) == 0) {
          result.details.add(label + ": выключен в системе");
          continue;
        }
        try {
          readPrograms(context, TvContract.buildPreviewProgramsUriForChannel(c.getLong(0)),
              channel, result);
          result.details.add(label + ": " + channel.programs.size() + " карточек");
          if (!channel.programs.isEmpty()) result.channels.add(channel);
        } catch (RuntimeException e) {
          result.details.add(label + ": " + failure(e));
        }
      }
      result.channels.sort(Comparator.comparing(channel -> channel.source, String.CASE_INSENSITIVE_ORDER));
      if (result.channels.isEmpty()) result.message = "Нет доступных каналов приложений. Подробности в диагностике.";
    } catch (RuntimeException e) {
      result.message = failure(e);
    }
  }

  private static String sourceName(Context context, String pkg) {
    if (pkg == null || pkg.isEmpty()) return "Приложение";
    try {
      PackageManager pm = context.getPackageManager();
      return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
    } catch (PackageManager.NameNotFoundException ignored) {
      return "Приложение";
    }
  }

  private static String failure(RuntimeException e) {
    return e instanceof SecurityException ? "Прошивка ограничивает чтение TV Provider."
        : "TV Provider не поддерживает запрос.";
  }

  private static void readPrograms(
      Context context, Uri uri, Channel channel, Result result) {
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                uri,
                new String[] {"title", "poster_art_uri", "intent_uri", "package_name", "thumbnail_uri"},
                null,
                null,
                null)) {
      if (c != null)
        while (c.moveToNext() && channel.programs.size() < 12) {
          String intent = c.getString(2);
          if (intent == null || intent.isEmpty()) {
            result.skippedWithoutLink++;
            continue;
          }
          String artwork = c.getString(1);
          if (artwork == null || artwork.isEmpty()) artwork = c.getString(4);
          String pkg = c.getString(3);
          channel.programs.add(new Program(c.getString(0), artwork, intent,
              pkg == null || pkg.isEmpty() ? channel.pkg : pkg));
          result.displayedPrograms++;
        }
    }
  }
}
