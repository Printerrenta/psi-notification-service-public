package lt.printerrental.notifications;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import lt.printerrental.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class NotificationServiceTest {
  final NotificationRepository repository = mock(NotificationRepository.class);
  final CurrentUser user = mock(CurrentUser.class);
  final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
  final NotificationService service = new NotificationService(repository, user, clock);
  final UUID id = UUID.randomUUID();

  Notification ownedBy(String subject) {
    return new Notification(
        id, UUID.randomUUID(), subject, "RepairRequestCreated", Instant.now(), null);
  }

  @Test
  void customerCannotMarkAnotherCustomersNotificationRead() {
    when(user.subject()).thenReturn("alice");
    when(repository.get(id, "alice")).thenReturn(Optional.of(ownedBy("bob")));
    assertThatThrownBy(() -> service.read(id)).isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).markRead(any(), any(), any());
  }

  @Test
  void customerCanReadTheirOwnNotification() {
    when(user.subject()).thenReturn("alice");
    when(repository.get(id, "alice")).thenReturn(Optional.of(ownedBy("alice")));
    service.read(id);
    verify(repository).markRead(id, "alice", clock.instant());
  }

  @Test
  void staffCanReadCustomerNotifications() {
    when(user.isStaff()).thenReturn(true);
    when(user.subject()).thenReturn("staff-1");
    when(repository.get(id, "staff-1")).thenReturn(Optional.of(ownedBy("alice")));
    service.read(id);
    verify(repository).markRead(id, "staff-1", clock.instant());
  }

  @Test
  void customerListIsScopedToCurrentSubject() {
    when(user.subject()).thenReturn("alice");
    when(repository.list("alice", "alice", 0, 20)).thenReturn(List.of());
    service.list(0, 20);
    verify(repository).list("alice", "alice", 0, 20);
    verify(repository).count("alice");
  }

  @Test
  void staffListUsesStaffSubjectForPerReaderReadState() {
    when(user.isStaff()).thenReturn(true);
    when(user.subject()).thenReturn("staff-1");
    when(repository.list(null, "staff-1", 0, 20)).thenReturn(List.of());
    service.list(0, 20);
    verify(repository).list(null, "staff-1", 0, 20);
    verify(repository).count(null);
  }
}
