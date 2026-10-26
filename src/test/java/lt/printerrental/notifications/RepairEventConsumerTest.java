package lt.printerrental.notifications;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepairEventConsumerTest {
  @ParameterizedTest
  @ValueSource(strings = {"RepairRequestCreated", "RepairStatusChanged"})
  void acceptsVersionedRepairEvents(String type) {
    var inbox = mock(NotificationInbox.class);
    var consumer = new RentalEventConsumer(inbox);
    var event =
        new DomainEvent(
            UUID.randomUUID(),
            1,
            type,
            Instant.now(),
            UUID.randomUUID(),
            "alice",
            Map.of("status", "OPEN"));
    consumer.accept(event);
    verify(inbox).accept(event);
  }
}
