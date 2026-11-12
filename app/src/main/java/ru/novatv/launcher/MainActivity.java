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
  private TextView clock;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ArrayList<App> installed = new ArrayList<>();
  private int theme = 0;
  private String movingPackage, movingOrder;
  private boolean movingFavorites;
  private TextView weather;
  private final WeatherRequests weatherRequests = new WeatherRequests();
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
    Drawable icon;

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
    if (b == null) {
      String auto = getPreferences(0).getString("autostart", "");
      for (App app : installed)
        if (app.pkg.equals(auto)) {
          launch(app);
          break;
        }
    }
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
        map.put(pkg, new App(pkg, r.activityInfo.name, r.loadLabel(pm).toString(), r.loadIcon(pm)));
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
    sc.setFillViewport(true);
    sc.setBackground(background());
    String wallpaper = getPreferences(0).getString("wallpaper", "");
    if (!wallpaper.isEmpty())
      try {
        android.graphics.ImageDecoder.Source source =
            android.graphics.ImageDecoder.createSource(
                getContentResolver(), android.net.Uri.parse(wallpaper));
        Drawable d =
            android.graphics.ImageDecoder.decodeDrawable(
                source, (decoder, info, src) -> decoder.setTargetSize(1920, 1080));
        d.setColorFilter(0xbb000000, PorterDuff.Mode.SRC_ATOP);
        sc.setBackground(d);
      } catch (Exception e) {
        getPreferences(0).edit().remove("wallpaper").apply();
      }
    setContentView(sc);
    page = vertical();
    page.setPadding(dp(32), dp(16), dp(32), dp(24));
    sc.addView(page);
    header();
    gap(page, 17);
    weatherPanel();
    if (getPreferences(0).getBoolean("show_banner", false)) banner();
    gap(page, 20);
    content = vertical();
    page.addView(content);
    home();
  }

  void header() {
    LinearLayout bar = horizontal();
    page.addView(bar, lp(-1, 48));
    TextView logo = label("✦  NovaTV", 22, WHITE);
    logo.setTypeface(null, Typeface.BOLD);
    marginAdd(bar, logo, 112, -1, 8);
    menu = horizontal();
    bar.addView(menu, new LinearLayout.LayoutParams(0, -1, 1));
    nav("Главная", () -> home());
    nav(
        "Приложения",
        () -> {
          showHidden = false;
          allApps();
        });
    nav("Избранное", () -> favorites());
    nav("Настройки", () -> settings());
    clock = label("", 14, WHITE);
    bar.addView(clock);
    updateClock();
  }

  void updateClock() {
    if (weather != null) loadWeather();
    if (clock != null)
      clock.setText(
          new SimpleDateFormat("EEE, d MMM  HH:mm", new Locale("ru", "RU")).format(new Date()));
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    worker.shutdownNow();
    unregisterReceiver(packages);
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (content != null) {
      refreshApps();
      home();
      restoreAppFocus();
    }
  }

  void restoreAppFocus() {
    if (returnFocusTag == null) return;
    View card = content.findViewWithTag(returnFocusTag);
    if (card != null) card.post(() -> card.requestFocus());
    returnFocusTag = null;
  }

  void nav(String s, Runnable r) {
    TextView t = label(s, 14, WHITE);
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
    TextView title = label("Добро пожаловать в NovaTV", 29, WHITE);
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
        content.addView(label("Избранное", 18, WHITE));
        rowApps(getFavorites(), true);
      }
    }
    if (getPreferences(0).getBoolean("show_all", true)) {
      gap(content, 18);
      content.addView(label("Все приложения", 22, WHITE));
      gap(content, 9);
      rowApps(installed);
    }
    for (String category : readIds("categories")) {
      List<App> apps = new ArrayList<>();
      for (App app : installed)
        if (category.equals(getPreferences(0).getString("category_" + app.pkg, ""))) apps.add(app);
      gap(content, 14);
      content.addView(label(category, 22, WHITE));
      if (visibleApps(apps, false).isEmpty())
        content.addView(label("Удерживайте OK на приложении → Категория", 14, MUTED));
      else rowApps(apps, false, "category_order_" + category);
    }
    loadWeather();
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
    if (rowKey.startsWith("category_order_")) {
      List<String> order = readIds(rowKey);
      list.sort(
          (a, b) ->
              Integer.compare(
                  order.contains(a.pkg) ? order.indexOf(a.pkg) : Integer.MAX_VALUE,
                  order.contains(b.pkg) ? order.indexOf(b.pkg) : Integer.MAX_VALUE));
    }
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setClipToPadding(false);
    scroll.setHorizontalScrollBarEnabled(false);
    content.addView(scroll, lp(-1, 104));
    LinearLayout row = horizontal();
    row.setPadding(dp(6), dp(7), dp(6), dp(7));
    scroll.addView(row);
    for (App a : list) {
      if (!showHidden && readIds("hidden").contains(a.pkg)) continue;
      final int cardWidth = getPreferences(0).getInt("card_size_v2", 116);
      LinearLayout card = vertical();
      card.setGravity(Gravity.CENTER);
      card.setPadding(dp(8), dp(6), dp(8), dp(6));
      card.setBackground(shape(0xee172131, 16));
      card.setFocusable(true);
      card.setTag(rowKey + ":" + a.pkg);
      card.setContentDescription(a.label + ". OK — запуск, удержание OK — меню");
      card.setOnFocusChangeListener(
          (v, f) -> {
            card.animate().scaleX(f ? 1.04f : 1).scaleY(f ? 1.04f : 1).setDuration(140).start();
            GradientDrawable bg = shape(f ? 0xff2b405b : 0xee172131, 16);
            if (f) bg.setStroke(dp(2), WHITE);
            card.setBackground(bg);
          });
      marginAdd(row, card, cardWidth, 88, 10);
      ImageView icon = new ImageView(this);
      icon.setImageDrawable(a.icon);
      icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      card.addView(icon, lp(46, 46));
      TextView name = label((favoriteIds().contains(a.pkg) ? "★ " : "") + a.label, 15, WHITE);
      name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER);
      card.addView(name, lp(cardWidth - 16, 24));
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
      card.setOnKeyListener(
          new View.OnKeyListener() {
            final CenterPress press = new CenterPress();
            final Runnable hold =
                () -> {
                  if (card.hasFocus() && hasWindowFocus() && press.hold()) card.performLongClick();
                };

            public boolean onKey(View v, int key, KeyEvent event) {
              if (movingPackage != null
                  && movingPackage.equals(a.pkg)
                  && event.getAction() == KeyEvent.ACTION_DOWN
                  && (key == KeyEvent.KEYCODE_DPAD_LEFT
                      || key == KeyEvent.KEYCODE_DPAD_RIGHT
                      || key == KeyEvent.KEYCODE_DPAD_UP
                      || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
                reorder(
                    a,
                    rowKey,
                    list,
                    (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_UP)
                        ? -1
                        : 1);
                return true;
              }
              if (key != KeyEvent.KEYCODE_DPAD_CENTER
                  && key != KeyEvent.KEYCODE_ENTER
                  && key != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
              if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                  press.down();
                  handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                }
                return true;
              }
              if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(hold);
                boolean click = press.up(event.isCanceled());
                if (click) card.performClick();
                return true;
              }
              return true;
            }
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
    new AlertDialog.Builder(this)
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
                  movingPackage = a.pkg;
                  movingFavorites = fav;
                  movingRowKey = rowKey;
                  movingScreen = currentScreen;
                  movingOrder = getPreferences(0).getString(rowKey, "");
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
    } else home();
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
      new AlertDialog.Builder(this)
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
    currentScreen = 3;
    showHidden = false;
    clear("Настройки");
    settingsGroup("ОФОРМЛЕНИЕ");
    settingsPair("Главный экран", this::appearance, "Размер шрифта", this::fontSettings);
    settingsPair(
        "Тема оформления",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Тема оформления")
                .setSingleChoiceItems(
                    new String[] {"Ночная", "Закат", "Океан"},
                    theme,
                    (d, n) -> {
                      d.dismiss();
                      changeNovaTheme(n);
                    })
                .setNegativeButton("Закрыть", null)
                .show(),
        "Выбрать обои",
        this::safeStartForWallpaper);
    settingsPair(
        "Сбросить обои",
        () -> {
          getPreferences(0).edit().remove("wallpaper").apply();
          render();
          settings();
        },
        "Категории",
        this::manageCategories);
    settingsGroup("ПРИЛОЖЕНИЯ");
    settingsPair(
        "Скрытые приложения",
        () -> {
          showHidden = true;
          allApps();
        },
        "Автозапуск",
        this::autostartSettings);
    settingsPair(
        "Обновить список",
        () -> {
          refreshApps();
          home();
        },
        "Диагностика TCL",
        this::diagnostics);
    settingsGroup("ПОГОДА И СИСТЕМА");
    settingsPair(
        "Город погоды",
        this::weatherCity,
        "Обновить погоду",
        () -> {
          getPreferences(0).edit().putLong("weather_time", 0).apply();
          loadWeather();
        });
    settingsPair(
        "Настройки ТВ",
        () -> safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS)),
        "Сеть",
        () -> safeStart(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
    settingsPair(
        "Дата и время",
        () -> safeStart(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS)),
        "Ошибка погоды",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Диагностика погоды")
                .setMessage(
                    getPreferences(0).getString("weather_error", "Ошибок нет")
                        + "\nВремя ТВ: "
                        + new Date())
                .setPositiveButton("Закрыть", null)
                .show());
  }

  void settingsGroup(String title) {
    gap(content, 12);
    content.addView(label(title, 13, MUTED));
    gap(content, 6);
  }

  void settingsPair(String left, Runnable a, String right, Runnable b) {
    LinearLayout row = horizontal();
    content.addView(row, lp(-1, 48));
    for (int i = 0; i < 2; i++) {
      final Runnable callback = i == 0 ? a : b;
      TextView button = label(i == 0 ? left : right, 17, WHITE);
      button.setPadding(dp(16), 0, dp(16), 0);
      button.setBackground(shape(0xee172131, 8));
      button.setFocusable(true);
      button.setOnClickListener(v -> callback.run());
      button.setOnFocusChangeListener(
          (v, focus) -> {
            button.setBackground(shape(focus ? 0xffd9eaff : 0xee172131, 8));
            button.setTextColor(focus ? 0xff101725 : WHITE);
          });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
      params.rightMargin = dp(10);
      row.addView(button, params);
    }
  }

  void fontSettings() {
    float scale = getPreferences(0).getFloat("font_scale", 0.8f);
    new AlertDialog.Builder(this)
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

  void changeNovaTheme(int value) {
    theme = value;
    getPreferences(0).edit().putInt("theme", theme).apply();
    render();
    settings();
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
            + "\nВерсия NovaTV: 0.5.0";
    new AlertDialog.Builder(this)
        .setTitle("Диагностика TCL")
        .setMessage(s)
        .setPositiveButton("Закрыть", null)
        .show();
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
    if (request == 7 && result == RESULT_OK && data != null && data.getData() != null) {
      try {
        getContentResolver()
            .takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getPreferences(0).edit().putString("wallpaper", data.getData().toString()).apply();
        render();
        settings();
      } catch (SecurityException e) {
        Toast.makeText(this, "Не удалось сохранить доступ к обоям", Toast.LENGTH_LONG).show();
      }
    }
  }

  void appearance() {
    String[] names = {
      "Показывать приветственный баннер", "Показывать избранное", "Показывать все приложения"
    };
    boolean[] values = {
      getPreferences(0).getBoolean("show_banner", false),
      getPreferences(0).getBoolean("show_favorites", true),
      getPreferences(0).getBoolean("show_all", true)
    };
    new AlertDialog.Builder(this)
        .setTitle("Главный экран")
        .setMultiChoiceItems(names, values, (d, n, checked) -> values[n] = checked)
        .setPositiveButton(
            "Сохранить",
            (d, n) -> {
              getPreferences(0)
                  .edit()
                  .putBoolean("show_banner", values[0])
                  .putBoolean("show_favorites", values[1])
                  .putBoolean("show_all", values[2])
                  .apply();
              render();
            })
        .setNeutralButton(
            "Размер карточек",
            (d, n) ->
                new AlertDialog.Builder(this)
                    .setTitle("Размер карточек")
                    .setSingleChoiceItems(
                        new String[] {"Компактные", "Обычные", "Крупные"},
                        getPreferences(0).getInt("card_size_v2", 116) == 96
                            ? 0
                            : getPreferences(0).getInt("card_size_v2", 116) == 136 ? 2 : 1,
                        (dialog, index) -> {
                          getPreferences(0)
                              .edit()
                              .putInt("card_size_v2", new int[] {96, 116, 136}[index])
                              .apply();
                          dialog.dismiss();
                          home();
                        })
                    .setNegativeButton("Закрыть", null)
                    .show())
        .setNegativeButton("Отмена", null)
        .show();
  }

  void manageCategories() {
    List<String> categories = readIds("categories");
    String[] choices = new String[categories.size() + 1];
    choices[0] = "Создать категорию";
    for (int i = 0; i < categories.size(); i++) choices[i + 1] = categories.get(i);
    new AlertDialog.Builder(this)
        .setTitle("Категории приложений")
        .setItems(
            choices,
            (d, n) -> {
              if (n == 0) {
                EditText name = new EditText(this);
                name.setSingleLine(true);
                new AlertDialog.Builder(this)
                    .setTitle("Название категории")
                    .setView(name)
                    .setPositiveButton(
                        "Создать",
                        (d2, n2) -> {
                          String value = name.getText().toString().trim().replace("|", "");
                          if (value.isEmpty() || categories.contains(value)) return;
                          categories.add(value);
                          getPreferences(0)
                              .edit()
                              .putString("categories", android.text.TextUtils.join("|", categories))
                              .apply();
                          home();
                        })
                    .setNegativeButton("Отмена", null)
                    .show();
              } else {
                categoryActions(categories.get(n - 1));
              }
            })
        .setNegativeButton("Закрыть", null)
        .show();
  }

  void categoryActions(String category) {
    new AlertDialog.Builder(this)
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
                home();
                manageCategories();
              } else if (n == 2) {
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(category);
                AlertDialog dialog =
                    new AlertDialog.Builder(this)
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
                new AlertDialog.Builder(this)
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
    new AlertDialog.Builder(this)
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
    new AlertDialog.Builder(this)
        .setTitle("Запуск приложения при старте NovaTV")
        .setItems(
            choices,
            (d, n) -> {
              getPreferences(0)
                  .edit()
                  .putString("autostart", n == 0 ? "" : installed.get(n - 1).pkg)
                  .apply();
              Toast.makeText(
                      this,
                      "Автозапуск сохранён. Сработает при следующем запуске NovaTV",
                      Toast.LENGTH_LONG)
                  .show();
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  void weatherPanel() {
    LinearLayout panel = horizontal();
    panel.setPadding(dp(16), dp(8), dp(16), dp(8));
    panel.setBackground(shape(0xbb20334e, 14));
    page.addView(panel, lp(-1, 48));
    weather = label("", 15, WHITE);
    weather.setMaxLines(2);
    panel.addView(weather, new LinearLayout.LayoutParams(0, -1, 1));
    action(panel, "Город", this::weatherCity);
    action(
        panel,
        "Обновить",
        () -> {
          getPreferences(0).edit().putLong("weather_time", 0).apply();
          loadWeather();
        });
    weather.setText(getPreferences(0).getString("weather_cache", "Погода: выберите город →"));
  }

  void weatherCity() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(getPreferences(0).getString("weather_city", ""));
    AlertDialog dialog =
        new AlertDialog.Builder(this)
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
                    worker.execute(
                        () -> {
                          try {
                            org.json.JSONArray places =
                                json("https://geocoding-api.open-meteo.com/v1/search?count=8&language=ru&name="
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
                                  new AlertDialog.Builder(this)
                                      .setTitle("Выберите город")
                                      .setItems(
                                          labels,
                                          (d2, n) -> {
                                            org.json.JSONObject place = places.optJSONObject(n);
                                            weatherRequests.invalidate();
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
                                        "Не удалось найти город. Проверьте интернет и повторите"
                                            + " поиск.");
                                  }
                                });
                          }
                        });
                  });
        });
    dialog.show();
  }

  org.json.JSONObject json(String url) throws Exception {
    java.net.HttpURLConnection c =
        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
    c.setConnectTimeout(10000);
    c.setReadTimeout(12000);
    c.setRequestProperty("Accept", "application/json");
    c.setRequestProperty("User-Agent", "NovaTVLauncher/0.5 AndroidTV");
    int status = c.getResponseCode();
    if (status != 200) {
      c.disconnect();
      throw new java.io.IOException("HTTP " + status);
    }
    try (java.io.InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] bytes = new byte[4096];
      int n;
      while ((n = in.read(bytes)) != -1) {
        out.write(bytes, 0, n);
        if (out.size() > 1048576) throw new java.io.IOException("Ответ слишком большой");
      }
      return new org.json.JSONObject(out.toString("UTF-8"));
    } finally {
      c.disconnect();
    }
  }

  String weatherFailure(Exception e) {
    if (e instanceof org.json.JSONException) return "Некорректный ответ сервиса";
    return WeatherErrors.describe(e);
  }

  void loadWeather() {
    String city = getPreferences(0).getString("weather_city", "");
    if (!weatherRequests.shouldLoad(
        city, System.currentTimeMillis(), getPreferences(0).getLong("weather_time", 0))) return;
    final int generation = weatherRequests.begin();
    final String latitude = getPreferences(0).getString("weather_lat", "");
    final String longitude = getPreferences(0).getString("weather_lon", "");
    if (weather != null) weather.setText(city + ": загружаем погоду…");
    worker.execute(
        () -> {
          String result;
          boolean ok = false;
          String weatherError = "";
          try {
            String lat = latitude;
            String lon = longitude;
            if (lat.isEmpty() || lon.isEmpty()) {
              org.json.JSONArray places =
                  json("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name="
                          + java.net.URLEncoder.encode(city, "UTF-8"))
                      .optJSONArray("results");
              if (places == null || places.length() == 0)
                throw new java.io.IOException("Город не найден");
              org.json.JSONObject place = places.getJSONObject(0);
              lat = place.getString("latitude");
              lon = place.getString("longitude");
            }
            org.json.JSONObject current;
            String provider = "Open-Meteo";
            try {
              current =
                  json("https://api.open-meteo.com/v1/forecast?latitude="
                          + lat
                          + "&longitude="
                          + lon
                          + "&current=temperature_2m,weather_code")
                      .getJSONObject("current");
            } catch (Exception primary) {
              try {
                org.json.JSONObject legacy =
                    json("https://api.open-meteo.com/v1/forecast?latitude="
                            + lat
                            + "&longitude="
                            + lon
                            + "&current_weather=true")
                        .getJSONObject("current_weather");
                current =
                    new org.json.JSONObject()
                        .put("temperature_2m", legacy.getDouble("temperature"))
                        .put("weather_code", legacy.getInt("weathercode"));
              } catch (Exception secondary) {
                try {
                  org.json.JSONObject backup =
                      json("https://wttr.in/" + lat + "," + lon + "?format=j1")
                          .getJSONArray("current_condition")
                          .getJSONObject(0);
                  current =
                      new org.json.JSONObject()
                          .put("temperature_2m", backup.getDouble("temp_C"))
                          .put("weather_code", -1);
                  provider = "wttr.in";
                } catch (Exception fallback) {
                  throw new java.io.IOException(
                      weatherFailure(primary) + "; резерв: " + weatherFailure(fallback), primary);
                }
              }
            }
            int code = current.getInt("weather_code");
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
                    + Math.round(current.getDouble("temperature_2m"))
                    + " °C"
                    + (sky.isEmpty() ? "" : ", " + sky)
                    + " · "
                    + provider
                    + " · "
                    + new SimpleDateFormat("HH:mm", new Locale("ru")).format(new Date());
            ok = true;
          } catch (Exception e) {
            weatherError = weatherFailure(e);
            result =
                getPreferences(0).getString("weather_cache", city + ": погода недоступна")
                    + " · "
                    + weatherError;
          }
          final String text = result;
          final String error = weatherError;
          final boolean success = ok;
          handler.post(
              () -> {
                if (!weatherRequests.complete(generation)) return;
                if (isDestroyed() || !city.equals(getPreferences(0).getString("weather_city", "")))
                  return;
                getPreferences(0).edit().putString("weather_error", error).apply();
                if (success)
                  getPreferences(0)
                      .edit()
                      .putString("weather_cache", text)
                      .putLong("weather_time", System.currentTimeMillis())
                      .apply();
                if (weather != null) weather.setText(text);
              });
        });
  }
}
