package docrecall

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
import docrecall.common.SkunkJson.*
import scala.util.Try
import scala.util.control.NoStackTrace
import skunk.*
import skunk.codec.all.*
import skunk.syntax.all.*

import docrecall.postgres.*

import PostgresContextRepository.*
import ContextInfo.given

final class PostgresContextRepository(using Logger[IO]):
  def get(id: ContextId)(using session: Session[IO]): IO[Option[ContextInfo]] =
    session
      .prepare:
        sql"""
          $selectContextFragment
          WHERE id = ${ContextId.pgCodec}
          """
          .query(contextInfoCodec)
      .flatMap(_.option(id))

  // `name` is not unique (!)
  def getByName(name: String)(using session: Session[IO]): IO[Vector[ContextInfo]] =
    session
      .prepare:
        sql"""
          $selectContextFragment
          WHERE name = $text
          """
          .query(contextInfoCodec)
      .flatMap:
        _.stream(name, chunkSize = 32).compile.toVector

  def getAll(using session: Session[IO]): IO[Vector[ContextInfo]] =
    session
      .execute:
        selectContextFragment.query(contextInfoCodec)
      .map(_.toVector)

  def createOrUpdate(info: ContextInfo)(using session: Session[IO]): IO[Unit] =
    session
      .prepare:
        sql"""
          INSERT INTO contexts (
            id,
            name,
            description,
            prompt_template,
            retrieval_settings,
            chat_completion_settings,
            chat_model,
            embeddings_model
          )
          VALUES ($contextInfoCodec)
          ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            prompt_template = EXCLUDED.prompt_template,
            retrieval_settings = EXCLUDED.retrieval_settings,
            chat_completion_settings = EXCLUDED.chat_completion_settings,
            chat_model = EXCLUDED.chat_model,
            embeddings_model = EXCLUDED.embeddings_model,
            updated_at = CURRENT_TIMESTAMP
        """.command
      .flatMap(_.execute(info))
      .void

  def delete(id: ContextId)(using session: Session[IO]): IO[Unit] =
    session
      .prepare:
        sql"""DELETE FROM contexts WHERE id = ${ContextId.pgCodec}""".command
      .flatMap(_.execute(id))
      .void

object PostgresContextRepository:
  def of: IO[PostgresContextRepository] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield PostgresContextRepository()

  private lazy val selectContextFragment =
    sql"""
    SELECT
      id,
      name,
      description,
      prompt_template,
      retrieval_settings,
      chat_completion_settings,
      chat_model,
      embeddings_model
    FROM contexts
    """
  
  private lazy val contextInfoCodec: Codec[ContextInfo] =
    (
      ContextId.pgCodec *:
        text *:
        text *:
        jsonb[PromptTemplate] *:
        jsonb[RetrievalSettings] *:
        jsonb[ChatCompletionSettings] *:
        jsonb[Model] *:
        jsonb[Model]
    ).to[ContextInfo]
