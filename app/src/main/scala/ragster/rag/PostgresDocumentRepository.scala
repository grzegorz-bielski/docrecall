package docrecall
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

import docrecall.common.SkunkJson.*
import docrecall.postgres.*

import PostgresDocumentRepository.*

final class PostgresDocumentRepository(using Logger[IO]):
  // TODO: pagination?
  def getAll(contextId: ContextId)(using session: Session[IO]): IO[Vector[Document.Info]] =
      session
        .execute:
          selectDocumentFragment.query(documentInfoCodec)
        .map(_.toVector)

  def createOrUpdate(document: Document.Info)(using session: Session[IO]): IO[Unit] =
      session
        .prepare:
          sql"""
          INSERT INTO documents (
            id,
            context_id,
            name,
            description,
            type,
            metadata
          ) VALUES ($documentInfoCodec)
           ON CONFLICT (id) DO UPDATE SET
              description = EXCLUDED.description,
              type = EXCLUDED.type,
              metadata = EXCLUDED.metadata
          """
          .command
        .flatMap(_.execute(document))
      .void

  def delete(id: DocumentId)(using session: Session[IO]): IO[Unit] =
    session
      .prepare:
        sql"""DELETE FROM documents WHERE id = ${DocumentId.pgCodec}""".command
      .flatMap(_.execute(id))
      .void

object PostgresDocumentRepository:
  def of: IO[PostgresDocumentRepository] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield PostgresDocumentRepository()

  private lazy val selectDocumentFragment =
    sql"""
    SELECT
      id, 
      context_id, 
      name,
      description, 
      type,
      metadata
    FROM documents
    """

  private lazy val documentInfoCodec: Codec[Document.Info] =
    (
      DocumentId.pgCodec *:
        ContextId.pgCodec *:
        DocumentName.pgCodec *:
        text *:
        text *:
        Metadata.pgCodec
    ).to[Document.Info]
