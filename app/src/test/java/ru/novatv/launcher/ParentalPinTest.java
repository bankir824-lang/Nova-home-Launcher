package ru.novatv.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class ParentalPinTest {
  @Test
  public void validPinAndIncorrectPinAreDistinguished() {
    String salt = ParentalPin.salt(), hash = ParentalPin.hash("1234", salt);
    assertTrue(ParentalPin.matches("1234", salt, hash));
    assertFalse(ParentalPin.matches("4321", salt, hash));
  }

  @Test
  public void saltsChangeStoredHashes() {
    String a = ParentalPin.salt(), b = ParentalPin.salt();
    assertNotEquals(a, b);
    assertNotEquals(ParentalPin.hash("1234", a), ParentalPin.hash("1234", b));
  }

  @Test
  public void invalidPinAndMalformedDataAreRejected() {
    assertFalse(ParentalPin.valid("123"));
    assertFalse(ParentalPin.valid("abcd"));
    assertFalse(ParentalPin.matches("1234", "bad", "bad"));
  }
}
