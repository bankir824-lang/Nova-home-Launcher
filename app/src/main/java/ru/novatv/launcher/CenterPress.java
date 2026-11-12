package ru.novatv.launcher;

/** One activation per physical press, even when the remote sends key repeats. */
final class CenterPress {
  private boolean pressed;
  private boolean held;

  void down() {
    pressed = true;
    held = false;
  }

  boolean hold() {
    if (!pressed || held) return false;
    held = true;
    return true;
  }

  boolean up(boolean canceled) {
    boolean click = pressed && !held && !canceled;
    pressed = false;
    return click;
  }
}
