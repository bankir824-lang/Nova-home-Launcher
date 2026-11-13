package ru.novatv.launcher;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.media.tv.TvContract;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class TvFeedsTest {
  private boolean rejectPreview;

  @Before
  public void addProvider() {
    ShadowContentResolver.registerProviderInternal(TvContract.AUTHORITY, new ContentProvider() {
      @Override public boolean onCreate() { return true; }

      @Override
      public Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(columns);
        if (uri.equals(TvContract.Channels.CONTENT_URI)) {
          if (rejectPreview) throw new SecurityException();
          cursor.addRow(new Object[] {4, "Кинотеатр", "org.example.cinema", "TYPE_PREVIEW", 1});
          cursor.addRow(new Object[] {5, "Скрытый", "org.example.cinema", "TYPE_PREVIEW", 0});
        } else {
          cursor.addRow(new Object[] {null, null, "https://example.org/film", "org.example.cinema",
              "https://example.org/poster"});
          cursor.addRow(new Object[] {"Без ссылки", null, null, "org.example.cinema", null});
        }
        return cursor;
      }

      @Override public String getType(Uri uri) { return null; }
      @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
      @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
      @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    });
  }

  @Test
  public void deniedPermissionStillReadsWatchNext() {
    rejectPreview = true;
    TvFeeds.Result result = TvFeeds.read(RuntimeEnvironment.getApplication(), false);
    assertEquals(1, result.channels.size());
    assertEquals("watch_next", result.channels.get(0).id);
    assertTrue(result.message.contains("разрешение"));
  }

  @Test
  public void validPreviewUsesThumbnailAndSkipsHiddenChannelsAndMissingLinks() {
    TvFeeds.Result result = TvFeeds.read(RuntimeEnvironment.getApplication(), true);
    assertEquals(2, result.discovered);
    assertEquals(2, result.channels.size());
    assertEquals(2, result.skippedWithoutLink);
    TvFeeds.Channel preview = result.channels.get(1);
    assertEquals("4", preview.id);
    assertEquals("https://example.org/poster", preview.programs.get(0).artwork);
  }

  @Test
  public void providerCanStillRejectPreviewAfterPermissionWasGranted() {
    rejectPreview = true;
    TvFeeds.Result result = TvFeeds.read(RuntimeEnvironment.getApplication(), true);
    assertEquals("watch_next", result.channels.get(0).id);
    assertTrue(result.message.contains("ограничивает"));
  }
}
