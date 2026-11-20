package lt.printerrental.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PubSubPushControllerTest {
  static final String SUBSCRIPTION = "projects/test/subscriptions/notifications";
  ObjectMapper mapper;
  NotificationInbox inbox;
  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    inbox = mock(NotificationInbox.class);
    var controller =
        new PubSubPushController(new RentalEventConsumer(inbox), mapper, SUBSCRIPTION);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new NotificationExceptionHandler())
            .build();
  }

  private String envelope(String subscription, String data) throws Exception {
    return mapper.writeValueAsString(
        Map.of("subscription", subscription, "message", Map.of("data", data)));
  }

  @Test
  void validPushReturnsNoContentAndDeliversEvent() throws Exception {
    var event =
        new DomainEvent(
            UUID.randomUUID(),
            1,
            "RentalRequestCreated",
            Instant.now(),
            UUID.randomUUID(),
            "alice",
            Map.of());
    String encoded = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(event));

    mvc.perform(
            post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope(SUBSCRIPTION, encoded)))
        .andExpect(status().isNoContent());

    verify(inbox).accept(any(DomainEvent.class));
  }

  @Test
  void unknownSubscriptionReturnsBadRequest() throws Exception {
    var event =
        new DomainEvent(
            UUID.randomUUID(),
            1,
            "RentalRequestCreated",
            Instant.now(),
            UUID.randomUUID(),
            "alice",
            Map.of());
    String encoded = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(event));

    mvc.perform(
            post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope("projects/other/subscriptions/rogue", encoded)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(inbox);
  }

  @Test
  void oversizedPayloadReturnsBadRequest() throws Exception {
    String oversized = "A".repeat(65537);

    mvc.perform(
            post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope(SUBSCRIPTION, oversized)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(inbox);
  }

  @Test
  void malformedJsonPayloadReturnsBadRequest() throws Exception {
    String malformed =
        Base64.getEncoder().encodeToString("{not-valid-json".getBytes(StandardCharsets.UTF_8));

    mvc.perform(
            post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope(SUBSCRIPTION, malformed)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(inbox);
  }

  @Test
  void malformedBase64PayloadReturnsBadRequest() throws Exception {
    mvc.perform(
            post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope(SUBSCRIPTION, "!!!not-base64!!!")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(inbox);
  }

  @Test
  void unknownEventTypeReturnsBadRequest() throws Exception {
    var event =
        new DomainEvent(
            UUID.randomUUID(),
            1,
            "UnknownEventType",
            Instant.now(),
            UUID.randomUUID(),
            "alice",
            Map.of());
    String encoded = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(event));

    mvc.perform(
            post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope(SUBSCRIPTION, encoded)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(inbox);
  }
}
