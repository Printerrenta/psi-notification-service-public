package lt.printerrental.notifications;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountingEventConsumerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "MeterReadingAccepted",
        "MeterReadingNeedsReview",
        "MeterReadingRejected",
        "MeterReadingReminder",
        "InvoiceIssued",
        "UsageReconciled",
        "PaymentReceived",
        "PaymentReversed"
      })
  void supportsOwnedAccountingNotifications(String type) {
    var inbox = mock(NotificationInbox.class);
    var event =
        new DomainEvent(
            UUID.randomUUID(), 1, type, Instant.now(), UUID.randomUUID(), "alice", Map.of());
    new RentalEventConsumer(inbox).accept(event);
    verify(inbox).accept(event);
  }

  @Test
  void technicalContractProjectionIsAcknowledgedWithoutUserNotification() {
    var inbox = mock(NotificationInbox.class);
    new RentalEventConsumer(inbox)
        .accept(
            new DomainEvent(
                UUID.randomUUID(),
                1,
                "ContractAccountingChanged",
                Instant.now(),
                UUID.randomUUID(),
                "alice",
                Map.of()));
    verifyNoInteractions(inbox);
  }
}
