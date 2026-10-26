package lt.printerrental.notifications;

import java.time.Instant;
import java.util.UUID;

public record Notification(
    UUID id,
    UUID aggregateId,
    String ownerSubject,
    String type,
    Instant occurredAt,
    Instant readAt) {}
