package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lt.printerrental.transport.ServiceHttpClient;
import lt.printerrental.transport.UpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class HttpUserIdentityResolverTest {
  final ServiceHttpClient client = mock(ServiceHttpClient.class);
  final ObjectMapper mapper = new ObjectMapper();
  final HttpUserIdentityResolver resolver =
      new HttpUserIdentityResolver("https://identity.example.test", client, mapper);
  final Jwt jwt =
      Jwt.withTokenValue("test-token").header("alg", "EdDSA").subject("alice").build();

  @Test
  void wrapsUpstreamUnavailableInAuthenticationServiceException() {
    when(client.send(
            "https://identity.example.test", "/api/auth/me", "GET", null, null, "Bearer test-token"))
        .thenThrow(new UpstreamUnavailableException());

    assertThatThrownBy(() -> resolver.resolve(jwt))
        .isInstanceOf(AuthenticationServiceException.class)
        .hasCauseInstanceOf(UpstreamUnavailableException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsUnauthorizedAndForbiddenToInvalidBearerTokenException() {
    HttpResponse<byte[]> unauthorized = mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(401);
    when(client.send(
            "https://identity.example.test", "/api/auth/me", "GET", null, null, "Bearer test-token"))
        .thenReturn(unauthorized);

    assertThatThrownBy(() -> resolver.resolve(jwt))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsResolvedAppUserOn200() throws Exception {
    HttpResponse<byte[]> ok = mock(HttpResponse.class);
    when(ok.statusCode()).thenReturn(200);
    when(ok.body())
        .thenReturn(
            "{\"subject\":\"alice\",\"email\":\"alice@test.com\",\"name\":\"Alice\",\"role\":\"CUSTOMER\"}"
                .getBytes(StandardCharsets.UTF_8));
    when(client.send(
            "https://identity.example.test", "/api/auth/me", "GET", null, null, "Bearer test-token"))
        .thenReturn(ok);

    var user = resolver.resolve(jwt);
    assertThat(user).isEqualTo(new AppUser("alice", "alice@test.com", "Alice", UserRole.CUSTOMER));
  }
}
