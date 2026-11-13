package ru.novatv.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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

    PageState(Page page) {
      this.page = page;
    }
  }

  private final ArrayDeque<PageState> pages = new ArrayDeque<>();
  private ScrollView pageScroll;
  private LinearLayout body;
  private View first;
  private String currentTitle = "";

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
    super.show();
    Window window = getWindow();
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.setGravity(Gravity.END);
    window.setLayout((int) (host.getResources().getDisplayMetrics().widthPixels * 0.43f), -1);
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    WindowManager.LayoutParams attrs = window.getAttributes();
    attrs.dimAmount = 0.08f;
    window.setAttributes(attrs);
    host.previewSettings(true);
    drawPage();
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
    } else dismiss();
  }

  private void rememberPage() {
    if (pageScroll == null) return;
    PageState state = pages.peek();
    View focused = pageScroll.findFocus();
    state.focusKey = focused == null ? null : focused.getTag();
    state.scrollY = pageScroll.getScrollY();
  }

  private void drawPage() {
    accent = host.accent();
    ScrollView scroll = new ScrollView(host);
    pageScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    GradientDrawable bg =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xfa20324c, 0xfa101a30});
    bg.setCornerRadius(host.dp(22));
    scroll.setBackground(bg);
    body = host.vertical();
    body.setPadding(host.dp(18), host.dp(20), host.dp(18), host.dp(24));
    scroll.addView(body);
    setContentView(scroll);
    first = null;
    PageState state = pages.peek();
    state.page.build(this);
    View target = state.focusKey == null ? null : scroll.findViewWithTag(state.focusKey);
    if (target == null || !target.isFocusable() || !target.isEnabled()) target = first;
    if (target != null) target.requestFocus();
    scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        v.removeOnLayoutChangeListener(this);
        if (pageScroll == scroll) scroll.scrollTo(0, state.scrollY);
      }
    });
  }

  void title(String icon, String title, String subtitle) {
    currentTitle = title;
    TextView heading = host.label(title, 24, 0xfff4fdff);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    body.addView(heading);
    if (!subtitle.isEmpty()) {
      TextView text = host.label(subtitle, 13, 0xffbdc6dc);
      text.setPadding(0, host.dp(8), 0, host.dp(16));
      body.addView(text);
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
    row.setFocusable(true);
    row.setFocusableInTouchMode(true);
    row.setBackground(host.shape(0x00212a40, 26));
    ImageView badge = new ImageView(host);
    badge.setImageResource(iconFor(icon));
    badge.setColorFilter(accent);
    badge.setPadding(host.dp(7), host.dp(7), host.dp(7), host.dp(7));
    badge.setBackground(host.shape((accent & 0x00ffffff) | 0x22000000, 18));
    row.addView(badge, host.lp(32, 36));
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
    row.setOnFocusChangeListener(
        (v, focus) -> {
          GradientDrawable background = host.shape(focus ? 0xff2a4462 : 0x00212a40, 16);
          if (focus) background.setStroke(host.dp(2), accent);
          row.setBackground(background);
        });
    LinearLayout.LayoutParams rp = host.lp(-1, -2);
    rp.bottomMargin = host.dp(4);
    body.addView(row, rp);
    if (first == null) first = row;
    return row;
  }

  private int iconFor(String icon) {
    switch (icon) {
      case "♧": return R.drawable.nova_bell;
      case "◷":
      case "☾": return R.drawable.nova_clock;
      case "☁": return R.drawable.nova_weather;
      case "▦":
      case "▣": return R.drawable.nova_apps;
      case "◉": return R.drawable.nova_wifi;
      default: return R.drawable.nova_menu;
    }
  }

  void action(String icon, String title, String subtitle, Runnable action) {
    action("setting:" + title, icon, title, subtitle, action);
  }

  void action(String key, String icon, String title, String subtitle, Runnable action) {
    LinearLayout row = row(icon, title, subtitle);
    row.setTag(key);
    TextView arrow = host.label("›", 24, accent);
    row.addView(arrow);
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
      String title,
      String subtitle,
      android.graphics.drawable.Drawable icon,
      Runnable click,
      Runnable longClick) {
    LinearLayout row = row("", title, subtitle);
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
    TextView text = host.label(icon + "  " + title + " · " + value + suffix, 15, 0xffedf2ff);
    container.addView(text);
    SeekBar seek = new SeekBar(host);
    seek.setTag("slider:" + title);
    seek.setFocusable(true);
    seek.setFocusableInTouchMode(true);
    seek.setMax(max - min);
    seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    container.addView(seek, host.lp(-1, 34));
    seek.setOnFocusChangeListener(
        (v, focus) -> container.setBackground(host.shape(focus ? 0x667e91b6 : 0x00212a40, 22)));
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
            text.setText(icon + "  " + title + " · " + actual + suffix);
            changed.accept(actual);
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    body.addView(container, host.lp(-1, -2));
    if (first == null) first = seek;
  }
}
