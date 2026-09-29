# Semantic Search w Javie: PostgreSQL + pgvector

Minimalny projekt demonstracyjny do prezentacji o semantic search. Aplikacja:

- generuje embeddingi przez lokalny Ollama,
- zapisuje tekst i wektor w PostgreSQL,
- wyszukuje podobne artykuły przez cosine distance (`<=>`),
- używa indeksu HNSW (`vector_cosine_ops`),
- pozwala filtrować wyniki relacyjnym polem `category`.

## Wymagania

- Java 21+
- Maven 3.6.3+
- Docker i Docker Compose
- Ollama

Projekt używa Spring Boot 4.1.1, pgvector-java 0.1.6 i modelu embeddingowego `bge-m3`.

## Uruchomienie

### 1. Uruchom PostgreSQL z pgvector

```bash
docker compose up -d postgres
```

Sprawdź stan bazy:

```bash
docker compose ps
```

Przy pierwszym starcie `schema.sql` wykona:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE INDEX article_embedding_hnsw_idx
    ON article USING hnsw (embedding vector_cosine_ops);
```

### 2. Uruchom lokalny model embeddingowy

Zainstaluj Ollama z https://ollama.com, a następnie:

```bash
ollama pull bge-m3
ollama serve
```

Domyślny endpoint aplikacji to `http://localhost:11434`, a domyślny model to `bge-m3`.

Jeśli używasz innego modelu:

```bash
OLLAMA_EMBEDDING_MODEL=nomic-embed-text mvn spring-boot:run
```

Uwaga: wymiar modelu musi odpowiadać kolumnie `VECTOR(1024)`. Dla innego wymiaru zmień jednocześnie `EMBEDDING_DIMENSIONS` oraz definicję kolumny i indeksu w `schema.sql`.

### 3. Uruchom aplikację

```bash
mvn spring-boot:run
```

Aplikacja wystartuje na `http://localhost:8080`.

## Przykładowe użycie

### Zapisz artykuły

```bash
curl -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Konfiguracja HikariCP w Spring Boot",
    "content": "HikariCP zarządza pulą połączeń JDBC. Gdy pula jest wyczerpana, nowe żądania czekają na wolne połączenie.",
    "category": "JAVA"
  }'
```

```bash
curl -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Obsługa wyjątków w Javie",
    "content": "Wyjątki domenowe pomagają jasno komunikować błędy aplikacji i kontrolować przepływ obsługi żądania.",
    "category": "JAVA"
  }'
```

### Wykonaj wyszukiwanie semantyczne

```bash
curl "http://localhost:8080/api/articles/search?q=aplikacja%20ma%20za%20ma%C5%82o%20po%C5%82%C4%85cze%C5%84%20do%20bazy&category=JAVA&limit=5"
```

Przykładowy wynik będzie zawierał artykuł o HikariCP, nawet jeśli dokładna fraza `za mało połączeń do bazy` nie występuje w jego treści.

### Wygeneruj 5000 artykułów demonstracyjnych

Endpoint domyślnie generuje 5000 zróżnicowanych wpisów w kategoriach `JAVA`, `SPRING`, `DATABASE` i `DEVOPS`. Embeddingi są wysyłane do Ollama partiami, a rekordy zapisywane batchowo.

```bash
curl -X POST "http://localhost:8080/api/demo/seed"
```

Możesz zmienić liczbę wpisów i rozmiar partii:

```bash
curl -X POST "http://localhost:8080/api/demo/seed?count=10000&batchSize=64"
```

Limit bezpieczeństwa endpointu to 10 000 wpisów na jedno wywołanie. Operacja może potrwać kilka minut, zależnie od sprzętu i modelu Ollama.

## Najważniejsze elementy kodu

### Generowanie embeddingu

`OllamaEmbeddingClient` wywołuje lokalny endpoint Ollama `/api/embed`. Interfejs `EmbeddingClient` pozwala później podmienić model na Spring AI, OpenAI, ONNX Runtime lub inną implementację bez zmiany warstwy domenowej.

### Similarity search

Repozytorium wykonuje zapytanie:

```sql
SELECT id, title, content, category,
       embedding <=> ? AS distance
FROM article
WHERE (? IS NULL OR category = ?)
ORDER BY embedding <=> ?
LIMIT ?;
```

`<=>` oznacza cosine distance. Im mniejsza wartość `distance`, tym bardziej podobny jest dokument.

### HNSW

Indeks jest tworzony w `schema.sql`:

```sql
CREATE INDEX article_embedding_hnsw_idx
    ON article USING hnsw (embedding vector_cosine_ops);
```

To indeks ANN (Approximate Nearest Neighbor). Przy małych tabelach różnica będzie niewielka, ale przy większej liczbie wektorów HNSW ogranicza koszt przeszukiwania całego zbioru.

## Przydatne komendy diagnostyczne

Sprawdź dane w bazie:

```bash
docker exec -it semantic-search-postgres \
  psql -U semantic -d semantic_search \
  -c "SELECT id, title, category FROM article;"
```

Sprawdź plan zapytania:

```sql
EXPLAIN ANALYZE
SELECT id, title
FROM article
ORDER BY embedding <=> '[0,0,0]'::vector
LIMIT 10;
```

Health check aplikacji:

```bash
curl http://localhost:8080/actuator/health
```

## Konfiguracja przez zmienne środowiskowe

| Zmienna | Domyślna wartość | Znaczenie |
|---|---|---|
| `DB_HOST` | `localhost` | host PostgreSQL |
| `DB_PORT` | `5432` | port PostgreSQL |
| `DB_NAME` | `semantic_search` | nazwa bazy |
| `DB_USER` | `semantic` | użytkownik bazy |
| `DB_PASSWORD` | `semantic` | hasło bazy |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | adres Ollama |
| `OLLAMA_EMBEDDING_MODEL` | `bge-m3` | model embeddingowy |
| `EMBEDDING_DIMENSIONS` | `1024` | wymiar wektora |

## Źródła i dalsza lektura

- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
- [Spring AI Embedding Model API](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
- [pgvector-java: Spring JDBC examples](https://github.com/pgvector/pgvector-java#spring-jdbc)
- [Ollama](https://ollama.com)
