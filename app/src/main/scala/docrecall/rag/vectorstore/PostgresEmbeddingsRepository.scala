package docrecall
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
import java.util.UUID
import skunk.*
import skunk.data.*
import skunk.codec.all.*
import skunk.syntax.all.*

import docrecall.postgres.*

final class PostgresEmbeddingsRepository(using Logger[IO]):
  import PostgresEmbeddingsRepository.*

  def delete(contextId: ContextId, documentId: DocumentId)(using session: Session[IO]): IO[Unit] =
    session
      .prepare:
        sql"""
        DELETE FROM embeddings
        WHERE
          context_id = ${ContextId.pgCodec} AND
          document_id = ${DocumentId.pgCodec}
        """.command
      .flatMap(_.execute((contextId, documentId)))
      .void

  def store(index: Vector[Embedding.Index])(using session: Session[IO]): IO[Unit] =
    val values = index.toList.map: e =>
      (e.contextId, e.documentId, e.fragmentIndex, e.chunk.index, e.chunk.text, e.chunk.metadata, Arr(e.value*))

    session
      .prepare:
        sql"""
        INSERT INTO embeddings (context_id, document_id, fragment_index, chunk_index, value, metadata, embedding)
        VALUES ${embeddingToStoreCodec.values.list(values)}
        """.command
      .flatMap(_.execute(values))
      .void

  def documentEmbeddingsExists(contextId: ContextId, documentId: DocumentId)(using session: Session[IO]): IO[Boolean] =
    session
      .prepare:
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

  def retrieve(embedding: Embedding.Query, settings: RetrievalSettings)(using
    session: Session[IO],
  ): Stream[IO, Embedding.Retrieved] =
    val args =
      (
        StringEscapeUtils.toTantivySearchString(embedding.chunk.text),
        settings.fullTextSearchLimit,
        embedding.contextId,
        Arr(embedding.value*),
        embedding.contextId,
        settings.semanticSearchLimit,
        settings.rrfLimit,
        settings.fragmentLookupRange.lookBack,
        settings.fragmentLookupRange.lookAhead,
      )

    Stream
      .eval(session.prepare(retrieveHybridQuery))
      .flatMap(_.stream(args = args, chunkSize = 1024))

object PostgresEmbeddingsRepository:
  def of: IO[PostgresEmbeddingsRepository] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield PostgresEmbeddingsRepository()

  private lazy val embeddingToStoreCodec =
    ContextId.pgCodec *:
      DocumentId.pgCodec *:
      int8 *:
      int8 *:
      text *:
      Metadata.pgCodec *:
      _float4

  private lazy val storedEmbeddingCodec =
    (embeddingToStoreCodec *: int8 *: float4 *: float8 *: numeric).map:
      case (
            (contextId, documentId, fragmentIndex, chunkIndex, text, metadata, embedding),
            matchedFragmentIndex,
            fullTextScore,
            semanticScore,
            rrfScore,
          ) =>
        Embedding.Retrieved(
          chunk = Chunk(text, chunkIndex, metadata),
          contextId = contextId,
          documentId = documentId,
          fragmentIndex = fragmentIndex,
          matchedFragmentIndex = matchedFragmentIndex,
          value = embedding.flattenTo(Vector),
          fullTextScore = fullTextScore,
          semanticScore = semanticScore,
          rrfScore = rrfScore,
        )

  // TODO: `full_text_search_from_ctx` is a workaround
  // filtering on context_id in the full text search with @@@ breaks the scoring function, so we need another CTE
  // waiting for https://github.com/paradedb/paradedb/pull/2197 to be published

  // TODO: BM25 scores are for whole embeddings table, which is shared between contexts
  // should we create a separate embeddings table for each context?
  private lazy val retrieveHybridQuery =
    sql"""
    WITH
      full_text_search AS (
        SELECT
          id,
          fragment_index,
          document_id,
          context_id,
          paradedb.score(id) AS full_text_score
        FROM embeddings
        WHERE
          id @@@ paradedb.match('value', $text)
        ORDER BY full_text_score DESC
        LIMIT $int4
      ),
      full_text_search_from_ctx AS (
        SELECT * FROM full_text_search WHERE context_id = ${ContextId.pgCodec}
      ),
      full_text_search_ranked AS (
        SELECT
          id,
          fragment_index,
          document_id,
          context_id,
          full_text_score,
          RANK() OVER (ORDER BY full_text_score DESC) AS rank
        FROM full_text_search_from_ctx
      ),
      semantic_search AS (
        SELECT
          id,
          fragment_index,
          document_id,
          context_id,
          embedding <=> $_float4::vector AS semantic_score
        FROM embeddings
        WHERE
          context_id = ${ContextId.pgCodec}
        ORDER BY semantic_score ASC
        LIMIT $int4
      ),
      semantic_search_ranked AS (
        SELECT
          id,
          fragment_index,
          document_id,
          context_id,
          semantic_score,
          RANK() OVER (ORDER BY semantic_score) AS rank
        FROM semantic_search
      ),
      matched AS (
        SELECT
          COALESCE(semantic_search_ranked.fragment_index, full_text_search_ranked.fragment_index) AS matched_fragment_index,
          COALESCE(semantic_search_ranked.document_id, full_text_search_ranked.document_id) AS document_id,
          COALESCE(semantic_search_ranked.context_id, full_text_search_ranked.context_id) AS context_id,
          COALESCE(semantic_search_ranked.id, full_text_search_ranked.id) AS id,
          COALESCE(full_text_search_ranked.full_text_score, 0,0) AS full_text_score, 
          COALESCE(semantic_search_ranked.semantic_score, 0,0) AS semantic_score,
          COALESCE(1.0 / (60 + semantic_search_ranked.rank), 0.0) + 
          COALESCE(1.0 / (60 + full_text_search_ranked.rank), 0.0) AS rrf_score
        FROM semantic_search_ranked
        FULL OUTER JOIN full_text_search_ranked ON semantic_search_ranked.id = full_text_search_ranked.id
        ORDER BY rrf_score DESC
        LIMIT $int4
      ),
      matched_deduped AS (
        SELECT DISTINCT ON (document_id, matched_fragment_index) *
        FROM matched
        ORDER BY document_id, matched_fragment_index, rrf_score
      )
    SELECT DISTINCT ON (document_id, fragment_index)
      ae.context_id AS context_id,
      ae.document_id AS document_id,
      ae.fragment_index AS fragment_index,
      ae.chunk_index AS chunk_index,
      ae.value AS value,
      ae.metadata AS metadata,
      CAST(ae.embedding AS FLOAT4[]) AS embedding,
      e.matched_fragment_index AS matched_fragment_index,
      e.full_text_score AS full_text_score,
      e.semantic_score AS semantic_score,
      e.rrf_score AS rrf_score
    FROM matched_deduped AS e
    LEFT JOIN embeddings AS ae
    USING (context_id, document_id)
    WHERE fragment_index 
      BETWEEN matched_fragment_index - $int4 AND matched_fragment_index + $int4
    ORDER BY document_id, fragment_index, chunk_index
    """
      .query(storedEmbeddingCodec)
