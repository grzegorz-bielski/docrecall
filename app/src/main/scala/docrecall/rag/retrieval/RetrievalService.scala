package docrecall
package rag
package retrieval

import cats.effect.*
import cats.effect.syntax.all.*
import fs2.{Chunk as _, io as _, *}
import org.typelevel.log4cats.syntax.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.*
import skunk.*
import docrecall.rag.vectorstore.*
import docrecall.postgres.*

import RetrievalService.*

trait RetrievalService[F[_]]:
  def retrieve(input: Input): F[Vector[Embedding.Retrieved]]

object RetrievalService:
  final case class Input(
    query: String,
    contextId: ContextId,
    embeddingsModel: Model,
    retrievalSettings: RetrievalSettings,
  )

  def of(using
    SessionResource,
    EmbeddingService[IO],
    PostgresEmbeddingsRepository,
  ): IO[RetrievalService[IO]] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield RetrievalServiceImpl()

final class RetrievalServiceImpl(using
  embeddingsRepository: PostgresEmbeddingsRepository,
  embeddingService: EmbeddingService[IO],
  sessionResource: SessionResource,
  logger: Logger[IO],
) extends RetrievalService[IO]:
  def retrieve(input: Input) =
    import input.*

    for
      queryEmbeddings     <- embeddingService.createQueryEmbeddings(
                               contextId = contextId,
                               chunk = Chunk(query, index = 0),
                               model = embeddingsModel,
                             )
      retrievedEmbeddings <- sessionResource.useGiven:
                               embeddingsRepository.retrieve(queryEmbeddings, retrievalSettings).compile.toVector

      _ <- logger.debug(s"Retrieved embeddings: ${retrievedEmbeddings.map(_.chunk.toEmbeddingInput)}")
    yield retrievedEmbeddings
