CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY, -- unique identifier of the document
    context_id UUID REFERENCES contexts(id), -- unique identifier of the context
    name TEXT, -- name of the document, like a file name
    description TEXT, -- description of the document
    version BIGINT, -- version of the document
    type TEXT, -- type of the document, like 'pdf', 'docx', 'txt', etc. TODO: make it an enum ?
    metadata JSONB, -- any additional metadata of the document
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- default to the current timestamp
);
