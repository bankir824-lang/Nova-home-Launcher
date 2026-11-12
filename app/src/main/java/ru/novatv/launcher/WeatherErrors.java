package ru.novatv.launcher;

final class WeatherErrors {
  static String describe(Exception e) {
    if (e instanceof javax.net.ssl.SSLException) return "Ошибка TLS: проверьте дату и время ТВ";
    if (e instanceof java.net.UnknownHostException) return "Не найден адрес сервиса (DNS)";
    if (e instanceof java.net.SocketTimeoutException) return "Сервис не ответил вовремя";
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }
}
