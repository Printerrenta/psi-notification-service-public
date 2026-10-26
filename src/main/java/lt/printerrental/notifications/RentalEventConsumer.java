package lt.printerrental.notifications;

import java.util.Set;
import lt.printerrental.events.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalEventConsumer {
  private static final Set<String> TYPES =
      Set.of(
          "RentalRequestCreated",
          "RentalStatusChanged",
          "PrinterAssigned",
          "ContractCreated",
          "ContractActivated",
          "ContractCompleted",
          "RepairRequestCreated",
          "RepairStatusChanged",
          "ContractAccountingChanged",
          "MeterReadingAccepted",
          "MeterReadingNeedsReview",
          "MeterReadingRejected",
          "MeterReadingReminder",
          "InvoiceIssued",
          "UsageReconciled",
          "PaymentReceived",
          "PaymentReversed");

  private final NotificationInbox repository;

  public RentalEventConsumer(NotificationInbox repository) {
    this.repository = repository;
  }

  @Transactional
  public void accept(DomainEvent event) {
    if (event.schemaVersion() != 1
        || event.eventId() == null
        || event.aggregateId() == null
        || event.occurredAt() == null
        || (event.type() == null || !TYPES.contains(event.type()))
        || (event.ownerSubject() != null && event.ownerSubject().length() > 255))
      throw new IllegalArgumentException("Unsupported rental event");
    if (!event.type().equals("ContractAccountingChanged")) repository.accept(event);
  }
}
