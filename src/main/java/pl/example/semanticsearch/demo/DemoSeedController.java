package pl.example.semanticsearch.demo;

import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.example.semanticsearch.article.ArticleService;

@RestController
@RequestMapping("/api/demo")
public class DemoSeedController {

  private final ArticleService articleService;

  public DemoSeedController(ArticleService articleService) {
    this.articleService = articleService;
  }

  @PostMapping("/seed")
  public SeedResponse seed(
      @RequestParam(defaultValue = "100") int count,
      @RequestParam(defaultValue = "20") int batchSize) {
    if (count < 1 || count > 1_000) {
      throw new IllegalArgumentException("count musi być w zakresie 1..1000");
    }
    if (batchSize < 1 || batchSize > 20) {
      throw new IllegalArgumentException("batchSize musi być w zakresie 1..20");
    }
    var inserted = articleService.seedDemoArticles(count, batchSize);
    return new SeedResponse(inserted, batchSize, OffsetDateTime.now());
  }

  public record SeedResponse(int inserted, int batchSize, OffsetDateTime completedAt) {}
}
