package ru.novatv.launcher;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.*;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.Button;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class RemoteOkHandlerTest {
  private static final int[] KEYS = {
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER
  };

  private static final class Fixture implements AutoCloseable {
    final ActivityController<Activity> controller =
        Robolectric.buildActivity(Activity.class).setup().visible();
    final Button button = new Button(controller.get());
    final RemoteOkHandler remote;
    final Button other = new Button(controller.get());
    int clicks, menus, focusChanges;

    Fixture() {
      button.setFocusableInTouchMode(true);
      button.setOnClickListener(v -> clicks++);
      button.setOnLongClickListener(
          v -> {
            menus++;
            return true;
          });
      button.setOnFocusChangeListener((v, f) -> focusChanges++);
      android.widget.LinearLayout group = new android.widget.LinearLayout(controller.get());
      group.setOrientation(android.widget.LinearLayout.VERTICAL);
      group.addView(button);
      other.setFocusableInTouchMode(true);
      group.addView(other);
      controller.get().setContentView(group);
      button.requestFocus();
      remote = RemoteOkHandler.bind(button);
    }

    void event(int action, int key, int repeat) {
      long now = SystemClock.uptimeMillis();
      remote.onKey(button, key, new KeyEvent(now, now, action, key, repeat));
    }

    void waitForHold() {
      shadowOf(Looper.getMainLooper())
          .idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
    }

    @Override
    public void close() {
      controller.get().setContentView(new View(controller.get()));
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void eachOkKeyLaunchesExactlyOnceOnRelease() {
    for (int key : KEYS)
      try (Fixture f = new Fixture()) {
        f.event(KeyEvent.ACTION_DOWN, key, 0);
        assertEquals(0, f.clicks);
        f.event(KeyEvent.ACTION_UP, key, 0);
        f.event(KeyEvent.ACTION_UP, key, 0);
        f.waitForHold();
        assertEquals(1, f.clicks);
        assertEquals(0, f.menus);
      }
  }

  @Test
  public void eachOkKeyHoldingOpensOnlyOneMenuAndNeverLaunchesOnRelease() {
    for (int key : KEYS)
      try (Fixture f = new Fixture()) {
        f.event(KeyEvent.ACTION_DOWN, key, 0);
        f.waitForHold();
        f.event(KeyEvent.ACTION_DOWN, key, 1);
        f.waitForHold();
        f.event(KeyEvent.ACTION_UP, key, 0);
        assertEquals(1, f.menus);
        assertEquals(0, f.clicks);
      }
  }

  @Test
  public void repeatEventsDoNotRestartTimerOrActivateAgain() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 3);
      f.waitForHold();
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      assertEquals(1, f.menus);
      assertEquals(0, f.clicks);
    }
  }

  @Test
  public void orphanRepeatsAndReleasesAreIgnored() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 5);
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      f.waitForHold();
      assertEquals(0, f.clicks);
      assertEquals(0, f.menus);
    }
  }

  @Test
  public void mismatchedReleaseCannotActivateAnotherKey() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      f.event(KeyEvent.ACTION_UP, KEYS[1], 0);
      assertEquals(0, f.clicks);
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      assertEquals(1, f.clicks);
    }
  }

  @Test
  public void canceledReleaseDoesNotLaunch() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      long now = SystemClock.uptimeMillis();
      f.remote.onKey(
          f.button,
          KEYS[0],
          new KeyEvent(now, now, KeyEvent.ACTION_UP, KEYS[0], 0, 0, 0, 0, KeyEvent.FLAG_CANCELED));
      f.waitForHold();
      assertEquals(0, f.clicks);
      assertEquals(0, f.menus);
    }
  }

  @Test
  public void focusLossCancelsPressAndPreservesStylingListener() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      int before = f.focusChanges;
      f.other.requestFocus();
      assertTrue(f.focusChanges > before);
      f.waitForHold();
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      assertEquals(0, f.clicks);
      assertEquals(0, f.menus);
    }
  }

  @Test
  public void losingWindowFocusCancelsPendingClickAndMenu() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      org.robolectric.util.ReflectionHelpers.callInstanceMethod(
          f.button.getViewTreeObserver(),
          "dispatchOnWindowFocusChange",
          org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, false));
      f.waitForHold();
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      assertEquals(0, f.clicks);
      assertEquals(0, f.menus);
    }
  }

  @Test
  public void detachedViewCannotActivateAfterTimeout() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      f.controller.get().setContentView(new View(f.controller.get()));
      f.waitForHold();
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      assertEquals(0, f.clicks);
      assertEquals(0, f.menus);
    }
  }

  @Test
  public void directionalKeyCancelsPendingOk() {
    try (Fixture f = new Fixture()) {
      f.event(KeyEvent.ACTION_DOWN, KEYS[0], 0);
      f.event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0);
      f.waitForHold();
      f.event(KeyEvent.ACTION_UP, KEYS[0], 0);
      assertEquals(0, f.clicks);
      assertEquals(0, f.menus);
    }
  }
}
