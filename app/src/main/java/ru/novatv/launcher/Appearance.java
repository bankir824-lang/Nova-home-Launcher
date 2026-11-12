package ru.novatv.launcher;

/** Pixel-independent geometry shared by layout and tests. */
final class Appearance {
  static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  static int cardHeight(int width, int ratio, boolean title) {
    int safe = clamp(width, 80, 240);
    int image = ratio == 1 ? safe : ratio == 2 ? safe * 3 / 4 : safe * 9 / 16;
    return image + (title ? 24 : 0) + 8;
  }
}
