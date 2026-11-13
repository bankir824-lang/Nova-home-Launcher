package ru.novatv.launcher;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

final class NetworkDiagnostics {
  private static final String ENDPOINT = "https://www.google.com/generate_204";

  static String check(ConnectivityManager manager) {
    Network network = manager == null ? null : manager.getActiveNetwork();
    if (network == null) return "Нет активного подключения.";
    NetworkCapabilities caps = manager.getNetworkCapabilities(network);
    if (caps == null) return "Подключение изменилось. Повторите проверку.";
    String type = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN"
        : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet"
        : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Другое";
    String validation = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        ? "требуется вход в сеть"
        : caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ? "интернет подтверждён" : "интернет не подтверждён";
    StringBuilder report = new StringBuilder(type).append(" · Android: ").append(validation);
    long started = SystemClock.elapsedRealtime();
    HttpURLConnection connection = null;
    try {
      URL url = new URL(ENDPOINT);
      network.getAllByName(url.getHost());
      report.append("\nDNS: ").append(elapsed(started));
      started = SystemClock.elapsedRealtime();
      connection = (HttpURLConnection) network.openConnection(url);
      connection.setConnectTimeout(4000);
      connection.setReadTimeout(4000);
      connection.setInstanceFollowRedirects(false);
      connection.setUseCaches(false);
      int status = connection.getResponseCode();
      report.append("\nHTTPS · www.google.com: ").append(status).append(" · ").append(elapsed(started));
      if (status != 204) report.append("\nОжидался ответ 204. Проверьте доступ к контрольному адресу.");
    } catch (UnknownHostException e) {
      report.append("\nНе удалось разрешить имя www.google.com (DNS).");
    } catch (SSLException e) {
      report.append("\nОшибка защищённого соединения (TLS).");
    } catch (SocketTimeoutException e) {
      report.append("\nКонтрольный адрес не ответил вовремя.");
    } catch (IOException | SecurityException e) {
      report.append("\nКонтрольный адрес недоступен.");
    } finally {
      if (connection != null) connection.disconnect();
    }
    if (!network.equals(manager.getActiveNetwork())) report.append("\nВо время проверки сеть изменилась.");
    return report.toString();
  }

  private static String elapsed(long started) {
    return (SystemClock.elapsedRealtime() - started) + " мс";
  }
}
