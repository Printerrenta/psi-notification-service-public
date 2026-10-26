package lt.printerrental.notifications;

public class NotificationNotFoundException extends RuntimeException {
  public NotificationNotFoundException() {
    super("Notification not found");
  }
}
