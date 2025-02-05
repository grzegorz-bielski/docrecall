CREATE TABLE IF NOT EXISTS contexts
(
    id UUID PRIMARY KEY, -- unique identifier of the context
    name TEXT, -- name of the context
    description TEXT, -- description of the context
    prompt_template JSONB, -- a JSONB object of the prompt template
    retrieval_settings JSONB, -- a JSONB object of the retrieval settings
    chat_completion_settings JSONB, -- a JSONB object of the chat completion settings
    chat_model TEXT, -- name of the chat model
    embeddings_model TEXT, -- name of the embeddings model
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- default to the current timestamp
);

-- Create an index on the name column for faster filtering
CREATE INDEX idx_contexts_name ON contexts (name);
