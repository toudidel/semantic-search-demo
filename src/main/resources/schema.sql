CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS article (
    id         BIGSERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    content    TEXT NOT NULL,
    category   TEXT NOT NULL,
    embedding  VECTOR(1024) NOT NULL,
    search_tsv TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS article_embedding_hnsw_idx
    ON article USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS article_search_tsv_idx
    ON article USING gin (search_tsv);

CREATE INDEX IF NOT EXISTS article_category_idx
    ON article (category);
