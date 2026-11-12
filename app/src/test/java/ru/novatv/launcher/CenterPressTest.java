package ru.novatv.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class CenterPressTest {
  @Test
  public void shortPressActivatesExactlyOnce() {
    CenterPress p = new CenterPress();
    p.down();
    assertTrue(p.up(false));
    assertFalse(p.up(false));
  }

  @Test
  public void holdingNeverLaunchesOnRelease() {
    CenterPress p = new CenterPress();
    p.down();
    assertTrue(p.hold());
    assertFalse(p.hold());
    assertFalse(p.up(false));
  }

  @Test
  public void canceledAndOrphanReleasesDoNotLaunch() {
    CenterPress p = new CenterPress();
    assertFalse(p.up(false));
    p.down();
    assertFalse(p.up(true));
    assertFalse(p.hold());
  }

  @Test
  public void shortPressAfterLongPressWorks() {
    CenterPress p = new CenterPress();
    p.down();
    p.hold();
    p.up(false);
    p.down();
    assertTrue(p.up(false));
  }
}
