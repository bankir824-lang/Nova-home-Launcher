package ru.novatv.launcher;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

/** One physical OK press owns one click or long click, and never survives losing its target. */
final class RemoteOkHandler implements View.OnKeyListener, View.OnAttachStateChangeListener {
  private final View view;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final CenterPress press = new CenterPress();
  private int activeKey = KeyEvent.KEYCODE_UNKNOWN;
  private ViewTreeObserver observer;
  private final ViewTreeObserver.OnWindowFocusChangeListener windowFocus =
      focused -> {
        if (!focused) cancel();
      };
  private final Runnable hold;

  static RemoteOkHandler bind(View view) {
    return new RemoteOkHandler(view);
  }

  private RemoteOkHandler(View view) {
    this.view = view;
    hold =
        () -> {
          if (activeKey != KeyEvent.KEYCODE_UNKNOWN
              && view.hasFocus()
              && view.isShown()
              && press.hold()) view.performLongClick();
        };
    View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
    view.setOnFocusChangeListener(
        (target, focused) -> {
          if (!focused) cancel();
          if (previous != null) previous.onFocusChange(target, focused);
        });
    view.addOnAttachStateChangeListener(this);
    view.setOnKeyListener(this);
    if (view.isAttachedToWindow()) onViewAttachedToWindow(view);
  }

  void cancel() {
    handler.removeCallbacks(hold);
    press.up(true);
    activeKey = KeyEvent.KEYCODE_UNKNOWN;
  }

  @Override
  public boolean onKey(View target, int key, KeyEvent event) {
    boolean ok =
        key == KeyEvent.KEYCODE_DPAD_CENTER
            || key == KeyEvent.KEYCODE_ENTER
            || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
    if (!ok) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) cancel();
      return false;
    }
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (activeKey == KeyEvent.KEYCODE_UNKNOWN
          && event.getRepeatCount() == 0
          && target.hasFocus()
          && target.isAttachedToWindow()) {
        activeKey = key;
        press.down();
        handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
      }
      return true;
    }
    if (event.getAction() == KeyEvent.ACTION_UP && activeKey == key) {
      handler.removeCallbacks(hold);
      activeKey = KeyEvent.KEYCODE_UNKNOWN;
      if (press.up(event.isCanceled() || !target.hasFocus() || !target.isAttachedToWindow())) {
        target.performClick();
      }
    }
    return true;
  }

  @Override
  public void onViewAttachedToWindow(View target) {
    observer = target.getViewTreeObserver();
    observer.addOnWindowFocusChangeListener(windowFocus);
  }

  @Override
  public void onViewDetachedFromWindow(View target) {
    cancel();
    if (observer != null && observer.isAlive())
      observer.removeOnWindowFocusChangeListener(windowFocus);
    observer = null;
  }
}
