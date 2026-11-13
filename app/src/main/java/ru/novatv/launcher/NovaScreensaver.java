package ru.novatv.launcher;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

/** Uses the existing wallpaper layer so opening the screensaver needs no new media player. */
final class NovaScreensaver extends FrameLayout {
  static final String ENABLED = "nova_screensaver_enabled";
  static final String DELAY = "nova_screensaver_delay";
  static final String WALLPAPER = "nova_screensaver_wallpaper";
  static final String DAILY_PHOTO = "nova_screensaver_daily_photo";
  static final int DEFAULT_DELAY_MINUTES = 5;

  private static final int[] POSITIONS = {
    Gravity.CENTER,
    Gravity.TOP | Gravity.START,
    Gravity.BOTTOM | Gravity.END,
    Gravity.TOP | Gravity.END,
    Gravity.BOTTOM | Gravity.START
  };

  private final TextClock clock;
  private ImageView photoView;
  private int position;
  private final Runnable moveClock = new Runnable() {
    @Override public void run() {
      position = (position + 1) % POSITIONS.length;
      FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) clock.getLayoutParams();
      bounds.gravity = POSITIONS[position];
      clock.setLayoutParams(bounds);
      postDelayed(this, 60_000);
    }
  };

  NovaScreensaver(MainActivity host, boolean showWallpaper) {
    super(host);
    setTag("nova.screensaver");
    setBackgroundColor(showWallpaper ? 0xc0000000 : Color.BLACK);
    clock = new TextClock(host);
    clock.setTextSize(44);
    clock.setTextColor(0xffc0c7d2);
    clock.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, POSITIONS[0]);
    int margin = host.dp(48);
    bounds.setMargins(margin, margin, margin, margin);
    addView(clock, bounds);
  }

  void setDailyPhoto(Bitmap photo) {
    if (photoView == null) {
      photoView = new ImageView(getContext());
      photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
      photoView.setColorFilter(0x77000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
      addView(photoView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
    photoView.setImageBitmap(photo);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    postDelayed(moveClock, 60_000);
  }

  @Override protected void onDetachedFromWindow() {
    removeCallbacks(moveClock);
    super.onDetachedFromWindow();
  }
}
