package pl.example.semanticsearch.embedding;

import java.util.List;

public interface EmbeddingClient {

    float[] embed(String text);

    default List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
