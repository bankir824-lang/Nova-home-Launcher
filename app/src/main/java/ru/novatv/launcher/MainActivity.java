package ru.novatv.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
  private LinearLayout page, content, menu;
  private TextView clock, notificationCounter;
  private boolean resumed;
  private final Runnable redraw =
      () -> {
        if (!isDestroyed()) applyPendingAppearance();
      };
  private final Set<String> pendingAppearance = new HashSet<>();
  private final Map<String, View> previewRows = new LinkedHashMap<>();
  private ScrollView launcherScroll;
  private View launcherHeader;
  private View weatherRow;
  private View wallpaperShade;
  private ImageView launcherWallpaper;
  private String previewRow;
  private int homeScrollBeforePreview;
  private String focusBeforePreview;
  private boolean settingsUnlocked;
  private int pinFailures;
  private long pinBlockedUntil;
  private final java.util.concurrent.ExecutorService authWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private View launcherView;
  private Drawable currentWallpaper;
  private android.media.MediaPlayer wallpaperPlayer;
  private LinearLayout statusBar;
  private TvSettingsPanel settingsPanel;
  private boolean previewOpen;
  private int activeRows;
  private final List<View> appRows = new ArrayList<>();
  private TvFeeds.Result feeds;
  private static final String TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
  private static final int REQUEST_TV_LISTINGS = 3201;
  private boolean lastTvListingsGrant;
  private boolean feedsLoading;
  private long feedsLoaded;
  private final java.util.concurrent.ExecutorService artworkWorker =
      java.util.concurrent.Executors.newFixedThreadPool(2);
  private final Map<ImageView, String> artworkViews = new IdentityHashMap<>();
  private final Set<ImageView> artworkLoading =
      Collections.newSetFromMap(new IdentityHashMap<ImageView, Boolean>());
  private final Set<String> failedArtwork = new HashSet<>();
  private final android.util.LruCache<String, Bitmap> artworkCache =
      new android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap value) {
          return value.getAllocationByteCount();
        }
      };
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ArrayList<App> installed = new ArrayList<>();
  private int theme = 0;
  private String movingPackage, movingOrder;
  private boolean movingFavorites;
  private TextView weather;
  private final WeatherRequests weatherRequests = new WeatherRequests();
  private final java.util.concurrent.ExecutorService weatherWorker =
      java.util.concurrent.Executors.newFixedThreadPool(2);
  private java.util.concurrent.Future<?> weatherTask;
  private long retryWeatherAfter;
  private boolean showHidden;
  private String movingRowKey;
  private String returnFocusTag;
  private int currentScreen, movingScreen;
  private final Map<String, List<App>> fullRows = new HashMap<>();
  private final java.util.concurrent.ExecutorService worker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          updateClock();
          handler.postDelayed(this, 30000);
        }
      };
  private final BroadcastReceiver packages =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          refreshApps();
          if (content != null) home();
        }
      };
  private final int WHITE = 0xfff7f9ff, MUTED = 0xffb8c7df;

  static class App {
    String pkg, activity, label;
    Drawable icon, banner;
    boolean tv;

    App(String p, String a, String l, Drawable d) {
      pkg = p;
      activity = a;
      label = l;
      icon = d;
    }
  }

  int dp(float v) {
    return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
  }

  GradientDrawable shape(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  GradientDrawable background() {
    int[][] colors = {
      {0xff10172b, 0xff1d365a, 0xff090f20},
      {0xff171126, 0xff4c284b, 0xff0c1227},
      {0xff092c34, 0xff164b57, 0xff101c2c}
    };
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[theme]);
    d.setCornerRadius(0);
    return d;
  }

  TextView label(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(color);
    t.setTextSize(size);
    t.setFontFeatureSettings("kern");
    t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }

  LinearLayout horizontal() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  LinearLayout vertical() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  LinearLayout.LayoutParams lp(int w, int h) {
    return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
  }

  void gap(LinearLayout l, int h) {
    View v = new View(this);
    l.addView(v, lp(1, h));
  }

  void marginAdd(LinearLayout parent, View child, int w, int h, int right) {
    LinearLayout.LayoutParams p = lp(w, h);
    p.rightMargin = dp(right);
    parent.addView(child, p);
  }

  @Override
  protected void attachBaseContext(Context base) {
    android.content.res.Configuration config =
        new android.content.res.Configuration(base.getResources().getConfiguration());
    config.fontScale *= base.getSharedPreferences("MainActivity", 0).getFloat("font_scale", 0.8f);
    super.attachBaseContext(base.createConfigurationContext(config));
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    theme = Math.max(0, Math.min(2, getPreferences(0).getInt("theme", 0)));
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    registerReceiver(packages, filter);
    refreshApps();
    render();
    handler.post(ticker);
    if (b != null && b.getBoolean("settings_open", false)) handler.post(this::settings);
    if (b == null) {
      String auto = getPreferences(0).getString("autostart", "");
      for (App app : installed)
        if (app.pkg.equals(auto)) {
          launch(app);
          break;
        }
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putBoolean("settings_open", settingsPanel != null && settingsPanel.isShowing());
    super.onSaveInstanceState(out);
  }

  void refreshApps() {
    installed.clear();
    PackageManager pm = getPackageManager();
    Map<String, App> map = new LinkedHashMap<>();
    for (String category :
        new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
      Intent i = new Intent(Intent.ACTION_MAIN);
      i.addCategory(category);
      for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
        String pkg = r.activityInfo.packageName;
        if (pkg.equals(getPackageName()) || map.containsKey(pkg)) continue;
        App app = new App(pkg, r.activityInfo.name, r.loadLabel(pm).toString(), r.loadIcon(pm));
        app.tv = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
        app.banner = r.activityInfo.loadBanner(pm);
        map.put(pkg, app);
      }
    }
    installed.addAll(map.values());
    installed.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
    List<String> order = readIds("app_order");
    installed.sort(
        (a, b) ->
            Integer.compare(
                order.contains(a.pkg) ? order.indexOf(a.pkg) : Integer.MAX_VALUE,
                order.contains(b.pkg) ? order.indexOf(b.pkg) : Integer.MAX_VALUE));
  }

  void render() {
    ScrollView sc = new ScrollView(this);
    launcherScroll = sc;
    sc.setFillViewport(true);
    sc.setVerticalScrollBarEnabled(false);
    sc.setHorizontalScrollBarEnabled(false);
    sc.setOnScrollChangeListener(
        (View v, int x, int y, int oldX, int oldY) -> refreshVisibleArtwork());
    releaseWallpaper();
    getWindow().setBackgroundDrawable(background());
    FrameLayout stage = new FrameLayout(this);
    stage.setBackground(background());
    ImageView wallpaperImage = new ImageView(this);
    launcherWallpaper = wallpaperImage;
    wallpaperShade = null;
    wallpaperImage.setScaleType(
        getPreferences(0).getBoolean("wallpaper_fit", false)
            ? ImageView.ScaleType.FIT_CENTER
            : ImageView.ScaleType.CENTER_CROP);
    stage.addView(wallpaperImage, new FrameLayout.LayoutParams(-1, -1));
    String wallpaper = getPreferences(0).getString("wallpaper", "");
    if (!wallpaper.isEmpty()
        && "video".equals(getPreferences(0).getString("wallpaper_type", "image"))) {
      TextureView video = new TextureView(this);
      stage.addView(video, new FrameLayout.LayoutParams(-1, -1));
      video.setSurfaceTextureListener(
          new TextureView.SurfaceTextureListener() {
            private android.media.MediaPlayer surfacePlayer;

            public void onSurfaceTextureAvailable(
                android.graphics.SurfaceTexture texture, int w, int h) {
              try {
                android.media.MediaPlayer player = new android.media.MediaPlayer();
                wallpaperPlayer = player;
                surfacePlayer = player;
                player.setDataSource(MainActivity.this, android.net.Uri.parse(wallpaper));
                android.view.Surface surface = new android.view.Surface(texture);
                player.setSurface(surface);
                surface.release();
                player.setOnVideoSizeChangedListener(
                    (p, vw, vh) -> {
                      if (vw > 0 && vh > 0 && video.getWidth() > 0 && video.getHeight() > 0) {
                        float viewRatio = (float) video.getWidth() / video.getHeight(),
                            sourceRatio = (float) vw / vh;
                        Matrix transform = new Matrix();
                        transform.setScale(
                            sourceRatio > viewRatio ? sourceRatio / viewRatio : 1f,
                            sourceRatio < viewRatio ? viewRatio / sourceRatio : 1f,
                            video.getWidth() / 2f,
                            video.getHeight() / 2f);
                        video.setTransform(transform);
                      }
                    });
                player.setLooping(true);
                player.setVolume(0, 0);
                player.setOnPreparedListener(
                    p -> {
                      if (resumed && !isDestroyed() && !isFinishing()) {
                        p.start();
                      }
                    });
                player.setOnErrorListener(
                    (p, what, extra) -> {
                      getPreferences(0).edit().remove("wallpaper").apply();
                      p.release();
                      if (wallpaperPlayer == p) wallpaperPlayer = null;
                      Toast.makeText(
                              MainActivity.this,
                              "Видеообои недоступны. Выберите другой файл",
                              Toast.LENGTH_LONG)
                          .show();
                      return true;
                    });
                player.prepareAsync();
              } catch (Exception e) {
                releaseWallpaper();
                Toast.makeText(MainActivity.this, "Не удалось открыть видеообои", Toast.LENGTH_LONG)
                    .show();
              }
            }

            public void onSurfaceTextureSizeChanged(
                android.graphics.SurfaceTexture t, int w, int h) {}

            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture t) {
              if (surfacePlayer != null) {
                try {
                  surfacePlayer.release();
                } catch (Exception ignored) {
                }
                if (wallpaperPlayer == surfacePlayer) wallpaperPlayer = null;
                surfacePlayer = null;
              }
              return true;
            }

            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture t) {}
          });
      View dim = new View(this);
      wallpaperShade = dim;
      dim.setBackgroundColor(Color.BLACK);
      dim.setAlpha(getPreferences(0).getInt("wallpaper_dim", 55) / 100f);
      stage.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    } else if (!wallpaper.isEmpty())
      try {
        android.graphics.ImageDecoder.Source source =
            android.graphics.ImageDecoder.createSource(
                getContentResolver(), android.net.Uri.parse(wallpaper));
        Drawable d =
            android.graphics.ImageDecoder.decodeDrawable(
                source,
                (decoder, info, src) -> {
                  float scale =
                      Math.min(
                          1f,
                          1920f / Math.max(info.getSize().getWidth(), info.getSize().getHeight()));
                  decoder.setTargetSize(
                      Math.max(1, (int) (info.getSize().getWidth() * scale)),
                      Math.max(1, (int) (info.getSize().getHeight() * scale)));
                });
        d.setColorFilter(
            (Appearance.clamp(getPreferences(0).getInt("wallpaper_dim", 55), 0, 90) * 255 / 100)
                << 24,
            PorterDuff.Mode.SRC_ATOP);
        currentWallpaper = d;
        wallpaperImage.setImageDrawable(d);
        if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
      } catch (Exception e) {
        getPreferences(0).edit().remove("wallpaper").apply();
      }
    stage.addView(sc, new FrameLayout.LayoutParams(-1, -1));
    launcherView = stage;
    setContentView(stage);
    page = vertical();
    applyLauncherPadding();
    sc.addView(page);
    header();
    gap(page, 6);
    weatherPanel();
    if (getPreferences(0).getBoolean("show_banner", false)) banner();
    gap(page, 14);
    content = vertical();
    page.addView(content);
    home();
    if (getPreferences(0).getBoolean("keep_screen_on", false))
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (previewOpen) applyPreviewTransform(true, false);
  }

  private void applyLauncherPadding() {
    page.setPadding(
        dp(getPreferences(0).getInt("margin_left", 28)),
        dp(getPreferences(0).getInt("margin_top", 20)),
        dp(getPreferences(0).getInt("margin_right", 28)),
        dp(24));
  }

  int accent() {
    return getPreferences(0).getInt("accent_color", 0xffff94b2);
  }

  void header() {
    LinearLayout bar = horizontal();
    launcherHeader = bar;
    page.addView(bar, 0, lp(-1, 38));
    TextView logo = label("Nova home Launcher", 22, WHITE);
    logo.setTypeface(null, Typeface.BOLD);
    bar.addView(logo, new LinearLayout.LayoutParams(0, -1, 1));
    menu = horizontal();
    bar.addView(menu);
    nav("Приложения", R.drawable.nova_apps, this::openLibrary);
    nav("Меню", R.drawable.nova_menu, this::settings);
    notificationCounter = null;
    StatusBarView status = new StatusBarView(this);
    statusBar = status;
    if (getPreferences(0).getInt("status_alignment", 1) == 0) bar.addView(status, 1, lp(-2, 38));
    else bar.addView(status, lp(-2, 38));
    if (getPreferences(0).getBoolean("show_network", true)) {
      String network = networkStatus();
      int icon = "Ethernet".equals(network) ? R.drawable.nova_ethernet
          : "Нет сети".equals(network) ? R.drawable.nova_offline : R.drawable.nova_wifi;
      status.item(network, network, icon, false);
    }
    if (getPreferences(0).getBoolean("show_notifications", false) && notificationGranted()) {
      notificationCounter = status.item("", "Уведомления", R.drawable.nova_bell, true);
      notificationCounter.setFocusable(true);
      notificationCounter.setOnClickListener(
          v -> {
            openPanelPage(this::notificationSettings);
          });
    }
    clock = getPreferences(0).getBoolean("show_clock", true)
        ? status.item("", "Время", R.drawable.nova_clock, true) : null;
    if (clock != null) {
      if (getPreferences(0).contains("clock_size"))
        clock.setTextSize(getPreferences(0).getInt("clock_size", 17));
      if (getPreferences(0).contains("clock_color"))
        clock.setTextColor(getPreferences(0).getInt("clock_color", WHITE));
      if (getPreferences(0).getBoolean("clock_shadow", false))
        clock.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
    }
    statusBar.setVisibility(
        getPreferences(0).getBoolean("show_status", true) ? View.VISIBLE : View.GONE);
    updateClock();
  }

  String networkStatus() {
    try {
      android.net.ConnectivityManager cm =
          (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
      android.net.NetworkCapabilities n = cm.getNetworkCapabilities(cm.getActiveNetwork());
      return n == null
          ? "Нет сети"
          : n.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
              ? "Ethernet"
              : n.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                  ? "Wi-Fi"
                  : "Сеть";
    } catch (Exception e) {
      return "Сеть";
    }
  }

  void updateClock() {
    if (notificationCounter != null) {
      int count = visibleNotifications().size();
      notificationCounter.setText(getPreferences(0).getInt("status_label_mode", 2) == 1
          ? "Уведомления " + count : String.valueOf(count));
      notificationCounter.setContentDescription("Уведомления: " + count);
      notificationCounter.setVisibility(
          count == 0 && getPreferences(0).getBoolean("hide_empty_notifications", true)
              ? View.GONE
              : View.VISIBLE);
    }
    if (weather != null) loadWeather();
    if (clock != null)
      clock.setText(
          new SimpleDateFormat(
                  getPreferences(0).getString("clock_format", "HH:mm"), new Locale("ru", "RU"))
              .format(new Date()));
  }

  @Override
  protected void onDestroy() {
    if (settingsPanel != null) settingsPanel.dismiss();
    handler.removeCallbacksAndMessages(null);
    releaseWallpaper();
    authWorker.shutdownNow();
    worker.shutdownNow();
    weatherWorker.shutdownNow();
    artworkWorker.shutdownNow();
    artworkCache.evictAll();
    unregisterReceiver(packages);
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    boolean tvListingsGranted = hasTvListingsAccess();
    if (tvListingsGranted != lastTvListingsGrant) {
      lastTvListingsGrant = tvListingsGranted;
      feedsLoaded = 0;
      feeds = null;
      loadChannels();
    }
    if (currentWallpaper instanceof AnimatedImageDrawable)
      ((AnimatedImageDrawable) currentWallpaper).start();
    if (wallpaperPlayer != null)
      try {
        wallpaperPlayer.start();
      } catch (IllegalStateException ignored) {
      }
    if (content != null) {
      refreshApps();
      home();
      if (returnFocusTag == null && getPreferences(0).getBoolean("remember_focus", true))
        returnFocusTag = getPreferences(0).getString("last_focus", null);
      restoreAppFocus();
    }
  }

  @Override
  protected void onPause() {
    resumed = false;
    if (currentWallpaper instanceof AnimatedImageDrawable)
      ((AnimatedImageDrawable) currentWallpaper).stop();
    if (wallpaperPlayer != null)
      try {
        wallpaperPlayer.pause();
      } catch (IllegalStateException ignored) {
      }
    super.onPause();
  }

  void releaseWallpaper() {
    if (currentWallpaper instanceof AnimatedImageDrawable)
      ((AnimatedImageDrawable) currentWallpaper).stop();
    currentWallpaper = null;
    if (wallpaperPlayer != null) {
      try {
        wallpaperPlayer.release();
      } catch (Exception ignored) {
      }
      wallpaperPlayer = null;
    }
  }

  void restoreAppFocus() {
    if (returnFocusTag == null) {
      ArrayList<View> focusables = content.getFocusables(View.FOCUS_FORWARD);
      if (!focusables.isEmpty()) focusables.get(0).post(() -> focusables.get(0).requestFocus());
      return;
    }
    View card = content.findViewWithTag(returnFocusTag);
    if (card != null) card.post(() -> card.requestFocus());
    returnFocusTag = null;
  }

  void nav(String s, int icon, Runnable r) {
    int mode = getPreferences(0).getInt("status_label_mode", 2);
    TextView t = label(mode == 0 || (mode == 2 && icon == R.drawable.nova_menu) ? "" : s,
        14, WHITE);
    t.setContentDescription(s);
    if (mode != 1) {
      Drawable image = getDrawable(icon).mutate();
      image.setBounds(0, 0, dp(18), dp(18));
      t.setCompoundDrawablesRelative(image, null, null, null);
      t.setCompoundDrawablePadding(dp(5));
    }
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(10), 0, dp(10), 0);
    t.setBackground(shape(0x99172131, 14));
    t.setFocusable(true);
    t.setOnClickListener(v -> r.run());
    t.setOnFocusChangeListener(
        (v, f) -> {
          t.setBackground(shape(f ? 0xffd9eaff : 0x99172131, 14));
          t.setTextColor(f ? 0xff10182a : WHITE);
        });
    marginAdd(menu, t, -2, 42, 9);
  }

  void banner() {
    LinearLayout b = vertical();
    b.setPadding(dp(30), dp(20), dp(25), dp(22));
    b.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {0xff172b52, 0xff394d78, 0xff192342}));
    page.addView(b, lp(-1, 155));
    TextView eyebrow = label("ВАШ ДОМАШНИЙ ЭКРАН", 13, 0xffbcd9ff);
    b.addView(eyebrow);
    gap(b, 5);
    TextView title = label("Добро пожаловать в Nova home Launcher", 29, WHITE);
    title.setTypeface(null, Typeface.BOLD);
    b.addView(title);
    gap(b, 4);
    b.addView(label("Любимые приложения и развлечения — в одном месте", 16, WHITE));
    gap(b, 8);
    b.addView(
        label("✦  Быстрый доступ     •     Избранное     •     Темы оформления", 13, 0xffc8d8ef));
  }

  void clear(String title) {
    content.removeAllViews();
    fullRows.clear();
    artworkViews.clear();
    artworkLoading.clear();
    activeRows = 0;
    appRows.clear();
    previewRows.clear();
    TextView t = label(title, 22, WHITE);
    t.setTypeface(null, Typeface.BOLD);
    if (!title.isEmpty()) {
      content.addView(t);
      gap(content, 9);
    }
  }

  void home() {
    currentScreen = 0;
    showHidden = false;
    clear(movingPackage == null ? "" : "Перемещение: стрелки · OK — сохранить · Назад — отменить");
    if (getPreferences(0).getBoolean("show_favorites", true)) {

      List<App> fav = visibleApps(getFavorites(), false);
      if (!fav.isEmpty()) {
        rowTitle("☆", "Избранное");
        rowApps(getFavorites(), true);
      }
    }
    if (getPreferences(0).getBoolean("show_all", true)) {
      List<App> tv = new ArrayList<>(), mobile = new ArrayList<>();
      for (App app : installed) (app.tv ? tv : mobile).add(app);
      if (!visibleApps(tv, false).isEmpty()) {
        rowTitle("▣", "ТВ приложения");
        rowApps(tv, false, "app_order");
      }
      if (getPreferences(0).getBoolean("show_mobile", true)
          && !visibleApps(mobile, false).isEmpty()) {
        rowTitle("▯", "Мобильные приложения");
        rowApps(mobile, false, "mobile_order");
      }
    }
    for (String category : readIds("categories")) {
      List<App> apps = new ArrayList<>();
      for (App app : installed)
        if (category.equals(getPreferences(0).getString("category_" + app.pkg, ""))) apps.add(app);
      if (readIds("hidden_categories").contains(category)) continue;
      rowTitle("≡", category);
      if (!visibleApps(apps, false).isEmpty()) rowApps(apps, false, "category_order_" + category);
    }
    if (getPreferences(0).getBoolean("show_inputs", false) && !availableTvInputs().isEmpty()) {
      rowTitle("⇥", "Источники сигнала");
      LinearLayout inputs = horizontal();
      content.addView(inputs);
      action(inputs, "Выбрать вход телевизора", this::showInputs);
    }
    if (getPreferences(0).getBoolean("show_channels", false)) renderChannels();
    loadWeather();
  }

  void rowTitle(String icon, String title) {
    gap(content, getPreferences(0).getInt("row_gap", 12));
    if (getPreferences(0).getBoolean("show_titles", true)) {
      TextView heading =
          label(
              (getPreferences(0).getBoolean("show_category_icons", true) ? icon + "  " : "")
                  + title,
              17,
              WHITE);
      content.addView(heading);
      gap(content, 6);
    }
  }

  void allApps() {
    currentScreen = 1;
    clear(showHidden ? "Скрытые приложения" : "Все приложения");
    if (showHidden) {
      List<App> hidden = visibleApps(installed, true);
      if (hidden.isEmpty()) content.addView(label("Нет скрытых приложений", 16, MUTED));
      else rowApps(hidden);
    } else rowApps(installed);
  }

  void favorites() {
    currentScreen = 2;
    showHidden = false;
    clear("Избранное");
    List<App> fav = visibleApps(getFavorites(), false);
    if (fav.isEmpty())
      content.addView(
          label("Удерживайте OK на карточке приложения, чтобы добавить его сюда", 16, MUTED));
    else rowApps(getFavorites(), true);
  }

  List<String> readIds(String key) {
    String raw = getPreferences(0).getString(key, "");
    return new ArrayList<>(
        raw.isEmpty() ? Collections.emptyList() : Arrays.asList(raw.split("\\|")));
  }

  List<String> favoriteIds() {
    return readIds("favorite_order");
  }

  void saveFavorites(List<String> ids) {
    getPreferences(0)
        .edit()
        .putString("favorite_order", android.text.TextUtils.join("|", ids))
        .apply();
  }

  List<App> getFavorites() {
    List<App> list = new ArrayList<>();
    for (String id : favoriteIds())
      for (App a : installed)
        if (a.pkg.equals(id)) {
          list.add(a);
          break;
        }
    return list;
  }

  void toggle(App a) {
    List<String> ids = favoriteIds();
    if (ids.contains(a.pkg)) ids.remove(a.pkg);
    else ids.add(a.pkg);
    saveFavorites(ids);
    if (content != null) home();
  }

  void action(LinearLayout parent, String s, Runnable r) {
    TextView t = label(s, 16, WHITE);
    t.setGravity(Gravity.CENTER);
    t.setBackground(shape(0xdd182334, 12));
    t.setFocusable(true);
    t.setOnClickListener(v -> r.run());
    t.setOnFocusChangeListener(
        (v, f) -> {
          t.setBackground(shape(f ? 0xffd9eaff : 0xdd182334, 12));
          t.setTextColor(f ? 0xff0b1425 : WHITE);
        });
    LinearLayout.LayoutParams params = lp(-2, 38);
    params.rightMargin = dp(8);
    params.bottomMargin = dp(8);
    t.setPadding(dp(14), 0, dp(14), 0);
    parent.addView(t, params);
  }

  List<App> visibleApps(List<App> apps, boolean hiddenOnly) {
    List<App> result = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    for (App app : apps) ids.add(app.pkg);
    List<String> selected = AppVisibility.select(ids, readIds("hidden"), hiddenOnly);
    for (App app : apps) if (selected.contains(app.pkg)) result.add(app);
    return result;
  }

  void rowApps(List<App> list) {
    rowApps(list, false);
  }

  void rowApps(List<App> list, boolean favoritesRow) {
    rowApps(list, favoritesRow, favoritesRow ? "favorite_order" : "app_order");
  }

  void rowApps(List<App> input, boolean favoritesRow, String rowKey) {
    fullRows.put(rowKey, new ArrayList<>(input));
    List<App> list = new ArrayList<>();
    for (App app : input) if (showHidden || !readIds("hidden").contains(app.pkg)) list.add(app);
    if (!rowKey.equals("app_order") && !rowKey.equals("favorite_order")) {
      List<String> order = readIds(rowKey);
      list.sort(
          (a, b) ->
              Integer.compare(
                  order.contains(a.pkg) ? order.indexOf(a.pkg) : Integer.MAX_VALUE,
                  order.contains(b.pkg) ? order.indexOf(b.pkg) : Integer.MAX_VALUE));
    }
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    previewRows.put(rowKey, scroll);
    scroll.setClipToPadding(false);
    scroll.setHorizontalScrollBarEnabled(false);
    int width = Appearance.clamp(getPreferences(0).getInt("card_width", 126), 80, 240);
    boolean titles = getPreferences(0).getBoolean("card_titles", false);
    int ratio =
        getPreferences(0)
            .getInt(
                rowKey.equals("mobile_order") ? "mobile_ratio" : "card_ratio",
                rowKey.equals("mobile_order") ? 1 : 0);
    int height = Appearance.cardHeight(width, ratio, titles);
    int availableDp =
        (int)
                (getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density)
            - getPreferences(0).getInt("margin_left", 28)
            - getPreferences(0).getInt("margin_right", 28);
    final int columns =
        Math.max(1, availableDp / (width + getPreferences(0).getInt("card_gap", 10)));
    final int maxRows =
        favoritesRow ? 1 : Appearance.clamp(getPreferences(0).getInt("grid_rows", 3), 1, 3);
    int rows = Math.min(maxRows, Math.max(1, (list.size() + columns - 1) / columns));
    int focusSpace = Math.max(6, (int) Math.ceil(Math.max(width, height) * 0.03f) + 2);
    int rowHeight = height + 2 * focusSpace;
    content.addView(scroll, lp(-1, rowHeight * rows + 6));
    appRows.add(scroll);
    LinearLayout grid = vertical();
    grid.setClipChildren(false);
    grid.setClipToPadding(false);
    scroll.addView(grid);
    List<LinearLayout> gridRows = new ArrayList<>();
    for (int n = 0; n < rows; n++) {
      LinearLayout line = horizontal();
      line.setClipChildren(false);
      line.setClipToPadding(false);
      line.setPadding(dp(focusSpace), dp(focusSpace), dp(focusSpace), dp(focusSpace));
      grid.addView(line, lp(-2, rowHeight));
      gridRows.add(line);
    }
    int cardIndex = 0;
    for (App a : list) {
      if (!showHidden && readIds("hidden").contains(a.pkg)) continue;
      LinearLayout row = gridRows.get((cardIndex++ / columns) % rows);
      final int cardWidth = width;
      final int radius = getPreferences(0).getInt(a.tv ? "card_radius" : "mobile_radius", 10);
      final int cardColor = getPreferences(0).getInt("card_color", 0xdd222c40);
      AppCardView card = new AppCardView(this, radius, cardColor);
      card.setTag(rowKey + ":" + a.pkg);
      card.setContentDescription(a.label + ". OK — запуск, удержание OK — меню");
      card.setOnFocusChangeListener(
          (v, f) -> {
            card.showFocus(f, getPreferences(0).getBoolean("focus_zoom", true), accent());
            if (f) {
              if (getPreferences(0).getBoolean("pin_row", false))
                ((ScrollView) page.getParent())
                    .smoothScrollTo(0, content.getTop() + scroll.getTop() - dp(72));
              for (View appRow : appRows)
                appRow
                    .animate()
                    .alpha(
                        appRow == scroll
                            ? 1
                            : getPreferences(0).getInt("inactive_opacity", 75) / 100f)
                    .setDuration(150)
                    .start();
              if (getPreferences(0).getBoolean("remember_focus", true))
                getPreferences(0).edit().putString("last_focus", rowKey + ":" + a.pkg).apply();
            }
          });
      marginAdd(row, card, cardWidth, height, getPreferences(0).getInt("card_gap", 10));
      boolean useBanner = a.tv && a.banner != null
          && getPreferences(0).getBoolean("use_banners", true);
      card.addArtwork(useBanner ? a.banner : a.icon, height - (titles ? 24 : 0), useBanner, !a.tv);
      TextView name = label((favoriteIds().contains(a.pkg) ? "★ " : "") + a.label, 15, WHITE);
      name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER);
      if (titles) card.contents.addView(name, lp(cardWidth - 8, 24));
      card.setOnClickListener(
          v -> {
            if (movingPackage != null) finishMove();
            else {
              returnFocusTag = rowKey + ":" + a.pkg;
              launch(a);
            }
          });
      card.setOnLongClickListener(
          v -> {
            contextMenu(a, favoritesRow, rowKey);
            return true;
          });
      RemoteOkHandler remote = RemoteOkHandler.bind(card);
      card.setOnKeyListener(
          (v, key, event) -> {
            if (movingPackage != null
                && movingPackage.equals(a.pkg)
                && event.getAction() == KeyEvent.ACTION_DOWN
                && (key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_DPAD_UP
                    || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
              remote.cancel();
              int index = list.indexOf(a);
              int target =
                  Appearance.neighbor(
                      index,
                      list.size(),
                      columns,
                      rows,
                      key == KeyEvent.KEYCODE_DPAD_LEFT
                          ? -1
                          : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0,
                      key == KeyEvent.KEYCODE_DPAD_UP
                          ? -1
                          : key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0);
              if (target >= 0) reorder(a, rowKey, list, target - index);
              return true;
            }
            return remote.onKey(v, key, event);
          });
      if (a.pkg.equals(movingPackage) && rowKey.equals(movingRowKey)) card.requestFocus();
    }
  }

  void contextMenu(App a, boolean fav, String rowKey) {
    if (movingPackage != null) return;
    String[] items = {
      favoriteIds().contains(a.pkg) ? "Убрать из избранного" : "Добавить в избранное",
      "Переместить приложение",
      "Настройки приложения",
      "Информация",
      "Удалить приложение",
      "Категория",
      readIds("hidden").contains(a.pkg) ? "Показать приложение" : "Скрыть приложение"
    };
    new NovaDialog(this)
        .setTitle(a.label)
        .setItems(
            items,
            (d, n) -> {
              switch (n) {
                case 0:
                  toggle(a);
                  break;
                case 1:
                  if (showHidden) {
                    Toast.makeText(
                            this,
                            "Сначала покажите приложение, затем переместите его",
                            Toast.LENGTH_LONG)
                        .show();
                    break;
                  }
                  if (settingsPanel != null && settingsPanel.isShowing()) {
                    settingsPanel.dismiss();
                    showHidden = false;
                    allApps();
                    currentScreen = 1;
                  }
                  movingPackage = a.pkg;
                  movingFavorites = fav;
                  movingRowKey = rowKey;
                  movingScreen = currentScreen;
                  movingOrder = getPreferences(0).getString(rowKey, "");
                  View moved = content.findViewWithTag(movingRowKey + ":" + a.pkg);
                  if (moved != null) moved.requestFocus();
                  Toast.makeText(
                          this,
                          "Стрелки — перемещение, OK — сохранить, Назад — отменить",
                          Toast.LENGTH_LONG)
                      .show();
                  break;
                case 2:
                  safeStart(
                      new Intent(
                          android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          android.net.Uri.parse("package:" + a.pkg)));
                  break;
                case 3:
                  appInfo(a);
                  break;
                case 5:
                  chooseCategory(a);
                  break;
                case 6:
                  List<String> hidden = readIds("hidden");
                  if (!hidden.remove(a.pkg)) hidden.add(a.pkg);
                  getPreferences(0)
                      .edit()
                      .putString("hidden", android.text.TextUtils.join("|", hidden))
                      .apply();
                  home();
                  if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.refresh();
                  break;
                case 4:
                  safeStart(
                      new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + a.pkg)));
                  break;
              }
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  List<String> appIds() {
    List<String> ids = new ArrayList<>();
    for (App a : installed) ids.add(a.pkg);
    return ids;
  }

  void reorder(App a, String rowKey, List<App> rowApps, int delta) {
    List<String> visible = new ArrayList<>();
    for (App app : rowApps) visible.add(app.pkg);
    List<String> ids = new ArrayList<>();
    for (App app : fullRows.get(rowKey)) ids.add(app.pkg);
    List<String> stored = readIds(rowKey);
    ids.sort(
        (x, y) ->
            Integer.compare(
                stored.contains(x) ? stored.indexOf(x) : Integer.MAX_VALUE,
                stored.contains(y) ? stored.indexOf(y) : Integer.MAX_VALUE));
    if (!AppOrder.moveVisible(ids, visible, a.pkg, delta)) return;
    getPreferences(0).edit().putString(rowKey, android.text.TextUtils.join("|", ids)).apply();
    refreshApps();
    renderMoveScreen();
  }

  void renderMoveScreen() {
    if (movingScreen == 1) allApps();
    else if (movingScreen == 2) favorites();
    else home();
  }

  void finishMove() {
    returnFocusTag = movingRowKey + ":" + movingPackage;
    movingPackage = null;
    renderMoveScreen();
    restoreAppFocus();
    Toast.makeText(this, "Порядок сохранён", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onBackPressed() {
    if (movingPackage != null) {
      getPreferences(0).edit().putString(movingRowKey, movingOrder).apply();
      returnFocusTag = movingRowKey + ":" + movingPackage;
      movingPackage = null;
      refreshApps();
      renderMoveScreen();
      restoreAppFocus();
    } else settings();
  }

  @Override
  public boolean onKeyDown(int key, KeyEvent event) {
    if (key == KeyEvent.KEYCODE_MENU) {
      if (event.getRepeatCount() == 0) settings();
      return true;
    }
    return super.onKeyDown(key, event);
  }

  void safeStart(Intent i) {
    try {
      startActivity(i);
    } catch (ActivityNotFoundException | SecurityException e) {
      Toast.makeText(this, "Действие недоступно на этом устройстве", Toast.LENGTH_LONG).show();
    }
  }

  void appInfo(App a) {
    try {
      PackageInfo p = getPackageManager().getPackageInfo(a.pkg, 0);
      new NovaDialog(this)
          .setTitle(a.label)
          .setMessage(
              "Пакет: "
                  + a.pkg
                  + "\nВерсия: "
                  + p.versionName
                  + "\nКод версии: "
                  + p.getLongVersionCode()
                  + "\nСистемное: "
                  + ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? "да" : "нет"))
          .setPositiveButton("Закрыть", null)
          .show();
    } catch (PackageManager.NameNotFoundException e) {
      refreshApps();
      home();
    }
  }

  void launch(App a) {
    guardApp(a.pkg, () -> launchUnchecked(a));
  }

  void guardApp(String pkg, Runnable action) {
    if (getPreferences(0).contains("pin_hash") && readIds("locked_apps").contains(pkg))
      askPin(action);
    else action.run();
  }

  void launchUnchecked(App a) {
    try {
      Intent i = new Intent();
      i.setClassName(a.pkg, a.activity);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    } catch (Exception e) {
      Toast.makeText(this, "Не удалось открыть «" + a.label + "»", Toast.LENGTH_LONG).show();
    }
  }

  void settings() {
    if (getPreferences(0).getBoolean("pin_settings", false)
        && getPreferences(0).contains("pin_hash")
        && !settingsUnlocked) {
      askPin(
          () -> {
            settingsUnlocked = true;
            showSettings();
          });
      return;
    }
    showSettings();
  }

  void showSettings() {
    if (settingsPanel != null && settingsPanel.isShowing()) return;
    if (currentScreen != 0) home();
    settingsPanel = new TvSettingsPanel(this, accent(), this::rootSettings);
    settingsPanel.show();
  }

  void previewSettings(boolean open) {
    if (previewOpen == open) return;
    previewOpen = open;
    if (open) {
      homeScrollBeforePreview = launcherScroll.getScrollY();
      View focused = launcherScroll.findFocus();
      focusBeforePreview = focused != null && focused.getTag() instanceof String
          ? (String) focused.getTag() : null;
      previewRow = null;
    }
    if (!open) settingsUnlocked = false;
    if (!open && content != null) {
      returnFocusTag =
          getPreferences(0).getBoolean("remember_focus", true)
              ? (focusBeforePreview != null
                  ? focusBeforePreview : getPreferences(0).getString("last_focus", null))
              : null;
      launcherScroll.scrollTo(0, homeScrollBeforePreview);
      restoreAppFocus();
    }
    applyPreviewTransform(open, true);
  }

  private void applyPreviewTransform(boolean open, boolean animate) {
    if (launcherView == null) return;
    launcherView.animate().cancel();
    float width = getResources().getDisplayMetrics().widthPixels;
    launcherView.setPivotX(width * 0.5f);
    launcherView.setPivotY(getResources().getDisplayMetrics().heightPixels / 2f);
    launcherView.setCameraDistance(width * 4f);
    float scale = open ? 0.52f : 1f;
    float shift = open ? -width * 0.215f : 0f;
    if (!animate) {
      launcherView.setScaleX(scale);
      launcherView.setScaleY(scale);
      launcherView.setRotationY(open ? -5f : 0f);
      launcherView.setTranslationX(shift);
      return;
    }
    launcherView
        .animate()
        .scaleX(scale)
        .scaleY(scale)
        .rotationY(open ? -5f : 0f)
        .translationX(shift)
        .setDuration(180)
        .start();
  }

  void setBool(String key, boolean value) {
    getPreferences(0).edit().putBoolean(key, value).apply();
    pendingAppearance.add(key);
    if ("show_all".equals(key)) previewRow = "app_order";
    if ("show_mobile".equals(key)) previewRow = "mobile_order";
    if ("show_favorites".equals(key)) previewRow = "favorite_order";
    handler.removeCallbacks(redraw);
    applyPendingAppearance();
  }

  void setInt(String key, int value) {
    getPreferences(0).edit().putInt(key, value).apply();
    pendingAppearance.add(key);
    handler.removeCallbacks(redraw);
    handler.postDelayed(redraw, 120);
  }

  private void applyPendingAppearance() {
    Set<String> changed = new HashSet<>(pendingAppearance);
    pendingAppearance.clear();
    if (changed.isEmpty() || launcherScroll == null) return;
    if (changed.remove("wallpaper_fit")) {
      launcherWallpaper.setScaleType(getPreferences(0).getBoolean("wallpaper_fit", false)
          ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
    }
    if (changed.remove("keep_screen_on")) {
      if (getPreferences(0).getBoolean("keep_screen_on", false))
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    if (changed.remove("wallpaper_dim")) {
      int dim = getPreferences(0).getInt("wallpaper_dim", 55);
      if (wallpaperShade != null) wallpaperShade.setAlpha(dim / 100f);
      if (currentWallpaper != null)
        currentWallpaper.setColorFilter((dim * 255 / 100) << 24, PorterDuff.Mode.SRC_ATOP);
    }
    if (changed.removeIf(key -> key.equals("show_status") || key.equals("show_network")
        || key.equals("show_clock") || key.startsWith("clock_") || key.startsWith("status_")
        || key.equals("show_notifications") || key.equals("hide_empty_notifications"))) {
      page.removeView(launcherHeader);
      header();
      updateWeatherAppearance();
    }
    if (changed.removeIf(key -> key.equals("show_weather") || key.startsWith("weather_")))
      updateWeatherAppearance();
    if (changed.removeIf(key -> key.startsWith("margin_"))) applyLauncherPadding();
    if (!changed.isEmpty()) rebuildPreviewRows();
  }

  private void rebuildPreviewRows() {
    int y = launcherScroll.getScrollY();
    List<String> previousRows = new ArrayList<>(previewRows.keySet());
    Map<String, Integer> horizontalPositions = new HashMap<>();
    previewRows.forEach((key, view) -> horizontalPositions.put(key, view.getScrollX()));
    home();
    launcherScroll.post(() -> {
      previewRows.forEach((key, view) -> view.scrollTo(horizontalPositions.getOrDefault(key, 0), 0));
      launcherScroll.scrollTo(0, y);
      if (previewOpen && previewRow != null) {
        if (!previewRows.containsKey(previewRow) && !previewRows.isEmpty()) {
          List<String> rows = new ArrayList<>(previewRows.keySet());
          int index = previousRows.indexOf(previewRow);
          previewRow = rows.get(Math.max(0, Math.min(index, rows.size() - 1)));
        }
        scrollToPreviewRow();
      }
    });
  }

  private void selectPreviewRow(String key) {
    if (!previewOpen) return;
    previewRow = key;
    scrollToPreviewRow();
  }

  private void scrollToPreviewRow() {
    View row = previewRows.get(previewRow);
    if (row == null) return;
    int center = content.getTop() + row.getTop() + row.getHeight() / 2;
    launcherScroll.smoothScrollTo(0, Math.max(0, center - launcherScroll.getHeight() / 2));
  }

  void toggle(TvSettingsPanel p, String icon, String title, String key, boolean value) {
    p.toggle(icon, title, "", getPreferences(0).getBoolean(key, value), v -> setBool(key, v));
  }

  void slider(
      TvSettingsPanel p,
      String icon,
      String title,
      String key,
      int value,
      int min,
      int max,
      String suffix) {
    p.slider(
        icon, title, min, max, getPreferences(0).getInt(key, value), suffix, v -> setInt(key, v));
  }

  void rootSettings(TvSettingsPanel p) {
    p.title("✦", "Nova home Launcher", "Настройки домашнего экрана");
    p.section("ПРИЛОЖЕНИЯ");
    p.action("▦", "Все приложения", "ТВ и мобильные приложения", this::openLibrary);
    p.action("≡", "Изменить категории", "Создание, порядок, видимость", this::manageCategories);
    p.action(
        "▤",
        "Изменить каналы",
        "Рекомендации установленных приложений",
        () -> p.open(this::channelsSettings));
    p.section("НАСТРОЙКИ");
    p.action(
        "⚙",
        "Настройки Nova home Launcher",
        "Оформление, поведение, система",
        () -> p.open(this::mainSettings));
    systemSetting(p,
        "⌂",
        "Настройки Android",
        "Штатные настройки телевизора",
        android.provider.Settings.ACTION_SETTINGS);
    p.action(
        "▣",
        "Профили экрана",
        "Сохранение и восстановление оформления",
        () -> p.open(this::profileSettings));
  }

  void mainSettings(TvSettingsPanel p) {
    p.title("⚙", "Настройки Nova home Launcher", "");
    p.action("☷", "Основные", "Автозапуск, приложения", () -> p.open(this::generalSettings));
    p.action(
        "▣",
        "Внешний вид",
        "Карточки, категории, часы и обои",
        () -> p.open(this::appearanceSettings));
    systemSetting(p,
        "☾",
        "Режим простоя",
        "Заставка Android",
        android.provider.Settings.ACTION_DREAM_SETTINGS);
    if (availableTvInputs().isEmpty())
      p.unavailable("⇥", "Источник сигнала", "Нет доступных входов телевизора");
    else p.action("⇥", "Источник сигнала", "Входы телевизора", this::showInputs);
    systemSetting(p, "⌁", "Пульты и устройства", "Системное сопряжение Bluetooth",
        android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
    p.action("⏻", "Электропитание", "Экран и заставка Android", () -> p.open(this::powerSettings));
    p.action(
        "♧", "Родительский контроль", "PIN настроек и запуска в Nova home Launcher",
        () -> p.open(this::parentalSettings));
    systemSetting(p,
        "♿",
        "Специальные возможности",
        "Системные настройки доступности",
        android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
    p.action("◎", "Диагностика", "Устройство, память и погода", this::diagnostics);
    p.action(
        "↺", "Сбросить оформление", "Приложения и избранное сохранятся", this::resetAppearance);
  }

  void powerSettings(TvSettingsPanel p) {
    p.title("⏻", "Электропитание", "Nova home Launcher не меняет системные правила выключения");
    toggle(p, "☀", "Не гасить экран в Nova home Launcher", "keep_screen_on", false);
    systemSetting(p, "☾", "Заставка Android", "",
        android.provider.Settings.ACTION_DREAM_SETTINGS);
    systemSetting(p, "▣", "Настройки экрана", "",
        android.provider.Settings.ACTION_DISPLAY_SETTINGS,
        android.provider.Settings.ACTION_SETTINGS);
  }

  private void systemSetting(TvSettingsPanel panel, String icon, String title, String subtitle,
      String action, String fallbackAction) {
    Intent primary = new Intent(action);
    if (canOpen(primary)) {
      panel.action(icon, title, subtitle, () -> safeStart(primary));
      return;
    }
    if (fallbackAction != null) {
      Intent fallback = new Intent(fallbackAction);
      if (canOpen(fallback)) {
        panel.action(icon, title, "Откроются общие настройки Android", () -> safeStart(fallback));
        return;
      }
    }
    panel.unavailable(icon, title, "Не поддерживается прошивкой");
  }

  private void systemSetting(TvSettingsPanel panel, String icon, String title, String subtitle,
      String action) {
    systemSetting(panel, icon, title, subtitle, action, null);
  }

  private boolean canOpen(Intent intent) {
    ResolveInfo resolved = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
    if (resolved == null || resolved.activityInfo == null || !resolved.activityInfo.exported)
      return false;
    String permission = resolved.activityInfo.permission;
    return permission == null || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  void parentalSettings(TvSettingsPanel p) {
    p.title(
        "♧",
        "Родительский контроль",
        "PIN действует только внутри Nova home Launcher, не блокирует Android или Google TV");
    if (!getPreferences(0).contains("pin_hash")) {
      p.action(
          "+",
          "Создать PIN",
          "4–8 цифр. При потере потребуется сброс данных Nova home Launcher",
          this::createPin);
      return;
    }
    toggle(p, "⚙", "Запрашивать PIN для настроек", "pin_settings", false);
    p.action(
        "▦",
        "Защищённые приложения",
        "Проверять PIN при запуске из Nova home Launcher",
        () -> {
          List<String> locked = readIds("locked_apps");
          String[] names = new String[installed.size()];
          boolean[] checked = new boolean[names.length];
          for (int i = 0; i < names.length; i++) {
            names[i] = installed.get(i).label;
            checked[i] = locked.contains(installed.get(i).pkg);
          }
          new NovaDialog(this)
              .setTitle("Защищённые приложения")
              .setMultiChoiceItems(names, checked, (d, n, value) -> checked[n] = value)
              .setPositiveButton(
                  "Сохранить",
                  (d, n) -> {
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++)
                      if (checked[i]) ids.add(installed.get(i).pkg);
                    getPreferences(0)
                        .edit()
                        .putString("locked_apps", android.text.TextUtils.join("|", ids))
                        .apply();
                  })
              .setNegativeButton("Отмена", null)
              .show();
        });
    p.action(
        "↺",
        "Отключить PIN",
        "Требуется текущий PIN",
        () ->
            askPin(
                () -> {
                  getPreferences(0)
                      .edit()
                      .remove("pin_hash")
                      .remove("pin_salt")
                      .remove("pin_settings")
                      .remove("locked_apps")
                      .apply();
                  settingsPanel.refresh();
                }));
  }

  EditText pinInput() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    return input;
  }

  void createPin() {
    LinearLayout fields = vertical();
    EditText first = pinInput(), second = pinInput();
    first.setHint("PIN: 4–8 цифр");
    second.setHint("Повторите PIN");
    fields.addView(first);
    fields.addView(second);
    AlertDialog dialog =
        new NovaDialog(this)
            .setTitle("Создать PIN Nova home Launcher")
            .setView(fields)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String pin = first.getText().toString();
                      if (!ParentalPin.valid(pin) || !pin.equals(second.getText().toString())) {
                        second.setError("PIN должны совпадать и содержать 4–8 цифр");
                        return;
                      }
                      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                      authWorker.execute(
                          () -> {
                            String salt = ParentalPin.salt(), hash = ParentalPin.hash(pin, salt);
                            handler.post(
                                () -> {
                                  if (isDestroyed() || !dialog.isShowing()) return;
                                  getPreferences(0)
                                      .edit()
                                      .putString("pin_salt", salt)
                                      .putString("pin_hash", hash)
                                      .apply();
                                  dialog.dismiss();
                                  settingsPanel.refresh();
                                });
                          });
                    }));
    dialog.show();
  }

  void askPin(Runnable success) {
    if (SystemClock.elapsedRealtime() < pinBlockedUntil) {
      Toast.makeText(this, "Слишком много попыток. Подождите 30 секунд", Toast.LENGTH_LONG).show();
      return;
    }
    EditText input = pinInput();
    AlertDialog dialog =
        new NovaDialog(this)
            .setTitle("PIN Nova home Launcher")
            .setView(input)
            .setPositiveButton("Подтвердить", null)
            .setNegativeButton("Отмена", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String value = input.getText().toString();
                      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                      String salt = getPreferences(0).getString("pin_salt", ""),
                          hash = getPreferences(0).getString("pin_hash", "");
                      authWorker.execute(
                          () -> {
                            boolean valid = ParentalPin.matches(value, salt, hash);
                            handler.post(
                                () -> {
                                  if (isDestroyed() || !dialog.isShowing()) return;
                                  if (valid) {
                                    pinFailures = 0;
                                    dialog.dismiss();
                                    success.run();
                                  } else {
                                    pinFailures++;
                                    if (pinFailures >= 3) {
                                      pinFailures = 0;
                                      pinBlockedUntil = SystemClock.elapsedRealtime() + 30000;
                                      dialog.dismiss();
                                      Toast.makeText(
                                              this, "Повторите через 30 секунд", Toast.LENGTH_LONG)
                                          .show();
                                    } else {
                                      dialog
                                          .getButton(AlertDialog.BUTTON_POSITIVE)
                                          .setEnabled(true);
                                      input.setText("");
                                      input.setError("Неверный PIN");
                                    }
                                  }
                                });
                          });
                    }));
    dialog.show();
  }

  void appearanceSettings(TvSettingsPanel p) {
    p.title("▣", "Внешний вид", "Изменения видны слева сразу");
    p.action(
        "≡", "Категории / строки", "Заголовки, интервалы, поля", () -> p.open(this::rowsSettings));
    p.action("appearance.cards", "▭", "Карточки", "Размер, пропорции и фокус",
        () -> p.open(this::cardsSettings));
    p.action("▧", "Обои", "Фон, затемнение, темы", () -> p.open(this::wallpaperSettings));
    p.action("━", "Строка состояния", "Часы, погода, сеть", () -> p.open(this::statusSettings));
    p.action("T", "Шрифт", "Размер интерфейса и диалогов", this::fontSettings);
    p.action(
        "◐", "Цвет акцента", "Выделение карточек и меню", () -> chooseColor("accent_color", true));
  }

  void rowsSettings(TvSettingsPanel p) {
    p.title("≡", "Категории / строки", "");
    p.action("rows.preview_all", "▣", "Показать весь экран", "", () -> {
      previewRow = null;
      launcherScroll.smoothScrollTo(0, 0);
    });
    toggle(p, "T", "Показывать названия категорий", "show_titles", true);
    toggle(p, "▣", "Значок перед заголовком", "show_category_icons", true);
    toggle(p, "☆", "Избранное", "show_favorites", true);
    toggle(p, "▦", "ТВ приложения", "show_all", true);
    toggle(p, "▯", "Мобильные приложения", "show_mobile", true);
    slider(p, "↕", "Вертикальный интервал", "row_gap", 12, 0, 48, " dp");
    toggle(p, "⌖", "Запоминать карточку", "remember_focus", true);
    toggle(p, "⌖", "Закреплять выбранную строку", "pin_row", false);
    slider(p, "◐", "Непрозрачность неактивных строк", "inactive_opacity", 75, 20, 100, " %");
    slider(p, "↑", "Верхний отступ", "margin_top", 20, 0, 100, " dp");
    slider(p, "←", "Отступ слева", "margin_left", 28, 0, 100, " dp");
    slider(p, "→", "Отступ справа", "margin_right", 28, 0, 100, " dp");
    p.action("≡", "Управление категориями", "", this::manageCategories);
  }

  void cardsSettings(TvSettingsPanel p) {
    p.title("▭", "Карточки", "");
    toggle(p, "T", "Показывать название приложения", "card_titles", false);
    toggle(p, "▧", "Использовать TV-баннеры", "use_banners", true);
    slider(p, "▦", "Строк приложений в категории", "grid_rows", 3, 1, 3, "");
    slider(p, "▭", "Размер карточки", "card_width", 126, 80, 240, " dp");
    slider(p, "◯", "Радиус скругления", "card_radius", 10, 0, 28, " dp");
    slider(p, "↔", "Расстояние между карточками", "card_gap", 10, 2, 32, " dp");
    slider(p, "▣", "Размер значков без баннера", "icon_size", 44, 24, 80, " dp");
    p.section("МОБИЛЬНЫЕ КАРТОЧКИ");
    p.action("▣", "Режим изображения", "", () -> chooseSetting(
        "mobile_icon_mode", "Режим изображения",
        new String[] {"Заполнить", "Вписать", "Оригинальный размер"}, 0));
    slider(p, "▣", "Масштаб значка", "mobile_icon_scale", 100, 60, 140, " %");
    slider(p, "↔", "Внутренние поля", "mobile_icon_padding", 4, 0, 24, " dp");
    slider(p, "◯", "Скругление мобильной карточки", "mobile_radius", 10, 0, 28, " dp");
    p.action("◐", "Фон мобильной карточки", "", () -> chooseColor("mobile_card_color", false));
    toggle(p, "⌖", "Увеличение выбранной карточки", "focus_zoom", true);
    p.action(
        "▭",
        "Соотношение сторон",
        "16:9 / 1:1 / 4:3",
        () ->
            new NovaDialog(this)
                .setTitle("Пропорции карточек")
                .setSingleChoiceItems(
                    new String[] {"16:9", "1:1", "4:3"},
                    getPreferences(0).getInt("card_ratio", 0),
                    (d, n) -> {
                      setInt("card_ratio", n);
                      d.dismiss();
                    })
                .setNegativeButton("Закрыть", null)
                .show());
    p.action(
        "▯",
        "Пропорции мобильных карточек",
        "1:1 / 16:9 / 4:3",
        () ->
            new NovaDialog(this)
                .setTitle("Мобильные карточки")
                .setSingleChoiceItems(
                    new String[] {"16:9", "1:1", "4:3"},
                    getPreferences(0).getInt("mobile_ratio", 1),
                    (d, n) -> {
                      setInt("mobile_ratio", n);
                      d.dismiss();
                    })
                .setNegativeButton("Закрыть", null)
                .show());
    p.action("◐", "Цвет карточек", "", () -> chooseColor("card_color", false));
  }

  void wallpaperSettings(TvSettingsPanel p) {
    p.title("▧", "Обои", "Изображения из системного проводника");
    p.action(
        "▧",
        "Выбрать фон",
        "Постоянный доступ к выбранному изображению",
        this::safeStartForWallpaper);
    p.action("▷", "Видеообои", "Локальный файл, без звука", this::selectVideoWallpaper);
    toggle(p, "▧", "Вписать изображение целиком", "wallpaper_fit", false);
    slider(p, "◐", "Затемнение фона", "wallpaper_dim", 55, 0, 90, " %");
    p.action(
        "◐",
        "Тема фона",
        "Ночная / Закат / Океан",
        () ->
            new NovaDialog(this)
                .setTitle("Тема фона")
                .setSingleChoiceItems(
                    new String[] {"Ночная", "Закат", "Океан"},
                    theme,
                    (d, n) -> {
                      theme = n;
                      getPreferences(0).edit().putInt("theme", n).apply();
                      launcherView.setBackground(background());
                      getWindow().setBackgroundDrawable(background());
                      d.dismiss();
                    })
                .setNegativeButton("Закрыть", null)
                .show());
    p.action(
        "↺",
        "Сбросить обои",
        "",
        () -> {
          getPreferences(0).edit().remove("wallpaper").apply();
          render();
        });
  }

  void statusSettings(TvSettingsPanel p) {
    p.title("━", "Строка состояния", "");
    toggle(p, "━", "Показывать строку состояния", "show_status", true);
    p.action("▣", "Значки и текст", "", () -> chooseSetting("status_label_mode", "Значки и текст",
        new String[] {"Только значки", "Только текст", "Значки и текст"}, 2));
    slider(p, "◯", "Скругление", "status_radius", 100, 0, 100, " %");
    slider(p, "◐", "Прозрачность фона", "status_transparency", 30, 0, 100, " %");
    slider(p, "T", "Размер текста", "status_size", 15, 10, 28, " sp");
    slider(p, "▣", "Размер значков", "status_icon_size", 20, 12, 28, " dp");
    slider(p, "↔", "Интервал", "status_gap", 8, 0, 24, " dp");
    slider(p, "↔", "Внутренние поля", "status_padding", 8, 0, 24, " dp");
    toggle(p, "◐", "Тень текста", "status_shadow", false);
    toggle(p, "◐", "Тень панели", "status_elevation", false);
    p.action("◐", "Цвет текста и значков", "", () -> chooseColor("status_color", false));
    p.action("◐", "Цвет фона", "", () -> chooseColor("status_background", false));
    p.action("↔", "Расположение", "", () -> chooseSetting("status_alignment", "Расположение",
        new String[] {"Перед кнопками", "После кнопок"}, 1));
    p.action("◷", "Часы", "Размер, цвет, формат", () -> p.open(this::clockSettings));
    p.action("☁", "Погода", "Город, размер, цвет", () -> p.open(this::weatherSettings));
    toggle(p, "◉", "Показывать подключение", "show_network", true);
    toggle(p, "♧", "Показывать значок уведомлений", "show_notifications", false);
    toggle(p, "◉", "Скрывать пустой значок", "hide_empty_notifications", true);
    p.action(
        "♧",
        "Уведомления",
        "Доступ и список уведомлений",
        () -> p.open(this::notificationSettings));
  }

  private void chooseSetting(String key, String title, String[] options, int defaultValue) {
    new NovaDialog(this).setTitle(title)
        .setSingleChoiceItems(options, getPreferences(0).getInt(key, defaultValue), (dialog, index) -> {
          setInt(key, index);
          dialog.dismiss();
        })
        .setNegativeButton("Отмена", null)
        .show();
  }

  void clockSettings(TvSettingsPanel p) {
    p.title("◷", "Часы", "");
    toggle(p, "◷", "Показывать часы", "show_clock", true);
    slider(p, "T", "Размер шрифта часов", "clock_size", 17, 10, 32, " sp");
    toggle(p, "◐", "Тень часов", "clock_shadow", true);
    p.action("◐", "Цвет часов", "", () -> chooseColor("clock_color", false));
    p.action(
        "◷",
        "Формат часов",
        "24 / 12 часов / дата и время",
        () ->
            new NovaDialog(this)
                .setTitle("Формат часов")
                .setItems(
                    new String[] {"24 часа — HH:mm", "12 часов — hh:mm a", "Дата и время"},
                    (d, n) -> {
                      getPreferences(0)
                          .edit()
                          .putString(
                              "clock_format",
                              new String[] {"HH:mm", "hh:mm a", "EEE, d MMM HH:mm"}[n])
                          .apply();
                      pendingAppearance.add("clock_format");
                      applyPendingAppearance();
                    })
                .setNegativeButton("Закрыть", null)
                .show());
  }

  void weatherSettings(TvSettingsPanel p) {
    p.title("☁", "Погода", "Open-Meteo · резервный прогноз MET Norway (CC BY 4.0)");
    toggle(p, "☁", "Показывать погоду", "show_weather", true);
    p.action(
        "⌖", "Город", getPreferences(0).getString("weather_city", "Не выбран"), this::weatherCity);
    slider(p, "T", "Размер шрифта погоды", "weather_size", 13, 10, 26, " sp");
    toggle(p, "◐", "Тень погоды", "weather_shadow", true);
    p.action("◐", "Цвет погоды", "", () -> chooseColor("weather_color", false));
    p.action(
        "↻",
        "Обновить",
        "",
        this::refreshWeather);
    p.action("weather.connection", "↻", "Проверить соединение", "", this::checkWeatherConnection);
    p.action(
        "!",
        "Последняя ошибка",
        getPreferences(0).getString("weather_error", "Ошибок нет"),
        () ->
            new NovaDialog(this)
                .setTitle("Диагностика погоды")
                .setMessage(
                    getPreferences(0).getString("weather_diagnostics", "Запросы ещё не выполнялись")
                        + "\nВремя ТВ: "
                        + new Date())
                .setPositiveButton("Закрыть", null)
                .show());
  }

  private void refreshWeather() {
    retryWeatherAfter = 0;
    getPreferences(0).edit().putLong("weather_time", 0).apply();
    loadWeather();
  }

  private void checkWeatherConnection() {
    AlertDialog diagnostic = new NovaDialog(this).setTitle("Проверка погоды")
        .setMessage("Проверяем доступность погодных сервисов…")
        .setPositiveButton("Закрыть", null).show();
    weatherWorker.execute(() -> {
      String result;
      try {
        result = weatherRepository().forecast("55.75", "37.62").diagnostics;
      } catch (java.io.IOException failure) {
        result = WeatherRepository.describe(failure);
      }
      String report = result;
      handler.post(() -> {
        if (!isDestroyed() && diagnostic.isShowing()) diagnostic.setMessage(report);
      });
    });
  }

  void generalSettings(TvSettingsPanel p) {
    p.title("☷", "Основные", "Google TV и системный HOME не изменяются");
    p.action("▷", "Автозапуск приложения", "При старте Nova home Launcher", this::autostartSettings);
    p.action("◉", "Скрытые приложения", "Восстановление скрытых карточек", () -> openAppList(true));
    p.action(
        "↻",
        "Обновить приложения",
        "",
        () -> {
          refreshApps();
          render();
        });
    systemSetting(p,
        "◉",
        "Сеть",
        "",
        android.provider.Settings.ACTION_WIFI_SETTINGS);
    systemSetting(p,
        "◷",
        "Дата и время",
        "",
        android.provider.Settings.ACTION_DATE_SETTINGS);
    systemSetting(p,
        "⌁",
        "Голосовой поиск",
        "Системный поиск, если установлен",
        android.app.SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
  }

  void chooseColor(String key, boolean reopen) {
    String[] names = {"Розовый", "Бирюзовый", "Голубой", "Белый", "Тёмный"};
    int[] colors = {0xffff94b2, 0xff8de7d3, 0xff9ecaff, 0xfff5f7ff, 0xdd222c40};
    new NovaDialog(this)
        .setTitle("Цвет")
        .setItems(
            names,
            (d, n) -> {
              setInt(key, colors[n]);
              if (reopen) {
                handler.removeCallbacks(redraw);
                applyPendingAppearance();
                if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.refresh();
              }
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  void openPanelPage(TvSettingsPanel.Page page) {
    if (settingsPanel != null && settingsPanel.isShowing()) {
      settingsPanel.open(page);
      return;
    }
    if (getPreferences(0).getBoolean("pin_settings", false)
        && getPreferences(0).contains("pin_hash")
        && !settingsUnlocked) {
      askPin(
          () -> {
            settingsUnlocked = true;
            showSettings();
            settingsPanel.open(page);
          });
    } else {
      showSettings();
      settingsPanel.open(page);
    }
  }

  void openLibrary() {
    openAppList(false);
  }

  void openAppList(boolean hidden) {
    final boolean hiddenOnly = hidden;
    openPanelPage(
        p -> {
          p.title(
              hiddenOnly ? "◉" : "▦",
              hiddenOnly ? "Скрытые приложения" : "Все приложения",
              "OK — запуск · удержание OK — меню");
          for (App app : visibleApps(installed, hiddenOnly)) {
            p.app(
                app.label,
                app.tv ? "ТВ приложение" : "Мобильное приложение",
                app.banner == null ? app.icon : app.banner,
                () -> {
                  if (hiddenOnly) {
                    showHidden = true;
                    contextMenu(app, false, "app_order");
                  } else {
                    settingsPanel.dismiss();
                    launch(app);
                  }
                },
                () -> {
                  showHidden = hiddenOnly;
                  contextMenu(app, false, "app_order");
                });
          }
          if (visibleApps(installed, hiddenOnly).isEmpty()) p.section("Приложений нет");
        });
  }

  void resetAppearance() {
    new NovaDialog(this)
        .setTitle("Сбросить оформление?")
        .setMessage("Избранное, категории и установленные приложения сохранятся.")
        .setPositiveButton(
            "Сбросить",
            (d, n) -> {
              android.content.SharedPreferences.Editor edit = getPreferences(0).edit();
              for (String key : appearanceKeys()) edit.remove(key);
              edit.apply();
              theme = 0;
              render();
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  String[] appearanceKeys() {
    return new String[] {
      "card_width",
      "grid_rows",
      "card_titles",
      "card_ratio",
      "card_radius",
      "card_gap",
      "card_color",
      "icon_size",
      "focus_zoom",
      "use_banners",
      "show_titles",
      "show_category_icons",
      "show_favorites",
      "show_all",
      "show_mobile",
      "row_gap",
      "remember_focus",
      "inactive_opacity",
      "margin_left",
      "margin_right",
      "margin_top",
      "wallpaper_dim",
      "wallpaper",
      "theme",
      "show_clock",
      "clock_size",
      "clock_color",
      "clock_format",
      "show_weather",
      "weather_size",
      "weather_color",
      "show_status",
      "show_network",
      "accent_color",
      "show_inputs",
      "wallpaper_type",
      "wallpaper_fit",
      "pin_row",
      "clock_shadow",
      "weather_shadow",
      "keep_screen_on",
      "mobile_ratio",
      "show_notifications",
      "hide_empty_notifications",
      "mobile_icon_mode", "mobile_icon_scale", "mobile_icon_padding", "mobile_radius",
      "mobile_card_color", "status_label_mode", "status_radius", "status_transparency",
      "status_size", "status_icon_size", "status_gap", "status_padding",
      "status_shadow", "status_elevation", "status_color", "status_background", "status_alignment"
    };
  }

  void profileSettings(TvSettingsPanel p) {
    p.title("▣", "Профили экрана", "Сохраняются параметры оформления и фон");
    p.action(
        "+",
        "Сохранить текущий профиль",
        "",
        () -> {
          EditText name = new EditText(this);
          new NovaDialog(this)
              .setTitle("Название профиля")
              .setView(name)
              .setPositiveButton(
                  "Сохранить",
                  (d, n) -> {
                    String value = name.getText().toString().trim().replace("|", "");
                    if (value.isEmpty()) return;
                    try {
                      org.json.JSONObject data = new org.json.JSONObject();
                      for (String key : appearanceKeys())
                        if (getPreferences(0).contains(key))
                          data.put(key, getPreferences(0).getAll().get(key));
                      List<String> profiles = readIds("profiles");
                      if (!profiles.contains(value)) profiles.add(value);
                      getPreferences(0)
                          .edit()
                          .putString("profiles", android.text.TextUtils.join("|", profiles))
                          .putString("profile_" + value, data.toString())
                          .apply();
                      p.open(this::profileSettings);
                    } catch (Exception e) {
                      Toast.makeText(this, "Не удалось сохранить профиль", Toast.LENGTH_LONG)
                          .show();
                    }
                  })
              .setNegativeButton("Отмена", null)
              .show();
        });
    for (String name : readIds("profiles"))
      p.action(
          "▣",
          name,
          "Применить / удалить",
          () ->
              new NovaDialog(this)
                  .setTitle(name)
                  .setItems(
                      new String[] {"Применить", "Удалить"},
                      (d, n) -> {
                        if (n == 0) {
                          try {
                            org.json.JSONObject data =
                                new org.json.JSONObject(
                                    getPreferences(0).getString("profile_" + name, "{}"));
                            android.content.SharedPreferences.Editor edit =
                                getPreferences(0).edit();
                            for (String key : appearanceKeys()) {
                              edit.remove(key);
                              Object value = data.opt(key);
                              if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                              else if (value instanceof Number)
                                edit.putInt(key, ((Number) value).intValue());
                              else if (value instanceof String) edit.putString(key, (String) value);
                            }
                            edit.apply();
                            theme = getPreferences(0).getInt("theme", 0);
                            render();
                          } catch (Exception e) {
                            Toast.makeText(this, "Профиль повреждён", Toast.LENGTH_LONG).show();
                          }
                        } else {
                          List<String> profiles = readIds("profiles");
                          profiles.remove(name);
                          getPreferences(0)
                              .edit()
                              .putString("profiles", android.text.TextUtils.join("|", profiles))
                              .remove("profile_" + name)
                              .apply();
                          p.open(this::profileSettings);
                        }
                      })
                  .setNegativeButton("Отмена", null)
                  .show());
  }

  private List<android.media.tv.TvInputInfo> availableTvInputs() {
    android.media.tv.TvInputManager manager =
        (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
    List<android.media.tv.TvInputInfo> inputs = new ArrayList<>();
    try {
      if (manager != null)
        for (android.media.tv.TvInputInfo input : manager.getTvInputList())
          if (canOpen(tvInputIntent(input))) inputs.add(input);
    } catch (SecurityException ignored) {
      // Some firmware exposes the service only to its built-in launcher.
    }
    return inputs;
  }

  private Intent tvInputIntent(android.media.tv.TvInputInfo input) {
    android.net.Uri uri = input.isPassthroughInput()
        ? android.media.tv.TvContract.buildChannelUriForPassthroughInput(input.getId())
        : android.media.tv.TvContract.Channels.CONTENT_URI;
    return new Intent(Intent.ACTION_VIEW, uri);
  }

  void showInputs() {
    List<android.media.tv.TvInputInfo> inputs = availableTvInputs();
    String[] names = new String[inputs.size()];
    for (int i = 0; i < inputs.size(); i++) names[i] = inputs.get(i).loadLabel(this).toString();
    if (names.length == 0) {
      Toast.makeText(this, "Телевизор не предоставил список входов", Toast.LENGTH_LONG).show();
      return;
    }
    new NovaDialog(this)
        .setTitle("Источники сигнала")
        .setItems(names, (d, n) -> safeStart(tvInputIntent(inputs.get(n))))
        .setNegativeButton("Закрыть", null)
        .show();
  }

  boolean notificationGranted() {
    String enabled =
        android.provider.Settings.Secure.getString(
            getContentResolver(), "enabled_notification_listeners");
    return enabled != null
        && Arrays.asList(enabled.split(":"))
            .contains(new ComponentName(this, NotificationAccess.class).flattenToString());
  }

  void notificationSettings(TvSettingsPanel p) {
    p.title("♧", "Уведомления", "Тексты хранятся только в памяти Nova home Launcher");
    systemSetting(p,
        "⚙",
        "Доступ к уведомлениям",
        notificationGranted() ? "Предоставлен" : "Требуется ваше разрешение в Android",
        android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    if (notificationGranted()) {
      p.toggle("▣", "Показывать системные уведомления", "Включая фоновые службы",
          getPreferences(0).getBoolean("show_ongoing_notifications", false), value -> {
            getPreferences(0).edit().putBoolean("show_ongoing_notifications", value).apply();
            p.refresh();
            updateClock();
          });
      List<NotificationAccess.Item> notifications = visibleNotifications();
      for (NotificationAccess.Item item : notifications) {
        String app = "Приложение";
        Drawable icon = getDrawable(R.drawable.nova_bell);
        try {
          ApplicationInfo info = getPackageManager().getApplicationInfo(item.pkg, 0);
          app = info.loadLabel(getPackageManager()).toString();
          icon = info.loadIcon(getPackageManager());
        } catch (PackageManager.NameNotFoundException ignored) {
          // Some notification senders are not visible to Nova home Launcher.
        }
        p.notification(
            item.title.isEmpty() ? app : item.title,
            item.text.isEmpty() ? (item.title.isEmpty() ? "" : app) : app + " · " + item.text,
            icon,
            () -> {
              Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(item.pkg);
              if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(item.pkg);
              if (launch != null) {
                Intent target = launch;
                guardApp(item.pkg, () -> safeStart(target));
              }
            });
      }
      if (notifications.isEmpty()) p.section("Нет уведомлений");
      p.action("▷", "Сейчас играет", "Активные медиасессии", this::showNowPlaying);
    }
  }

  private List<NotificationAccess.Item> visibleNotifications() {
    return NotificationAccess.snapshot(getPreferences(0).getBoolean("show_ongoing_notifications", false));
  }

  void showNowPlaying() {
    try {
      android.media.session.MediaSessionManager manager =
          (android.media.session.MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
      List<android.media.session.MediaController> sessions =
          manager.getActiveSessions(new ComponentName(this, NotificationAccess.class));
      if (sessions.isEmpty()) {
        Toast.makeText(this, "Нет активных медиасессий", Toast.LENGTH_LONG).show();
        return;
      }
      String[] titles = new String[sessions.size()];
      for (int i = 0; i < sessions.size(); i++) {
        android.media.MediaMetadata metadata = sessions.get(i).getMetadata();
        titles[i] =
            metadata == null
                ? sessions.get(i).getPackageName()
                : String.valueOf(metadata.getDescription().getTitle());
      }
      new NovaDialog(this)
          .setTitle("Сейчас играет")
          .setItems(
              titles,
              (d, n) -> {
                android.app.PendingIntent open = sessions.get(n).getSessionActivity();
                if (open != null)
                  guardApp(
                      sessions.get(n).getPackageName(),
                      () -> {
                        try {
                          open.send();
                        } catch (android.app.PendingIntent.CanceledException e) {
                          Toast.makeText(this, "Сессия закрыта", Toast.LENGTH_SHORT).show();
                        }
                      });
                else
                  Toast.makeText(
                          this, "Приложение не предоставило переход к плееру", Toast.LENGTH_LONG)
                      .show();
              })
          .setNegativeButton("Закрыть", null)
          .show();
    } catch (SecurityException e) {
      safeStart(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }
  }

  void loadChannels() {
    if (feedsLoading) return;
    feedsLoading = true;
    boolean readPreview = hasTvListingsAccess();
    worker.execute(
        () -> {
          TvFeeds.Result result = TvFeeds.read(this, readPreview);
          handler.post(
              () -> {
                feedsLoading = false;
                if (isDestroyed()) return;
                if (readPreview != hasTvListingsAccess()) {
                  loadChannels();
                  return;
                }
                feeds = result;
                failedArtwork.clear();
                feedsLoaded = System.currentTimeMillis();
                if (settingsPanel != null
                    && settingsPanel.isShowing()
                    && settingsPanel.isPage("Каналы приложений")) settingsPanel.refresh();
                if (currentScreen == 0) rebuildPreviewRows();
              });
        });
  }

  private boolean hasTvListingsAccess() {
    return checkSelfPermission(TV_LISTINGS) == PackageManager.PERMISSION_GRANTED;
  }

  private void openTvListingsPermission() {
    boolean asked = getPreferences(0).getBoolean("tv_listings_permission_asked", false);
    if (asked && !shouldShowRequestPermissionRationale(TV_LISTINGS)) {
      safeStart(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          android.net.Uri.fromParts("package", getPackageName(), null)));
      return;
    }
    getPreferences(0).edit().putBoolean("tv_listings_permission_asked", true).apply();
    requestPermissions(new String[] {TV_LISTINGS}, REQUEST_TV_LISTINGS);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request != REQUEST_TV_LISTINGS) return;
    lastTvListingsGrant = hasTvListingsAccess();
    feeds = null;
    feedsLoaded = 0;
    if (settingsPanel != null && settingsPanel.isShowing()
        && settingsPanel.isPage("Каналы приложений")) settingsPanel.refresh();
    loadChannels();
  }

  void channelsSettings(TvSettingsPanel p) {
    p.title("▤", "Каналы приложений", "Данные системного TV Provider");
    toggle(p, "▤", "Показывать рекомендации", "show_channels", false);
    if (!hasTvListingsAccess()) {
      boolean settingsRequired = getPreferences(0).getBoolean("tv_listings_permission_asked", false)
          && !shouldShowRequestPermissionRationale(TV_LISTINGS);
      p.action("tv_feeds.permission", "⚙",
          settingsRequired ? "Открыть разрешения приложения" : "Разрешить чтение каналов",
          "Доступ нужен для рекомендаций кинотеатров. «Смотреть далее» проверяется отдельно.",
          this::openTvListingsPermission);
    }
    p.action(
        "↻",
        "Обновить каналы",
        "",
        () -> {
          feedsLoaded = 0;
          loadChannels();
        });
    if (feeds == null) {
      p.section("Читаем доступные каналы…");
      loadChannels();
      return;
    }
    TvFeeds.Result displayed = feeds;
    if (!displayed.message.isEmpty()) p.section(displayed.message);
    p.action("tv_feeds.diagnostics", "◎", "Диагностика каналов", "",
        () -> new NovaDialog(this).setTitle("Диагностика каналов")
            .setMessage((hasTvListingsAccess() ? "Доступ выдан" : "Доступ не выдан")
                + "\n" + displayed.diagnostics())
            .setPositiveButton("Закрыть", null).show());
    for (TvFeeds.Channel channel : displayed.channels) {
      boolean shown = !readIds("hidden_channels").contains(channel.id);
      p.toggle(
          "▤",
          channel.title,
          channel.source + " · " + channel.programs.size() + " карточек",
          shown,
          value -> {
            List<String> hidden = readIds("hidden_channels");
            hidden.remove(channel.id);
            if (!value) hidden.add(channel.id);
            getPreferences(0)
                .edit()
                .putString("hidden_channels", android.text.TextUtils.join("|", hidden))
                .apply();
            home();
          });
    }
  }

  void renderChannels() {
    if (feeds == null) {
      content.addView(label("Загрузка рекомендаций…", 13, MUTED));
      loadChannels();
      return;
    }
    if (System.currentTimeMillis() - feedsLoaded > 300000) loadChannels();
    if (feeds.channels.isEmpty()) {
      content.addView(label(feeds.message, 13, MUTED));
      return;
    }
    for (TvFeeds.Channel channel : feeds.channels) {
      if (readIds("hidden_channels").contains(channel.id)) continue;
      rowTitle("▤", channel.title);
      HorizontalScrollView scroll = new HorizontalScrollView(this);
      scroll.setHorizontalScrollBarEnabled(false);
      scroll.setOnScrollChangeListener(
          (View v, int x, int y, int oldX, int oldY) -> refreshVisibleArtwork());
      content.addView(scroll, lp(-1, 142));
      LinearLayout row = horizontal();
      row.setPadding(dp(6), dp(6), dp(6), dp(6));
      scroll.addView(row);
      for (TvFeeds.Program program : channel.programs) {
        LinearLayout card = vertical();
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setFocusable(true);
        card.setBackground(shape(0xdd222c40, 10));
        marginAdd(row, card, 108, 128, 10);
        ImageView art = new ImageView(this);
        art.setImageDrawable(getDrawable(R.mipmap.ic_launcher));
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, lp(-1, 96));
        TextView title = label(program.title == null ? "Без названия" : program.title, 13, WHITE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, lp(-1, 24));
        card.setOnFocusChangeListener(
            (v, f) -> {
              GradientDrawable bg = shape(0xdd222c40, 10);
              if (f) bg.setStroke(dp(2), accent());
              card.setBackground(bg);
            });
        card.setOnClickListener(v -> openProgram(program));
        loadArtwork(program.artwork, art);
      }
    }
  }

  void openProgram(TvFeeds.Program program) {
    guardApp(program.pkg, () -> openProgramUnchecked(program));
  }

  void openProgramUnchecked(TvFeeds.Program program) {
    try {
      Intent intent = Intent.parseUri(program.intent, Intent.URI_INTENT_SCHEME);
      if (program.pkg == null || program.pkg.isEmpty()) throw new SecurityException();
      if (intent.getComponent() != null
          && !program.pkg.equals(intent.getComponent().getPackageName()))
        throw new SecurityException();
      intent.setPackage(program.pkg);
      intent.setSelector(null);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      safeStart(intent);
    } catch (Exception e) {
      Toast.makeText(this, "Карточка не содержит доступной ссылки приложения", Toast.LENGTH_LONG)
          .show();
    }
  }

  void loadArtwork(String uri, ImageView image) {
    if (uri == null || uri.isEmpty()) return;
    artworkViews.put(image, uri);
    image.post(this::refreshVisibleArtwork);
  }

  void refreshVisibleArtwork() {
    if (isDestroyed() || !resumed) return;
    Rect visible = new Rect();
    for (Map.Entry<ImageView, String> entry : new ArrayList<>(artworkViews.entrySet())) {
      ImageView image = entry.getKey();
      if (image.isAttachedToWindow()
          && image.getGlobalVisibleRect(visible)
          && !failedArtwork.contains(entry.getValue())
          && artworkLoading.add(image)) requestArtwork(entry.getValue(), image);
    }
  }

  void requestArtwork(String uri, ImageView image) {
    if (uri == null || uri.isEmpty()) return;
    Bitmap cached = artworkCache.get(uri);
    if (cached != null) {
      image.setImageBitmap(cached);
      artworkLoading.remove(image);
      return;
    }
    artworkWorker.execute(
        () -> {
          try {
            byte[] bytes;
            android.net.Uri address = android.net.Uri.parse(uri);
            if ("content".equals(address.getScheme())) {
              try (java.io.InputStream in = getContentResolver().openInputStream(address)) {
                bytes = readArtwork(in);
              }
            } else if ("https".equals(address.getScheme())) {
              java.net.HttpURLConnection connection =
                  (java.net.HttpURLConnection) new java.net.URL(uri).openConnection();
              connection.setConnectTimeout(5000);
              connection.setReadTimeout(5000);
              try (java.io.InputStream in = connection.getInputStream()) {
                bytes = readArtwork(in);
              } finally {
                connection.disconnect();
              }
            } else return;
            Bitmap bitmap =
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)),
                    (decoder, info, source) -> {
                      int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                      float scale = Math.min(1f, 240f / Math.max(w, h));
                      decoder.setTargetSize(
                          Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)));
                    });
            artworkCache.put(uri, bitmap);
            handler.post(
                () -> {
                  artworkLoading.remove(image);
                  if (!isDestroyed()) image.setImageBitmap(bitmap);
                });
          } catch (Exception ignored) {
            handler.post(
                () -> {
                  artworkLoading.remove(image);
                  failedArtwork.add(uri);
                });
          }
        });
  }

  byte[] readArtwork(java.io.InputStream in) throws java.io.IOException {
    if (in == null) throw new java.io.IOException();
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = in.read(buffer)) != -1) {
      out.write(buffer, 0, n);
      if (out.size() > 4 * 1024 * 1024)
        throw new java.io.IOException("Изображение слишком большое");
    }
    return out.toByteArray();
  }

  void fontSettings() {
    float scale = getPreferences(0).getFloat("font_scale", 0.8f);
    new NovaDialog(this)
        .setTitle("Размер шрифта")
        .setSingleChoiceItems(
            new String[] {"Маленький", "Средний", "Большой"},
            scale < 0.85f ? 0 : scale < 1 ? 1 : 2,
            (d, n) -> {
              getPreferences(0)
                  .edit()
                  .putFloat("font_scale", new float[] {0.8f, 0.9f, 1f}[n])
                  .apply();
              d.dismiss();
              recreate();
            })
        .setNegativeButton("Закрыть", null)
        .show();
  }

  void diagnostics() {
    android.app.ActivityManager.MemoryInfo mem = new android.app.ActivityManager.MemoryInfo();
    ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mem);
    String s =
        "Устройство: "
            + Build.MANUFACTURER
            + " "
            + Build.MODEL
            + "\nAndroid: "
            + Build.VERSION.RELEASE
            + " (API "
            + Build.VERSION.SDK_INT
            + ")\nОЗУ всего: "
            + (mem.totalMem / 1024 / 1024)
            + " МБ\nОЗУ доступно: "
            + (mem.availMem / 1024 / 1024)
            + " МБ\nЭкран: "
            + getResources().getDisplayMetrics().widthPixels
            + " × "
            + getResources().getDisplayMetrics().heightPixels
            + "\nСборка: "
            + Build.DISPLAY
            + "\nСвободно: "
            + (getFilesDir().getUsableSpace() / 1024 / 1024)
            + " МБ\nНайдено приложений: "
            + installed.size()
            + "\nВерсия Nova home Launcher: " + BuildConfig.VERSION_NAME;
    new NovaDialog(this)
        .setTitle("Диагностика устройства")
        .setMessage(s)
        .setPositiveButton("Закрыть", null)
        .show();
  }

  void selectVideoWallpaper() {
    try {
      Intent pick =
          new Intent(Intent.ACTION_OPEN_DOCUMENT)
              .setType("video/*")
              .addCategory(Intent.CATEGORY_OPENABLE)
              .addFlags(
                  Intent.FLAG_GRANT_READ_URI_PERMISSION
                      | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      startActivityForResult(pick, 8);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, "Нет приложения выбора файлов", Toast.LENGTH_LONG).show();
    }
  }

  void safeStartForWallpaper() {
    try {
      Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      i.setType("image/*");
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.addFlags(
          Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      startActivityForResult(i, 7);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, "На телевизоре нет приложения выбора файлов", Toast.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if ((request == 7 || request == 8)
        && result == RESULT_OK
        && data != null
        && data.getData() != null) {
      try {
        getContentResolver()
            .takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getPreferences(0)
            .edit()
            .putString("wallpaper", data.getData().toString())
            .putString("wallpaper_type", request == 8 ? "video" : "image")
            .apply();
        render();
        settings();
      } catch (SecurityException e) {
        Toast.makeText(this, "Не удалось сохранить доступ к обоям", Toast.LENGTH_LONG).show();
      }
    }
  }

  void manageCategories() {
    openPanelPage(this::categoryManager);
  }

  void categoryManager(TvSettingsPanel p) {
    p.title("≡", "Категории", "");
    p.action("+", "Новая категория", "", this::createCategory);
    p.section("КАТЕГОРИИ");
    toggle(p, "▣", "ТВ приложения", "show_all", true);
    toggle(p, "▯", "Мобильные приложения", "show_mobile", true);
    toggle(p, "☆", "Избранное", "show_favorites", true);
    toggle(p, "⇥", "Входы телевизора", "show_inputs", false);
    for (String category : readIds("categories"))
      p.action(
          "category:" + category,
          "≡",
          category,
          readIds("hidden_categories").contains(category) ? "Скрыта" : "Показывается",
          () -> {
              selectPreviewRow("category_order_" + category);
              p.open(
                  page -> {
                    page.title("≡", category, "");
                    page.toggle(
                        "◉",
                        "Показывать категорию",
                        "",
                        !readIds("hidden_categories").contains(category),
                        value -> {
                          List<String> hidden = readIds("hidden_categories");
                          hidden.remove(category);
                          if (!value) hidden.add(category);
                          getPreferences(0)
                              .edit()
                              .putString(
                                  "hidden_categories", android.text.TextUtils.join("|", hidden))
                              .apply();
                          previewRow = "category_order_" + category;
                          rebuildPreviewRows();
                        });
                    page.action(
                        "▦",
                        "Приложения в категории",
                        "Выбор приложений",
                        () -> assignCategoryApps(category));
                    page.action(
                        "↕", "Порядок, название и удаление", "", () -> categoryActions(category));
                  });
          });
  }

  void createCategory() {
    EditText name = new EditText(this);
    name.setSingleLine(true);
    AlertDialog dialog =
        new NovaDialog(this)
            .setTitle("Название категории")
            .setView(name)
            .setPositiveButton("Создать", null)
            .setNegativeButton("Отмена", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String value = name.getText().toString().trim().replace("|", "");
                      List<String> categories = readIds("categories");
                      if (value.isEmpty() || categories.contains(value)) {
                        name.setError("Введите уникальное название");
                        return;
                      }
                      categories.add(value);
                      getPreferences(0)
                          .edit()
                          .putString("categories", android.text.TextUtils.join("|", categories))
                          .apply();
                      dialog.dismiss();
                      home();
                      settingsPanel.refresh();
                    }));
    dialog.show();
  }

  void assignCategoryApps(String category) {
    String[] names = new String[installed.size()];
    boolean[] selected = new boolean[names.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = installed.get(i).label;
      selected[i] =
          category.equals(getPreferences(0).getString("category_" + installed.get(i).pkg, ""));
    }
    new NovaDialog(this)
        .setTitle(category + ": приложения")
        .setMultiChoiceItems(names, selected, (d, n, value) -> selected[n] = value)
        .setPositiveButton(
            "Сохранить",
            (d, n) -> {
              android.content.SharedPreferences.Editor edit = getPreferences(0).edit();
              for (int i = 0; i < selected.length; i++) {
                String key = "category_" + installed.get(i).pkg;
                if (selected[i]) edit.putString(key, category);
                else if (category.equals(getPreferences(0).getString(key, ""))) edit.remove(key);
              }
              edit.apply();
              home();
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  void categoryActions(String category) {
    new NovaDialog(this)
        .setTitle(category)
        .setItems(
            new String[] {
              "Переместить выше", "Переместить ниже", "Переименовать", "Удалить категорию"
            },
            (d, n) -> {
              List<String> categories = readIds("categories");
              int index = categories.indexOf(category);
              if (index < 0) return;
              if (n < 2) {
                int to = index + (n == 0 ? -1 : 1);
                if (to < 0 || to >= categories.size()) {
                  Toast.makeText(this, "Категория уже у края списка", Toast.LENGTH_SHORT).show();
                  return;
                }
                Collections.swap(categories, index, to);
                getPreferences(0)
                    .edit()
                    .putString("categories", android.text.TextUtils.join("|", categories))
                    .apply();
                previewRow = "category_order_" + category;
                rebuildPreviewRows();
                if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.refresh();
              } else if (n == 2) {
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(category);
                AlertDialog dialog =
                    new NovaDialog(this)
                        .setTitle("Новое название категории")
                        .setView(input)
                        .setPositiveButton("Сохранить", null)
                        .setNegativeButton("Отмена", null)
                        .create();
                dialog.setOnShowListener(
                    x ->
                        dialog
                            .getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(
                                v -> {
                                  String name = input.getText().toString().trim().replace("|", "");
                                  if (name.isEmpty()
                                      || (!name.equals(category) && categories.contains(name))) {
                                    input.setError("Введите уникальное название");
                                    return;
                                  }
                                  categories.set(index, name);
                                  android.content.SharedPreferences.Editor edit =
                                      getPreferences(0)
                                          .edit()
                                          .putString(
                                              "categories",
                                              android.text.TextUtils.join("|", categories))
                                          .putString(
                                              "category_order_" + name,
                                              getPreferences(0)
                                                  .getString("category_order_" + category, ""));
                                  if (!name.equals(category))
                                    edit.remove("category_order_" + category);
                                  for (String key : getPreferences(0).getAll().keySet())
                                    if (key.startsWith("category_")
                                        && !key.startsWith("category_order_")
                                        && category.equals(getPreferences(0).getString(key, "")))
                                      edit.putString(key, name);
                                  edit.apply();
                                  dialog.dismiss();
                                  home();
                                }));
                dialog.show();
              } else
                new NovaDialog(this)
                    .setTitle("Удалить категорию «" + category + "»?")
                    .setMessage("Приложения останутся установленными.")
                    .setPositiveButton(
                        "Удалить",
                        (x, y) -> {
                          categories.remove(category);
                          android.content.SharedPreferences.Editor edit =
                              getPreferences(0)
                                  .edit()
                                  .putString(
                                      "categories", android.text.TextUtils.join("|", categories))
                                  .remove("category_order_" + category);
                          for (String key : getPreferences(0).getAll().keySet())
                            if (key.startsWith("category_")
                                && !key.startsWith("category_order_")
                                && category.equals(getPreferences(0).getString(key, "")))
                              edit.remove(key);
                          edit.apply();
                          home();
                        })
                    .setNegativeButton("Отмена", null)
                    .show();
            })
        .setNegativeButton("Закрыть", null)
        .show();
  }

  void chooseCategory(App app) {
    List<String> categories = readIds("categories");
    if (categories.isEmpty()) {
      manageCategories();
      return;
    }
    categories.add(0, "Без категории");
    new NovaDialog(this)
        .setTitle(app.label + ": категория")
        .setItems(
            categories.toArray(new String[0]),
            (d, n) -> {
              getPreferences(0)
                  .edit()
                  .putString("category_" + app.pkg, n == 0 ? "" : categories.get(n))
                  .apply();
              home();
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  void autostartSettings() {
    String[] choices = new String[installed.size() + 1];
    choices[0] = "Выключен";
    for (int i = 0; i < installed.size(); i++) choices[i + 1] = installed.get(i).label;
    new NovaDialog(this)
        .setTitle("Запуск приложения при старте Nova home Launcher")
        .setItems(
            choices,
            (d, n) -> {
              getPreferences(0)
                  .edit()
                  .putString("autostart", n == 0 ? "" : installed.get(n - 1).pkg)
                  .apply();
              Toast.makeText(
                      this,
                      "Автозапуск сохранён. Сработает при следующем запуске Nova home Launcher",
                      Toast.LENGTH_LONG)
                  .show();
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  void weatherPanel() {
    LinearLayout line = horizontal();
    weatherRow = line;
    page.addView(line, lp(-1, 24));
    weather =
        label(
            getPreferences(0).getString("weather_cache", "Погода: выберите город в настройках"),
            getPreferences(0).getInt("weather_size", 13),
            getPreferences(0).getInt("weather_color", MUTED));
    weather.setSingleLine(true);
    weather.setEllipsize(android.text.TextUtils.TruncateAt.END);
    weather.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nova_weather, 0, 0, 0);
    weather.setCompoundDrawablePadding(dp(5));
    line.addView(weather, new LinearLayout.LayoutParams(0, -1, 1));
    updateWeatherAppearance();
  }

  private void updateWeatherAppearance() {
    if (weather == null || weatherRow == null) return;
    boolean shown = getPreferences(0).getBoolean("show_weather", true)
        && getPreferences(0).getBoolean("show_status", true);
    weatherRow.setVisibility(shown ? View.VISIBLE : View.GONE);
    weather.setTextSize(getPreferences(0).getInt("weather_size", 13));
    weather.setTextColor(getPreferences(0).getInt("weather_color", MUTED));
    weather.setShadowLayer(getPreferences(0).getBoolean("weather_shadow", true) ? dp(2) : 0,
        0, dp(1), Color.BLACK);
    if (shown) loadWeather();
  }

  void weatherCity() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(getPreferences(0).getString("weather_city", ""));
    AlertDialog dialog =
        new NovaDialog(this)
            .setTitle("Поиск города")
            .setMessage("Введите город и нажмите «Найти». Данные Open-Meteo.")
            .setView(input)
            .setPositiveButton("Найти", null)
            .setNegativeButton("Отмена", null)
            .create();
    dialog.setOnShowListener(
        d -> {
          input.requestFocus();
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    String query = input.getText().toString().trim();
                    if (query.length() < 2) {
                      input.setError("Введите хотя бы две буквы");
                      return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.setMessage("Ищем город…");
                    weatherWorker.execute(
                        () -> {
                          try {
                            org.json.JSONArray places =
                                weatherRepository().json("https://geocoding-api.open-meteo.com/v1/search?count=8&language=ru&name="
                                        + java.net.URLEncoder.encode(query, "UTF-8"))
                                    .optJSONArray("results");
                            handler.post(
                                () -> {
                                  if (isDestroyed() || !dialog.isShowing()) return;
                                  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                  if (places == null || places.length() == 0) {
                                    dialog.setMessage(
                                        "Город не найден. Попробуйте другое название.");
                                    return;
                                  }
                                  String[] labels = new String[places.length()];
                                  for (int i = 0; i < labels.length; i++) {
                                    org.json.JSONObject place = places.optJSONObject(i);
                                    labels[i] =
                                        place.optString("name")
                                            + ", "
                                            + place.optString("admin1")
                                            + ", "
                                            + place.optString("country");
                                  }
                                  dialog.dismiss();
                                  new NovaDialog(this)
                                      .setTitle("Выберите город")
                                      .setItems(
                                          labels,
                                          (d2, n) -> {
                                            org.json.JSONObject place = places.optJSONObject(n);
                                            if (weatherTask != null) weatherTask.cancel(true);
                                            weatherRequests.invalidate();
                                            retryWeatherAfter = 0;
                                            getPreferences(0)
                                                .edit()
                                                .putString("weather_city", place.optString("name"))
                                                .putString(
                                                    "weather_lat", place.optString("latitude"))
                                                .putString(
                                                    "weather_lon", place.optString("longitude"))
                                                .remove("weather_cache")
                                                .putLong("weather_time", 0)
                                                .apply();
                                            loadWeather();
                                            ((ScrollView) page.getParent()).smoothScrollTo(0, 0);
                                          })
                                      .setNegativeButton("Отмена", null)
                                      .show();
                                });
                          } catch (Exception e) {
                            handler.post(
                                () -> {
                                  if (!isDestroyed() && dialog.isShowing()) {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    dialog.setMessage(
                                        "geocoding-api.open-meteo.com · " + WeatherRepository.describe(e));
                                  }
                                });
                          }
                        });
                  });
        });
    dialog.show();
  }

  private WeatherRepository weatherRepository() {
    String version = "";
    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (PackageManager.NameNotFoundException ignored) {
      // The package manager can be unavailable during shutdown.
    }
    return new WeatherRepository(version, getSharedPreferences("met_forecast_cache", MODE_PRIVATE));
  }

  void loadWeather() {
    if (SystemClock.elapsedRealtime() < retryWeatherAfter) return;
    String city = getPreferences(0).getString("weather_city", "");
    if (!weatherRequests.shouldLoad(
        city, System.currentTimeMillis(), getPreferences(0).getLong("weather_time", 0))) return;
    final int generation = weatherRequests.begin();
    final String latitude = getPreferences(0).getString("weather_lat", "");
    final String longitude = getPreferences(0).getString("weather_lon", "");
    if (weather != null && !getPreferences(0).contains("weather_cache"))
      weather.setText(city + ": загружаем погоду…");
    android.net.ConnectivityManager connectivity =
        (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    android.net.NetworkCapabilities network = connectivity == null ? null
        : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
    String connection = network == null ? "Нет активной сети"
        : network.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ? "Android подтвердил доступ в интернет" : "Android не подтвердил доступ в интернет";
    weatherTask = weatherWorker.submit(
        () -> {
          String result;
          boolean ok = false;
          String weatherError = "";
          String weatherDiagnostics = connection;
          try {
            if (network == null) throw new java.io.IOException(connection);
            String lat = latitude;
            String lon = longitude;
            if (lat.isEmpty() || lon.isEmpty()) {
              org.json.JSONArray places =
                  weatherRepository().json("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name="
                          + java.net.URLEncoder.encode(city, "UTF-8"))
                      .optJSONArray("results");
              if (places == null || places.length() == 0)
                throw new java.io.IOException("Город не найден");
              org.json.JSONObject place = places.getJSONObject(0);
              lat = place.getString("latitude");
              lon = place.getString("longitude");
            }
            WeatherRepository.Forecast forecast = weatherRepository().forecast(lat, lon);
            weatherDiagnostics += "\n" + forecast.diagnostics;
            int code = forecast.weatherCode;
            String sky =
                code < 0
                    ? ""
                    : code == 0
                        ? "ясно"
                        : code <= 3
                            ? "облачно"
                            : code <= 48
                                ? "туман"
                                : code <= 67
                                    ? "дождь"
                                    : code <= 77 ? "снег" : code <= 86 ? "осадки" : "гроза";
            result =
                city
                    + ": "
                    + Math.round(forecast.temperature)
                    + " °C"
                    + (sky.isEmpty() ? "" : ", " + sky)
                    + " · "
                    + forecast.provider
                    + " · "
                    + new SimpleDateFormat("HH:mm", new Locale("ru")).format(new Date(forecast.fetchedAt));
            ok = true;
          } catch (Exception e) {
            weatherError = WeatherRepository.describe(e);
            weatherDiagnostics += "\n" + weatherError;
            result =
                getPreferences(0).getString("weather_cache", city + ": погода недоступна")
                    + (getPreferences(0).contains("weather_cache") ? " · данные устарели" : "");
          }
          final String text = result;
          final String error = weatherError;
          final String diagnostics = weatherDiagnostics;
          final boolean success = ok;
          handler.post(
              () -> {
                if (!weatherRequests.complete(generation)) return;
                if (isDestroyed() || !city.equals(getPreferences(0).getString("weather_city", "")))
                  return;
                getPreferences(0).edit().putString("weather_error", error)
                    .putString("weather_diagnostics", diagnostics).apply();
                if (!success) retryWeatherAfter = SystemClock.elapsedRealtime() + 60_000L;
                if (success)
                  getPreferences(0)
                      .edit()
                      .putString("weather_cache", text)
                      .putLong("weather_time", System.currentTimeMillis())
                      .apply();
                if (weather != null) weather.setText(text);
                if (settingsPanel != null && settingsPanel.isShowing()
                    && settingsPanel.isPage("Погода")) settingsPanel.refresh();
              });
        });
  }
}
