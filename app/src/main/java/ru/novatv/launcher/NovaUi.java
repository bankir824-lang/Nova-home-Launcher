package ru.novatv.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

final class NovaUi {
  static final int TEXT = 0xfff4fdff;
  static final int SECONDARY = 0xffb9cee4;
  static final int DEFAULT_ACCENT = 0xff78fff0;

  private NovaUi() {}

  static int dp(Context context, int size) {
    return Math.round(size * context.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable surface(Context context) {
    GradientDrawable drawable = new GradientDrawable(
        GradientDrawable.Orientation.TL_BR, new int[] {0xfc20324c, 0xfc101a30});
    drawable.setCornerRadius(dp(context, 22));
    drawable.setStroke(dp(context, 1), 0x44b9cee4);
    return drawable;
  }

  static StateListDrawable interaction(Context context, int accent) {
    StateListDrawable drawable = new StateListDrawable();
    drawable.addState(new int[] {-android.R.attr.state_enabled}, shape(context, 0, 0));
    drawable.addState(new int[] {android.R.attr.state_pressed}, shape(context, 0xff365675, accent));
    drawable.addState(new int[] {android.R.attr.state_focused}, shape(context, 0xff2a4462, accent));
    drawable.addState(new int[] {android.R.attr.state_hovered}, shape(context, 0xff263c56, 0x55ffffff));
    drawable.addState(new int[0], shape(context, 0, 0));
    return drawable;
  }

  static StateListDrawable listSelection(Context context, int accent) {
    StateListDrawable drawable = new StateListDrawable();
    drawable.addState(new int[] {android.R.attr.state_pressed}, shape(context, 0xff365675, accent));
    drawable.addState(new int[] {android.R.attr.state_hovered}, shape(context, 0xff263c56, 0x55ffffff));
    drawable.addState(new int[] {android.R.attr.state_focused}, shape(context, 0xff2a4462, accent));
    drawable.addState(new int[] {android.R.attr.state_selected}, shape(context, 0xff2a4462, accent));
    drawable.addState(new int[0], shape(context, 0, 0));
    return drawable;
  }

  private static GradientDrawable shape(Context context, int fill, int stroke) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(fill);
    drawable.setCornerRadius(dp(context, 14));
    drawable.setStroke(dp(context, 2), stroke);
    return drawable;
  }
}
