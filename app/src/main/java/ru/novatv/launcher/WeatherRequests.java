package ru.novatv.launcher;

/** Accessed only on the UI thread; old cities cannot overwrite newer requests. */
final class WeatherRequests {
  static final long INTERVAL_MS = 60 * 60 * 1000L;
  private int generation;
  private boolean loading;

  void invalidate() {
    generation++;
    loading = false;
  }

  boolean shouldLoad(String city, long now, long updated) {
    return !city.isEmpty()
        && !loading
        && (updated == 0 || now < updated || now - updated >= INTERVAL_MS);
  }

  int begin() {
    loading = true;
    return ++generation;
  }

  boolean complete(int request) {
    if (request != generation) return false;
    loading = false;
    return true;
  }
}
