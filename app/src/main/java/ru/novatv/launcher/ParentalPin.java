package ru.novatv.launcher;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class ParentalPin {
  static boolean valid(String pin) {
    return pin != null && pin.matches("[0-9]{4,8}");
  }

  static String salt() {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt);
  }

  static String hash(String pin, String salt) {
    try {
      PBEKeySpec key =
          new PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), 60000, 256);
      try {
        return Base64.getEncoder()
            .encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(key)
                    .getEncoded());
      } finally {
        key.clearPassword();
      }
    } catch (Exception e) {
      throw new IllegalStateException("Не удалось обработать PIN", e);
    }
  }

  static boolean matches(String pin, String salt, String expected) {
    if (!valid(pin)) return false;
    try {
      return MessageDigest.isEqual(
          Base64.getDecoder().decode(expected), Base64.getDecoder().decode(hash(pin, salt)));
    } catch (Exception e) {
      return false;
    }
  }
}
