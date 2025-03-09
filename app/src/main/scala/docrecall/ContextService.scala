package docrecall

import cats.effect.*
import cats.syntax.all.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*
import skunk.*

import docrecall.postgres.*
import docrecall.rag.*
import docrecall.rag.ingestion.*
import docrecall.rag.retrieval.*

final class ContextReadService(using
  logger: Logger[IO],
  contextRepository: PostgresContextRepository,
  retrievalService: RetrievalService[IO],
  documentRepository: PostgresDocumentRepository,
):
  def retrieve(input: RetrievalService.Input): IO[Vector[Embedding.Retrieved]] =
    retrievalService.retrieve(input)

  def getContext(contextId: ContextId)(using Session[IO]): IO[Option[ContextInfo]] =
    contextRepository.get(contextId)

  def getContextDocuments(contextId: ContextId)(using Session[IO]): IO[Vector[Document.Info]] =
    documentRepository.getAll(contextId)

  def getContexts(using Session[IO]): IO[Vector[ContextInfo]] =
    contextRepository.getAll

  def getDocuments(contextId: ContextId)(using Session[IO]): IO[Vector[Document.Info]] =
    documentRepository.getAll(contextId)

object ContextReadService:
  def of(using
    SessionResource,
    PostgresContextRepository,
    PostgresDocumentRepository,
    RetrievalService[IO],
  ): IO[ContextReadService] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield ContextReadService()

final class ContextWriteService(using
  logger: Logger[IO],
  contextRepository: PostgresContextRepository,
  ingestionService: IngestionService[IO],
  documentRepository: PostgresDocumentRepository,
):

  def createContext(using Session[IO]): IO[ContextInfo] =
    for
      context <- ContextInfo.default
      _       <- contextRepository.createOrUpdate(context)
    yield context

  def updateContext(using Session[IO])(context: ContextInfo): IO[Unit] =
    contextRepository.createOrUpdate(context)

  def getOrCreateDefaultContext(using Session[IO]): IO[ContextInfo] =
    contextRepository.getAll
      .map(_.headOption)
      .flatMap:
        case None          =>
          warn"No contexts, creating a new default one" *> createContext
        case Some(context) => IO.pure(context)

  def ingest(input: IngestionService.Input) =
    ingestionService.ingest(input)

  def purgeContextDocument(contextId: ContextId, documentId: DocumentId)(using Session[IO]): IO[Unit] =
    for
      _ <- warn"Purging documentId: $documentId from contextId: ${contextId} (!)"
      _ <- ingestionService.purge(contextId, documentId)
    yield ()

  def purgeContext(contextId: ContextId)(using Session[IO]): IO[Unit] =
    for
      documents <- documentRepository.getAll(contextId)
      _         <- warn"Purging context: $contextId with all of its ${documents.length} documents (!)"
      _         <- documents.parTraverse: doc =>
                     ingestionService.purge(contextId, doc.id)
      _         <- contextRepository.delete(contextId)
    yield ()

object ContextWriteService:
  def of(using
    SessionResource,
    PostgresContextRepository,
    PostgresDocumentRepository,
    IngestionService[IO],
  ): IO[ContextWriteService] =
    for given Logger[IO] <- Slf4jLogger.create[IO]
    yield ContextWriteService()
