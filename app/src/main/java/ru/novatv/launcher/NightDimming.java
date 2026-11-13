package ru.novatv.launcher;

final class NightDimming {
  static final String ENABLED = "wallpaper_night_enabled";
  static final String START = "wallpaper_night_start";
  static final String END = "wallpaper_night_end";
  static final String AMOUNT = "wallpaper_night_amount";
  static final String STEP = "wallpaper_night_step";
  static final int DEFAULT_START = 22 * 60;
  static final int DEFAULT_END = 7 * 60;
  static final int DEFAULT_AMOUNT = 35;
  static final int DEFAULT_STEP = 10;

  static boolean includes(int minute, int start, int end) {
    if (start == end) return false;
    if (start < end) return minute >= start && minute < end;
    return minute >= start || minute < end;
  }

  static int amountAt(int minute, int start, int end, int maximum, int step) {
    if (!includes(minute, start, end)) return 0;
    int elapsed = (minute - start + 1440) % 1440;
    int duration = (end - start + 1440) % 1440;
    if (maximum == 0) return 0;
    int levels = (maximum + step - 1) / step;
    if (duration <= 120 * (levels - 1)) {
      // Compress both ramps into the available minutes while reserving the midpoint for the peak.
      int peak = (duration - 1) / 2;
      int fromEdge = Math.min(elapsed, duration - 1 - elapsed);
      if (fromEdge == peak) return maximum;
      return step * (1 + fromEdge * (levels - 1) / peak);
    }
    int stepsUp = 1 + elapsed / 60;
    int stepsDown = (duration - elapsed + 59) / 60;
    return Math.min(maximum, step * Math.min(stepsUp, stepsDown));
  }
}
