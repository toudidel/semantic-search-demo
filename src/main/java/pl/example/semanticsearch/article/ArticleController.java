package pl.example.semanticsearch.article;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

  private final ArticleService service;

  public ArticleController(ArticleService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<Void> create(@Valid @RequestBody CreateArticleRequest request) {
    // Zwracamy 201 Created oraz adres nowo utworzonego zasobu zgodnie z konwencją REST.
    var id = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(location).build();
  }

  @GetMapping("/search")
  public List<Article> search(
      @RequestParam @NotBlank String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
    // Walidacja parametrów HTTP zatrzymuje niepoprawne żądanie przed uruchomieniem modelu
    // embeddingowego.
    return service.search(q, category, limit);
  }
}
