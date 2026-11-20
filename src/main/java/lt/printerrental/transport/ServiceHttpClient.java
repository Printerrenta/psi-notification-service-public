package lt.printerrental.transport;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ServiceHttpClient {
  private static final Logger log = LoggerFactory.getLogger(ServiceHttpClient.class);
  private final ServiceCredentials credentials;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public ServiceHttpClient(ServiceCredentials credentials) {
    this.credentials = credentials;
  }

  public HttpResponse<byte[]> send(
      String serviceUrl,
      String path,
      String method,
      byte[] body,
      String contentType,
      String bearer) {
    String baseUrl = serviceUrl == null ? "" : serviceUrl.replaceAll("/+$", "");
    String normalizedPath =
        path == null || path.isEmpty() ? "" : (path.startsWith("/") ? path : "/" + path);
    int queryIndex = normalizedPath.indexOf('?');
    String safePath = queryIndex >= 0 ? normalizedPath.substring(0, queryIndex) : normalizedPath;
    try {
      var request =
          HttpRequest.newBuilder(URI.create(baseUrl + normalizedPath))
              .timeout(Duration.ofSeconds(30));
      if (contentType != null) request.header("Content-Type", contentType);
      if (bearer != null) request.header("Authorization", bearer);
      var cloud = credentials.identityToken(baseUrl);
      if (cloud != null) request.header("X-Serverless-Authorization", "Bearer " + cloud);
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofByteArray(body));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.warn("Upstream request interrupted: method={} target={}{}", method, baseUrl, safePath);
      throw new UpstreamUnavailableException();
    } catch (Exception error) {
      log.warn(
          "Upstream request failed: method={} target={}{} error={}",
          method,
          baseUrl,
          safePath,
          error.getMessage(),
          error);
      throw new UpstreamUnavailableException();
    }
  }
}
