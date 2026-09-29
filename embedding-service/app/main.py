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

    model = BGEM3FlagModel(
        MODEL_NAME,
        use_fp16=False
    )

    yield

    model = None


app = FastAPI(
    title="FotoStudioTop Embedding Service",
    version="1.0.0",
    lifespan=lifespan
)


class EmbeddingRequest(BaseModel):
    text: str = Field(min_length=1)


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    embedding: list[float]


@app.get("/health")
def health():
    return {
        "status": "UP",
        "model": MODEL_NAME,
        "loaded": model is not None
    }


@app.post("/embeddings", response_model=EmbeddingResponse)
def create_embedding(request: EmbeddingRequest):

    if model is None:
        raise HTTPException(
            status_code=503,
            detail="Embedding model is not loaded"
        )

    result = model.encode(
        [request.text],
        batch_size=BATCH_SIZE,
        max_length=MAX_LENGTH,
        return_dense=True,
        return_sparse=False,
        return_colbert_vecs=False
    )

    embedding = result["dense_vecs"][0].tolist()

    return EmbeddingResponse(
        model=MODEL_NAME,
        dimensions=len(embedding),
        embedding=embedding
    )