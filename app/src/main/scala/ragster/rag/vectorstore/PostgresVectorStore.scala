package ragster
package rag
package vectorstore

import cats.effect.*
import cats.syntax.all.*
import fs2.{Chunk as _, text as _, *}
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*
import unindent.*
import java.util.UUID
import skunk.*
import skunk.data.*
import skunk.codec.all.*
import skunk.syntax.all.*

import ragster.postgres.*
final class PostgresVectorStore(sessionResource: SessionResource)(using Logger[IO]) extends VectorStoreRepository[IO]:
  import PostgresVectorStore.*

  def delete(contextId: ContextId, documentId: DocumentId): IO[Unit] =
    sessionResource.use:
      _.prepare:
        sql"""
        DELETE FROM embeddings
        WHERE
          context_id = ${ContextId.pgCodec} AND
          document_id = ${DocumentId.pgCodec}
        """.command
      .flatMap(_.execute((contextId, documentId)))
        .void

  def store(index: Vector[Embedding.Index]): IO[Unit] =
    val values = index.toList.map: e =>
      (e.contextId, e.documentId, e.fragmentIndex, e.chunk.index, e.chunk.text, e.chunk.metadata, Arr(e.value*))

    sessionResource.use:
      _.prepare:
        sql"""
        INSERT INTO embeddings (context_id, document_id, fragment_index, chunk_index, value, metadata, embedding)
        VALUES ${embeddingToStoreCodec.values.list(values)}
        """.command
      .flatMap(_.execute(values))
        .void

  def documentEmbeddingsExists(contextId: ContextId, documentId: DocumentId): IO[Boolean] =
    sessionResource.use:
      _.prepare:
        sql"""
        SELECT
         EXISTS(
          SELECT document_id
          FROM embeddings
          WHERE
            context_id = ${ContextId.pgCodec} AND
            document_id = ${DocumentId.pgCodec}
          LIMIT 1
         )
        """.query(bool)
      .flatMap(_.unique((contextId, documentId)))

  def retrieve(embedding: Embedding.Query, settings: RetrievalSettings): Stream[IO, Embedding.Retrieved] =
    val args =
      (
        embedding.contextId,
        embedding.chunk.text,
        settings.fullTextSearchLimit,
        Arr(embedding.value*),
        embedding.contextId,
        settings.semanticSearchLimit,
        settings.rrfLimit,
        settings.fragmentLookupRange.lookBack,
        settings.fragmentLookupRange.lookAhead,
      )

    Stream
      .resource(sessionResource)
      .flatMap(session => Stream.eval(session.prepare(retrieveQuery)))
      .flatMap(_.stream(args = args, chunkSize = 1024))

object PostgresVectorStore:
  def of(using sessionResource: SessionResource): IO[PostgresVectorStore] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield PostgresVectorStore(sessionResource)

  private lazy val embeddingToStoreCodec =
    ContextId.pgCodec *:
      DocumentId.pgCodec *:
      int8 *:
      int8 *:
      text *:
      Metadata.pgCodec *:
      _float4

  private lazy val storedEmbeddingCodec =
    (embeddingToStoreCodec *: float8 *: float8 *: float8).map:
      case (
            (contextId, documentId, fragmentIndex, chunkIndex, text, metadata, embedding),
            fullTextScore,
            semanticScore,
            rrfScore,
          ) =>
        Embedding.Retrieved(
          chunk = Chunk(text, chunkIndex, metadata),
          contextId = contextId,
          documentId = documentId,
          fragmentIndex = fragmentIndex,
          value = embedding.flattenTo(Vector),
          fullTextScore = fullTextScore,
          semanticScore = semanticScore,
          rrfScore = rrfScore,
        )

  // see: https://docs.paradedb.com/documentation/guides/hybrid        
  private lazy val retrieveQuery =  
    sql"""
    WITH
      full_text_search AS (
        SELECT
          id,
          paradedb.score(id) AS full_text_score,
          RANK() OVER (ORDER BY full_text_score DESC) AS rank
        FROM embeddings
        WHERE
          context_id = ${ContextId.pgCodec} AND
          value @@@ $text
        ORDER BY score DESC
        LIMIT $int4
      ),
      semantic_search AS (
        SELECT
          id,
          embedding <=> $_float4 AS semantic_score,
          RANK() OVER (ORDER BY semantic_score) AS rank
        FROM embeddings
        WHERE
          context_id = ${ContextId.pgCodec}
        ORDER BY semantic_score
        LIMIT $int4
      ),
      matched AS (
        SELECT
          COALESCE(semantic_search.id, full_text_search.id) AS id,
          COALESCE(1.0 / (60 + semantic_search.rank), 0.0) + 
          COALESCE(1.0 / (60 + full_text_search.rank), 0.0) AS rrf_score
        FROM semantic_search
        FULL OUTER JOIN full_text_search ON semantic_search.id = full_text_search.id
        ORDER BY rrf_score DESC
        LIMIT $int4
      ),
      matched_deduped AS (
        SELECT DISTINCT ON (document_id, matched_fragment_index) *
        FROM matched
        ORDER BY document_id, matched_fragment_index, rrf_score
      )
    SELECT DISTINCT ON (document_id, fragment_index)
      context_id,
      document_id,
      ae.fragment_index AS fragment_index,
      ae.chunk_index AS chunk_index,
      ae.value AS value,
      ae.metadata as metadata,
      ae.embedding,
      full_text_score,
      semantic_score,
      rrf_score
    FROM matched_deduped AS e
    INNER JOIN embeddings AS ae
    ON ae.id = e.id
    WHERE fragment_index 
      BETWEEN matched_fragment_index - $int4 AND matched_fragment_index + $int4
    ORDER BY document_id, fragment_index, chunk_index
    """
    .query(storedEmbeddingCodec)
