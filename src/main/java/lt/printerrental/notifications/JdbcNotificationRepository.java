package lt.printerrental.notifications;

import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository, NotificationInbox {
  private final JdbcTemplate db;
  private final RowMapper<Notification> rows =
      (rs, n) ->
          new Notification(
              rs.getObject("event_id", UUID.class),
              rs.getObject("aggregate_id", UUID.class),
              rs.getString("owner_subject"),
              rs.getString("event_type"),
              rs.getTimestamp("occurred_at").toInstant(),
              rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());

  public JdbcNotificationRepository(JdbcTemplate db) {
    this.db = db;
  }

  public void accept(DomainEvent e) {
    db.update(
        "INSERT INTO"
            + " notifications.notifications(event_id,aggregate_id,owner_subject,event_type,occurred_at)"
            + " VALUES (?,?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
        e.eventId(),
        e.aggregateId(),
        e.ownerSubject(),
        e.type(),
        java.sql.Timestamp.from(e.occurredAt()));
  }

  public List<Notification> list(String owner, int page, int size) {
    return owner == null
        ? db.query(
            "SELECT * FROM notifications.notifications ORDER BY occurred_at DESC,event_id LIMIT ?"
                + " OFFSET ?",
            rows,
            size,
            (long) page * size)
        : db.query(
            "SELECT * FROM notifications.notifications WHERE owner_subject=? ORDER BY occurred_at"
                + " DESC,event_id LIMIT ? OFFSET ?",
            rows,
            owner,
            size,
            (long) page * size);
  }

  public long count(String owner) {
    return owner == null
        ? db.queryForObject("SELECT count(*) FROM notifications.notifications", Long.class)
        : db.queryForObject(
            "SELECT count(*) FROM notifications.notifications WHERE owner_subject=?",
            Long.class,
            owner);
  }

  public Optional<Notification> get(UUID id) {
    return db.query("SELECT * FROM notifications.notifications WHERE event_id=?", rows, id).stream()
        .findFirst();
  }

  public void markRead(UUID id) {
    db.update(
        "UPDATE notifications.notifications SET read_at=COALESCE(read_at,now()) WHERE event_id=?",
        id);
  }
}
