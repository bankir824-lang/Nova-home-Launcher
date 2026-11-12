package ru.novatv.launcher;

import java.util.*;

final class AppVisibility {
  static List<String> select(
      List<String> installed, Collection<String> hidden, boolean hiddenOnly) {
    List<String> result = new ArrayList<>();
    for (String id : installed) if (hiddenOnly == hidden.contains(id)) result.add(id);
    return result;
  }
}
