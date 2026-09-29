package pl.example.semanticsearch.article;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "article")
public class ArticleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private String category;

  @JdbcTypeCode(SqlTypes.VECTOR)
  @Column(nullable = false, columnDefinition = "vector(1024)")
  private float[] embedding;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private OffsetDateTime updatedAt;

  protected ArticleEntity() {}

  public ArticleEntity(String title, String content, String category, float[] embedding) {
    this.title = title;
    this.content = content;
    this.category = category;
    this.embedding = embedding;
  }

  public Long getId() {
    return id;
  }
}
