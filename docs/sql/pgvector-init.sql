-- Run this script after connecting to the "smart_ticket_vector" database.
-- The application has spring.ai.vectorstore.pgvector.initialize-schema=true,
-- so after this extension is installed it will create/update the vector table
-- on startup.

CREATE EXTENSION IF NOT EXISTS vector;

-- Optional verification
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';
