package pl.example.semanticsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(String baseUrl, String endpoint, String model, int dimensions) {}
