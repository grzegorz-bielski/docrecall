package docrecall
package rag
package ingestion

import cats.effect.*
import cats.effect.syntax.all.*
import fs2.*
import org.typelevel.log4cats.syntax.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.*
import skunk.*
import docrecall.rag.vectorstore.*
import docrecall.postgres.*

trait IngestionService[F[_]]:
  def ingest(input: IngestionService.Input): F[Document.Ingested]
  def purge(contextId: ContextId, documentId: DocumentId): F[Unit]

object IngestionService:
  final case class Input(
    contextId: ContextId,
    documentName: DocumentName,
    documentId: Option[DocumentId] = None,
    embeddingsModel: Model,
    content: Stream[IO, Byte],
  )

final class PostgresIngestionService(using
  sessionResource: SessionResource,
  documentRepository: PostgresDocumentRepository,
  embeddingsService: EmbeddingService[IO],
  vectorStoreRepository: PostgresEmbeddingsRepository,
  documentTokenizer: DocumentTokenizer[IO],
  logger: Logger[IO],
) extends IngestionService[IO]:
  def purge(contextId: ContextId, documentId: DocumentId): IO[Unit] =
    sessionResource.useGiven:
      for
        _ <- vectorStoreRepository.delete(contextId, documentId)
        _ <- documentRepository.delete(documentId)
        _ <- info"Deleted document: $documentId and all its embeddings."
      yield ()

  def ingest(input: IngestionService.Input): IO[Document.Ingested] =
    import input.*

    sessionResource.useGiven: (session: Session[IO]) ?=>
      for
        documentId <- input.documentId.fold(DocumentId.of)(IO.pure)

        documentFragments <- documentTokenizer.tokenize(content, maxTokens = embeddingsModel.contextLength)
        _                 <- info"Document $documentId: loaded ${documentFragments.size} fragments from the document."

        documentInfo = Document.Info(
                         id = documentId,
                         contextId = contextId,
                         name = documentName,
                         description = "",
                         `type` = "PDF", // TODO: infer from content...
                         metadata = Metadata.empty,
                       )
        document     = Document.Ingested(
                         info = documentInfo,
                         fragments = documentFragments,
                       )

        _               <- info"Creating embeddings for document: $documentId."
        indexEmbeddings <- embeddingsService.createIndexEmbeddings(document, model = embeddingsModel)
        _               <- info"Document $documentId: created ${indexEmbeddings.size} embeddings."

        _ <- session.transaction.use: _ =>
               for
                 _ <- documentRepository.createOrUpdate(document.info)
                 _ <- info"Document $documentId metadata persisted."

                 _ <- vectorStoreRepository.store(indexEmbeddings)
                 _ <- info"Document $documentId: embeddings persisted."
               yield ()
      yield document

object PostgresIngestionService:
  def of(using
    SessionResource,
    PostgresDocumentRepository,
    EmbeddingService[IO],
    PostgresEmbeddingsRepository,
  ): IO[PostgresIngestionService] =
    for
      given Logger[IO]            <- Slf4jLogger.create[IO]
      given DocumentTokenizer[IO] <- DocumentTokenizer.of
    yield PostgresIngestionService()
