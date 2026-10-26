package lt.printerrental.notifications;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "NEON_AUTH_BASE_URL=https://auth.test/auth",
      "NEON_AUTH_JWKS_URL=https://auth.test/jwks",
      "PUBSUB_SUBSCRIPTION=projects/test/subscriptions/notifications"
    })
@AutoConfigureMockMvc
@Testcontainers
class NotificationIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @MockitoBean lt.printerrental.auth.UserIdentityResolver resolver;
  @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
  @Autowired RentalEventConsumer service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM notifications.notifications");
  }

  @Test
  void duplicateDeliveryIsIdempotentAndCustomerCanOnlyReadOwnNotifications() throws Exception {
    var event =
        new DomainEvent(
            UUID.randomUUID(),
            1,
            "RentalRequestCreated",
            Instant.now(),
            UUID.randomUUID(),
            "alice",
            Map.of());
    service.accept(event);
    service.accept(event);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications.notifications", Long.class))
        .isEqualTo(1);
    mvc.perform(get("/api/notifications").with(user("alice").roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/notifications").with(user("bob").roles("CUSTOMER")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            patch("/api/notifications/" + event.eventId() + "/read")
                .with(user("bob").roles("CUSTOMER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/notifications/" + event.eventId() + "/read")
                .with(user("alice").roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").isNotEmpty());
  }

  @Test
  void unsupportedVersionIsRejectedWithoutWritingInbox() {
    assertThatThrownBy(
            () ->
                service.accept(
                    new DomainEvent(
                        UUID.randomUUID(),
                        2,
                        "RentalRequestCreated",
                        Instant.now(),
                        UUID.randomUUID(),
                        "alice",
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications.notifications", Long.class))
        .isZero();
  }

  @Test
  void publicAndNeonAuthenticatedRequestsCannotInjectPushEvents() throws Exception {
    mvc.perform(post("/internal/events").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/internal/events")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }
}
