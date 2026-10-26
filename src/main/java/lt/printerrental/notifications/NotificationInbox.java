package lt.printerrental.notifications;

import lt.printerrental.events.DomainEvent;

/** Consumer only needs idempotent insertion, not user query operations. */
public interface NotificationInbox {
  void accept(DomainEvent event);
}
