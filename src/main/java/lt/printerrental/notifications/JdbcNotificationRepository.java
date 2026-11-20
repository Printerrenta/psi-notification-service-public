package lt.printerrental.notifications;

import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository, NotificationInbox {
  private final JdbcTemplate db;
  private final String schema;
  private final RowMapper<Notification> rows =
      (rs, n) ->
          new Notification(
              rs.getObject("event_id", UUID.class),
              rs.getObject("aggregate_id", UUID.class),
              rs.getString("owner_subject"),
              rs.getString("event_type"),
              rs.getTimestamp("occurred_at").toInstant(),
              rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());

  @Autowired
  public JdbcNotificationRepository(
      JdbcTemplate db, @Value("${DB_SCHEMA:notifications}") String schema) {
    if (schema == null || !schema.matches("[a-z][a-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid schema");
    }
    this.db = db;
    this.schema = schema;
  }

  public JdbcNotificationRepository(JdbcTemplate db) {
    this(db, "notifications");
  }

  @Override
  public void accept(DomainEvent e) {
    db.update(
        "INSERT INTO "
            + schema
            + ".notifications(event_id,aggregate_id,owner_subject,event_type,occurred_at)"
            + " VALUES (?,?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
        e.eventId(),
        e.aggregateId(),
        e.ownerSubject(),
        e.type(),
        java.sql.Timestamp.from(e.occurredAt()));
  }

  @Override
  public List<Notification> list(String owner, String readerSubject, int page, int size) {
    return owner == null
        ? db.query(
            selectForReader() + " ORDER BY n.occurred_at DESC, n.event_id LIMIT ? OFFSET ?",
            rows,
            readerSubject,
            readerSubject,
            size,
            (long) page * size)
        : db.query(
            selectForReader()
                + " WHERE n.owner_subject = ? ORDER BY n.occurred_at DESC, n.event_id LIMIT ?"
                + " OFFSET ?",
            rows,
            readerSubject,
            readerSubject,
            owner,
            size,
            (long) page * size);
  }

  @Override
  public long count(String owner) {
    return owner == null
        ? db.queryForObject("SELECT count(*) FROM " + schema + ".notifications", Long.class)
        : db.queryForObject(
            "SELECT count(*) FROM " + schema + ".notifications WHERE owner_subject = ?",
            Long.class,
            owner);
  }

  @Override
  public Optional<Notification> get(UUID id, String readerSubject) {
    return db.query(
            selectForReader() + " WHERE n.event_id = ?", rows, readerSubject, readerSubject, id)
        .stream()
        .findFirst();
  }

  @Override
  public void markRead(UUID id, String readerSubject, Instant readAt) {
    var timestamp = java.sql.Timestamp.from(readAt);
    db.update(
        "INSERT INTO "
            + schema
            + ".notification_reads(event_id, reader_subject, read_at)"
            + " VALUES (?, ?, ?) ON CONFLICT (event_id, reader_subject) DO NOTHING",
        id,
        readerSubject,
        timestamp);
    db.update(
        "UPDATE "
            + schema
            + ".notifications SET read_at = COALESCE(read_at, ?)"
            + " WHERE event_id = ? AND owner_subject = ?",
        timestamp,
        id,
        readerSubject);
  }

  private String selectForReader() {
    return "SELECT n.event_id, n.aggregate_id, n.owner_subject, n.event_type, n.occurred_at,"
        + " COALESCE(r.read_at, CASE WHEN n.owner_subject = ? THEN n.read_at ELSE NULL END)"
        + " AS read_at FROM "
        + schema
        + ".notifications n LEFT JOIN "
        + schema
        + ".notification_reads r ON r.event_id = n.event_id AND r.reader_subject = ?";
  }
}
