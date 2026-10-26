package lt.printerrental.notifications;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PubSubPushController {
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
    if (!subscriptions.contains(body.path("subscription").asText()))
      throw new IllegalArgumentException("Unexpected subscription");
    String encoded = body.path("message").path("data").asText();
    if (encoded.isBlank() || encoded.length() > 65536)
      throw new IllegalArgumentException("Invalid event size");
    try {
      var event = mapper.readValue(Base64.getDecoder().decode(encoded), DomainEvent.class);
      service.accept(event);
    } catch (java.io.IOException | IllegalArgumentException error) {
      throw new IllegalArgumentException("Invalid rental event");
    }
    // ACK only after the inbox transaction commits; duplicate event IDs are harmless.
    return ResponseEntity.noContent().build();
  }
}
