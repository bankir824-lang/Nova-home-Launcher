package ru.novatv.launcher;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Notification contents stay in process memory and are cleared when access is disconnected. */
public class NotificationAccess extends NotificationListenerService {
  static final class Item {
    final String title, pkg;

    Item(String title, String pkg) {
      this.title = title;
      this.pkg = pkg;
    }
  }

  private static final Map<String, Item> items = new LinkedHashMap<>();

  static synchronized List<Item> snapshot() {
    return new ArrayList<>(items.values());
  }

  @Override
  public void onListenerConnected() {
    synchronized (NotificationAccess.class) {
      items.clear();
    }
    try {
      for (StatusBarNotification item : getActiveNotifications()) onNotificationPosted(item);
    } catch (SecurityException ignored) {
    }
  }

  @Override
  public void onNotificationPosted(StatusBarNotification item) {
    CharSequence title = item.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
    if (title == null) return;
    synchronized (NotificationAccess.class) {
      items.put(item.getKey(), new Item(title.toString(), item.getPackageName()));
    }
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification item) {
    synchronized (NotificationAccess.class) {
      items.remove(item.getKey());
    }
  }

  @Override
  public void onDestroy() {
    synchronized (NotificationAccess.class) {
      items.clear();
    }
    super.onDestroy();
  }

  @Override
  public void onListenerDisconnected() {
    synchronized (NotificationAccess.class) {
      items.clear();
    }
  }
}
