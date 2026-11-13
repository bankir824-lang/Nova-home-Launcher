package ru.novatv.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** A remote-friendly settings stack. The launcher behind the dialog remains a live preview. */
final class TvSettingsPanel extends Dialog {
  interface Page {
    void build(TvSettingsPanel panel);
  }

  private final MainActivity host;
  private static final class PageState {
    final Page page;
    Object focusKey;
    int scrollY;
    int focusIndex;

    PageState(Page page) {
      this.page = page;
    }
  }

  private final ArrayDeque<PageState> pages = new ArrayDeque<>();
  private ScrollView pageScroll;
  private LinearLayout body;
  private LinearLayout heading;
  private View first;
  private String currentTitle = "";
  private boolean parked;

  boolean isPage(String title) {
    return title.equals(currentTitle);
  }

  private int accent;

  TvSettingsPanel(MainActivity host, int accent, Page root) {
    super(host);
    this.host = host;
    this.accent = accent;
    pages.push(new PageState(root));
    setOnDismissListener(d -> host.previewSettings(false));
  }

  @Override
  public void show() {
    Window window = getWindow();
    if (parked) {
      parked = false;
      if (accent != host.accent()) drawPage();
      host.previewSettings(true);
      super.show();
      Object key = pages.peek().focusKey;
      View target = key == null ? first : body.findViewWithTag(key);
      if (target != null) target.requestFocus();
      return;
    }
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.setGravity(Gravity.END);
    window.setLayout((int) (host.getResources().getDisplayMetrics().widthPixels * 0.43f), -1);
    window.setWindowAnimations(0);
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    drawPage();
    host.previewSettings(true);
    super.show();
  }

  boolean canResume() {
    return parked;
  }

  @Override
  public boolean isShowing() {
    return !parked && super.isShowing();
  }

  @Override
  public void dismiss() {
    parked = false;
    super.dismiss();
  }

  void open(Page page) {
    rememberPage();
    pages.push(new PageState(page));
    drawPage();
  }

  @Override
  public void onBackPressed() {
    if (pages.size() > 1) {
      pages.pop();
      drawPage();
    } else {
      rememberPage();
      super.hide();
      parked = true;
      host.previewSettings(false);
    }
  }

  private void rememberPage() {
    if (pageScroll == null) return;
    PageState state = pages.peek();
    View focused = pageScroll.findFocus();
    state.focusKey = focused == null ? null : focused.getTag();
    if (focused != null) {
      java.util.ArrayList<View> targets = body.getFocusables(View.FOCUS_FORWARD);
      state.focusIndex = Math.max(0, targets.indexOf(focused));
    }
    state.scrollY = pageScroll.getScrollY();
  }

  private void drawPage() {
    accent = host.accent();
    if (pageScroll == null) createPanel();
    heading.removeAllViews();
    body.removeAllViews();
    currentTitle = "";
    pageScroll.scrollTo(0, 0);
    first = null;
    PageState state = pages.peek();
    state.page.build(this);
    View target = state.focusKey == null ? null : body.findViewWithTag(state.focusKey);
    boolean restoreScroll = target != null && target.isFocusable() && target.isEnabled();
    if (!restoreScroll) {
      java.util.ArrayList<View> targets = body.getFocusables(View.FOCUS_FORWARD);
      target = targets.isEmpty() ? first : targets.get(Math.min(state.focusIndex, targets.size() - 1));
    }
    if (target == null) return;
    target.requestFocus();
    if (restoreScroll) {
      final View focused = target;
      body.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
          v.removeOnLayoutChangeListener(this);
          if (pages.peek() == state && focused.isFocused()) pageScroll.scrollTo(0, state.scrollY);
        }
      });
    }
  }

  private void createPanel() {
    LinearLayout panel = host.vertical();
    panel.setBackground(NovaUi.surface(host));
    heading = host.vertical();
    heading.setPadding(host.dp(20), host.dp(20), host.dp(20), host.dp(10));
    panel.addView(heading, host.lp(-1, -2));
    pageScroll = new ScrollView(host);
    pageScroll.setFillViewport(true);
    pageScroll.setVerticalScrollBarEnabled(false);
    pageScroll.setSmoothScrollingEnabled(false);
    body = host.vertical();
    body.setPadding(host.dp(14), 0, host.dp(14), host.dp(18));
    pageScroll.addView(body);
    panel.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(panel);
  }

  void title(String icon, String title, String subtitle) {
    currentTitle = title;
    if (pages.size() > 1) heading.addView(host.label("Настройки", 13, accent));
    LinearLayout titleRow = host.horizontal();
    titleRow.setGravity(Gravity.CENTER_VERTICAL);
    titleRow.addView(badge(icon), host.lp(32, 32));
    TextView caption = host.label(title, 24, NovaUi.TEXT);
    caption.setTypeface(null, android.graphics.Typeface.BOLD);
    caption.setPadding(host.dp(8), 0, 0, 0);
    titleRow.addView(caption);
    heading.addView(titleRow);
    if (!subtitle.isEmpty()) {
      TextView text = host.label(subtitle, 13, NovaUi.SECONDARY);
      text.setPadding(0, host.dp(6), 0, 0);
      heading.addView(text);
    }
    host.gap(body, 12);
  }

  void section(String label) {
    host.gap(body, 10);
    body.addView(host.label(label, 13, accent));
    host.gap(body, 6);
  }

  private LinearLayout row(String icon, String title, String subtitle) {
    LinearLayout row = host.horizontal();
    row.setContentDescription(title);
    row.setTag("setting:" + title);
    row.setPadding(host.dp(12), host.dp(9), host.dp(12), host.dp(9));
    row.setMinimumHeight(host.dp(52));
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setFocusable(true);
    row.setFocusableInTouchMode(true);
    row.setBackground(NovaUi.interaction(host, accent));
    row.addView(badge(icon), host.lp(32, 32));
    LinearLayout labels = host.vertical();
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    params.leftMargin = host.dp(10);
    row.addView(labels, params);
    TextView name = host.label(title, 17, 0xfff5f7ff);
    labels.addView(name);
    if (!subtitle.isEmpty()) {
      TextView hint = host.label(subtitle, 12, 0xffb9c2d7);
      labels.addView(hint);
    }
    LinearLayout.LayoutParams rp = host.lp(-1, -2);
    rp.bottomMargin = host.dp(4);
    body.addView(row, rp);
    if (first == null) first = row;
    return row;
  }

  private ImageView badge(String icon) {
    ImageView badge = new ImageView(host);
    badge.setImageResource(iconFor(icon));
    badge.setColorFilter(accent);
    badge.setPadding(host.dp(6), host.dp(6), host.dp(6), host.dp(6));
    badge.setBackground(host.shape((accent & 0x00ffffff) | 0x22000000, 9));
    return badge;
  }

  private int iconFor(String icon) {
    switch (icon) {
      case "♧": return R.drawable.nova_bell;
      case "◷": return R.drawable.nova_clock;
      case "☾": return R.drawable.nova_ui_moon;
      case "☁": return R.drawable.nova_weather;
      case "▦":
      case "☷": return R.drawable.nova_apps;
      case "▣":
      case "▭": return R.drawable.nova_ui_card;
      case "◉": return R.drawable.nova_wifi;
      case "⚙": return R.drawable.nova_ui_settings;
      case "⌂": return R.drawable.nova_ui_home;
      case "≡": return R.drawable.nova_ui_rows;
      case "━": return R.drawable.nova_ui_panel;
      case "▧": return R.drawable.nova_ui_photo;
      case "◐": return R.drawable.nova_ui_palette;
      case "◯": return R.drawable.nova_ui_drop;
      case "▤": return R.drawable.nova_ui_tv;
      case "◎": return R.drawable.nova_ui_pulse;
      case "▯": return R.drawable.nova_ui_phone;
      case "eye": return R.drawable.nova_ui_eye;
      case "eye_off": return R.drawable.nova_ui_eye_off;
      case "folder": return R.drawable.nova_ui_folder;
      case "layers": return R.drawable.nova_ui_layers;
      case "⇥": return R.drawable.nova_ui_input;
      case "⌁": return R.drawable.nova_ui_remote;
      case "shield": return R.drawable.nova_ui_shield;
      case "♿": return R.drawable.nova_ui_access;
      case "↺":
      case "↻": return R.drawable.nova_ui_reset;
      case "▷": return R.drawable.nova_ui_play;
      case "calendar": return R.drawable.nova_ui_calendar;
      case "ruler": return R.drawable.nova_ui_ruler;
      case "T": return R.drawable.nova_ui_type;
      case "☀": return R.drawable.nova_ui_sun;
      case "!": return R.drawable.nova_ui_info;
      case "⌖": return R.drawable.nova_ui_pin;
      case "☆":
      case "✦": return R.drawable.nova_ui_star;
      case "+": return R.drawable.nova_ui_plus;
      case "↔": return R.drawable.nova_ui_horizontal;
      case "↕": return R.drawable.nova_ui_vertical;
      case "←": return R.drawable.nova_ui_left;
      case "→": return R.drawable.nova_ui_right;
      case "↑": return R.drawable.nova_ui_up;
      case "⏻": return R.drawable.nova_ui_power;
      default: return R.drawable.nova_ui_more;
    }
  }

  void action(String icon, String title, String subtitle, Runnable action) {
    action("setting:" + title, icon, title, subtitle, action);
  }

  void action(String key, String icon, String title, String subtitle, Runnable action) {
    LinearLayout row = row(icon, title, subtitle);
    row.setTag(key);
    ImageView arrow = new ImageView(host);
    arrow.setImageResource(R.drawable.nova_ui_chevron);
    arrow.setColorFilter(accent);
    row.addView(arrow, host.lp(20, 20));
    row.setOnClickListener(v -> action.run());
  }

  void unavailable(String icon, String title, String reason) {
    LinearLayout row = row(icon, title, reason);
    row.setEnabled(false);
    row.setFocusable(false);
    if (first == row) first = null;
  }

  void refresh() {
    rememberPage();
    drawPage();
  }

  void app(
      String key,
      String title,
      String subtitle,
      android.graphics.drawable.Drawable icon,
      Runnable click,
      Runnable longClick) {
    LinearLayout row = row("", title, subtitle);
    row.setTag(key);
    replaceBadge(row, icon);
    row.setOnClickListener(v -> click.run());
    row.setOnLongClickListener(
        v -> {
          longClick.run();
          return true;
        });
    RemoteOkHandler.bind(row);
  }

  void notification(String title, String subtitle, android.graphics.drawable.Drawable icon,
      Runnable click) {
    LinearLayout row = row("", title, subtitle);
    replaceBadge(row, icon);
    row.setOnClickListener(v -> click.run());
  }

  private void replaceBadge(LinearLayout row, android.graphics.drawable.Drawable icon) {
    if (icon != null) {
      View old = row.getChildAt(0);
      android.view.ViewGroup.LayoutParams params = old.getLayoutParams();
      row.removeViewAt(0);
      ImageView image = new ImageView(host);
      image.setImageDrawable(icon);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      row.addView(image, 0, params);
    }
  }

  void toggle(
      String icon, String title, String subtitle, boolean value, Consumer<Boolean> changed) {
    LinearLayout row = row(icon, title, subtitle);
    Switch toggle = new Switch(host);
    toggle.setChecked(value);
    int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
    int[] colors = {accent, 0xff9aacc5};
    toggle.setThumbTintList(new android.content.res.ColorStateList(states, colors));
    toggle.setTrackTintList(
        new android.content.res.ColorStateList(
            states, new int[] {(accent & 0x00ffffff) | 0x99000000, 0xff4a556c}));
    toggle.setClickable(false);
    toggle.setFocusable(false);
    row.addView(toggle);
    row.setOnClickListener(
        v -> {
          toggle.setChecked(!toggle.isChecked());
          changed.accept(toggle.isChecked());
        });
  }

  void slider(
      String icon, String title, int min, int max, int value, String suffix, IntConsumer changed) {
    LinearLayout container = host.vertical();
    container.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
    LinearLayout labelRow = host.horizontal();
    labelRow.setGravity(Gravity.CENTER_VERTICAL);
    labelRow.addView(badge(icon), host.lp(28, 28));
    TextView text = host.label(title + " · " + value + suffix, 15, NovaUi.TEXT);
    text.setPadding(host.dp(8), 0, 0, 0);
    labelRow.addView(text);
    container.addView(labelRow);
    SeekBar seek = new SeekBar(host);
    seek.setTag("slider:" + title);
    seek.setFocusable(true);
    seek.setFocusableInTouchMode(true);
    seek.setMax(max - min);
    seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    container.addView(seek, host.lp(-1, 34));
    container.setAddStatesFromChildren(true);
    container.setBackground(NovaUi.interaction(host, accent));
    seek.setContentDescription(title);
    seek.setOnKeyListener(
        (v, key, event) -> {
          if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
          if (event.getAction() == KeyEvent.ACTION_DOWN)
            seek.setProgress(
                Math.max(
                    0,
                    Math.min(
                        seek.getMax(),
                        seek.getProgress() + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1))));
          return true;
        });
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            int actual = progress + min;
            text.setText(title + " · " + actual + suffix);
            changed.accept(actual);
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    body.addView(container, host.lp(-1, -2));
    if (first == null) first = seek;
  }
}
