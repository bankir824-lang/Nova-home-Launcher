package ru.novatv.launcher;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

final class AppCardView extends FrameLayout {
  final LinearLayout contents;
  private final MainActivity host;
  private final int radius;

  AppCardView(MainActivity host, int radius, int color) {
    super(host);
    this.host = host;
    this.radius = radius;
    setFocusable(true);
    contents = host.vertical();
    contents.setGravity(Gravity.CENTER);
    contents.setBackground(host.shape(color, radius));
    contents.setClipToOutline(true);
    addView(contents, new LayoutParams(-1, -1));
  }

  void showFocus(boolean focused, boolean zoom, int color) {
    float scale = focused && zoom ? 1.06f : 1f;
    animate().scaleX(scale).scaleY(scale).setDuration(150).start();
    setTranslationZ(focused ? host.dp(4) : 0);
    GradientDrawable outline = host.shape(Color.TRANSPARENT, radius);
    outline.setStroke(host.dp(2), color);
    setForeground(focused ? outline : null);
  }

  void addArtwork(Drawable artwork, int height, boolean banner, boolean mobile) {
    android.content.SharedPreferences prefs = host.getPreferences(0);
    FrameLayout box = new FrameLayout(host);
    if (mobile) {
      box.setBackgroundColor(prefs.getInt("mobile_card_color", 0x00000000));
      int padding = host.dp(prefs.getInt("mobile_icon_padding", 4));
      box.setPadding(padding, padding, padding, padding);
    }
    ImageView image = new ImageView(host);
    image.setImageDrawable(artwork);
    int mode = prefs.getInt("mobile_icon_mode", 0);
    image.setScaleType(banner || (mobile && mode == 0)
        ? ImageView.ScaleType.CENTER_CROP
        : mobile && mode == 2 ? ImageView.ScaleType.CENTER : ImageView.ScaleType.FIT_CENTER);
    int size = banner || mobile ? -1 : host.dp(Math.min(height, prefs.getInt("icon_size", 44)));
    if (mobile) {
      float scale = prefs.getInt("mobile_icon_scale", 100) / 100f;
      image.setScaleX(scale);
      image.setScaleY(scale);
    }
    box.addView(image, new LayoutParams(size, size, Gravity.CENTER));
    contents.addView(box, host.lp(-1, height));
  }
}
