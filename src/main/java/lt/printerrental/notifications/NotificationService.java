package lt.printerrental.notifications;

import java.time.Clock;
import java.util.*;
import lt.printerrental.auth.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository repository;
  private final CurrentUser user;
  private final Clock clock;

  @Autowired
  public NotificationService(NotificationRepository repository, CurrentUser user, Clock clock) {
    this.repository = repository;
    this.user = user;
    this.clock = clock;
  }

  public NotificationService(NotificationRepository repository, CurrentUser user) {
    this(repository, user, Clock.systemUTC());
  }

  public record Page(List<Notification> content, int page, int size, long totalElements) {}

  @Transactional(readOnly = true)
  public Page list(int page, int size) {
    var reader = user.subject();
    var owner = user.isStaff() ? null : reader;
    return new Page(
        repository.list(owner, reader, page, size), page, size, repository.count(owner));
  }

  @Transactional
  public Notification read(UUID id) {
    var reader = user.subject();
    var n = repository.get(id, reader).orElseThrow(NotificationNotFoundException::new);
    if (!user.isStaff() && !reader.equals(n.ownerSubject()))
      throw new org.springframework.security.access.AccessDeniedException("Access denied");
    repository.markRead(id, reader, clock.instant());
    return repository.get(id, reader).orElseThrow();
  }
}
