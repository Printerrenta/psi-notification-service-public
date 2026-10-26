package lt.printerrental.notifications;

import java.util.*;

/** Persistence port for user-facing notification queries and read state. */
public interface NotificationRepository {
  List<Notification> list(String owner, int page, int size);

  long count(String owner);

  Optional<Notification> get(UUID id);

  void markRead(UUID id);
}
