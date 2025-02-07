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

import ragster.postgres.*
import skunk.*

import PostgresDocumentRepository.*

final class PostgresDocumentRepository(client: PostgresClient[IO])(using Logger[IO]) extends DocumentRepository[IO]:
  def getAll(contextId: ContextId): IO[Vector[Document.Info]] =
    // client
    //   .streamQueryJson[IngestedDocumentRow]:
    //     i"""
    //     SELECT 
    //       id, 
    //       context_id, 
    //       name, 
    //       description, 
    //       version, 
    //       type,
    //       metadata
    //     FROM documents
    //     WHERE context_id = toUUID('$contextId') 
    //     FORMAT JSONEachRow
    //     """
    //   .map(_.asDocumentInfo)
    //   .compile
    //   .toVector
    ???

  def get(contextId: ContextId, name: DocumentName): IO[Option[Document.Info]] =
    // client
    //   .streamQueryJson[IngestedDocumentRow]:
    //     i"""
    //     SELECT 
    //       id, 
    //       context_id, 
    //       name, 
    //       description, 
    //       version, 
    //       type,
    //       metadata
    //     FROM documents
    //     WHERE context_id = toUUID('$contextId') 
    //     AND name = '$name'
    //     FORMAT JSONEachRow
    //     """
    //   .map(_.asDocumentInfo)
    //   .compile
    //   .last
    ???

  override def createOrUpdate(document: Document.Info): IO[Unit] =
    // should be merged by ReplacingMergeTree on sorting key duplicates, but not at once
    // client.executeQuery:
    //   i"""
    //   INSERT INTO documents (
    //     id, 
    //     context_id, 
    //     name, 
    //     description, 
    //     version, 
    //     type,
    //     metadata
    //   ) VALUES (
    //     toUUID('${document.id}'),
    //     toUUID('${document.contextId}'),
    //     '${document.name}',
    //     '${document.description}',
    //     ${document.version},
    //     '${document.`type`}',
    //     ${document.metadata.toClickHouseMap}
    //   )
    //   """
    ???

  override def delete(id: DocumentId): IO[Unit] =
    ???
    // client.executeQuery:
    //   i"""DELETE FROM documents WHERE id = toUUID('$id')"""

object PostgresDocumentRepository:
  def of(using client: PostgresClient[IO]): IO[PostgresDocumentRepository] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield PostgresDocumentRepository(client)

  // private final case class IngestedDocumentRow(
  //   id: DocumentId,
  //   context_id: ContextId,
  //   name: DocumentName,
  //   version: DocumentVersion,
  //   description: String,
  //   `type`: String,
  //   metadata: Map[String, String],
  // ) derives ConfiguredJsonValueCodec:
  //   def asDocumentInfo: Document.Info =
  //     this.into[Document.Info]
  //       .withFieldRenamed(_.context_id, _.contextId)
  //       .transform
