package ru.novatv.launcher;

import android.content.Context;
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
    final String id, title, pkg;
    final List<Program> programs = new ArrayList<>();

    Channel(String id, String title, String pkg) {
      this.id = id;
      this.title = title;
      this.pkg = pkg;
    }
  }

  static final class Result {
    final List<Channel> channels = new ArrayList<>();
    String message = "";
  }

  static Result read(Context context) {
    Result result = new Result();
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                TvContract.Channels.CONTENT_URI,
                new String[] {"_id", "display_name", "package_name"},
                "type=?",
                new String[] {TvContract.Channels.TYPE_PREVIEW},
                null)) {
      if (c != null)
        while (c.moveToNext() && result.channels.size() < 40) {
          Channel channel = new Channel(c.getString(0), c.getString(1), c.getString(2));
          readPrograms(
              context,
              TvContract.PreviewPrograms.CONTENT_URI,
              "channel_id=?",
              new String[] {channel.id},
              channel);
          if (!channel.programs.isEmpty()) result.channels.add(channel);
        }
    } catch (SecurityException e) {
      result.message = "Android ограничил чтение рекомендаций других приложений.";
    } catch (Exception e) {
      result.message = "TV Provider недоступен на этой прошивке.";
    }
    try {
      Channel next = new Channel("watch_next", "Смотреть далее", "");
      readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, null, null, next);
      if (!next.programs.isEmpty()) result.channels.add(0, next);
    } catch (Exception e) {
      if (result.channels.isEmpty() && result.message.isEmpty())
        result.message = "Нет доступа к списку «Смотреть далее».";
    }
    if (result.channels.isEmpty() && result.message.isEmpty())
      result.message = "Приложения пока не опубликовали доступные рекомендации.";
    return result;
  }

  private static void readPrograms(
      Context context, Uri uri, String selection, String[] args, Channel channel) {
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                uri,
                new String[] {"title", "poster_art_uri", "intent_uri", "package_name"},
                selection,
                args,
                null)) {
      if (c != null)
        while (c.moveToNext() && channel.programs.size() < 12) {
          String intent = c.getString(2);
          if (intent == null || intent.isEmpty()) continue;
          channel.programs.add(new Program(c.getString(0), c.getString(1), intent, c.getString(3)));
        }
    }
  }
}
