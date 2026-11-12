package ru.novatv.launcher;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AppVisibilityTest {
  @Test
  public void allHiddenFavoritesProduceNoVisibleRow() {
    assertTrue(
        AppVisibility.select(Arrays.asList("favorite"), Arrays.asList("favorite"), false)
            .isEmpty());
  }

  @Test
  public void hiddenScreenContainsOnlyInstalledHiddenApps() {
    assertEquals(
        Arrays.asList("b"),
        AppVisibility.select(
            Arrays.asList("a", "b", "c"), Arrays.asList("b", "uninstalled"), true));
  }

  @Test
  public void showingAppAgainRestoresFavoriteWithoutChangingStoredIds() {
    List<String> favorites = new ArrayList<>(Arrays.asList("a", "b"));
    assertEquals(Arrays.asList("b"), AppVisibility.select(favorites, Arrays.asList("a"), false));
    assertEquals(
        Arrays.asList("a", "b"), AppVisibility.select(favorites, Collections.emptyList(), false));
    assertEquals(Arrays.asList("a", "b"), favorites);
  }
}
