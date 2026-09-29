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
            @RequestParam(defaultValue = "5000") int count,
            @RequestParam(defaultValue = "32") int batchSize) {
        if (count < 1 || count > 10_000) {
            throw new IllegalArgumentException("count musi być w zakresie 1..10000");
        }
        if (batchSize < 1 || batchSize > 128) {
            throw new IllegalArgumentException("batchSize musi być w zakresie 1..128");
        }
        var inserted = articleService.seedDemoArticles(count, batchSize);
        return new SeedResponse(inserted, batchSize, OffsetDateTime.now());
    }

    public record SeedResponse(int inserted, int batchSize, OffsetDateTime completedAt) {
    }
}
