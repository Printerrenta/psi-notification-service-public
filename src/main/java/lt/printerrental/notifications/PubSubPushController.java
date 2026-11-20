package lt.printerrental.notifications;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PubSubPushController {
  private static final Logger log = LoggerFactory.getLogger(PubSubPushController.class);
  private final RentalEventConsumer service;
  private final ObjectMapper mapper;
  private final Set<String> subscriptions;

  public PubSubPushController(
      RentalEventConsumer service,
      ObjectMapper mapper,
      @Value("${PUBSUB_SUBSCRIPTIONS:${PUBSUB_SUBSCRIPTION:}}") String subscription) {
    this.service = service;
    this.mapper = mapper;
    this.subscriptions =
        Set.copyOf(
            Arrays.stream(subscription.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList());
  }

  @PostMapping("/internal/events")
  public ResponseEntity<Void> accept(@RequestBody JsonNode body) {
    if (body == null || !subscriptions.contains(body.path("subscription").asText())) {
      log.warn("Rejected Pub/Sub push for unexpected subscription");
      throw new IllegalArgumentException("Unexpected subscription");
    }
    String encoded = body.path("message").path("data").asText();
    if (encoded.isBlank() || encoded.length() > 65536) {
      log.warn("Rejected Pub/Sub push with invalid payload size: length={}", encoded.length());
      throw new IllegalArgumentException("Invalid event size");
    }
    DomainEvent event;
    try {
      event = mapper.readValue(Base64.getDecoder().decode(encoded), DomainEvent.class);
    } catch (java.io.IOException | IllegalArgumentException error) {
      log.warn("Failed to decode Pub/Sub event payload: {}", error.getClass().getSimpleName());
      throw new IllegalArgumentException("Invalid rental event");
    }
    if (event == null) {
      log.warn("Rejected null Pub/Sub event payload");
      throw new IllegalArgumentException("Invalid rental event");
    }
    try {
      service.accept(event);
    } catch (IllegalArgumentException error) {
      log.warn(
          "Rejected unsupported Pub/Sub event: eventId={}, type={}, schemaVersion={}",
          event.eventId(),
          event.type(),
          event.schemaVersion());
      throw new IllegalArgumentException("Invalid rental event");
    }
    // ACK only after the inbox transaction commits; duplicate event IDs are harmless.
    return ResponseEntity.noContent().build();
  }
}
