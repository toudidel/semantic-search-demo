package pl.example.semanticsearch.article;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<ArticleEntity, Long> {

  // Zapytanie natywne wykorzystuje operator <=> pgvector (odległość cosinusową).
  // Mniejsza odległość oznacza, że artykuł jest semantycznie bliższy zapytaniu.
  @Query(
      value =
          """
            SELECT id, title, content, category, created_at AS createdAt,
                   embedding <=> CAST(:queryVector AS vector) AS distance
            FROM article
            -- Brak kategorii oznacza wyszukiwanie we wszystkich kategoriach.
            WHERE (:category IS NULL OR category = :category)
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """,
      nativeQuery = true)
  List<ArticleSearchProjection> search(
      @Param("queryVector") String queryVector,
      @Param("category") String category,
      @Param("limit") int limit);

  interface ArticleSearchProjection {
    // Projekcja odczytuje tylko kolumny potrzebne do odpowiedzi API, bez ładowania pełnej encji
    // JPA.
    Long getId();

    String getTitle();

    String getContent();

    String getCategory();

    Float getDistance();

    Instant getCreatedAt();
  }
}
