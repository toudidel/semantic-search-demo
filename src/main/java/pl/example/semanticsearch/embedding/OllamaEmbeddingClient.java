package pl.example.semanticsearch.embedding;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import pl.example.semanticsearch.config.EmbeddingProperties;

@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public OllamaEmbeddingClient(RestClient.Builder restClientBuilder, EmbeddingProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public float[] embed(String text) {
        return embed(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        var response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(properties.model(), texts))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama nie zwrócił embeddingów dla modelu " + properties.model());
        }

        return response.embeddings().stream().map(this::toVector).toList();
    }

    private float[] toVector(List<Double> values) {
        if (values.size() != properties.dimensions()) {
            throw new IllegalStateException("Model zwrócił " + values.size()
                    + " wymiarów, oczekiwano " + properties.dimensions());
        }
        var vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    private record EmbedRequest(String model, List<String> input) {
    }

    private record EmbedResponse(List<List<Double>> embeddings) {
    }
}
