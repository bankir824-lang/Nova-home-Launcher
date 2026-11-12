package ru.novatv.launcher;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AppOrderTest {
  @Test
  public void movingRetainsHiddenApps() {
    List<String> all = new ArrayList<>(Arrays.asList("a", "hidden", "b"));
    assertTrue(AppOrder.moveVisible(all, Arrays.asList("a", "b"), "b", -1));
    assertEquals(Arrays.asList("b", "hidden", "a"), all);
  }

  @Test
  public void edgesAndMissingIdsDoNotChangeOrder() {
    List<String> all = new ArrayList<>(Arrays.asList("a", "b"));
    assertFalse(AppOrder.moveVisible(all, all, "a", -1));
    assertFalse(AppOrder.moveVisible(all, all, "b", 1));
    assertFalse(AppOrder.moveVisible(all, all, "missing", 1));
    assertEquals(Arrays.asList("a", "b"), all);
  }

  @Test
  public void categoryMoveDoesNotModifyAnotherList() {
    List<String> main = new ArrayList<>(Arrays.asList("a", "b", "c"));
    List<String> category = new ArrayList<>(Arrays.asList("a", "c"));
    assertTrue(AppOrder.moveVisible(category, category, "c", -1));
    assertEquals(Arrays.asList("a", "b", "c"), main);
    assertEquals(Arrays.asList("c", "a"), category);
  }
}
