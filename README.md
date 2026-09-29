# Semantic Search w Javie: PostgreSQL + pgvector

Minimalny projekt demonstracyjny do prezentacji o semantic search. Aplikacja:

- generuje embeddingi przez lokalny serwis REST uruchomiony w Dockerze,
- zapisuje tekst i wektor w PostgreSQL,
- wyszukuje podobne artykuły przez cosine distance (`<=>`),
- używa indeksu HNSW (`vector_cosine_ops`),
- pozwala filtrować wyniki relacyjnym polem `category`.

## Wymagania

- Java 21+
- Maven 3.6.3+
- Docker i Docker Compose
- obraz Docker `embedding-service-embedding-service:latest`

Projekt używa Spring Boot 4.1.1, Spring Data JPA, Hibernate ORM z modułem `hibernate-vector` oraz lokalnego serwisu
FastAPI z modelem `BAAI/bge-m3`. Model zwraca wektory o wymiarze 1024, zgodnym z kolumną `VECTOR(1024)`.

## Uruchomienie

### 1. Uruchom PostgreSQL z pgvector

```bash
docker compose up -d postgres embedding-service
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

### 2. Uruchom lokalny serwis embeddingów

Obraz `embedding-service-embedding-service:latest` jest uruchamiany razem z PostgreSQL przez główny compose:

```bash
docker compose up -d postgres embedding-service
```

Serwis jest dostępny z aplikacji uruchomionej lokalnie pod `http://localhost:8090`.

Przy pierwszym uruchomieniu kontener pobiera i ładuje model `BAAI/bge-m3`. Do czasu pojawienia się w logach
`Application startup complete` endpoint może zamykać połączenia (`socket hang up` / `ECONNRESET`). Sprawdź gotowość:

```bash
docker compose logs -f embedding-service
curl http://localhost:8090/health
```

Po pierwszym pobraniu model jest przechowywany w wolumenie `embedding-cache`, więc kolejne uruchomienia nie powinny
pobierać go ponownie.

Jeśli obraz nie istnieje, zbuduj go z katalogu `embedding-service`:

```bash
docker compose -f embedding-service/docker-compose.yml build
```

Aplikacja korzysta z endpointu:

```bash
POST http://localhost:8090/embeddings
Content-Type: application/json
```

Przykładowe wywołanie:

```bash
curl --request POST \
  --url http://localhost:8090/embeddings \
  --header 'Content-Type: application/json' \
  --data '{
    "text": "Ala ma kota"
  }'
```

Oczekiwana odpowiedź ma postać:

```json
{
  "model": "BAAI/bge-m3",
  "dimensions": 1024,
  "embedding": [
    0.0123,
    -0.0456
  ]
}
```

W przykładzie tablica została skrócona. Właściwa odpowiedź zawiera 1024 wartości.

### 3. Uruchom aplikację

```bash
mvn spring-boot:run
```

Aplikacja wystartuje na `http://localhost:8080` i domyślnie połączy się z lokalnym serwisem
`http://localhost:8090/embeddings`.

Jeśli adres API ma być zmieniony:

```bash
EMBEDDING_API_BASE_URL=http://localhost:8090 \
EMBEDDING_API_ENDPOINT=/embeddings \
mvn spring-boot:run
```

Wymiar modelu musi odpowiadać kolumnie `VECTOR(1024)`. Dla innego modelu lub wymiaru zmień jednocześnie
`EMBEDDING_DIMENSIONS` oraz definicję kolumny i indeksu w `schema.sql`.

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

Przykładowy wynik będzie zawierał artykuł o HikariCP, nawet jeśli dokładna fraza `za mało połączeń do bazy` nie
występuje w jego treści.

### Wygeneruj 5000 artykułów demonstracyjnych

Endpoint domyślnie generuje 5000 zróżnicowanych wpisów w kategoriach `JAVA`, `SPRING`, `DATABASE` i `DEVOPS`. Dla
każdego wpisu aplikacja wywołuje lokalny serwis embeddingów, a następnie zapisuje rekordy batchowo w PostgreSQL.

```bash
curl -X POST "http://localhost:8080/api/demo/seed"
```

Możesz zmienić liczbę wpisów i rozmiar partii:

```bash
curl -X POST "http://localhost:8080/api/demo/seed?count=10000&batchSize=64"
```

Limit bezpieczeństwa endpointu to 10 000 wpisów na jedno wywołanie. Czas operacji zależy przede wszystkim od czasu
obliczeń lokalnego modelu.

## Najważniejsze elementy kodu

### Encja i Hibernate

Tabela `article` jest mapowana przez encję `ArticleEntity` oznaczoną `@Entity`. Pole `float[] embedding` korzysta z
mapowania Hibernate Vector:

```java

@JdbcTypeCode(SqlTypes.VECTOR)
@Column(columnDefinition = "vector(1024)")
private float[] embedding;
```

`ArticleRepository` jest interfejsem `JpaRepository`. Zapis artykułów i batch seedowanie korzystają z `save` oraz
`saveAllAndFlush`. Wyszukiwanie podobieństwa używa zapytania natywnego Spring Data JPA, ponieważ operator `<=>` jest
specyficzny dla PostgreSQL/pgvector.

### Generowanie embeddingu

`LocalEmbeddingClient` wywołuje endpoint lokalnego serwisu `/embeddings` i wysyła JSON `{ "text": "..." }`. Odpowiedź
jest walidowana pod kątem wymiaru 1024, a następnie konwertowana do `float[]`. Interfejs `EmbeddingClient` izoluje
resztę aplikacji od szczegółów transportu.

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

To indeks ANN (Approximate Nearest Neighbor). Przy małych tabelach różnica będzie niewielka, ale przy większej liczbie
wektorów HNSW ogranicza koszt przeszukiwania całego zbioru.

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

| Zmienna                  | Domyślna wartość        | Znaczenie                                     |
|--------------------------|-------------------------|-----------------------------------------------|
| `DB_HOST`                | `localhost`             | host PostgreSQL                               |
| `DB_PORT`                | `5432`                  | port PostgreSQL                               |
| `DB_NAME`                | `semantic_search`       | nazwa bazy                                    |
| `DB_USER`                | `semantic`              | użytkownik bazy                               |
| `DB_PASSWORD`            | `semantic`              | hasło bazy                                    |
| `EMBEDDING_API_BASE_URL` | `http://localhost:8090` | adres bazowy lokalnego API                    |
| `EMBEDDING_API_ENDPOINT` | `/embeddings`           | ścieżka endpointu embeddingów                 |
| `EMBEDDING_MODEL`        | `BAAI/bge-m3`           | nazwa modelu używana w konfiguracji aplikacji |
| `EMBEDDING_DIMENSIONS`   | `1024`                  | wymiar wektora                                |

## Lokalny serwis embeddingów

Serwis został przygotowany jako aplikacja FastAPI uruchomiona w kontenerze Docker. Przy starcie procesu ładuje model
`BAAI/bge-m3` i udostępnia endpoint `/embeddings`.

### Dockerfile

```dockerfile
FROM python:3.12-slim

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc g++ \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8080

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

### requirements.txt

```text
fastapi
uvicorn[standard]
FlagEmbedding
torch
```

### app/main.py

```python
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from FlagEmbedding import BGEM3FlagModel

MODEL_NAME = os.getenv("MODEL_NAME", "BAAI/bge-m3")
MAX_LENGTH = int(os.getenv("MAX_LENGTH", "2048"))
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "16"))
model: BGEM3FlagModel | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    model = BGEM3FlagModel(MODEL_NAME, use_fp16=False)
    yield
    model = None


app = FastAPI(
    title="FotoStudioTop Embedding Service",
    version="1.0.0",
    lifespan=lifespan,
)


class EmbeddingRequest(BaseModel):
    text: str = Field(min_length=1)


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    embedding: list[float]


@app.get("/health")
def health():
    return {"status": "UP", "model": MODEL_NAME, "loaded": model is not None}


@app.post("/embeddings", response_model=EmbeddingResponse)
def create_embedding(request: EmbeddingRequest):
    if model is None:
        raise HTTPException(status_code=503, detail="Embedding model is not loaded")

    result = model.encode(
        [request.text],
        batch_size=BATCH_SIZE,
        max_length=MAX_LENGTH,
        return_dense=True,
        return_sparse=False,
        return_colbert_vecs=False,
    )
    embedding = result["dense_vecs"][0].tolist()
    return EmbeddingResponse(
        model=MODEL_NAME,
        dimensions=len(embedding),
        embedding=embedding,
    )
```

Budowanie i uruchomienie serwisu lokalnie:

```bash
docker compose -f embedding-service/docker-compose.yml build
docker compose -f embedding-service/docker-compose.yml up -d
```

## Źródła i dalsza lektura

- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
- [Spring AI Embedding Model API](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
- [Hibernate Vector](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#basic-mapping-column-type-vector)
- [FastAPI](https://fastapi.tiangolo.com/)
- [FlagEmbedding](https://github.com/FlagOpen/FlagEmbedding)
