package pl.example.semanticsearch.article;

import jakarta.validation.constraints.NotBlank;

public record CreateArticleRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotBlank String category) {
}
