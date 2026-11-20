package lt.printerrental.notifications;

import java.time.Instant;
import java.util.*;

/** Persistence port for user-facing notification queries and read state. */
public interface NotificationRepository {
  List<Notification> list(String owner, String readerSubject, int page, int size);

  long count(String owner);

  Optional<Notification> get(UUID id, String readerSubject);

  void markRead(UUID id, String readerSubject, Instant readAt);
}
