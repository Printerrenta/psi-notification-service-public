package lt.printerrental.notifications;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import lt.printerrental.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class NotificationServiceTest {
  final NotificationRepository repository = mock(NotificationRepository.class);
  final CurrentUser user = mock(CurrentUser.class);
  final NotificationService service = new NotificationService(repository, user);
  final UUID id = UUID.randomUUID();

  Notification ownedBy(String subject) {
    return new Notification(
        id, UUID.randomUUID(), subject, "RepairRequestCreated", Instant.now(), null);
  }

  @Test
  void customerCannotMarkAnotherCustomersNotificationRead() {
    when(user.subject()).thenReturn("alice");
    when(repository.get(id)).thenReturn(Optional.of(ownedBy("bob")));
    assertThatThrownBy(() -> service.read(id)).isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).markRead(any());
  }

  @Test
  void customerCanReadTheirOwnNotification() {
    when(user.subject()).thenReturn("alice");
    when(repository.get(id)).thenReturn(Optional.of(ownedBy("alice")));
    service.read(id);
    verify(repository).markRead(id);
  }

  @Test
  void staffCanReadCustomerNotifications() {
    when(user.isStaff()).thenReturn(true);
    when(repository.get(id)).thenReturn(Optional.of(ownedBy("alice")));
    service.read(id);
    verify(repository).markRead(id);
  }

  @Test
  void customerListIsScopedToCurrentSubject() {
    when(user.subject()).thenReturn("alice");
    when(repository.list("alice", 0, 20)).thenReturn(List.of());
    service.list(0, 20);
    verify(repository).list("alice", 0, 20);
    verify(repository).count("alice");
  }
}
