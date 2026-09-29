package pl.example.semanticsearch.article;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<ArticleEntity, Long> {

  @Query(
      value =
          """
            SELECT id, title, content, category, created_at AS createdAt,
                   embedding <=> CAST(:queryVector AS vector) AS distance
            FROM article
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
    Long getId();

    String getTitle();

    String getContent();

    String getCategory();

    Float getDistance();

    Instant getCreatedAt();
  }
}
