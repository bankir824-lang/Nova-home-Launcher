package ru.novatv.launcher;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.*;

import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvInterfaceTest {
  private MainActivity create() {
    android.app.Application app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("MainActivity", 0).edit().clear().apply();
    for (int n = 0; n < 6; n++) {
      ResolveInfo info = new ResolveInfo();
      info.activityInfo = new ActivityInfo();
      info.activityInfo.packageName = "test.tv.app" + n;
      info.activityInfo.name = "test.tv.app" + n + ".MainActivity";
      info.activityInfo.applicationInfo = new ApplicationInfo();
      info.activityInfo.applicationInfo.packageName = info.activityInfo.packageName;
      info.nonLocalizedLabel = "Приложение " + (n + 1);
      info.activityInfo.enabled = true;
      info.activityInfo.applicationInfo.enabled = true;
      info.activityInfo.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
      PackageInfo fixture = new PackageInfo();
      fixture.packageName = info.activityInfo.packageName;
      fixture.applicationInfo = info.activityInfo.applicationInfo;
      fixture.activities = new ActivityInfo[] {info.activityInfo};
      shadowOf(app.getPackageManager()).installPackage(fixture);
      info.activityInfo.exported = true;
      Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
      shadowOf(app.getPackageManager()).addResolveInfoForIntent(launch, info);
    }
    return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
  }

  @Test
  public void hiddenOnlyLibraryDoesNotContainVisibleApps() {
    MainActivity a = create();
    a.getPreferences(0)
        .edit()
        .putString("hidden", "test.tv.app0")
        .putString("favorite_order", "test.tv.app0")
        .apply();
    assertTrue(a.visibleApps(a.getFavorites(), false).isEmpty());
    assertEquals(1, a.visibleApps(a.getFavorites(), true).size());
    a.finish();
  }

  @Test
  public void settingsOpenAndNestedPageBackKeepsPanelOpen() throws Exception {
    MainActivity a = create();
    a.settings();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    Dialog panel = ShadowDialog.getLatestDialog();
    assertTrue(panel instanceof TvSettingsPanel);
    TvSettingsPanel settings = (TvSettingsPanel) panel;
    settings.open(a::cardsSettings);
    assertTrue(settings.isShowing());
    save(a, settings, "settings-cards.png");
    settings.onBackPressed();
    assertTrue(settings.isShowing());
    save(a, settings, "settings-root.png");
    settings.dismiss();
    assertFalse(settings.isShowing());
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(220));
    save(a, null, "home.png");
    a.finish();
  }

  @Test
  public void remoteSliderUpdatesPreferenceAndRetainsFocus() {
    MainActivity a = create();
    a.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    panel.open(
        p ->
            p.slider(
                "▭",
                "Размер карточки",
                80,
                240,
                126,
                " dp",
                value -> a.setInt("card_width", value)));
    View focus = panel.getCurrentFocus();
    assertTrue(focus instanceof SeekBar);
    focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
    assertEquals(127, a.getPreferences(0).getInt("card_width", 0));
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
    assertSame(focus, panel.getCurrentFocus());
    panel.dismiss();
    a.finish();
  }

  @Test
  public void libraryEnterLaunchesExactlyOnce() {
    MainActivity a = create();
    a.openLibrary();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View decor = panel.getWindow().getDecorView();
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(826, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, 826, 1080);
    View row = panel.getCurrentFocus();
    assertNotNull(row);
    assertEquals("Приложение 1", String.valueOf(row.getContentDescription()));
    assertTrue(row.isFocused());
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    Intent launched = shadowOf(a).getNextStartedActivity();
    assertNotNull(
        "Последнее сообщение: " + org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),
        launched);
    assertEquals("test.tv.app0", launched.getComponent().getPackageName());
    assertNull(shadowOf(a).getNextStartedActivity());
    a.finish();
  }

  @Test
  public void protectedLibraryWaitsForPinInsteadOfOpeningNullPanel() {
    MainActivity a = create();
    a.getPreferences(0)
        .edit()
        .putBoolean("pin_settings", true)
        .putString("pin_hash", "test-only")
        .apply();
    a.openLibrary();
    Dialog dialog = ShadowDialog.getLatestDialog();
    assertNotNull(dialog);
    assertTrue(dialog.isShowing());
    assertFalse(dialog instanceof TvSettingsPanel);
    dialog.dismiss();
    a.finish();
  }

  private void save(MainActivity activity, Dialog panel, String name) throws Exception {
    View view = activity.getWindow().getDecorView();
    int width = 1920, height = 1080;
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    view.draw(canvas);
    if (panel != null) {
      View overlay = panel.getWindow().getDecorView();
      int panelWidth = (int) (width * 0.43f);
      overlay.measure(
          View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      overlay.layout(0, 0, panelWidth, height);
      canvas.save();
      canvas.translate(width - panelWidth, 0);
      overlay.draw(canvas);
      canvas.restore();
    }
    File folder = new File("build/reports/ui");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, name))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }
}
