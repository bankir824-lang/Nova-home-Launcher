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
  @Test
  public void cardFocusOutlineIsASeparateForeground() {
    MainActivity activity = create();
    AppCardView card = new AppCardView(activity, 10, Color.BLUE);
    assertTrue(card.contents.getClipToOutline());
    card.showFocus(true, true, Color.WHITE);
    assertNotNull(card.getForeground());
    assertFalse(card.getClipToOutline());
    card.showFocus(false, true, Color.WHITE);
    assertNull(card.getForeground());
    activity.finish();
  }

  @Test
  public void statusRadiusReachesHalfTheHeightAtOneHundredPercent() {
    assertEquals(0f, StatusBarView.radius(200, 40, 0), 0f);
    assertEquals(20f, StatusBarView.radius(200, 40, 100), 0f);
  }

  @Test
  public void backReturnsFocusToCardsSetting() {
    MainActivity activity = create();
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View root = panel.findViewById(android.R.id.content);
    View home = root.findViewWithTag("root.home");
    assertTrue(home.performClick());
    View cards = panel.getWindow().getDecorView().findViewWithTag("appearance.cards");
    assertTrue(cards.requestFocus());
    assertTrue(cards.performClick());
    panel.onBackPressed();
    assertEquals("appearance.cards", panel.getCurrentFocus().getTag());
    panel.onBackPressed();
    assertEquals("root.home", panel.getCurrentFocus().getTag());
    assertSame(root, panel.findViewById(android.R.id.content));
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void reopeningSettingsReusesPanelAndRestoresFocus() {
    MainActivity activity = create();
    activity.settings();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    View content = panel.findViewById(android.R.id.content);
    View wallpaper = content.findViewWithTag("root.wallpaper");
    assertTrue(wallpaper.requestFocus());
    panel.onBackPressed();
    assertFalse(panel.isShowing());
    activity.settings();
    assertTrue(panel.isShowing());
    assertSame(content, panel.findViewById(android.R.id.content));
    assertSame(wallpaper, panel.getCurrentFocus());
    panel.dismiss();
    activity.finish();
  }

  @Test
  public void repeatedAppearanceChangesKeepTheSamePreviewRoot() {
    MainActivity activity = create();
    ViewGroup windowContent = activity.findViewById(android.R.id.content);
    View preview = windowContent.getChildAt(0);
    activity.settings();
    for (int width = 128; width < 136; width++) activity.setInt("card_width", width);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));
    assertSame(preview, windowContent.getChildAt(0));
    assertEquals(135, activity.getPreferences(0).getInt("card_width", 0));
    ((TvSettingsPanel) ShadowDialog.getLatestDialog()).dismiss();
    activity.finish();
  }

  @Test
  public void novaTvHasSeparateEntryPointsForInstallerTvAndHome() {
    Context context = RuntimeEnvironment.getApplication();
    PackageManager packages = context.getPackageManager();
    for (String category : new String[] {
        Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_HOME
    }) {
      Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category)
          .setPackage(context.getPackageName());
      int flags = Intent.CATEGORY_HOME.equals(category) ? PackageManager.MATCH_DEFAULT_ONLY : 0;
      ResolveInfo resolved = packages.resolveActivity(intent, flags);
      assertNotNull(category, resolved);
      assertEquals(MainActivity.class.getName(), resolved.activityInfo.name);
    }
  }

  private MainActivity create() {
    return create(6);
  }

  private MainActivity create(int count) {
    android.app.Application app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("MainActivity", 0).edit().clear().apply();
    for (int n = 0; n < count; n++) {
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
    MainActivity activity =
        Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    return activity;
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
    shadowOf(android.os.Looper.getMainLooper()).idle();
    View row = panel.getCurrentFocus();
    assertNotNull(row);
    assertEquals("Приложение 1", String.valueOf(row.getContentDescription()));
    assertTrue(row.isFocused());
    assertTrue("Строка должна быть присоединена к окну", row.isAttachedToWindow());
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

  @Test
  public void manyApplicationsUseMultipleRemoteNavigableRows() {
    MainActivity a = create(15);
    View first = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app0"),
        last = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app8");
    assertNotNull(first);
    assertNotNull(last);
    assertNotSame(first.getParent(), last.getParent());
    assertTrue(first.isFocusable());
    assertTrue(last.isFocusable());
    a.finish();
  }

  private void layoutRemoteTarget(View decor, int width) {
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, width, 1080);
    shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  private android.app.AlertDialog contextMenu() {
    Dialog dialog = ShadowDialog.getLatestDialog();
    assertTrue(dialog instanceof android.app.AlertDialog);
    android.app.AlertDialog menu = (android.app.AlertDialog) dialog;
    assertTrue(menu.isShowing());
    android.widget.ListAdapter adapter = menu.getListView().getAdapter();
    boolean favorite = false, move = false;
    for (int n = 0; n < adapter.getCount(); n++) {
      String item = String.valueOf(adapter.getItem(n));
      assertNotEquals("Открыть", item);
      favorite |= item.equals("Добавить в избранное");
      move |= item.equals("Переместить приложение");
    }
    assertTrue(favorite);
    assertTrue(move);
    return menu;
  }

  @Test
  public void mainCardShortOkLaunchesExactlyOnce() {
    MainActivity a = create();
    layoutRemoteTarget(a.getWindow().getDecorView(), 1920);
    View card = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app0");
    card.setFocusableInTouchMode(true);
    card.requestFocus();
    assertTrue("Карточка должна получить фокус пульта", card.hasFocus());
    assertTrue(card.isAttachedToWindow());
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    assertNotNull(shadowOf(a).getNextStartedActivity());
    assertNull(shadowOf(a).getNextStartedActivity());
    a.finish();
  }

  @Test
  public void mainCardHoldingOpensMenuAndStillAllowsFavorites() {
    MainActivity a = create();
    layoutRemoteTarget(a.getWindow().getDecorView(), 1920);
    View card = a.getWindow().getDecorView().findViewWithTag("app_order:test.tv.app0");
    card.setFocusableInTouchMode(true);
    card.requestFocus();
    assertTrue("Карточка должна получить фокус пульта", card.hasFocus());
    assertTrue(card.isAttachedToWindow());
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    shadowOf(android.os.Looper.getMainLooper())
        .idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
    android.app.AlertDialog menu = contextMenu();
    card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    assertNull(shadowOf(a).getNextStartedActivity());
    menu.getListView().performItemClick(null, 0, 0);
    assertTrue(a.favoriteIds().contains("test.tv.app0"));
    menu.dismiss();
    a.finish();
  }

  @Test
  public void libraryLongEnterDoesNotLaunchAfterContextMenu() {
    MainActivity a = create();
    a.openLibrary();
    TvSettingsPanel panel = (TvSettingsPanel) ShadowDialog.getLatestDialog();
    layoutRemoteTarget(panel.getWindow().getDecorView(), 826);
    View row = panel.getCurrentFocus();
    panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    shadowOf(android.os.Looper.getMainLooper())
        .idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
    android.app.AlertDialog menu = contextMenu();
    row.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    assertNull(shadowOf(a).getNextStartedActivity());
    menu.dismiss();
    panel.dismiss();
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
