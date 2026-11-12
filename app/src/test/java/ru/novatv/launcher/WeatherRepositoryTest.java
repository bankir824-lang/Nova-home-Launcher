package ru.novatv.launcher;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class WeatherRepositoryTest {
  @Test
  public void readsTemperatureFromFirstForecastPoint() throws Exception {
    JSONObject forecast = new JSONObject(
        "{\"properties\":{\"timeseries\":[{\"data\":{\"instant\":{\"details\":"
        + "{\"air_temperature\":-2.7}}}}]}}");
    assertEquals(-2.7, WeatherRepository.metTemperature(forecast), 0.01);
  }

  @Test(expected = JSONException.class)
  public void missingForecastDoesNotBecomeZeroDegrees() throws Exception {
    WeatherRepository.metTemperature(new JSONObject("{\"properties\":{\"timeseries\":[]}}"));
  }
}
