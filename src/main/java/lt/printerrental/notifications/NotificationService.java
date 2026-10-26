package lt.printerrental.notifications;

import java.util.*;
import lt.printerrental.auth.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository repository;
  private final CurrentUser user;

  public NotificationService(NotificationRepository repository, CurrentUser user) {
    this.repository = repository;
    this.user = user;
  }

  public record Page(List<Notification> content, int page, int size, long totalElements) {}

  @Transactional(readOnly = true)
  public Page list(int page, int size) {
    var owner = user.isStaff() ? null : user.subject();
    return new Page(repository.list(owner, page, size), page, size, repository.count(owner));
  }

  @Transactional
  public Notification read(UUID id) {
    var n = repository.get(id).orElseThrow(NotificationNotFoundException::new);
    if (!user.isStaff() && !user.subject().equals(n.ownerSubject()))
      throw new org.springframework.security.access.AccessDeniedException("Access denied");
    repository.markRead(id);
    return repository.get(id).orElseThrow();
  }
}
