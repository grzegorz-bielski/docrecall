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
    // TODO: https://docs.paradedb.com/documentation/guides/hybrid
    
    // client
    //   .streamQueryJson[ClickHouseRetrievedRow]:
    //     i"""
    //       WITH matched_embeddings AS (
    //         SELECT * FROM (
    //           SELECT
    //             document_id,
    //             context_id,
    //             fragment_index AS matched_fragment_index,
    //             chunk_index AS matched_chunk_index,
    //             value,
    //             metadata,
    //             cosineDistance(embedding, [${embedding.value.mkString(", ")}]) AS score
    //           FROM embeddings
    //           WHERE context_id = toUUID('${embedding.contextId}')
    //           ORDER BY score ASC
    //           LIMIT ${settings.topK}
    //         )
    //         LIMIT 1 BY document_id, matched_fragment_index
    //       )
    //       SELECT
    //         document_id,
    //         context_id,
    //         ae.fragment_index as fragment_index,
    //         ae.chunk_index as chunk_index,
    //         matched_fragment_index,
    //         matched_chunk_index,
    //         ae.value AS value,
    //         ae.metadata as metadata,
    //         score
    //       FROM matched_embeddings AS e
    //       INNER JOIN embeddings AS ae
    //       ON
    //         ae.context_id = e.context_id AND
    //         ae.document_id = e.document_id
    //       WHERE
    //         fragment_index BETWEEN
    //         matched_fragment_index - ${settings.fragmentLookupRange.lookBack} AND
    //         matched_fragment_index + ${settings.fragmentLookupRange.lookAhead}
    //       ORDER BY toUInt128(document_id), fragment_index, chunk_index
    //       LIMIT 1 BY document_id, fragment_index
    //       FORMAT JSONEachRow
    //     """
    //   .map: row =>
    //     Embedding.Retrieved(
    //       documentId = DocumentId(row.document_id),
    //       contextId = ContextId(row.context_id),
    //       chunk = Chunk(text = row.value, index = row.chunk_index, metadata = row.metadata),
    //       value = embedding.value,
    //       fragmentIndex = row.fragment_index,
    //       score = row.score,
    //     )
    //   .evalTap(retrieved => info"$retrieved")
    ???

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
    embeddingToStoreCodec *:
      float8 *:
      float8 *:
      float8
