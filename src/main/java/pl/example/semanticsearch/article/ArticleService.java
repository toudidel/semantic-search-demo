package pl.example.semanticsearch.article;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.example.semanticsearch.embedding.EmbeddingClient;

@Service
public class ArticleService {

  private final EmbeddingClient embeddingClient;
  private final ArticleRepository repository;

  public ArticleService(EmbeddingClient embeddingClient, ArticleRepository repository) {
    this.embeddingClient = embeddingClient;
    this.repository = repository;
  }

  @Transactional
  public long create(CreateArticleRequest request) {
    var textToEmbed = request.title() + "\n" + request.content();
    var embedding = embeddingClient.embed(textToEmbed);
    var article =
        repository.save(
            new ArticleEntity(request.title(), request.content(), request.category(), embedding));
    return article.getId();
  }

  @Transactional(readOnly = true)
  public List<Article> search(String query, String category, int limit) {
    var queryVector = embeddingClient.embed(query);
    return repository
        .search(toPgVectorLiteral(queryVector), category, Math.min(Math.max(limit, 1), 50))
        .stream()
        .map(
            result ->
                new Article(
                    result.getId(),
                    result.getTitle(),
                    result.getContent(),
                    result.getCategory(),
                    result.getDistance(),
                    result.getCreatedAt()))
        .toList();
  }

  @Transactional
  public int seedDemoArticles(int count, int batchSize) {
    var inserted = 0;
    for (int offset = 0; offset < count; offset += batchSize) {
      var currentSize = Math.min(batchSize, count - offset);
      var drafts = new java.util.ArrayList<DemoArticle>(currentSize);
      var texts = new java.util.ArrayList<String>(currentSize);

      for (int i = 0; i < currentSize; i++) {
        var number = offset + i + 1;
        var sample = DemoArticle.sample(number);
        texts.add(sample.title() + "\n" + sample.content());
        drafts.add(sample);
      }

      var embeddings = embeddingClient.embed(texts);
      var entities = new java.util.ArrayList<ArticleEntity>(currentSize);
      for (int i = 0; i < currentSize; i++) {
        var draft = drafts.get(i);
        entities.add(
            new ArticleEntity(draft.title(), draft.content(), draft.category(), embeddings.get(i)));
      }
      repository.saveAllAndFlush(entities);
      inserted += entities.size();
    }
    return inserted;
  }

  private String toPgVectorLiteral(float[] vector) {
    return Arrays.toString(vector);
  }

  private record DemoArticle(String title, String content, String category) {
    private static final String[] CATEGORIES = {"JAVA", "SPRING", "DATABASE", "DEVOPS"};
    private static final String[] TOPICS = {
      "HikariCP i pula połączeń JDBC",
      "obsługa wyjątków w aplikacji Spring Boot",
      "transakcje i izolacja w PostgreSQL",
      "indeksy i plan wykonania zapytania",
      "testy integracyjne z Testcontainers",
      "konfiguracja cache w aplikacji Java",
      "monitoring endpointów Actuator",
      "asynchroniczne przetwarzanie zdarzeń"
    };

    static DemoArticle sample(int number) {
      var topic = TOPICS[(number - 1) % TOPICS.length];
      var category = CATEGORIES[(number - 1) % CATEGORIES.length];
      var variant = number % 5;
      return new DemoArticle(
          "Przykład " + number + ": " + topic,
          "Artykuł demonstracyjny numer "
              + number
              + ". Temat: "
              + topic
              + ". "
              + "Wariant "
              + variant
              + " pokazuje praktyczny problem spotykany w usługach Java. "
              + "Warto zwrócić uwagę na wydajność, konfigurację, diagnostykę i bezpieczną obsługę błędów. "
              + "Ten tekst służy do testowania wyszukiwania semantycznego oraz filtrowania po kategorii.",
          category);
    }
  }
}
