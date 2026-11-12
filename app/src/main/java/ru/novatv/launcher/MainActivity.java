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
        d.setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP);
        sc.setBackground(d);
      } catch (Exception e) {
        getPreferences(0).edit().remove("wallpaper").apply();
      }
    setContentView(sc);
    page = vertical();
    page.setPadding(dp(38), dp(20), dp(38), dp(28));
    sc.addView(page);
    header();
    gap(page, 17);
    banner();
    gap(page, 20);
    content = vertical();
    page.addView(content);
    home();
  }

  void header() {
    LinearLayout bar = horizontal();
    page.addView(bar, lp(-1, 48));
    TextView logo = label("✦  NovaTV", 26, WHITE);
    logo.setTypeface(null, Typeface.BOLD);
    marginAdd(bar, logo, 136, -1, 8);
    menu = horizontal();
    bar.addView(menu, new LinearLayout.LayoutParams(0, -1, 1));
    nav("Главная", () -> home());
    nav("Приложения", () -> allApps());
    nav("Избранное", () -> favorites());
    nav("Настройки", () -> settings());
    clock = label("", 14, WHITE);
    bar.addView(clock);
    updateClock();
  }

  void updateClock() {
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
    }
  }

  void nav(String s, Runnable r) {
    TextView t = label(s, 14, WHITE);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(10), 0, dp(10), 0);
    t.setBackground(shape(0x553e587b, 14));
    t.setFocusable(true);
    t.setOnClickListener(v -> r.run());
    t.setOnFocusChangeListener(
        (v, f) -> {
          t.setBackground(shape(f ? 0xffd9eaff : 0x553e587b, 14));
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
    TextView t = label(title, 22, WHITE);
    t.setTypeface(null, Typeface.BOLD);
    content.addView(t);
    gap(content, 9);
  }

  void home() {
    clear("Избранные приложения");
    List<App> fav = getFavorites();
    if (fav.isEmpty())
      content.addView(
          label("Удерживайте OK на карточке, чтобы добавить приложение в избранное", 15, MUTED));
    else rowApps(fav, true);
    gap(content, 18);
    content.addView(label("Все приложения", 22, WHITE));
    gap(content, 9);
    rowApps(installed);
    gap(content, 15);
    weather =
        label(
            getPreferences(0).getString("weather_cache", "Погода: укажите город в настройках"),
            14,
            MUTED);
    content.addView(weather);
    loadWeather();
  }

  void allApps() {
    clear("Все приложения");
    rowApps(installed);
  }

  void favorites() {
    clear("Избранное");
    List<App> fav = getFavorites();
    if (fav.isEmpty())
      content.addView(
          label("Удерживайте OK на карточке приложения, чтобы добавить его сюда", 16, MUTED));
    else rowApps(fav, true);
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
    TextView t = label(s, 18, WHITE);
    t.setGravity(Gravity.CENTER);
    t.setBackground(shape(0x663d5376, 12));
    t.setFocusable(true);
    t.setOnClickListener(v -> r.run());
    t.setOnFocusChangeListener(
        (v, f) -> {
          t.setBackground(shape(f ? 0xffd9eaff : 0x663d5376, 12));
          t.setTextColor(f ? 0xff0b1425 : WHITE);
        });
    marginAdd(parent, t, Math.max(52, s.length() * 10 + 24), 40, 8);
  }

  void rowApps(List<App> list) {
    rowApps(list, false);
  }

  void rowApps(List<App> list, boolean favoritesRow) {
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setClipToPadding(false);
    scroll.setHorizontalScrollBarEnabled(false);
    content.addView(scroll, lp(-1, 150));
    LinearLayout row = horizontal();
    row.setPadding(dp(6), dp(7), dp(6), dp(7));
    scroll.addView(row);
    for (App a : list) {
      LinearLayout card = vertical();
      card.setGravity(Gravity.CENTER);
      card.setPadding(dp(10), dp(10), dp(10), dp(10));
      card.setBackground(shape(0xcc273b59, 16));
      card.setFocusable(true);
      card.setContentDescription(a.label + ". OK — запуск, удержание OK — меню");
      card.setOnFocusChangeListener(
          (v, f) -> {
            card.animate().scaleX(f ? 1.05f : 1).scaleY(f ? 1.05f : 1).setDuration(140).start();
            GradientDrawable bg = shape(f ? 0xff415d83 : 0xcc273b59, 16);
            if (f) bg.setStroke(dp(2), WHITE);
            card.setBackground(bg);
          });
      marginAdd(row, card, 176, 128, 14);
      ImageView icon = new ImageView(this);
      icon.setImageDrawable(a.icon);
      icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      card.addView(icon, lp(70, 70));
      TextView name = label((favoriteIds().contains(a.pkg) ? "★ " : "") + a.label, 15, WHITE);
      name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER);
      card.addView(name, lp(156, 30));
      card.setOnClickListener(
          v -> {
            if (movingPackage != null) finishMove();
            else launch(a);
          });
      card.setOnLongClickListener(
          v -> {
            contextMenu(a, favoritesRow);
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
                    favoritesRow,
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
      if (a.pkg.equals(movingPackage) && favoritesRow == movingFavorites) card.requestFocus();
    }
  }

  void contextMenu(App a, boolean fav) {
    if (movingPackage != null) return;
    String[] items = {
      favoriteIds().contains(a.pkg) ? "Убрать из избранного" : "Добавить в избранное",
      "Переместить приложение",
      "Настройки приложения",
      "Информация",
      "Удалить приложение"
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
                  movingPackage = a.pkg;
                  movingFavorites = fav;
                  movingOrder = android.text.TextUtils.join("|", fav ? favoriteIds() : appIds());
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

  void reorder(App a, boolean fav, int delta) {
    List<String> ids = fav ? favoriteIds() : appIds();
    int from = ids.indexOf(a.pkg), to = from + delta;
    if (from < 0 || to < 0 || to >= ids.size()) return;
    Collections.swap(ids, from, to);
    getPreferences(0)
        .edit()
        .putString(fav ? "favorite_order" : "app_order", android.text.TextUtils.join("|", ids))
        .apply();
    refreshApps();
    home();
  }

  void finishMove() {
    movingPackage = null;
    Toast.makeText(this, "Порядок сохранён", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onBackPressed() {
    if (movingPackage != null) {
      getPreferences(0)
          .edit()
          .putString(movingFavorites ? "favorite_order" : "app_order", movingOrder)
          .apply();
      movingPackage = null;
      refreshApps();
      home();
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
    clear("Настройки NovaTV");
    LinearLayout themes = horizontal();
    content.addView(themes);
    action(themes, "Ночная тема", () -> changeNovaTheme(0));
    action(themes, "Закат", () -> changeNovaTheme(1));
    action(themes, "Океан", () -> changeNovaTheme(2));
    gap(content, 16);
    LinearLayout opts = horizontal();
    content.addView(opts);
    action(
        opts,
        "Обновить приложения",
        () -> {
          refreshApps();
          home();
        });
    action(opts, "Диагностика", this::diagnostics);
    LinearLayout extra = horizontal();
    content.addView(extra);
    action(extra, "Выбрать обои", () -> safeStartForWallpaper());
    action(
        extra,
        "Сбросить обои",
        () -> {
          getPreferences(0).edit().remove("wallpaper").apply();
          render();
          settings();
        });
    action(extra, "Город погоды", this::weatherCity);
    gap(content, 18);
    content.addView(
        label(
            "Настройку лаунчера по умолчанию выполним отдельно. Google TV пока остаётся без"
                + " изменений.",
            15,
            MUTED));
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
            + "\nВерсия NovaTV: 0.3.0";
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

  void weatherCity() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(getPreferences(0).getString("weather_city", ""));
    new AlertDialog.Builder(this)
        .setTitle("Город для погоды")
        .setMessage("Введите название города. Прогноз предоставляет Open-Meteo.")
        .setView(input)
        .setPositiveButton(
            "Сохранить",
            (d, n) -> {
              getPreferences(0)
                  .edit()
                  .putString("weather_city", input.getText().toString().trim())
                  .remove("weather_cache")
                  .putLong("weather_time", 0)
                  .apply();
              home();
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  org.json.JSONObject json(String url) throws Exception {
    java.net.HttpURLConnection c =
        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
    c.setConnectTimeout(10000);
    c.setReadTimeout(10000);
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

  private boolean weatherLoading;

  void loadWeather() {
    String city = getPreferences(0).getString("weather_city", "");
    if (city.isEmpty()
        || weatherLoading
        || System.currentTimeMillis() - getPreferences(0).getLong("weather_time", 0) < 1800000)
      return;
    weatherLoading = true;
    worker.execute(
        () -> {
          String result;
          boolean ok = false;
          try {
            org.json.JSONArray places =
                json("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name="
                        + java.net.URLEncoder.encode(city, "UTF-8"))
                    .optJSONArray("results");
            if (places == null || places.length() == 0) throw new java.io.IOException();
            org.json.JSONObject place = places.getJSONObject(0);
            org.json.JSONObject current =
                json("https://api.open-meteo.com/v1/forecast?latitude="
                        + place.getDouble("latitude")
                        + "&longitude="
                        + place.getDouble("longitude")
                        + "&current=temperature_2m,weather_code")
                    .getJSONObject("current");
            int code = current.getInt("weather_code");
            String sky =
                code == 0
                    ? "ясно"
                    : code <= 3
                        ? "облачно"
                        : code <= 48
                            ? "туман"
                            : code <= 67
                                ? "дождь"
                                : code <= 77 ? "снег" : code <= 86 ? "осадки" : "гроза";
            result =
                place.getString("name")
                    + ": "
                    + Math.round(current.getDouble("temperature_2m"))
                    + " °C, "
                    + sky
                    + " · Open-Meteo · "
                    + new SimpleDateFormat("HH:mm", new Locale("ru")).format(new Date());
            ok = true;
          } catch (Exception e) {
            result =
                getPreferences(0)
                        .getString(
                            "weather_cache", "Погода недоступна. Проверьте сеть и название города.")
                    + " · нет свежих данных";
          }
          final String text = result;
          final boolean success = ok;
          handler.post(
              () -> {
                weatherLoading = false;
                if (isDestroyed() || !city.equals(getPreferences(0).getString("weather_city", "")))
                  return;
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
