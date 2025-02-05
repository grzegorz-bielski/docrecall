CREATE TABLE IF NOT EXISTS embeddings
(
    id BIGSERIAL PRIMARY KEY, -- unique identifier of the embedding
    context_id UUID REFERENCES contexts(id), -- unique identifier of the context
    document_id UUID REFERENCES documents(id), -- unique identifier of the document
    fragment_index BIGINT, -- index of the fragment (like page) in the document
    chunk_index BIGINT, -- index of the chunk in the fragment
    value TEXT, -- value (likely just text) of the chunk
    metadata JSONB, -- any additional metadata of the chunk
    embedding VECTOR(1024), -- embedding vector of the chunk (assuming 1024-dimensional vectors)
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX full_text_idx ON embeddings
USING bm25 (id, value)
WITH (key_field='id');

CREATE INDEX ON embeddings
USING hnsw (embedding vector_cosine_ops);
