package lt.printerrental.notifications;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import lt.printerrental.events.DomainEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "NEON_AUTH_BASE_URL=https://auth.test/auth",
      "NEON_AUTH_JWKS_URL=https://auth.test/jwks",
      "PUBSUB_SUBSCRIPTION=projects/test/subscriptions/notifications"
    })
@Testcontainers
class RepairEventIT {
  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", PG::getJdbcUrl);
    p.add("spring.datasource.username", PG::getUsername);
    p.add("spring.datasource.password", PG::getPassword);
  }

  @MockitoBean lt.printerrental.auth.UserIdentityResolver resolver;
  @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
  @Autowired RentalEventConsumer consumer;
  @Autowired JdbcTemplate db;

  @ParameterizedTest
  @ValueSource(strings = {"RepairRequestCreated", "RepairStatusChanged"})
  void repairNotificationsPreserveOwnerAndDeduplicate(String type) {
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
    consumer.accept(event);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM notifications.notifications WHERE event_id=?",
                Long.class,
                event.eventId()))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT owner_subject FROM notifications.notifications WHERE event_id=?",
                String.class,
                event.eventId()))
        .isEqualTo("alice");
    assertThat(
            db.queryForObject(
                "SELECT event_type FROM notifications.notifications WHERE event_id=?",
                String.class,
                event.eventId()))
        .isEqualTo(type);
  }
}
