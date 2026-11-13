package ru.novatv.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class WeatherRequestsTest {
  @Test
  public void changedCityCanLoadWhileOldRequestIsPending() {
    WeatherRequests r = new WeatherRequests();
    int old = r.begin();
    r.invalidate();
    assertTrue(r.shouldLoad("Казань", 100, 0));
    int current = r.begin();
    assertFalse(r.complete(old));
    assertFalse(r.shouldLoad("Казань", 100, 0));
    assertTrue(r.complete(current));
  }

  @Test
  public void cacheExpiresAndClockChangesDoNotBlockUpdates() {
    WeatherRequests r = new WeatherRequests();
    assertFalse(r.shouldLoad("", 100, 0));
    assertFalse(r.shouldLoad("Москва", 1000, 900));
    assertFalse(r.shouldLoad("Москва", 3600899, 900));
    assertTrue(r.shouldLoad("Москва", 3600900, 900));
    assertTrue(r.shouldLoad("Москва", 100, 900));
  }

  @Test
  public void failureAllowsRetry() {
    WeatherRequests r = new WeatherRequests();
    int request = r.begin();
    assertTrue(r.complete(request));
    assertTrue(r.shouldLoad("Омск", 100, 0));
  }
}
