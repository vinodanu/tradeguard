package com.tradeguard.trade;

import com.tradeguard.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FreshnessClient implements FreshnessVerifier {
  private final String url;
  private volatile HttpClient client;

  public FreshnessClient(@Value("${tradeguard.compliance-url}") String url) {
    this.url = url;
  }

  private HttpClient client() {
    if (client == null) {
      synchronized (this) {
        if (client == null)
          client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
      }
    }
    return client;
  }

  public Map<String, Object> check(String evaluationId, String token) {
    try {
      var request =
          HttpRequest.newBuilder(
                  URI.create(url + "/api/v1/evaluations/" + evaluationId + "/freshness"))
              .timeout(Duration.ofSeconds(5))
              .header("Authorization", "Bearer " + token)
              .GET()
              .build();
      var response = client().send(request, HttpResponse.BodyHandlers.ofString());
      Problem.require(
          response.statusCode() == 200,
          503,
          "Compliance verification unavailable; order not executed");
      Map<String, Object> result = Json.map(response.body());
      Problem.require(
          result.get("current") instanceof Boolean,
          503,
          "Compliance response was invalid; order not executed");
      return result;
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(503, "Compliance verification unavailable; order not executed");
    }
  }
}
