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
  private final Map<String, View> launcherRows = new LinkedHashMap<>();
  private final Map<String, LinearLayout> homeRowViews = new LinkedHashMap<>();
  private View channelStatus;
  private View focusedAppRow;
  private String pendingFocusTag;
  private final Runnable saveCardFocus = () -> {
    if (pendingFocusTag != null) {
      getPreferences(0).edit().putString("last_focus", pendingFocusTag).apply();
      pendingFocusTag = null;
    }
  };
  private ScrollView launcherScroll;
  private View launcherHeader;
  private TextView date;
  private static final Locale RUSSIAN = new Locale("ru", "RU");
  private static final String STATUS_BLOCK_ORDER = "status_block_order";
  private static final String HOME_ROW_ORDER = "home_row_order";
  private static final String CONTROLS_BLOCK = "controls";
  private static final String CLOCK_BLOCK = "clock";
  private static final int[] SLEEP_MINUTES = {0, 15, 30, 60, 90};
  private java.text.DateFormat clockFormatter, dateFormatter;
  private View wallpaperShade;
  private View nightShade;
  private float nightShadeTarget;
  private final Runnable nightShadeTick = this::updateNightShade;
  private ImageView launcherWallpaper;
  private TextureView wallpaperVideo;
  private int homeScrollBeforeSettings;
  private String focusBeforeSettings;
  private boolean settingsUnlocked;
  private int pinFailures;
  private long pinBlockedUntil;
  private final java.util.concurrent.ExecutorService authWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private FrameLayout launcherView;
  private View screensaver;
  private boolean sleepDimmed;
  private long sleepDeadline;
  private float brightnessBeforeSleep;
  private final Runnable sleepAlarm = this::finishSleepTimer;
  private View focusBeforeScreensaver;
  private int screensaverWakeKey = KeyEvent.KEYCODE_UNKNOWN;
  private boolean screensaverWakeTouch;
  private final Runnable idleScreensaver = this::showScreensaver;
  private Drawable currentWallpaper;
  private final java.util.concurrent.ExecutorService dailyPhotoWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private DailyPhoto.Photo dailyPhoto;
  private boolean dailyPhotoLoading;
  private boolean dailyPhotoFailed;
  private String dailyPhotoMessage = "";
  private String dailyPhotoArchiveDay = "";
  private String dailyPhotoArchiveOpenedOn = "";
  private long nextDailyPhotoAttempt;
  private long nextDailyPhotoCheck;
  private android.media.MediaPlayer wallpaperPlayer;
  private LinearLayout statusBar;
  private TvSettingsPanel settingsPanel;
  private boolean settingsOpen;
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
  private boolean artworkScanPending;
  private final Runnable artworkScan = () -> {
    artworkScanPending = false;
    refreshVisibleArtwork();
  };
  private final android.util.LruCache<String, Bitmap> artworkCache =
      new android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap value) {
          return value.getAllocationByteCount();
        }
      };
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final FrameSample frameSample = new FrameSample(handler);
  private final Runnable endFrameSample = this::endPerformanceSample;
  private String performanceReport = "";
  private boolean checkingNetwork;
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
          if (resumed && SystemClock.elapsedRealtime() >= nextDailyPhotoCheck) {
            nextDailyPhotoCheck = SystemClock.elapsedRealtime() + 60_000;
            requestDailyPhoto(false);
          }
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
    if (b != null) sleepDeadline = b.getLong("sleep_deadline");
    scheduleSleepAlarm();
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
    out.putLong("sleep_deadline", sleepDeadline);
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
    hideScreensaver();
    ScrollView sc = new ScrollView(this);
    launcherScroll = sc;
    sc.setFillViewport(true);
    sc.setVerticalScrollBarEnabled(false);
    sc.setHorizontalScrollBarEnabled(false);
    sc.setOnScrollChangeListener(
        (View v, int x, int y, int oldX, int oldY) -> scheduleArtworkScan());
    launcherView = new FrameLayout(this);
    nightShade = new View(this);
    nightShade.setTag("wallpaper.night_shade");
    nightShade.setBackgroundColor(Color.BLACK);
    nightShade.setAlpha(0f);
    nightShadeTarget = 0f;
    launcherView.addView(nightShade, new FrameLayout.LayoutParams(-1, -1));
    launcherView.addView(sc, new FrameLayout.LayoutParams(-1, -1));
    updateWallpaperLayer();
    updateNightShade();
    setContentView(launcherView);
    page = vertical();
    applyLauncherPadding();
    sc.addView(page);
    header();
    gap(page, 6);
    if (getPreferences(0).getBoolean("show_banner", false)) banner();
    gap(page, 14);
    content = vertical();
    page.addView(content);
    home();
    applyKeepScreenOn();
    scheduleScreensaver();
  }

  private void updateWallpaperLayer() {
    releaseWallpaper();
    FrameLayout stage = launcherView;
    stage.setBackground(background());
    if (launcherWallpaper != null) stage.removeView(launcherWallpaper);
    if (wallpaperVideo != null) stage.removeView(wallpaperVideo);
    if (wallpaperShade != null) stage.removeView(wallpaperShade);
    ImageView wallpaperImage = new ImageView(this);
    launcherWallpaper = wallpaperImage;
    wallpaperVideo = null;
    wallpaperShade = null;
    wallpaperImage.setScaleType(
        getPreferences(0).getBoolean("wallpaper_fit", false)
            ? ImageView.ScaleType.FIT_CENTER
            : ImageView.ScaleType.CENTER_CROP);
    stage.addView(wallpaperImage, 0, new FrameLayout.LayoutParams(-1, -1));
    String wallpaper = getPreferences(0).getString("wallpaper", "");
    if (usesDailyWallpaper()) {
      applyDailyWallpaper();
    } else if (!wallpaper.isEmpty()
        && "video".equals(getPreferences(0).getString("wallpaper_type", "image"))) {
      TextureView video = new TextureView(this);
      wallpaperVideo = video;
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
      stage.addView(video, 1, new FrameLayout.LayoutParams(-1, -1));
      View dim = new View(this);
      wallpaperShade = dim;
      dim.setBackgroundColor(Color.BLACK);
      dim.setAlpha(getPreferences(0).getInt("wallpaper_dim", 55) / 100f);
      stage.addView(dim, 2, new FrameLayout.LayoutParams(-1, -1));
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
  }

  private boolean usesDailyWallpaper() {
    return getPreferences(0).getBoolean(DailyPhoto.WALLPAPER, false);
  }

  private void updateNightShade() {
    handler.removeCallbacks(nightShadeTick);
    if (nightShade == null) return;
    SharedPreferences preferences = getPreferences(0);
    boolean enabled = preferences.getBoolean(NightDimming.ENABLED, false);
    float alpha = 0f;
    if (enabled) {
      Calendar now = Calendar.getInstance();
      int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
      alpha = NightDimming.amountAt(minute, nightTime(NightDimming.START, NightDimming.DEFAULT_START),
          nightTime(NightDimming.END, NightDimming.DEFAULT_END),
          Appearance.clamp(preferences.getInt(NightDimming.AMOUNT, NightDimming.DEFAULT_AMOUNT), 0, 100),
          Appearance.clamp(preferences.getInt(NightDimming.STEP, NightDimming.DEFAULT_STEP), 1, 100))
          / 100f;
      if (resumed) handler.postDelayed(nightShadeTick,
          60_000L - now.get(Calendar.SECOND) * 1000L - now.get(Calendar.MILLISECOND));
    }
    if (nightShadeTarget != alpha) {
      nightShadeTarget = alpha;
      nightShade.animate().cancel();
      if (resumed && nightShade.isAttachedToWindow()) nightShade.animate().alpha(alpha).setDuration(1000).start();
      else nightShade.setAlpha(alpha);
    }
  }

  private int nightTime(String key, int defaultValue) {
    return Appearance.clamp(getPreferences(0).getInt(key, defaultValue), 0, 1439);
  }

  private void requestDailyPhoto(boolean force) {
    String today = DailyPhoto.today();
    if (!today.equals(dailyPhotoArchiveOpenedOn)) dailyPhotoArchiveDay = "";
    if (!force && !dailyPhotoArchiveDay.isEmpty()) return;
    requestDailyPhoto(today, force, false);
  }

  private void requestDailyPhoto(String date, boolean force, boolean archive) {
    if (dailyPhotoLoading || isDestroyed() || (!usesDailyWallpaper()
        && !getPreferences(0).getBoolean(NovaScreensaver.DAILY_PHOTO, false))) return;
    if (!force && ((dailyPhoto != null && date.equals(dailyPhoto.date))
        || SystemClock.elapsedRealtime() < nextDailyPhotoAttempt)) return;
    dailyPhotoLoading = true;
    dailyPhotoFailed = false;
    dailyPhotoMessage = "";
    DailyPhoto source = new DailyPhoto(getFilesDir());
    boolean readCache = dailyPhoto == null;
    String requestedOn = DailyPhoto.today();
    dailyPhotoWorker.execute(() -> {
      DailyPhoto.Photo cached = readCache ? source.cached() : null;
      if (cached != null) handler.post(() -> showDailyPhoto(cached));
      DailyPhoto.Photo downloaded = null;
      boolean failed = false;
      if (force || cached == null || !date.equals(cached.date)) {
        try {
          downloaded = source.download(date, !archive);
        } catch (Exception ignored) {
          failed = true;
        }
      }
      DailyPhoto.Photo result = downloaded;
      boolean failedRequest = failed;
      handler.post(() -> {
        if (isDestroyed()) return;
        dailyPhotoLoading = false;
        dailyPhotoFailed = failedRequest;
        nextDailyPhotoAttempt = SystemClock.elapsedRealtime() + (failedRequest ? 15 * 60_000L : 0);
        if (result != null) {
          if (force && dailyPhoto != null && result.date.equals(dailyPhoto.date)
              && result.source.equals(dailyPhoto.source)) {
            dailyPhotoMessage = "Фото за эту дату уже показано.";
          }
          dailyPhotoArchiveDay = archive ? result.date : "";
          dailyPhotoArchiveOpenedOn = archive ? requestedOn : "";
          showDailyPhoto(result);
        }
        refreshDailyPhotoPage();
      });
    });
  }

  private void showDailyPhoto(DailyPhoto.Photo photo) {
    if (isDestroyed()) return;
    dailyPhoto = photo;
    if (usesDailyWallpaper()) applyDailyWallpaper();
    if (screensaver instanceof NovaScreensaver
        && getPreferences(0).getBoolean(NovaScreensaver.DAILY_PHOTO, false)) {
      ((NovaScreensaver) screensaver).setDailyPhoto(photo.bitmap);
    }
  }

  private void applyDailyWallpaper() {
    if (launcherWallpaper == null) return;
    currentWallpaper = dailyPhoto == null ? null
        : new BitmapDrawable(getResources(), dailyPhoto.bitmap);
    launcherWallpaper.setImageDrawable(currentWallpaper);
    if (currentWallpaper != null) {
      int dim = Appearance.clamp(getPreferences(0).getInt("wallpaper_dim", 55), 0, 90);
      currentWallpaper.setColorFilter((dim * 255 / 100) << 24, PorterDuff.Mode.SRC_ATOP);
    }
  }

  private void refreshDailyPhotoPage() {
    if (settingsPanel != null && settingsPanel.isShowing() && settingsPanel.isPage("Фото дня")) {
      settingsPanel.refresh();
    }
  }

  private void applyKeepScreenOn() {
    int flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    int value = !sleepDimmed && getPreferences(0).getBoolean("keep_screen_on", false) ? flag : 0;
    getWindow().setFlags(value, flag);
    if (settingsPanel != null) settingsPanel.getWindow().setFlags(value, flag);
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
    logo.setSingleLine(true);
    logo.setEllipsize(android.text.TextUtils.TruncateAt.END);
    bar.addView(logo, new LinearLayout.LayoutParams(0, -1, 1));
    LinearLayout blocks = horizontal();
    blocks.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(blocks);
    StatusBarView controls = new StatusBarView(this);
    controls.setTag("header.controls");
    StatusBarView status = new StatusBarView(this);
    status.setTag("header.information");
    statusBar = status;
    for (String key : orderedKeys(STATUS_BLOCK_ORDER, Arrays.asList(CONTROLS_BLOCK, CLOCK_BLOCK))) {
      LinearLayout.LayoutParams bounds = lp(-2, 38);
      if (blocks.getChildCount() != 0) bounds.setMarginStart(dp(8));
      blocks.addView(CONTROLS_BLOCK.equals(key) ? controls : status, bounds);
    }
    menu = controls;
    if (getPreferences(0).getBoolean("show_apps_button", true))
      nav("Приложения", "apps_button", R.drawable.nova_apps, this::openLibrary).setTag("header.apps");
    if (getPreferences(0).getBoolean("show_search_button", true))
      nav("Поиск", "search_button", R.drawable.nova_ui_search, this::searchApps).setTag("header.search");
    if (getPreferences(0).getBoolean("show_settings_button", true)) {
      TextView settingsButton = nav("Настройки", "settings_button", R.drawable.nova_ui_settings,
          this::settings);
      settingsButton.setTag("header.settings");
      settingsButton.setContentDescription("Настройки Nova. Удерживайте OK для настроек Android");
      settingsButton.setOnLongClickListener(v -> {
        safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
        return true;
      });
      RemoteOkHandler.bind(settingsButton);
    }
    if (getPreferences(0).getBoolean("show_timer_button", true)) {
      nav("Таймер сна", "timer_button", R.drawable.nova_ui_sleep_timer,
          () -> openPanelPage(this::sleepTimerSettings)).setTag("header.timer");
    }
    notificationCounter = null;
    boolean showStatus = getPreferences(0).getBoolean("show_status", true);
    if (showStatus && getPreferences(0).getBoolean("show_network", true)) {
      String network = networkStatus();
      int icon = "Ethernet".equals(network) ? R.drawable.nova_ethernet
          : "Нет сети".equals(network) ? R.drawable.nova_offline : R.drawable.nova_wifi;
      TextView connection = controls.item(network, "Сеть: " + network, icon, false);
      statusAction(connection, "header.network", this::openNetworkSettings);
    }
    if (showStatus && getPreferences(0).getBoolean("show_notifications", true)) {
      notificationCounter = controls.item("", "Уведомления", R.drawable.nova_bell, true);
      statusAction(notificationCounter, "header.notifications", () -> openPanelPage(this::notificationSettings));
    }
    weather = getPreferences(0).getBoolean("show_weather", true)
        ? status.item(cachedWeather(), "Погода", R.drawable.nova_weather, true) : null;
    if (weather != null) weather.setTag("header.weather");
    styleStatusText(weather, "weather", 13, true);
    String clockFormat = getPreferences(0).getString("clock_format", "HH:mm");
    clockFormatter = "system".equals(clockFormat)
        ? android.text.format.DateFormat.getTimeFormat(this)
        : new SimpleDateFormat("hh:mm a".equals(clockFormat) ? "hh:mm a" : "HH:mm", RUSSIAN);
    dateFormatter = new SimpleDateFormat(
        getPreferences(0).getInt("date_format", 0) == 1 ? "EEE, d MMM" : "d MMM", RUSSIAN);
    date = getPreferences(0).getBoolean("show_date", true)
        ? status.item("", "Дата", R.drawable.nova_ui_calendar, true) : null;
    if (date != null) date.setTag("header.date");
    styleStatusText(date, "date", 15, false);
    clock = getPreferences(0).getBoolean("show_clock", true)
        ? status.item("", "Время", R.drawable.nova_clock, true) : null;
    if (clock != null) clock.setTag("header.clock");
    styleStatusText(clock, "clock", 17, true);
    controls.setVisibility(controls.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    status.setVisibility(showStatus && status.getChildCount() != 0 ? View.VISIBLE : View.GONE);
    updateClock();
  }

  private void statusAction(TextView view, String key, Runnable action) {
    view.setTag(key);
    view.setFocusable(true);
    view.setBackground(NovaUi.interaction(this, accent()));
    view.setOnClickListener(v -> action.run());
  }

  private void openNetworkSettings() {
    Intent network = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
    safeStart(canOpen(network) ? network : new Intent(android.provider.Settings.ACTION_SETTINGS));
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
      boolean granted = notificationGranted();
      int count = granted ? visibleNotifications().size() : 0;
      String value = granted ? String.valueOf(count) : "—";
      setChangedText(notificationCounter, getPreferences(0).getInt("status_label_mode", 2) == 1
          ? "Уведомления " + value : value);
      notificationCounter.setContentDescription(
          granted ? "Уведомления: " + count : "Уведомления: предоставить доступ");
      notificationCounter.setVisibility(
          granted && count == 0 && getPreferences(0).getBoolean("hide_empty_notifications", false)
              ? View.GONE
              : View.VISIBLE);
    }
    if (weather != null) loadWeather();
    Date now = new Date();
    if (date != null) setChangedText(date, dateFormatter.format(now));
    if (clock != null) setChangedText(clock, clockFormatter.format(now));
  }

  @Override
  protected void onDestroy() {
    if (settingsPanel != null) settingsPanel.dismiss();
    handler.removeCallbacksAndMessages(null);
    if (frameSample.isRunning()) frameSample.stop();
    hideScreensaver();
    releaseWallpaper();
    authWorker.shutdownNow();
    worker.shutdownNow();
    weatherWorker.shutdownNow();
    artworkWorker.shutdownNow();
    dailyPhotoWorker.shutdownNow();
    artworkCache.evictAll();
    unregisterReceiver(packages);
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    scheduleArtworkScan();
    updateNightShade();
    requestDailyPhoto(false);
    finishSleepTimer();
    scheduleScreensaver();
    loadWeather();
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
    if (content != null && !settingsOpen && (returnFocusTag != null || content.findFocus() == null)) {
      if (returnFocusTag == null && getPreferences(0).getBoolean("remember_focus", true))
        returnFocusTag = getPreferences(0).getString("last_focus", null);
      restoreAppFocus();
    }
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(saveCardFocus);
    saveCardFocus.run();
    handler.removeCallbacks(nightShadeTick);
    if (frameSample.isRunning()) endPerformanceSample();
    handler.removeCallbacks(idleScreensaver);
    hideScreensaver();
    screensaverWakeKey = KeyEvent.KEYCODE_UNKNOWN;
    screensaverWakeTouch = false;
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
    View card = page.findViewWithTag(returnFocusTag);
    if (card != null) card.post(() -> card.requestFocus());
    returnFocusTag = null;
  }

  private static void setChangedText(TextView view, String text) {
    if (!android.text.TextUtils.equals(view.getText(), text)) view.setText(text);
  }

  private void styleStatusText(TextView view, String key, int size, boolean shadow) {
    if (view == null) return;
    view.setTextSize(getPreferences(0).getInt(key + "_size", size));
    int color = getPreferences(0).getInt(key + "_color",
        getPreferences(0).getInt("status_color", WHITE));
    view.setTextColor(color);
    view.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
    view.setShadowLayer(getPreferences(0).getBoolean(key + "_shadow", shadow) ? dp(2) : 0,
        0, dp(1), Color.BLACK);
  }

  TextView nav(String s, String key, int icon, Runnable r) {
    int mode = getPreferences(0).getInt(key + "_mode",
        getPreferences(0).getInt("status_label_mode", 2));
    TextView t = label(mode == 0 ? "" : s, 14, WHITE);
    t.setSingleLine(true);
    styleStatusText(t, key, 14, false);
    t.setContentDescription(s);
    if (mode != 1) {
      Drawable image = getDrawable(icon).mutate();
      image.setBounds(0, 0, dp(18), dp(18));
      t.setCompoundDrawablesRelative(image, null, null, null);
      t.setCompoundDrawablePadding(dp(5));
      t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(t.getCurrentTextColor()));
    }
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(10), 0, dp(10), 0);
    t.setBackground(shape(0x99172131, 14));
    t.setFocusable(true);
    t.setOnClickListener(v -> r.run());
    t.setOnFocusChangeListener(
        (v, f) -> {
          t.setBackground(shape(f ? 0xffd9eaff : 0x99172131, 14));
          if (f) {
            t.setTextColor(0xff10182a);
            t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(0xff10182a));
          } else styleStatusText(t, key, 14, false);
        });
    LinearLayout.LayoutParams bounds = lp(-2, 38);
    if (menu.getChildCount() != 0) bounds.setMarginStart(dp(getPreferences(0).getInt("status_gap", 8)));
    menu.addView(t, bounds);
    return t;
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
    homeRowViews.clear();
    channelStatus = null;
    focusedAppRow = null;
    fullRows.clear();
    artworkViews.clear();
    artworkLoading.clear();
    activeRows = 0;
    appRows.clear();
    launcherRows.clear();
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
    Map<String, HomeRow> rows = homeRows();
    for (String key : orderedKeys(HOME_ROW_ORDER, rows.keySet())) {
      addHomeRow(key, rows.get(key), content.getChildCount());
    }
    updateChannelStatus();
    loadWeather();
  }

  private void updateChannelStatus() {
    if (channelStatus != null) content.removeView(channelStatus);
    channelStatus = null;
    if (getPreferences(0).getBoolean("show_channels", false)) {
      if (feeds == null) {
        channelStatus = label("Загрузка рекомендаций…", 13, MUTED);
        loadChannels();
      } else {
        if (System.currentTimeMillis() - feedsLoaded > 300000) loadChannels();
        if (feeds.channels.isEmpty()) channelStatus = label(feeds.message, 13, MUTED);
      }
      if (channelStatus != null) content.addView(channelStatus);
    }
  }

  private static final class HomeRow {
    final String title, icon;
    final Runnable render;
    final TvSettingsPanel.Page settings;

    HomeRow(String title, String icon, Runnable render, TvSettingsPanel.Page settings) {
      this.title = title;
      this.icon = icon;
      this.render = render;
      this.settings = settings;
    }
  }

  private void addHomeRow(String key, HomeRow row, int index) {
    LinearLayout parent = content;
    LinearLayout section = vertical();
    section.setTag("home.row:" + key);
    content = section;
    try {
      row.render.run();
    } finally {
      content = parent;
    }
    if (section.getChildCount() == 0) return;
    parent.addView(section, index, lp(-1, -2));
    homeRowViews.put(key, section);
  }

  private void updateHomeRowVisibility(String key, boolean visible) {
    if (currentScreen != 0 || content == null) return;
    LinearLayout section = homeRowViews.get(key);
    if (section != null) {
      section.setVisibility(visible ? View.VISIBLE : View.GONE);
    } else if (visible) {
      Map<String, HomeRow> rows = homeRows();
      int index = 0;
      for (String rowKey : orderedKeys(HOME_ROW_ORDER, rows.keySet())) {
        if (key.equals(rowKey)) {
          addHomeRow(key, rows.get(key), index);
          break;
        }
        LinearLayout previous = homeRowViews.get(rowKey);
        if (previous != null) index = content.indexOfChild(previous) + 1;
      }
    }
    refreshVisibleArtwork();
  }

  private Map<String, HomeRow> homeRows() {
    Map<String, HomeRow> rows = new LinkedHashMap<>();
    rows.put("favorites", new HomeRow("Избранное", "☆",
        () -> renderAppRow("☆", "Избранное", "favorite_order", getFavorites(), "show_favorites"),
        p -> rowVisibility(p, "Избранное", "☆", "show_favorites", true)));
    rows.put("tv", new HomeRow("ТВ-приложения", "▤",
        () -> renderAppRow("▣", "ТВ-приложения", "app_order", appsByType(true), "show_all"),
        p -> rowVisibility(p, "ТВ-приложения", "▤", "show_all", true)));
    rows.put("mobile", new HomeRow("Мобильные приложения", "▯",
        () -> {
          if (getPreferences(0).getBoolean("show_all", true))
            renderAppRow("▯", "Мобильные приложения", "mobile_order", appsByType(false), "show_mobile");
        }, p -> rowVisibility(p, "Мобильные приложения", "▯", "show_mobile", true)));
    for (String category : readIds("categories")) {
      rows.put("category:" + category, new HomeRow(category, "folder", () -> {
        if (readIds("hidden_categories").contains(category)) return;
        List<App> apps = new ArrayList<>();
        for (App app : installed)
          if (category.equals(getPreferences(0).getString("category_" + app.pkg, ""))) apps.add(app);
        rowTitle("≡", category);
        if (!visibleApps(apps, false).isEmpty()) rowApps(apps, false, "category_order_" + category);
      }, p -> categoryRowSettings(p, category)));
    }
    rows.put("inputs", new HomeRow("Источники сигнала", "⇥", () -> {
      if (!getPreferences(0).getBoolean("show_inputs", false) || availableTvInputs().isEmpty()) return;
      rowTitle("⇥", "Источники сигнала");
      LinearLayout inputs = horizontal();
      content.addView(inputs);
      action(inputs, "Выбрать вход телевизора", this::showInputs);
    }, p -> rowVisibility(p, "Источники сигнала", "⇥", "show_inputs", false)));
    if (feeds != null) for (TvFeeds.Channel channel : feeds.channels) {
      rows.put("channel:" + channel.id, new HomeRow(channel.title, "▤", () -> {
        if (getPreferences(0).getBoolean("show_channels", false)
            && !readIds("hidden_channels").contains(channel.id)) renderChannel(channel);
      }, p -> {
        p.title("▤", channel.title, channel.source);
        p.toggle("eye", "Показывать ряд", "", !readIds("hidden_channels").contains(channel.id),
            value -> setRowVisibility("hidden_channels", channel.id, value));
      }));
    }
    return rows;
  }

  private List<App> appsByType(boolean tv) {
    List<App> apps = new ArrayList<>();
    for (App app : installed) if (app.tv == tv) apps.add(app);
    return apps;
  }

  private void renderAppRow(String icon, String title, String key, List<App> apps, String setting) {
    if (!getPreferences(0).getBoolean(setting, true) || visibleApps(apps, false).isEmpty()) return;
    rowTitle(icon, title);
    rowApps(apps, "favorite_order".equals(key), key);
  }

  private List<String> orderedKeys(String preference, Collection<String> available) {
    Set<String> pending = new LinkedHashSet<>(available);
    List<String> order = new ArrayList<>();
    for (String key : readIds(preference)) if (pending.remove(key)) order.add(key);
    order.addAll(pending);
    return order;
  }

  private void saveOrderedKeys(String preference, List<String> order) {
    List<String> saved = readIds(preference);
    saved.removeAll(order);
    saved.addAll(0, order);
    getPreferences(0).edit().putString(preference, android.text.TextUtils.join("|", saved)).apply();
  }

  void rowTitle(String icon, String title) {
    gap(content, getPreferences(0).getInt("row_gap", 12));
    TextView heading = label("", 17, WHITE);
    heading.setTag(new RowHeading(icon, title));
    heading.setPadding(0, 0, 0, dp(6));
    content.addView(heading);
    updateRowHeading(heading);
  }

  private static final class RowHeading {
    final String icon, title;
    RowHeading(String icon, String title) {
      this.icon = icon;
      this.title = title;
    }
  }

  private void updateRowHeading(TextView heading) {
    RowHeading text = (RowHeading) heading.getTag();
    heading.setText((getPreferences(0).getBoolean("show_category_icons", true)
        ? text.icon + "  " : "") + text.title);
    heading.setVisibility(getPreferences(0).getBoolean("show_titles", true) ? View.VISIBLE : View.GONE);
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
    launcherRows.put(rowKey, scroll);
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
              focusAppRow(scroll);
              if (getPreferences(0).getBoolean("remember_focus", true)) {
                pendingFocusTag = (String) card.getTag();
                handler.removeCallbacks(saveCardFocus);
                handler.postDelayed(saveCardFocus, 250);
              }
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

  private void focusAppRow(View row) {
    View previous = focusedAppRow;
    if (previous == row) return;
    focusedAppRow = row;
    float inactive = getPreferences(0).getInt("inactive_opacity", 75) / 100f;
    if (previous == null) {
      for (View appRow : appRows) appRow.setAlpha(appRow == row ? 1f : inactive);
    } else {
      previous.setAlpha(inactive);
      row.setAlpha(1f);
    }
    if (getPreferences(0).getBoolean("pin_row", false)) {
      View section = (View) row.getParent();
      int top = content.getTop() + row.getTop() + (section == content ? 0 : section.getTop());
      launcherScroll.smoothScrollTo(0, top - dp(72));
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
    int[] icons = {
      R.drawable.nova_ui_star, R.drawable.nova_ui_horizontal, R.drawable.nova_ui_settings,
      R.drawable.nova_ui_info, R.drawable.nova_ui_trash, R.drawable.nova_ui_folder,
      readIds("hidden").contains(a.pkg) ? R.drawable.nova_ui_eye : R.drawable.nova_ui_eye_off
    };
    new NovaDialog(this)
        .setTitle(a.label)
        .setAdapter(
            new NovaDialog.IconOptions(this, items, icons),
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
    scheduleScreensaver();
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
      scheduleScreensaver();
      refreshApps();
      renderMoveScreen();
      restoreAppFocus();
    } else if (currentScreen != 0) {
      home();
      restoreAppFocus();
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int key = event.getKeyCode();
    if (screensaver != null) {
      screensaverWakeKey =
          event.getAction() == KeyEvent.ACTION_DOWN ? key : KeyEvent.KEYCODE_UNKNOWN;
      hideScreensaver();
      scheduleScreensaver();
      return true;
    }
    if (screensaverWakeKey != KeyEvent.KEYCODE_UNKNOWN && screensaverWakeKey == key) {
      if (event.getAction() == KeyEvent.ACTION_UP) screensaverWakeKey = KeyEvent.KEYCODE_UNKNOWN;
      return true;
    }
    if (key == KeyEvent.KEYCODE_MENU) {
      scheduleScreensaver();
      if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) settings();
      return true;
    }
    if (currentScreen == 0 && movingPackage == null && event.getAction() == KeyEvent.ACTION_DOWN
        && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)
        && moveWithinHomeRow(key == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT)) {
      scheduleScreensaver();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  private boolean moveWithinHomeRow(int direction) {
    View focused = launcherScroll.findFocus();
    if (focused == null) return false;
    LinearLayout line = null;
    for (ViewParent ancestor = focused.getParent(); ancestor instanceof ViewGroup;
        ancestor = ancestor.getParent()) {
      if (!(ancestor instanceof LinearLayout)) continue;
      LinearLayout layout = (LinearLayout) ancestor;
      if (line == null && layout.getOrientation() == LinearLayout.HORIZONTAL) line = layout;
      if (layout.getTag() instanceof String && ((String) layout.getTag()).startsWith("home.row:")) {
        View next = FocusFinder.getInstance().findNextFocus(line == null ? layout : line, focused, direction);
        if (next != null) next.requestFocus(direction);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (screensaver != null) {
      hideScreensaver();
      scheduleScreensaver();
      screensaverWakeTouch = true;
    }
    if (screensaverWakeTouch) {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        screensaverWakeTouch = false;
      }
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  @Override
  public void onUserInteraction() {
    super.onUserInteraction();
    scheduleScreensaver();
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (!focused) {
      hideScreensaver();
      screensaverWakeKey = KeyEvent.KEYCODE_UNKNOWN;
      screensaverWakeTouch = false;
    }
    if (focused) finishSleepTimer();
    scheduleScreensaver();
  }

  private boolean canStartScreensaver() {
    return resumed && hasWindowFocus() && !settingsOpen && movingPackage == null
        && getPreferences(0).getBoolean(NovaScreensaver.ENABLED, false);
  }

  private void scheduleScreensaver() {
    handler.removeCallbacks(idleScreensaver);
    if (screensaver == null && canStartScreensaver()) {
      int minutes = Appearance.clamp(
          getPreferences(0).getInt(NovaScreensaver.DELAY, NovaScreensaver.DEFAULT_DELAY_MINUTES),
          1, 60);
      handler.postDelayed(idleScreensaver, minutes * 60_000L);
    }
  }

  private void showScreensaver() {
    if (!canStartScreensaver() || screensaver != null) return;
    boolean photo = getPreferences(0).getBoolean(NovaScreensaver.DAILY_PHOTO, false);
    boolean wallpaper = !photo && getPreferences(0).getBoolean(NovaScreensaver.WALLPAPER, false);
    NovaScreensaver overlay = new NovaScreensaver(this, wallpaper);
    if (photo && dailyPhoto != null) overlay.setDailyPhoto(dailyPhoto.bitmap);
    showIdleOverlay(overlay);
  }

  private void showIdleOverlay(View overlay) {
    focusBeforeScreensaver = launcherScroll.findFocus();
    screensaver = overlay;
    launcherScroll.setVisibility(View.INVISIBLE);
    launcherView.addView(screensaver, new FrameLayout.LayoutParams(-1, -1));
  }

  private void hideScreensaver() {
    if (screensaver == null) return;
    launcherView.removeView(screensaver);
    screensaver = null;
    if (sleepDimmed) {
      sleepDimmed = false;
      WindowManager.LayoutParams bounds = getWindow().getAttributes();
      bounds.screenBrightness = brightnessBeforeSleep;
      getWindow().setAttributes(bounds);
      applyKeepScreenOn();
    }
    launcherScroll.setVisibility(View.VISIBLE);
    if (focusBeforeScreensaver != null) {
      if (focusBeforeScreensaver.isShown()) focusBeforeScreensaver.requestFocus();
      else if (focusBeforeScreensaver.getTag() instanceof String) {
        returnFocusTag = (String) focusBeforeScreensaver.getTag();
        restoreAppFocus();
      }
    }
    focusBeforeScreensaver = null;
  }

  private void refreshHeader() {
    View focused = launcherHeader.findFocus();
    Object tag = focused == null ? null : focused.getTag();
    page.removeView(launcherHeader);
    header();
    View target = tag == null ? null : launcherHeader.findViewWithTag(tag);
    if (target != null) target.requestFocus();
  }

  void setSleepTimer(int minutes) {
    sleepDeadline = minutes == 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60_000L;
    scheduleSleepAlarm();
  }

  private void scheduleSleepAlarm() {
    handler.removeCallbacks(sleepAlarm);
    if (sleepDeadline != 0) {
      handler.postDelayed(sleepAlarm, Math.max(0, sleepDeadline - SystemClock.elapsedRealtime()));
    }
  }

  private void finishSleepTimer() {
    if (!resumed || sleepDeadline == 0 || SystemClock.elapsedRealtime() < sleepDeadline) return;
    if (settingsPanel != null && settingsPanel.isShowing()
        && settingsPanel.getWindow().getDecorView().hasWindowFocus()) {
      settingsPanel.dismiss();
    }
    if (!hasWindowFocus()) return;
    sleepDeadline = 0;
    handler.removeCallbacks(idleScreensaver);
    hideScreensaver();
    WindowManager.LayoutParams bounds = getWindow().getAttributes();
    brightnessBeforeSleep = bounds.screenBrightness;
    bounds.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
    sleepDimmed = true;
    getWindow().setAttributes(bounds);
    applyKeepScreenOn();
    View black = new View(this);
    black.setTag("nova.sleep");
    black.setBackgroundColor(Color.BLACK);
    showIdleOverlay(black);
  }

  private void sleepTimerSettings(TvSettingsPanel p) {
    p.title("sleep_timer", "Таймер сна",
        "Затемняет экран Nova. Режим ожидания зависит от настроек Android.");
    if (sleepDeadline == 0) {
      p.section("Таймер выключен");
    } else {
      p.countdown(sleepDeadline);
      p.action("sleep.cancel", "↺", "Отменить таймер", "", () -> {
        setSleepTimer(0);
        p.refresh();
      });
    }
    p.action("sleep.duration", "◷", "Установить таймер", "15, 30, 60 или 90 минут", () ->
        new NovaDialog(this).setTitle("Таймер сна")
            .setItems(new String[] {"Выключен", "15 минут", "30 минут", "60 минут", "90 минут"},
                (dialog, index) -> {
                  setSleepTimer(SLEEP_MINUTES[index]);
                  p.refresh();
                })
            .setNegativeButton("Закрыть", null)
            .show());
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
    if (settingsPanel == null || !settingsPanel.canResume())
      settingsPanel = new TvSettingsPanel(this, accent(), this::rootSettings);
    settingsPanel.show();
  }

  int settingsPanelTop() {
    return launcherScroll.getPaddingTop() + page.getPaddingTop() + dp(50);
  }

  void settingsVisibilityChanged(boolean open) {
    if (settingsOpen == open) return;
    settingsOpen = open;
    if (open && settingsPanel != null) watchPerformanceWindow(settingsPanel.getWindow());
    scheduleScreensaver();
    if (open) {
      homeScrollBeforeSettings = launcherScroll.getScrollY();
      View focused = launcherScroll.findFocus();
      focusBeforeSettings = focused != null && focused.getTag() instanceof String
          ? (String) focused.getTag() : null;
      launcherScroll.scrollTo(0, 0);
    }
    if (!open) settingsUnlocked = false;
    if (!open && content != null) {
      returnFocusTag =
          getPreferences(0).getBoolean("remember_focus", true)
                  ? (focusBeforeSettings != null
                  ? focusBeforeSettings : getPreferences(0).getString("last_focus", null))
              : null;
      launcherScroll.scrollTo(0, homeScrollBeforeSettings);
      View focused = launcherScroll.findFocus();
      if (focused != null && returnFocusTag != null && returnFocusTag.equals(focused.getTag())) {
        returnFocusTag = null;
      } else {
        restoreAppFocus();
      }
    }
  }

  void setBool(String key, boolean value) {
    getPreferences(0).edit().putBoolean(key, value).apply();
    pendingAppearance.add(key);
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
    if (changed.remove("keep_screen_on")) applyKeepScreenOn();
    if (changed.removeIf(key -> key.startsWith("wallpaper_night_"))) updateNightShade();
    if (changed.remove("wallpaper_dim")) {
      int dim = getPreferences(0).getInt("wallpaper_dim", 55);
      if (wallpaperShade != null) wallpaperShade.setAlpha(dim / 100f);
      if (currentWallpaper != null)
        currentWallpaper.setColorFilter((dim * 255 / 100) << 24, PorterDuff.Mode.SRC_ATOP);
    }
    if (changed.removeIf(key -> key.equals("show_status") || key.equals("show_network")
        || key.equals("show_clock") || key.startsWith("clock_") || key.startsWith("status_")
        || key.equals("show_date") || key.startsWith("date_")
        || key.equals("show_weather") || key.startsWith("weather_")
        || key.contains("_button")
        || key.equals("show_notifications") || key.equals("hide_empty_notifications"))) {
      refreshHeader();
    }
    if (changed.removeIf(key -> key.startsWith("margin_"))) {
      applyLauncherPadding();
      if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.updateWindowBounds();
    }
    if (changed.remove("show_favorites"))
      updateHomeRowVisibility("favorites", getPreferences(0).getBoolean("show_favorites", true));
    boolean tvVisible = getPreferences(0).getBoolean("show_all", true);
    boolean tvChanged = changed.remove("show_all");
    if (tvChanged) updateHomeRowVisibility("tv", tvVisible);
    if (changed.remove("show_mobile") || tvChanged)
      updateHomeRowVisibility("mobile", tvVisible && getPreferences(0).getBoolean("show_mobile", true));
    if (changed.remove("show_inputs"))
      updateHomeRowVisibility("inputs", getPreferences(0).getBoolean("show_inputs", false));
    if (changed.remove("show_channels") && currentScreen == 0) {
      boolean show = getPreferences(0).getBoolean("show_channels", false);
      if (feeds != null) for (TvFeeds.Channel channel : feeds.channels) {
        updateHomeRowVisibility("channel:" + channel.id,
            show && !readIds("hidden_channels").contains(channel.id));
      }
      updateChannelStatus();
    }
    boolean titlesChanged = changed.remove("show_titles");
    if (changed.remove("show_category_icons") || titlesChanged) {
      for (LinearLayout section : homeRowViews.values()) {
        for (int i = 0; i < section.getChildCount(); i++) {
          View child = section.getChildAt(i);
          if (child.getTag() instanceof RowHeading) updateRowHeading((TextView) child);
        }
      }
    }
    changed.remove("remember_focus");
    changed.remove("pin_row");
    if (changed.remove("inactive_opacity")) {
      float alpha = getPreferences(0).getInt("inactive_opacity", 75) / 100f;
      if (focusedAppRow != null) for (View row : appRows) {
        row.animate().cancel();
        row.setAlpha(row == focusedAppRow ? 1f : alpha);
      }
    }
    if (!changed.isEmpty()) rebuildLauncherRows();
  }

  private void rebuildLauncherRows() {
    int y = launcherScroll.getScrollY();
    Map<String, Integer> horizontalPositions = new HashMap<>();
    launcherRows.forEach((key, view) -> horizontalPositions.put(key, view.getScrollX()));
    home();
    launcherScroll.post(() -> {
      launcherRows.forEach((key, view) -> view.scrollTo(horizontalPositions.getOrDefault(key, 0), 0));
      launcherScroll.scrollTo(0, settingsOpen ? 0 : y);
    });
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
    p.title("⚙", "Настройки", "Nova home Launcher");
    p.action("root.home", "⌂", "Главный экран", "Ряды, карточки и отступы",
        () -> p.open(this::homeSettings));
    p.action("root.status", "━", "Верхняя панель", "Часы, погода, сеть и уведомления",
        () -> p.open(this::statusSettings));
    p.action("root.wallpaper", "▧", "Обои", "Изображения, видео и встроенные темы",
        () -> p.open(this::wallpaperSettings));
    p.action("root.power", "☾", "Заставка и сон", "Экран и заставка Android",
        () -> p.open(this::powerSettings));
    p.action("root.apps", "▦", "Приложения и рекомендации", "Приложения, категории и каналы",
        () -> p.open(this::appsSettings));
    p.action("root.appearance", "◐", "Оформление интерфейса", "Шрифт, цвет и профили",
        () -> p.open(this::interfaceSettings));
    p.action("root.device", "▤", "Устройство", "Сеть, пульт и системные функции",
        () -> p.open(this::mainSettings));
    p.action("root.diagnostics", "◎", "Диагностика", "Устройство, сеть и производительность",
        () -> p.open(this::diagnosticSettings));
  }

  private void homeSettings(TvSettingsPanel p) {
    p.title("⌂", "Главный экран", "");
    p.action("home.rows", "≡", "Ряды", "Порядок, заголовки и видимость",
        () -> p.open(this::rowsSettings));
    p.action("appearance.cards", "▭", "Карточки", "Размер, пропорции и фокус",
        () -> p.open(this::cardsSettings));
    p.action("home.spacing", "ruler", "Поля и интервалы", "",
        () -> p.open(this::spacingSettings));
  }

  private void appsSettings(TvSettingsPanel p) {
    p.title("▦", "Приложения и рекомендации", "");
    p.action("apps.library", "▦", "Все приложения", "ТВ и мобильные приложения", this::openLibrary);
    p.action("apps.hidden", "eye_off", "Скрытые приложения", "", () -> openAppList(true));
    p.action("apps.categories", "folder", "Категории", "Создание, порядок и видимость",
        this::manageCategories);
    p.action("apps.channels", "▤", "Каналы приложений", "Рекомендации установленных приложений",
        () -> p.open(this::channelsSettings));
    p.action("apps.refresh", "↻", "Обновить приложения", "", this::refreshAppList);
  }

  private void interfaceSettings(TvSettingsPanel p) {
    p.title("◐", "Оформление интерфейса", "");
    p.action("interface.font", "T", "Шрифт", "Размер интерфейса и диалогов", this::fontSettings);
    p.action("interface.accent", "◐", "Цвет акцента", "Выделение карточек и меню",
        () -> chooseColor("accent_color", true));
    p.action("interface.profiles", "layers", "Профили экрана", "Сохранение и восстановление оформления",
        () -> p.open(this::profileSettings));
    p.action("interface.reset", "↺", "Сбросить оформление", "Приложения и избранное сохранятся",
        this::resetAppearance);
  }

  private void diagnosticSettings(TvSettingsPanel p) {
    p.title("◎", "Диагностика", "");
    p.action("diagnostics.device", "!", "О приложении и устройстве", "Версия, память и экран",
        this::diagnostics);
    p.action("diagnostics.network", "◉", "Проверка сети", "Подключение, DNS и HTTPS",
        this::checkNetwork);
    p.action("diagnostics.performance", "◎", "Производительность", "Кадры главного экрана и меню",
        () -> p.open(this::performanceSettings));
  }

  private void checkNetwork() {
    if (checkingNetwork) return;
    checkingNetwork = true;
    AlertDialog dialog = new NovaDialog(this)
        .setTitle("Проверка сети")
        .setMessage("Проверяем DNS и HTTPS для www.google.com…")
        .setPositiveButton("Закрыть", null)
        .show();
    android.net.ConnectivityManager manager =
        (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    worker.execute(() -> {
      String report = NetworkDiagnostics.check(manager);
      handler.post(() -> {
        checkingNetwork = false;
        if (!isDestroyed() && dialog.isShowing()) dialog.setMessage(report);
      });
    });
  }

  private void performanceSettings(TvSettingsPanel p) {
    p.title("◎", "Производительность",
        "В течение 15 секунд пролистайте меню или перейдите к рядам на главном экране.");
    if (frameSample.isRunning()) {
      p.action("performance.stop", "⏻", "Завершить замер", "", this::endPerformanceSample);
      return;
    }
    p.action("performance.start", "▷", "Начать замер", "", () -> {
      frameSample.start();
      watchPerformanceWindow(getWindow());
      watchPerformanceWindow(p.getWindow());
      handler.postDelayed(endFrameSample, 15_000);
      p.refresh();
    });
    if (!performanceReport.isEmpty()) {
      p.action("performance.last", "!", "Результат последнего замера", "",
          this::showPerformanceReport);
    }
  }

  private void watchPerformanceWindow(Window window) {
    if (frameSample.isRunning()) {
      frameSample.watch(window, getWindowManager().getDefaultDisplay().getRefreshRate());
    }
  }

  private void endPerformanceSample() {
    if (!frameSample.isRunning()) return;
    handler.removeCallbacks(endFrameSample);
    performanceReport = frameSample.stop();
    if (settingsPanel != null && settingsPanel.isShowing()
        && settingsPanel.isPage("Производительность")) settingsPanel.refresh();
    if (resumed && !isFinishing() && !isDestroyed()) showPerformanceReport();
  }

  private void showPerformanceReport() {
    new NovaDialog(this).setTitle("Результат замера").setMessage(performanceReport)
        .setPositiveButton("Закрыть", null).show();
  }

  private void refreshAppList() {
    refreshApps();
    rebuildLauncherRows();
  }

  private void mainSettings(TvSettingsPanel p) {
    p.title("▤", "Устройство", "");
    p.action("device.autostart", "▷", "Автозапуск приложения", "", this::autostartSettings);
    systemSetting(p, "◉", "Сеть", "", android.provider.Settings.ACTION_WIFI_SETTINGS);
    systemSetting(p, "calendar", "Дата и время Android", "", android.provider.Settings.ACTION_DATE_SETTINGS);
    systemSetting(p, "◉", "Голосовой поиск", "Системный поиск, если установлен",
        android.app.SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
    if (availableTvInputs().isEmpty())
      p.unavailable("⇥", "Источники сигнала", "Нет доступных входов телевизора");
    else p.action("device.inputs", "⇥", "Источники сигнала", "Входы телевизора", this::showInputs);
    systemSetting(p, "⌁", "Пульты и устройства", "Системное сопряжение Bluetooth",
        android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
    p.action("device.parental", "shield", "Родительский контроль", "PIN для настроек и приложений",
        () -> p.open(this::parentalSettings));
    systemSetting(p, "♿", "Специальные возможности", "Системные настройки доступности",
        android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
    systemSetting(p,
        "⚙",
        "Настройки Android",
        "Штатные настройки телевизора",
        android.provider.Settings.ACTION_SETTINGS);
  }

  void powerSettings(TvSettingsPanel p) {
    p.title("☾", "Заставка и сон", "");
    p.section("В Nova home Launcher");
    p.action("power.nova_screensaver", "screensaver", "Заставка Nova",
        getPreferences(0).getBoolean(NovaScreensaver.ENABLED, false) ? "Включена" : "Выключена",
        () -> p.open(this::novaScreensaverSettings));
    p.toggle("power.keep_screen_on", "☀", "Не гасить экран", "Пока открыт лаунчер",
        getPreferences(0).getBoolean("keep_screen_on", false),
        value -> setBool("keep_screen_on", value));
    p.section("Настройки Android");
    boolean hasScreensaver = systemSetting(p, "screensaver", "Заставка Android", "Выбор системной заставки",
        android.provider.Settings.ACTION_DREAM_SETTINGS);
    boolean hasDisplay = systemSetting(p, "display", "Экран Android", "Системные настройки экрана",
        android.provider.Settings.ACTION_DISPLAY_SETTINGS);
    if (!hasScreensaver || !hasDisplay) {
      systemSetting(p, "⚙", "Все настройки Android", "Другие параметры телевизора",
          android.provider.Settings.ACTION_SETTINGS);
    }
  }

  private void novaScreensaverSettings(TvSettingsPanel p) {
    p.title("screensaver", "Заставка Nova", "Запускается при бездействии в лаунчере");
    p.toggle("power.screensaver.enabled", "⏻", "Запускать автоматически", "",
        getPreferences(0).getBoolean(NovaScreensaver.ENABLED, false), enabled -> {
          getPreferences(0).edit().putBoolean(NovaScreensaver.ENABLED, enabled).apply();
          scheduleScreensaver();
        });
    p.slider("◷", "Запуск через", 1, 60, Appearance.clamp(
        getPreferences(0).getInt(NovaScreensaver.DELAY, NovaScreensaver.DEFAULT_DELAY_MINUTES),
        1, 60), " мин",
        minutes -> getPreferences(0).edit().putInt(NovaScreensaver.DELAY, minutes).apply());
    p.action("power.screensaver.source", "▧", "Источник заставки",
        getPreferences(0).getBoolean(NovaScreensaver.DAILY_PHOTO, false) ? "Фото дня"
            : getPreferences(0).getBoolean(NovaScreensaver.WALLPAPER, false)
                ? "Текущие обои" : "Чёрный фон", () -> p.open(this::screensaverSourceSettings));
  }

  private void screensaverSourceSettings(TvSettingsPanel p) {
    p.title("▧", "Источник заставки", "");
    p.action("◐", "Чёрный фон", "", () -> setScreensaverSource(p, false));
    p.action("▧", "Текущие обои", "", () -> setScreensaverSource(p, true));
    p.action("calendar", "Фото дня", "Wikimedia Commons",
        () -> p.open(page -> dailyPhotoSettings(page, true)));
  }

  private void setScreensaverSource(TvSettingsPanel p, boolean wallpaper) {
    getPreferences(0).edit().putBoolean(NovaScreensaver.WALLPAPER, wallpaper)
        .remove(NovaScreensaver.DAILY_PHOTO).apply();
    p.onBackPressed();
  }

  private void dailyPhotoSettings(TvSettingsPanel p, boolean forScreensaver) {
    p.title("calendar", "Фото дня", "Wikimedia Commons");
    boolean enabled = getPreferences(0).getBoolean(
        forScreensaver ? NovaScreensaver.DAILY_PHOTO : DailyPhoto.WALLPAPER, false);
    if (!enabled) {
      p.action("daily_photo.use", "eye", "Использовать фото дня", "", () -> {
        if (forScreensaver) {
          getPreferences(0).edit().putBoolean(NovaScreensaver.DAILY_PHOTO, true).apply();
        } else {
          getPreferences(0).edit().putBoolean(DailyPhoto.WALLPAPER, true)
              .remove("wallpaper").remove("wallpaper_type").apply();
          updateWallpaperLayer();
        }
        requestDailyPhoto(true);
        p.refresh();
      });
    } else {
      p.action("daily_photo.update", "↻", "Обновить фото", "", () -> {
        requestDailyPhoto(true);
        p.refresh();
      });
      p.action("daily_photo.previous", "←", "Предыдущее фото", "Из архива Wikimedia Commons", () -> {
        String day = dailyPhotoArchiveDay.isEmpty() ? DailyPhoto.today() : dailyPhotoArchiveDay;
        requestDailyPhoto(java.time.LocalDate.parse(day).minusDays(1).toString(), true, true);
        p.refresh();
      });
    }
    if (dailyPhotoLoading) p.section("Загружаем фото…");
    if (dailyPhotoFailed) p.section(dailyPhoto == null ? "Фото пока недоступно."
        : "Новое фото недоступно. Показано сохранённое.");
    if (!dailyPhotoMessage.isEmpty()) p.section(dailyPhotoMessage);
    if (dailyPhoto != null) {
      DailyPhoto.Photo photo = dailyPhoto;
      p.section(photo.date + " · " + photo.title);
      p.action("daily_photo.credit", "!", "Автор и лицензия", photo.author + " · " + photo.license,
          () -> new NovaDialog(this).setTitle("Фото дня")
              .setMessage(photo.title + "\n" + photo.author + "\n" + photo.license
                  + "\n" + photo.licenseUrl + "\n" + photo.source)
              .setPositiveButton("Закрыть", null).show());
    }
  }

  private boolean systemSetting(TvSettingsPanel panel, String icon, String title, String subtitle,
      String action) {
    Intent intent = new Intent(action);
    if (canOpen(intent)) {
      panel.action(icon, title, subtitle, () -> safeStart(intent));
      return true;
    }
    panel.unavailable(icon, title, "Не поддерживается прошивкой");
    return false;
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

  void rowsSettings(TvSettingsPanel p) {
    p.title("≡", "Ряды", "Удерживайте OK, затем ↑ ↓. OK — сохранить, Назад — отменить.");
    p.section("Порядок рядов");
    Map<String, HomeRow> rows = homeRows();
    for (String key : orderedKeys(HOME_ROW_ORDER, rows.keySet())) {
      HomeRow row = rows.get(key);
      p.reorderableAction(key, row.icon, row.title, "", () -> p.open(row.settings), order -> {
        saveOrderedKeys(HOME_ROW_ORDER, order);
        rebuildLauncherRows();
      });
    }
    p.section("Оформление и навигация");
    toggle(p, "T", "Показывать названия категорий", "show_titles", true);
    toggle(p, "▣", "Значок перед заголовком", "show_category_icons", true);
    toggle(p, "⌖", "Запоминать карточку", "remember_focus", true);
    toggle(p, "⌖", "Закреплять выбранную строку", "pin_row", false);
    slider(p, "◐", "Непрозрачность неактивных строк", "inactive_opacity", 75, 20, 100, " %");
    p.action("rows.categories", "folder", "Управление категориями", "", this::manageCategories);
  }

  private void rowVisibility(TvSettingsPanel p, String title, String icon, String key,
      boolean defaultValue) {
    p.title(icon, title, "");
    toggle(p, "eye", "Показывать ряд", key, defaultValue);
  }

  private void setRowVisibility(String preference, String key, boolean visible) {
    List<String> hidden = readIds(preference);
    hidden.remove(key);
    if (!visible) hidden.add(key);
    getPreferences(0).edit().putString(preference, android.text.TextUtils.join("|", hidden)).apply();
    boolean channel = "hidden_channels".equals(preference);
    updateHomeRowVisibility((channel ? "channel:" : "category:") + key,
        visible && (!channel || getPreferences(0).getBoolean("show_channels", false)));
  }

  private void spacingSettings(TvSettingsPanel p) {
    p.title("ruler", "Поля и интервалы", "");
    slider(p, "↑", "Верхний отступ", "margin_top", 20, 0, 100, " dp");
    slider(p, "←", "Отступ слева", "margin_left", 28, 0, 100, " dp");
    slider(p, "→", "Отступ справа", "margin_right", 28, 0, 100, " dp");
    slider(p, "↕", "Интервал между рядами", "row_gap", 12, 0, 48, " dp");
    slider(p, "↔", "Расстояние между карточками", "card_gap", 10, 2, 32, " dp");
  }

  void cardsSettings(TvSettingsPanel p) {
    p.title("▭", "Карточки", "");
    toggle(p, "T", "Показывать название приложения", "card_titles", false);
    toggle(p, "▧", "Использовать TV-баннеры", "use_banners", true);
    slider(p, "▦", "Строк приложений в категории", "grid_rows", 3, 1, 3, "");
    slider(p, "▭", "Размер карточки", "card_width", 126, 80, 240, " dp");
    slider(p, "◯", "Радиус скругления", "card_radius", 10, 0, 28, " dp");
    slider(p, "▣", "Размер значков без баннера", "icon_size", 44, 24, 80, " dp");
    toggle(p, "⌖", "Увеличение выбранной карточки", "focus_zoom", true);
    p.action("cards.ratio", "▭", "Пропорции ТВ-карточек", "16:9 / 1:1 / 4:3",
        () -> chooseSetting("card_ratio", "Пропорции ТВ-карточек", cardRatios(), 0));
    p.action("cards.color", "◐", "Цвет карточек", "", () -> chooseColor("card_color", false));
    p.action("cards.mobile", "▯", "Мобильные карточки", "Значок, пропорции и фон",
        () -> p.open(this::mobileCardSettings));
  }

  private void mobileCardSettings(TvSettingsPanel p) {
    p.title("▯", "Мобильные карточки", "");
    p.action("▣", "Режим изображения", "", () -> chooseSetting(
        "mobile_icon_mode", "Режим изображения",
        new String[] {"Заполнить", "Вписать", "Оригинальный размер"}, 0));
    slider(p, "▣", "Масштаб значка", "mobile_icon_scale", 100, 60, 140, " %");
    slider(p, "↔", "Внутренние поля", "mobile_icon_padding", 4, 0, 24, " dp");
    slider(p, "◯", "Скругление мобильной карточки", "mobile_radius", 10, 0, 28, " dp");
    p.action("◐", "Фон мобильной карточки", "", () -> chooseColor("mobile_card_color", false));
    p.action("cards.mobile.ratio", "▭", "Пропорции", "16:9 / 1:1 / 4:3",
        () -> chooseSetting("mobile_ratio", "Пропорции мобильных карточек", cardRatios(), 1));
  }

  private static String[] cardRatios() {
    // Keep the order of the saved preference indexes.
    return new String[] {"16:9", "1:1", "4:3"};
  }

  void wallpaperSettings(TvSettingsPanel p) {
    p.title("▧", "Обои", "");
    p.action("wallpaper.source", "▧", "Источник", usesDailyWallpaper() ? "Фото дня" : "Тема, фото или видео",
        () -> p.open(this::wallpaperSourceSettings));
    toggle(p, "▧", "Вписать изображение целиком", "wallpaper_fit", false);
    slider(p, "◐", "Затемнение фона", "wallpaper_dim", 55, 0, 90, " %");
    p.action("wallpaper.night", "☾", "Темнее ночью", "", () -> p.open(this::nightDimmingSettings));
    p.action("wallpaper.reset", "↺", "Сбросить обои", "", () -> {
      getPreferences(0).edit().remove("wallpaper").remove("wallpaper_type")
          .remove(DailyPhoto.WALLPAPER).apply();
      updateWallpaperLayer();
    });
  }

  private void nightDimmingSettings(TvSettingsPanel p) {
    p.title("☾", "Темнее ночью", "Дополнительное затемнение фона по времени телевизора");
    toggle(p, "⏻", "Затемнять ночью", NightDimming.ENABLED, false);
    nightTimeAction(p, "Начало", NightDimming.START, NightDimming.DEFAULT_START);
    nightTimeAction(p, "Окончание", NightDimming.END, NightDimming.DEFAULT_END);
    slider(p, "◐", "Максимальное затемнение", NightDimming.AMOUNT,
        NightDimming.DEFAULT_AMOUNT, 0, 100, " %");
    slider(p, "◷", "Шаг за час", NightDimming.STEP, NightDimming.DEFAULT_STEP, 1, 100, " %");
    p.section("В коротком интервале шаги происходят чаще, чтобы достичь максимума. "
        + "Одинаковое время начала и окончания отключает затемнение.");
  }

  private void nightTimeAction(TvSettingsPanel p, String title, String key, int defaultValue) {
    int time = nightTime(key, defaultValue);
    p.action(key, "◷", title, String.format(Locale.ROOT, "%02d:%02d", time / 60, time % 60),
        () -> p.open(page -> {
          page.title("◷", title, "Изменяйте часы и минуты стрелками влево и вправо");
          int selected = nightTime(key, defaultValue);
          page.slider("◷", "Часы", 0, 23, selected / 60, " ч",
              hour -> setInt(key, hour * 60 + nightTime(key, defaultValue) % 60));
          page.slider("◷", "Минуты", 0, 59, selected % 60, " мин",
              minute -> setInt(key, nightTime(key, defaultValue) / 60 * 60 + minute));
        }));
  }

  private void wallpaperSourceSettings(TvSettingsPanel p) {
    p.title("▧", "Источник обоев", "");
    p.action("calendar", "Фото дня", "Wikimedia Commons",
        () -> p.open(page -> dailyPhotoSettings(page, false)));
    p.action(
        "▧",
        "Моё фото",
        "Постоянный доступ к выбранному изображению",
        this::safeStartForWallpaper);
    p.action("▷", "Моё видео", "Локальный файл, без звука", this::selectVideoWallpaper);
    p.action(
        "◐",
        "Встроенная тема",
        "Ночная / Закат / Океан",
        () ->
            new NovaDialog(this)
                .setTitle("Тема фона")
                .setSingleChoiceItems(
                    new String[] {"Ночная", "Закат", "Океан"},
                    theme,
                    (d, n) -> {
                      theme = n;
                      boolean customWallpaper = usesDailyWallpaper() || getPreferences(0).contains("wallpaper");
                      getPreferences(0).edit().putInt("theme", n).remove("wallpaper")
                          .remove("wallpaper_type").remove(DailyPhoto.WALLPAPER).apply();
                      if (customWallpaper) {
                        updateWallpaperLayer();
                      } else {
                        launcherView.setBackground(background());
                      }
                      d.dismiss();
                    })
                .setNegativeButton("Закрыть", null)
                .show());
  }

  void statusSettings(TvSettingsPanel p) {
    p.title("━", "Верхняя панель", "");
    p.action("status.content", "eye", "Что показывать", "", () -> p.open(this::statusContent));
    p.action("status.style", "◐", "Оформление панели", "", () -> p.open(this::statusStyle));
  }

  private void statusStyle(TvSettingsPanel p) {
    p.title("◐", "Оформление панели", "");
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
  }

  private void statusContent(TvSettingsPanel p) {
    p.title("eye", "Что показывать", "Удерживайте OK, затем ↑ ↓. OK — сохранить, Назад — отменить.");
    p.section("Порядок блоков");
    for (String key : orderedKeys(STATUS_BLOCK_ORDER, Arrays.asList(CONTROLS_BLOCK, CLOCK_BLOCK))) {
      boolean controls = CONTROLS_BLOCK.equals(key);
      p.reorderableAction(key, controls ? "▦" : "◷",
          controls ? "Управление и связь" : "Погода, дата и часы",
          controls ? "Кнопки, сеть и уведомления" : "",
          () -> p.open(controls ? this::statusControls : this::statusInformation), order -> {
            saveOrderedKeys(STATUS_BLOCK_ORDER, order);
            refreshHeader();
          });
    }
  }

  private void statusInformation(TvSettingsPanel p) {
    p.title("◷", "Погода, дата и часы", "");
    p.action("status.weather", "☁", "Погода", "Город, размер, цвет",
        () -> p.open(this::weatherSettings));
    p.action("status.date", "calendar", "Дата", "Размер, цвет, формат", () -> p.open(this::dateSettings));
    p.action("status.clock", "◷", "Часы", "Размер, цвет, формат", () -> p.open(this::clockSettings));
  }

  private void statusControls(TvSettingsPanel p) {
    p.title("▦", "Управление и связь", "");
    p.action("status.apps", "▦", "Кнопка «Приложения»", "",
        () -> p.open(page -> headerButtonSettings(page, "▦", "Приложения", "apps_button")));
    p.action("status.search", "search", "Кнопка «Поиск»", "",
        () -> p.open(page -> headerButtonSettings(page, "search", "Поиск", "search_button")));
    p.action("status.settings", "⚙", "Кнопка «Настройки»", "",
        () -> p.open(page -> headerButtonSettings(page, "⚙", "Настройки", "settings_button")));
    p.action("status.timer", "sleep_timer", "Кнопка «Таймер сна»", "",
        () -> p.open(page -> headerButtonSettings(page, "sleep_timer", "Таймер сна", "timer_button")));
    p.action("status.network", "◉", "Сеть", "", () -> p.open(page -> {
      page.title("◉", "Сеть", "");
      toggle(page, "eye", "Показывать подключение", "show_network", true);
    }));
    p.action("status.notifications", "♧", "Уведомления", "", () -> p.open(page -> {
      page.title("♧", "Уведомления", "");
      toggle(page, "eye", "Показывать значок уведомлений", "show_notifications", true);
      toggle(page, "eye_off", "Скрывать пустой значок", "hide_empty_notifications", false);
      page.action("♧", "Доступ и список уведомлений", "", () -> page.open(this::notificationSettings));
    }));
  }

  private void headerButtonSettings(TvSettingsPanel p, String icon, String title, String key) {
    p.title(icon, "Кнопка «" + title + "»", "");
    toggle(p, "eye", "Показывать кнопку", "show_" + key, true);
    slider(p, "T", "Размер текста", key + "_size", 14, 10, 26, " sp");
    toggle(p, "◐", "Тень текста", key + "_shadow", false);
    p.action("◐", "Цвет текста и значка", "", () -> chooseColor(key + "_color", false));
    p.action(icon, "Вид кнопки", "", () -> chooseSetting(key + "_mode", "Вид кнопки",
        new String[] {"Только значок", "Только текст", "Значок и текст"},
        getPreferences(0).getInt("status_label_mode", 2)));
    if ("timer_button".equals(key)) {
      p.action("sleep_timer", "Настроить таймер", "", () -> p.open(this::sleepTimerSettings));
    }
  }

  private void dateSettings(TvSettingsPanel p) {
    p.title("calendar", "Дата", "");
    toggle(p, "calendar", "Показывать дату", "show_date", true);
    slider(p, "T", "Размер шрифта даты", "date_size", 15, 10, 28, " sp");
    toggle(p, "◐", "Тень даты", "date_shadow", false);
    p.action("◐", "Цвет даты", "", () -> chooseColor("date_color", false));
    p.action("calendar", "Формат даты", "", () -> chooseSetting("date_format", "Формат даты",
        new String[] {"Короткая", "С днём недели"}, 0));
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
        "24 часа / 12 часов / системный",
        () ->
            new NovaDialog(this)
                .setTitle("Формат часов")
                .setItems(
                    new String[] {"24 часа — HH:mm", "12 часов — hh:mm a", "Системный формат"},
                    (d, n) -> {
                      getPreferences(0)
                          .edit()
                          .putString(
                              "clock_format",
                              new String[] {"HH:mm", "hh:mm a", "system"}[n])
                          .apply();
                      pendingAppearance.add("clock_format");
                      applyPendingAppearance();
                    })
                .setNegativeButton("Закрыть", null)
                .show());
  }

  void weatherSettings(TvSettingsPanel p) {
    p.title("☁", "Погода", "Прогноз обновляется автоматически каждый час");
    toggle(p, "☁", "Показывать погоду", "show_weather", true);
    p.action(
        "⌖", "Город", getPreferences(0).getString("weather_city", "Не выбран"), this::weatherCity);
    slider(p, "T", "Размер шрифта погоды", "weather_size", 13, 10, 26, " sp");
    toggle(p, "◐", "Тень погоды", "weather_shadow", true);
    p.action("◐", "Цвет погоды", "", () -> chooseColor("weather_color", false));
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

  private void searchApps() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setHint("Название приложения");
    new NovaDialog(this).setTitle("Поиск приложений").setView(input)
        .setPositiveButton("Найти", (dialog, button) -> {
          String name = input.getText().toString().trim();
          String query = name.toLowerCase(RUSSIAN);
          openPanelPage(p -> {
            p.title("search", "Поиск приложений", name);
            boolean found = false;
            for (App app : visibleApps(installed, false)) {
              if (!app.label.toLowerCase(RUSSIAN).contains(query)) continue;
              addLibraryApp(p, app, false);
              found = true;
            }
            if (!found) p.section("Приложений не найдено");
          });
        }).setNegativeButton("Отмена", null).show();
  }

  void openAppList(boolean hidden) {
    final boolean hiddenOnly = hidden;
    openPanelPage(
        p -> {
          p.title(
              hiddenOnly ? "◉" : "▦",
              hiddenOnly ? "Скрытые приложения" : "Все приложения",
              "OK — запуск · удержание OK — меню");
          List<App> apps = visibleApps(installed, hiddenOnly);
          if (apps.isEmpty()) {
            p.section("Приложений нет");
            return;
          }
          if (hiddenOnly) {
            for (App app : apps) addLibraryApp(p, app, true);
          } else {
            addLibrarySection(p, apps, true, "ТВ-приложения");
            addLibrarySection(p, apps, false, "Мобильные приложения");
          }
        });
  }

  private void addLibrarySection(TvSettingsPanel p, List<App> apps, boolean tv, String title) {
    boolean sectionAdded = false;
    for (App app : apps) {
      if (app.tv != tv) continue;
      if (!sectionAdded) {
        p.section(title);
        sectionAdded = true;
      }
      addLibraryApp(p, app, false);
    }
  }

  private void addLibraryApp(TvSettingsPanel p, App app, boolean hiddenOnly) {
    Runnable openContext = () -> {
      showHidden = hiddenOnly;
      contextMenu(app, false, "app_order");
    };
    p.app(
        "app:" + app.pkg,
        app.label,
        app.tv ? "ТВ-приложение" : "Мобильное приложение",
        app.banner == null ? app.icon : app.banner,
        hiddenOnly ? openContext : () -> {
          settingsPanel.dismiss();
          launch(app);
        },
        openContext);
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
      NightDimming.ENABLED,
      NightDimming.START,
      NightDimming.END,
      NightDimming.AMOUNT,
      NightDimming.STEP,
      "wallpaper",
      "theme",
      "show_clock",
      "clock_size",
      "clock_color",
      "clock_format", "show_date", "date_format", "date_size", "date_color", "date_shadow",
      "show_apps_button", "apps_button_mode", "apps_button_size", "apps_button_color", "apps_button_shadow",
      "show_timer_button", "timer_button_mode", "timer_button_size", "timer_button_color", "timer_button_shadow",
      STATUS_BLOCK_ORDER, HOME_ROW_ORDER,
      "show_settings_button", "settings_button_mode", "settings_button_size", "settings_button_color", "settings_button_shadow",
      "show_search_button", "search_button_mode", "search_button_size", "search_button_color", "search_button_shadow",
      "show_weather",
      "weather_size",
      "weather_color",
      "show_status",
      "show_network",
      "accent_color",
      "show_inputs",
      "wallpaper_type",
      DailyPhoto.WALLPAPER,
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
      "status_shadow", "status_elevation", "status_color", "status_background"
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
                if (currentScreen == 0) replaceChannelRows();
              });
        });
  }

  private void replaceChannelRows() {
    Iterator<Map.Entry<String, LinearLayout>> rows = homeRowViews.entrySet().iterator();
    while (rows.hasNext()) {
      Map.Entry<String, LinearLayout> row = rows.next();
      if (!row.getKey().startsWith("channel:")) continue;
      LinearLayout section = row.getValue();
      artworkViews.keySet().removeIf(image -> belongsTo(image, section));
      content.removeView(section);
      rows.remove();
    }
    if (getPreferences(0).getBoolean("show_channels", false)) {
      Set<String> hidden = new HashSet<>(readIds("hidden_channels"));
      for (TvFeeds.Channel channel : feeds.channels) {
        if (!hidden.contains(channel.id)) updateHomeRowVisibility("channel:" + channel.id, true);
      }
    }
    updateChannelStatus();
  }

  private static boolean belongsTo(View view, View parent) {
    ViewParent ancestor = view.getParent();
    while (ancestor instanceof View) {
      if (ancestor == parent) return true;
      ancestor = ancestor.getParent();
    }
    return false;
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
            updateHomeRowVisibility("channel:" + channel.id,
                value && getPreferences(0).getBoolean("show_channels", false));
          });
    }
  }

  private void renderChannel(TvFeeds.Channel channel) {
    rowTitle("▤", channel.title);
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOnScrollChangeListener(
        (View v, int x, int y, int oldX, int oldY) -> scheduleArtworkScan());
    content.addView(scroll, lp(-1, 142));
    LinearLayout row = horizontal();
    row.setPadding(dp(6), dp(6), dp(6), dp(6));
    scroll.addView(row);
    for (TvFeeds.Program program : channel.programs) {
      LinearLayout card = vertical();
      card.setPadding(dp(4), dp(4), dp(4), dp(4));
      card.setFocusable(true);
      GradientDrawable normal = shape(0xdd222c40, 10);
      GradientDrawable selected = shape(0xdd222c40, 10);
      selected.setStroke(dp(2), accent());
      StateListDrawable background = new StateListDrawable();
      background.addState(new int[] {android.R.attr.state_focused}, selected);
      background.addState(new int[0], normal);
      card.setBackground(background);
      marginAdd(row, card, 108, 128, 10);
      ImageView art = new ImageView(this);
      art.setImageDrawable(getDrawable(R.mipmap.ic_launcher));
      art.setScaleType(ImageView.ScaleType.CENTER_CROP);
      card.addView(art, lp(-1, 96));
      TextView title = label(program.title == null ? "Без названия" : program.title, 13, WHITE);
      title.setSingleLine(true);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      card.addView(title, lp(-1, 24));
      card.setOnClickListener(v -> openProgram(program));
      loadArtwork(program.artwork, art);
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
    scheduleArtworkScan();
  }

  private void scheduleArtworkScan() {
    if (!artworkViews.isEmpty() && !artworkScanPending) {
      artworkScanPending = true;
      handler.postDelayed(artworkScan, 64);
    }
  }

  void refreshVisibleArtwork() {
    if (isDestroyed() || !resumed) return;
    Rect visible = new Rect();
    ArrayList<ImageView> visibleImages = new ArrayList<>();
    for (Map.Entry<ImageView, String> entry : artworkViews.entrySet()) {
      ImageView image = entry.getKey();
      if (image.isAttachedToWindow()
          && image.getGlobalVisibleRect(visible)
          && !failedArtwork.contains(entry.getValue())
          && artworkLoading.add(image)) visibleImages.add(image);
    }
    for (ImageView image : visibleImages) requestArtwork(artworkViews.get(image), image);
  }

  void requestArtwork(String uri, ImageView image) {
    if (uri == null || uri.isEmpty()) return;
    Bitmap cached = artworkCache.get(uri);
    if (cached != null) {
      image.setImageBitmap(cached);
      artworkViews.remove(image);
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
            } else throw new java.io.IOException("Unsupported artwork URI");
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
                  if (!isDestroyed() && artworkViews.remove(image) != null) image.setImageBitmap(bitmap);
                });
          } catch (Exception ignored) {
            handler.post(
                () -> {
                  artworkLoading.remove(image);
                  if (artworkViews.containsKey(image)) failedArtwork.add(uri);
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
        String selected = data.getData().toString();
        String type = request == 8 ? "video" : "image";
        if (!usesDailyWallpaper() && selected.equals(getPreferences(0).getString("wallpaper", ""))
            && type.equals(getPreferences(0).getString("wallpaper_type", "image"))) return;
        getContentResolver()
            .takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getPreferences(0)
            .edit()
            .putString("wallpaper", selected)
            .putString("wallpaper_type", type)
            .remove(DailyPhoto.WALLPAPER)
            .apply();
        updateWallpaperLayer();
        if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.refresh();
      } catch (SecurityException e) {
        Toast.makeText(this, "Не удалось сохранить доступ к обоям", Toast.LENGTH_LONG).show();
      }
    }
  }

  void manageCategories() {
    openPanelPage(this::categoryManager);
  }

  void categoryManager(TvSettingsPanel p) {
    p.title("folder", "Категории", "");
    p.action("+", "Новая категория", "", this::createCategory);
    p.section("КАТЕГОРИИ");
    toggle(p, "▣", "ТВ приложения", "show_all", true);
    toggle(p, "▯", "Мобильные приложения", "show_mobile", true);
    toggle(p, "☆", "Избранное", "show_favorites", true);
    toggle(p, "⇥", "Входы телевизора", "show_inputs", false);
    List<String> hiddenCategories = readIds("hidden_categories");
    for (String category : readIds("categories"))
      p.action(
          "category:" + category,
          "folder",
          category,
          hiddenCategories.contains(category) ? "Скрыта" : "Показывается",
          () -> p.open(page -> categoryRowSettings(page, category)));
  }

  private void categoryRowSettings(TvSettingsPanel p, String category) {
    p.title("folder", category, "");
    p.toggle("eye", "Показывать категорию", "", !readIds("hidden_categories").contains(category),
        value -> setRowVisibility("hidden_categories", category, value));
    p.action("▦", "Приложения в категории", "Выбор приложений", () -> assignCategoryApps(category));
    p.action("↕", "Порядок, название и удаление", "", () -> categoryActions(category));
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
        .setAdapter(
            new NovaDialog.IconOptions(this,
                new String[] {"Переместить выше", "Переместить ниже",
                    "Переименовать", "Удалить категорию"},
                new int[] {R.drawable.nova_ui_up, R.drawable.nova_ui_down,
                    R.drawable.nova_ui_edit, R.drawable.nova_ui_trash}),
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
                List<String> rowOrder = orderedKeys(HOME_ROW_ORDER, homeRows().keySet());
                Collections.swap(rowOrder, rowOrder.indexOf("category:" + category),
                    rowOrder.indexOf("category:" + categories.get(index)));
                saveOrderedKeys(HOME_ROW_ORDER, rowOrder);
                getPreferences(0)
                    .edit()
                    .putString("categories", android.text.TextUtils.join("|", categories))
                    .apply();
                rebuildLauncherRows();
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
                                  if (!name.equals(category)) {
                                    edit.remove("category_order_" + category);
                                    List<String> rows = readIds(HOME_ROW_ORDER);
                                    if (Collections.replaceAll(rows, "category:" + category, "category:" + name))
                                      edit.putString(HOME_ROW_ORDER, android.text.TextUtils.join("|", rows));
                                  }
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
                          List<String> rows = readIds(HOME_ROW_ORDER);
                          rows.remove("category:" + category);
                          android.content.SharedPreferences.Editor edit =
                              getPreferences(0)
                                  .edit()
                                  .putString(
                                      "categories", android.text.TextUtils.join("|", categories))
                                  .putString(HOME_ROW_ORDER, android.text.TextUtils.join("|", rows))
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

  private String cachedWeather() {
    android.content.SharedPreferences prefs = getPreferences(0);
    String temperature = prefs.getString("weather_temperature", "—");
    return "—".equals(temperature) || prefs.getString("weather_error", "").isEmpty()
        ? temperature : temperature + " *";
  }

  @SuppressWarnings("deprecation") // Geocoder runs on the worker and supports Android 9.
  private org.json.JSONArray findCities(String query) throws Exception {
    org.json.JSONArray results = new org.json.JSONArray();
    if (!android.location.Geocoder.isPresent()) return results;
    List<android.location.Address> found =
        new android.location.Geocoder(this, RUSSIAN).getFromLocationName(query, 8);
    if (found == null) return results;
    for (android.location.Address place : found) {
      if (!place.hasLatitude() || !place.hasLongitude()) continue;
      results.put(new org.json.JSONObject()
          .put("name", place.getLocality() == null ? query : place.getLocality())
          .put("admin1", place.getAdminArea()).put("country", place.getCountryName())
          .put("latitude", place.getLatitude()).put("longitude", place.getLongitude()));
    }
    return results;
  }

  private void saveWeatherCity(String city, String latitude, String longitude) {
    if (weatherTask != null) weatherTask.cancel(true);
    weatherRequests.invalidate();
    retryWeatherAfter = 0;
    getPreferences(0).edit().putString("weather_city", city).putString("weather_lat", latitude)
        .putString("weather_lon", longitude).remove("weather_temperature")
        .remove("weather_cache").remove("weather_error").remove("weather_time").apply();
    if (weather != null) setChangedText(weather, "—");
    loadWeather();
    if (settingsPanel != null && settingsPanel.isShowing() && settingsPanel.isPage("Погода"))
      settingsPanel.refresh();
  }

  private void weatherCoordinates() {
    LinearLayout fields = vertical();
    EditText city = new EditText(this);
    EditText lat = new EditText(this);
    EditText lon = new EditText(this);
    city.setHint("Город");
    lat.setHint("Широта");
    lon.setHint("Долгота");
    city.setText(getPreferences(0).getString("weather_city", ""));
    lat.setText(getPreferences(0).getString("weather_lat", ""));
    lon.setText(getPreferences(0).getString("weather_lon", ""));
    int decimal = android.text.InputType.TYPE_CLASS_NUMBER
        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
    lat.setInputType(decimal);
    lon.setInputType(decimal);
    fields.addView(city);
    fields.addView(lat);
    fields.addView(lon);
    AlertDialog dialog = new NovaDialog(this).setTitle("Координаты города").setView(fields)
        .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      String name = city.getText().toString().trim();
      if (name.isEmpty()) {
        city.setError("Введите название города");
        return;
      }
      String latitude = lat.getText().toString().trim().replace(',', '.');
      String longitude = lon.getText().toString().trim().replace(',', '.');
      try {
        WeatherRepository.coordinate(latitude, 90);
        WeatherRepository.coordinate(longitude, 180);
      } catch (java.io.IOException invalid) {
        lon.setError("Проверьте широту (±90) и долготу (±180)");
        return;
      }
      saveWeatherCity(name, latitude, longitude);
      dialog.dismiss();
    }));
    dialog.show();
  }

  void weatherCity() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(getPreferences(0).getString("weather_city", ""));
    AlertDialog dialog =
        new NovaDialog(this)
            .setTitle("Поиск города")
            .setMessage("Введите город и нажмите «Найти».")
            .setView(input)
            .setPositiveButton("Найти", null)
            .setNeutralButton("Координаты", (d, n) -> weatherCoordinates())
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
                            org.json.JSONArray places = findCities(query);
                            handler.post(
                                () -> {
                                  if (isDestroyed() || !dialog.isShowing()) return;
                                  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                  if (places == null || places.length() == 0) {
                                    dialog.setMessage(
                                        "Поиск Android не нашёл город. Можно ввести координаты.");
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
                                            saveWeatherCity(place.optString("name"),
                                                place.optString("latitude"), place.optString("longitude"));
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
                                        "Поиск Android недоступен. Можно ввести координаты.");
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
    if (!resumed || weather == null || statusBar.getVisibility() != View.VISIBLE
        || SystemClock.elapsedRealtime() < retryWeatherAfter) return;
    String city = getPreferences(0).getString("weather_city", "");
    if (!weatherRequests.shouldLoad(
        city, System.currentTimeMillis(), getPreferences(0).contains("weather_temperature")
            ? getPreferences(0).getLong("weather_time", 0) : 0)) return;
    final int generation = weatherRequests.begin();
    final String latitude = getPreferences(0).getString("weather_lat", "");
    final String longitude = getPreferences(0).getString("weather_lon", "");
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
            if (latitude.isEmpty() || longitude.isEmpty())
              throw new java.io.IOException("Укажите координаты города в настройках погоды");
            WeatherRepository.Forecast forecast = weatherRepository().forecast(latitude, longitude);
            weatherDiagnostics += "\n" + forecast.diagnostics;
            result = Math.round(forecast.temperature) + " °C";
            ok = true;
          } catch (Exception e) {
            weatherError = WeatherRepository.describe(e);
            weatherDiagnostics += "\n" + weatherError;
            result = getPreferences(0).getString("weather_temperature", "—");
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
                if (!success) retryWeatherAfter = SystemClock.elapsedRealtime() + WeatherRequests.INTERVAL_MS;
                if (success)
                  getPreferences(0)
                      .edit()
                      .putString("weather_temperature", text)
                      .putLong("weather_time", System.currentTimeMillis())
                      .apply();
                if (weather != null) setChangedText(weather, cachedWeather());
                if (settingsPanel != null && settingsPanel.isShowing()
                    && settingsPanel.isPage("Погода")) settingsPanel.refresh();
              });
        });
  }
}
