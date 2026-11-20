package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;

class InternalSecurityConfigurationTest {
  final InternalSecurityConfiguration config = new InternalSecurityConfiguration();
  final HttpSecurity http = mock(HttpSecurity.class);

  @Test
  void invalidModeFailsFast() {
    assertThatThrownBy(() -> config.internalSecurity(http, "invalid", "", "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INTERNAL_AUTH_MODE");
    assertThatThrownBy(() -> config.internalSecurity(http, "", "", "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INTERNAL_AUTH_MODE");
    assertThatThrownBy(() -> config.internalSecurity(http, null, "", "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INTERNAL_AUTH_MODE");
  }

  @Test
  void emulatorModeIsBlockedWhenRunningOnCloudRun() {
    assertThatThrownBy(
            () ->
                config.internalSecurity(
                    http, "emulator", "", "", "localhost:8085", "psi-notification-service"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Pub/Sub emulator");
  }

  @Test
  void emulatorModeRequiresNonBlankEmulatorHost() {
    assertThatThrownBy(() -> config.internalSecurity(http, "emulator", "", "", "  ", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Pub/Sub emulator");
  }

  @Test
  void googleModeRejectsBlankOrEmptyCallersAndFiltersBlankEntries() {
    assertThat(InternalSecurityConfiguration.parseAllowedEmails(" a@test.com , , b@test.com, "))
        .containsExactlyInAnyOrder("a@test.com", "b@test.com");
    assertThat(InternalSecurityConfiguration.parseAllowedEmails(" , , ")).isEmpty();
    assertThatThrownBy(
            () ->
                config.internalSecurity(
                    http, "google", "https://notify.example.com", " , , ", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audience and callers");
  }

  @Test
  void googleOidcValidatorHandlesNullAudienceAndNullEmailSafelyWithoutNpe() {
    var validator =
        InternalSecurityConfiguration.googleTokenValidator(
            "https://notify.example.com", Set.of("push@project.iam.gserviceaccount.com"));

    var jwtWithoutAudience =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("iss", "https://accounts.google.com")
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("email", "push@project.iam.gserviceaccount.com")
            .claim("email_verified", true)
            .build();
    assertThat(jwtWithoutAudience.getAudience()).isNull();
    assertThat(validator.validate(jwtWithoutAudience).hasErrors()).isTrue();

    var jwtWithoutEmail =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("iss", "https://accounts.google.com")
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("aud", List.of("https://notify.example.com"))
            .claim("email_verified", true)
            .build();
    assertThat(validator.validate(jwtWithoutEmail).hasErrors()).isTrue();
  }

  @Test
  void googleOidcValidatorAcceptsValidTokenAndRejectsUnverifiedOrWrongAudience() {
    var validator =
        InternalSecurityConfiguration.googleTokenValidator(
            "https://notify.example.com", Set.of("push@project.iam.gserviceaccount.com"));

    var validJwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("iss", "https://accounts.google.com")
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("aud", List.of("https://notify.example.com"))
            .claim("email", "push@project.iam.gserviceaccount.com")
            .claim("email_verified", true)
            .build();
    assertThat(validator.validate(validJwt).hasErrors()).isFalse();

    var unverifiedJwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("iss", "https://accounts.google.com")
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("aud", List.of("https://notify.example.com"))
            .claim("email", "push@project.iam.gserviceaccount.com")
            .claim("email_verified", false)
            .build();
    assertThat(validator.validate(unverifiedJwt).hasErrors()).isTrue();
  }
}
