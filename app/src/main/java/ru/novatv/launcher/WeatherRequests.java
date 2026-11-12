package ru.novatv.launcher;

/** Accessed only on the UI thread; old cities cannot overwrite newer requests. */
final class WeatherRequests {
  private int generation;
  private boolean loading;

  void invalidate() {
    generation++;
    loading = false;
  }

  boolean shouldLoad(String city, long now, long updated) {
    return !city.isEmpty()
        && !loading
        && (updated == 0 || now < updated || now - updated >= 1800000);
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
