package ru.novatv.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class WeatherErrorsTest {
  @Test
  public void tlsHasActionableMessage() {
    assertTrue(
        WeatherErrors.describe(new javax.net.ssl.SSLHandshakeException("certificate"))
            .contains("дату и время"));
  }

  @Test
  public void dnsAndTimeoutHaveDistinctMessages() {
    assertTrue(WeatherErrors.describe(new java.net.UnknownHostException()).contains("DNS"));
    assertTrue(WeatherErrors.describe(new java.net.SocketTimeoutException()).contains("вовремя"));
  }

  @Test
  public void httpStatusIsPreserved() {
    assertEquals("HTTP 429", WeatherErrors.describe(new java.io.IOException("HTTP 429")));
  }
}
