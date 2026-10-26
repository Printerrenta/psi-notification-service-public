package lt.printerrental.notifications;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.junit.jupiter.api.Test;

class RentalEventConsumerTest {
  @Test
  void unknownTypesNeverReachTheInbox() {
    var repository = mock(NotificationInbox.class);
    var consumer = new RentalEventConsumer(repository);
    assertThatThrownBy(
            () ->
                consumer.accept(
                    new DomainEvent(
                        UUID.randomUUID(),
                        1,
                        "UnknownEvent",
                        Instant.now(),
                        UUID.randomUUID(),
                        "alice",
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }
}
