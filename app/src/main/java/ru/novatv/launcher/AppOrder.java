package ru.novatv.launcher;

import java.util.Collections;
import java.util.List;

final class AppOrder {
  /** Move relative to visible neighbours while retaining hidden entries. */
  static boolean moveVisible(List<String> all, List<String> visible, String id, int delta) {
    int from = visible.indexOf(id), to = from + delta;
    if (from < 0 || to < 0 || to >= visible.size()) return false;
    int a = all.indexOf(id), b = all.indexOf(visible.get(to));
    if (a < 0 || b < 0) return false;
    Collections.swap(all, a, b);
    return true;
  }
}
