package ru.novatv.launcher;

import android.os.Build;
import android.os.Handler;
import android.view.FrameMetrics;
import android.view.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

final class FrameSample {
  private final Handler handler;
  private final Map<Window, Window.OnFrameMetricsAvailableListener> listeners = new IdentityHashMap<>();
  private final ArrayList<Long> durations = new ArrayList<>();
  private int late, missing;
  private boolean running;

  FrameSample(Handler handler) {
    this.handler = handler;
  }

  boolean isRunning() {
    return running;
  }

  void start() {
    durations.clear();
    late = 0;
    missing = 0;
    running = true;
  }

  void watch(Window window, float refreshRate) {
    if (!running || listeners.containsKey(window)) return;
    long frameInterval = (long) (1_000_000_000d / (refreshRate > 0 ? refreshRate : 60f));
    Window.OnFrameMetricsAvailableListener listener = (source, metrics, dropped) -> {
      if (!running) return;
      missing += dropped;
      long duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
      if (duration < 0 || metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
      durations.add(duration);
      long deadline = Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.DEADLINE) : -1;
      if (duration >= (deadline > 0 ? deadline : frameInterval)) late++;
    };
    window.addOnFrameMetricsAvailableListener(listener, handler);
    listeners.put(window, listener);
  }

  String stop() {
    running = false;
    listeners.forEach(Window::removeOnFrameMetricsAvailableListener);
    listeners.clear();
    if (durations.isEmpty()) return "Кадров мало для оценки. Повторите замер и двигайтесь по меню.";
    Collections.sort(durations);
    int count = durations.size();
    double p90 = durations.get((int) Math.ceil(count * 0.9) - 1) / 1_000_000d;
    return String.format(Locale.getDefault(),
        "Кадров: %d\nДольше бюджета: %d из %d (%.0f%%)\n90%% кадров: до %.1f мс"
            + "\nПропущено отчётов Android: %d",
        count, late, count, 100d * late / count, p90, missing);
  }
}
