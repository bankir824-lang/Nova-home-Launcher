package ru.novatv.launcher;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class StatusBarView extends LinearLayout {
  private final MainActivity host;
  private final SharedPreferences preferences;
  private final int color;

  StatusBarView(MainActivity host) {
    super(host);
    this.host = host;
    preferences = host.getPreferences(0);
    color = preferences.getInt("status_color", 0xfff4fdff);
    setGravity(Gravity.CENTER_VERTICAL);
    int pad = host.dp(preferences.getInt("status_padding", 8));
    setPadding(pad, 0, pad, 0);
    setElevation(preferences.getBoolean("status_elevation", false) ? host.dp(3) : 0);
  }

  static float radius(int width, int height, int percent) {
    return Math.min(width, height) * Appearance.clamp(percent, 0, 100) / 200f;
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    int tint = preferences.getInt("status_background", 0xff101a30);
    int alpha = 255 * (100 - preferences.getInt("status_transparency", 30)) / 100;
    GradientDrawable background = new GradientDrawable();
    background.setColor((alpha << 24) | (tint & 0x00ffffff));
    background.setCornerRadius(radius(width, height, preferences.getInt("status_radius", 100)));
    setBackground(background);
  }

  TextView item(String text, String description, int icon, boolean numeric) {
    int mode = preferences.getInt("status_label_mode", 2);
    TextView view = host.label(mode == 0 && !numeric ? "" : text,
        preferences.getInt("status_size", 15), color);
    view.setGravity(Gravity.CENTER_VERTICAL);
    view.setSingleLine(true);
    view.setContentDescription(description);
    if (icon != 0 && mode != 1) {
      Drawable image = host.getDrawable(icon).mutate();
      int size = host.dp(preferences.getInt("status_icon_size", 20));
      image.setBounds(0, 0, size, size);
      image.setTint(color);
      view.setCompoundDrawablesRelative(image, null, null, null);
      view.setCompoundDrawablePadding(host.dp(4));
    }
    if (preferences.getBoolean("status_shadow", false))
      view.setShadowLayer(host.dp(2), 0, host.dp(1), Color.BLACK);
    LayoutParams params = new LayoutParams(-2, -1);
    if (getChildCount() != 0) params.setMarginStart(host.dp(preferences.getInt("status_gap", 8)));
    addView(view, params);
    return view;
  }
}
