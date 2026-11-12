package ru.novatv.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class AppearanceTest {
  @Test
  public void wideCardsReserveTitleSpaceOnlyWhenEnabled() {
    assertEquals(98, Appearance.cardHeight(160, 0, false));
    assertEquals(122, Appearance.cardHeight(160, 0, true));
  }

  @Test
  public void ratiosProduceExpectedGeometry() {
    assertEquals(168, Appearance.cardHeight(160, 1, false));
    assertEquals(128, Appearance.cardHeight(160, 2, false));
  }

  @Test
  public void storedOutOfRangeWidthsCannotCreateHugeCards() {
    assertEquals(Appearance.cardHeight(240, 0, true), Appearance.cardHeight(10000, 0, true));
    assertEquals(Appearance.cardHeight(80, 0, true), Appearance.cardHeight(-10, 0, true));
  }
}
