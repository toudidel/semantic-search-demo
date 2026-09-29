package pl.example.semanticsearch.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.example.semanticsearch.config.EmbeddingProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocalEmbeddingClient implements EmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(LocalEmbeddingClient.class);

  private final HttpClient httpClient =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private final EmbeddingProperties properties;
  private final ObjectMapper objectMapper;

  public LocalEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public float[] embed(String text) {
    return toVector(requestEmbedding(text).embedding());
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  private EmbeddingResponse requestEmbedding(String text) {
    final EmbeddingRequest requestBody = new EmbeddingRequest(text);
    final byte[] requestBytes;
    try {
      requestBytes = objectMapper.writeValueAsBytes(requestBody);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Nie można zbudować requestu embeddingu", exception);
    }

    var url = properties.baseUrl() + properties.endpoint();
    log.info("Embedding request: {}", new String(requestBytes, StandardCharsets.UTF_8));

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
            .build();

    final HttpResponse<String> httpResponse;
    try {
      httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new IllegalStateException("Nie można połączyć się z serwisem embeddingów", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Przerwano żądanie do serwisu embeddingów", exception);
    }

    if (httpResponse.statusCode() >= 400) {
      throw new IllegalStateException(
          "Embedding API zwróciło HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
    }

    final JsonNode response;
    try {
      response = objectMapper.readTree(httpResponse.body());
    } catch (JacksonException exception) {
      throw new IllegalStateException("Embedding API zwróciło niepoprawny JSON", exception);
    }

    JsonNode embedding =
        response != null && response.isArray()
            ? response
            : response == null ? null : response.path("embedding");

    if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
      throw new IllegalStateException("Lokalny serwis embeddingów nie zwrócił wektora");
    }

    var values = new java.util.ArrayList<Double>(embedding.size());
    for (JsonNode value : embedding) {
      values.add(value.asDouble());
    }
    return new EmbeddingResponse(
        response != null ? response.path("model").asString(null) : null, embedding.size(), values);
  }

  private float[] toVector(List<Double> values) {
    if (values.size() != properties.dimensions()) {
      throw new IllegalStateException(
          "Model zwrócił " + values.size() + " wymiarów, oczekiwano " + properties.dimensions());
    }
    var vector = new float[values.size()];
    for (int i = 0; i < values.size(); i++) {
      vector[i] = values.get(i).floatValue();
    }
    return vector;
  }

  public record EmbeddingRequest(String text) {}

  private record EmbeddingResponse(String model, int dimensions, List<Double> embedding) {}
}
