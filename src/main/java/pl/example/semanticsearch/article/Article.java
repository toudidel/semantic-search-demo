package pl.example.semanticsearch.article;

import java.time.OffsetDateTime;

public record Article(
        long id,
        String title,
        String content,
        String category,
        float distance,
        OffsetDateTime createdAt) {
}
