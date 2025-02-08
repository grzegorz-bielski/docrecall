package ragster
package rag

import java.util.UUID
import cats.effect.*
import cats.syntax.all.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*
import unindent.*
import java.util.UUID
import java.time.*
import io.scalaland.chimney.dsl.*
import skunk.*
import skunk.codec.all.*
import skunk.syntax.all.*

import ragster.common.SkunkJson.*
import ragster.postgres.*

import PostgresDocumentRepository.*

final class PostgresDocumentRepository(sessionResource: SessionResource)(using Logger[IO]) extends DocumentRepository[IO]:
  private lazy val selectDocumentFragment =
    sql"""
    SELECT
      id, 
      context_id, 
      name, 
      description, 
      version, 
      type,
      metadata
    FROM documents
    """

  def getAll(contextId: ContextId): IO[Vector[Document.Info]] =
    sessionResource.use:
      _.execute:
        selectDocumentFragment.query(documentInfoCodec)
      .map(_.toVector)

  def get(contextId: ContextId, name: DocumentName): IO[Option[Document.Info]] =
    sessionResource.use:
      _.prepare:
        sql"""
        $selectDocumentFragment
        WHERE context_id = ${ContextId.pgCodec}
        AND name = $text
        """
        .query(documentInfoCodec)
      .flatMap(_.option((contextId, name)))

  def createOrUpdate(document: Document.Info): IO[Unit] =
    sessionResource
      .use:
        _.prepare:
          sql"""
          INSERT INTO documents (
            id,
            context_id,
            name,
            description,
            version,
            type,
            metadata
          ) VALUES ($documentInfoCodec),
           ON CONFLICT (id) DO UPDATE SET
              description = EXCLUDED.description,
              version = EXCLUDED.version,
              type = EXCLUDED.type,
              metadata = EXCLUDED.metadata
          """
          .command
        .flatMap(_.execute(document))
      .void

  def delete(id: DocumentId): IO[Unit] =
    sessionResource
      .use:
        _.prepare:
          sql"""DELETE FROM contexts WHERE id = ${DocumentId.pgCodec}""".command
        .flatMap(_.execute(id))
      .void

object PostgresDocumentRepository:
  def of(using sessionResource: SessionResource): IO[PostgresDocumentRepository] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield PostgresDocumentRepository(sessionResource)


  private lazy val documentInfoCodec: Codec[Document.Info] =
    (
      DocumentId.pgCodec *:
        ContextId.pgCodec *:
        DocumentName.pgCodec *:
        DocumentVersion.pgCodec *:
        text *:
        text *:
        Metadata.pgCodec
    ).to[Document.Info]
