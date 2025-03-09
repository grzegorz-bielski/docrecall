package docrecall

import fs2.{Chunk as _, *}
import fs2.io.*
import fs2.io.file.Files
import cats.effect.*
import cats.syntax.all.*
import cats.effect.syntax.all.*
import org.http4s.{scalatags as _, h2 as _, *}
import org.http4s.FormDataDecoder.*
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.implicits.*
import org.http4s.headers.Location
import org.http4s.multipart.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*
import scalatags.Text.all.*
import skunk.*
import scala.concurrent.duration.{span as _, *}
import java.util.UUID

import docrecall.chat.*
import docrecall.rag.vectorstore.*
import docrecall.rag.*
import docrecall.rag.ingestion.*
import docrecall.postgres.*

final class ContextController(using
  sessionResource: SessionResource,
  logger: Logger[IO],
  contextReadService: ContextReadService,
  contextWriteService: ContextWriteService,
  chatService: ChatService[IO],
  appConfig: AppConfig,
) extends TopLevelHtmxController:
  import ContextController.*

  protected val prefix = "contexts"

  private val fileFieldName = "file"

  protected val routes = IO:
    val documentDeleteUrl = (doc: Document.Info) => s"/$prefix/${doc.contextId}/documents/${doc.id}"

    HttpRoutes.of[IO]:
      case GET -> Root =>
        sessionResource.useGiven:
          redirectToDefault

      case GET -> Root / "new" =>
        sessionResource.useGiven:
          for
            context <- contextWriteService.createContext
            response = Response[IO]()
                         .withStatus(Status.SeeOther)
                         .withHeaders(Location(Uri.unsafeFromString(s"/$prefix/${context.id}")))
          yield response

      case GET -> Root / ContextIdVar(contextId) =>
        sessionResource.useGiven:
          getContextOrNotFound(contextId): contextInfo =>
            for
              contexts  <- contextReadService.getContexts
              documents <- contextReadService.getDocuments(contextInfo.id)
              response  <- Ok(
                             ContextView.view(
                               contexts = contexts,
                               contextUrl = id => s"/contexts/$id",
                               contextInfo = contextInfo,
                               uploadUrl = s"/$prefix/${contextInfo.id}/documents/upload",
                               chatPostUrl = s"/$prefix/${contextInfo.id}/chat/query",
                               contextUpdateUrl = s"/$prefix/${contextInfo.id}/update",
                               documents = documents,
                               fileFieldName = fileFieldName,
                               documentDeleteUrl = documentDeleteUrl,
                             ),
                           )
            yield response

      case req @ DELETE -> Root / ContextIdVar(contextId) =>
        sessionResource.useGiven:
          contextWriteService.purgeContext(contextId) *> Ok()
      // *> redirectToDefault

      case GET -> Root / ContextIdVar(contextId) / "chat" / "responses" :? QueryIdMatcher(queryId) =>
        sessionResource.useGiven:
          getContextOrNotFound(contextId): context =>
            val eventStream: EventStream[IO] = chatService
              .subscribeToQueryResponses(queryId)
              .map:
                case resp @ ChatService.Response.Retrieval(_, metadata) =>
                  ServerSentEvent(
                    data = ChatView.responseRetrievalChunk(metadata).render.some,
                    eventType = resp.eventType.toString.some,
                  )
                case resp @ ChatService.Response.Partial(_, content)    =>
                  ServerSentEvent(
                    data = ChatView.responseChunk(content).render.some,
                    eventType = resp.eventType.toString.some,
                  )
                case resp @ ChatService.Response.Finished(_)            =>
                  ServerSentEvent(
                    data = ChatView.responseClearEventSourceListener().render.some,
                    eventType = resp.eventType.toString.some,
                  )
              .evalTap: msg =>
                debug"SSE message to send: $msg"

            info"Subscribing to chat responses for queryId: $queryId" *>
              Ok(eventStream)

      case req @ POST -> Root / ContextIdVar(contextId) / "chat" / "query" =>
        sessionResource.useGiven:
          getContextOrNotFound(contextId): context =>
            for
              query   <- req.as[ChatQuery]
              queryId <- QueryId.of

              _   <- chatService
                       .processQuery(
                         ChatService.Input(
                           contextId = context.id,
                           query = query,
                           queryId = queryId,
                           promptTemplate = context.promptTemplate,
                           retrievalSettings = context.retrievalSettings,
                           chatModel = context.chatModel,
                           embeddingsModel = context.embeddingsModel,
                         ),
                       )
              //  .start // fire and forget -- handled by the supervisor
              res <-
                Ok(
                  ChatView.responseMessage(
                    queryId = queryId,
                    query = query,
                    sseUrl = s"/$prefix/${context.id}/chat/responses?queryId=$queryId",
                    queryCloseEvent = ChatService.ResponseType.Finished.toString,
                    queryResponseEvent = ChatService.ResponseType.Partial.toString,
                    ChatService.ResponseType.Retrieval.toString,
                  ),
                )
            yield res

      case req @ POST -> Root / ContextIdVar(contextId) / "update" =>
        sessionResource.useGiven:
          getContextOrNotFound(contextId): context =>
            for
              contextInfo <- req.as[ContextInfoFormDto].map(_.asContextInfo(context.id))
              _           <- info"Updating context: $contextInfo"
              response    <- contextInfo.fold(
                               BadRequest(_),
                               contextWriteService.updateContext(_) *> Ok(),
                             )
            yield response

      case req @ POST -> Root / ContextIdVar(contextId) / "documents" / "upload" =>
        sessionResource.useGiven:
          getContextOrNotFound(contextId): context =>
            EntityDecoder
              .mixedMultipartResource[IO]()
              .use: decoder =>
                req.decodeWith(decoder, strict = true): multipart =>
                  val ingestedDocuments = multipart.parts
                    .filter(_.name.contains(fileFieldName))
                    .parTraverse: part =>
                      contextWriteService.ingest(
                        IngestionService.Input(
                          contextId = context.id,
                          documentName = DocumentName(part.filename.getOrElse("unknown")),
                          embeddingsModel = context.embeddingsModel,
                          content = part.body,
                        ),
                      )

                  ingestedDocuments.flatMap: docs =>
                    Ok(
                      ContextView.uploadedDocuments(docs, documentDeleteUrl = documentDeleteUrl),
                    )

      case req @ DELETE -> Root / ContextIdVar(contextId) / "documents" / DocumentIdVar(documentId) =>
        sessionResource.useGiven:
          getContextOrNotFound(contextId): context =>
            for
              _        <- contextWriteService.purgeContextDocument(contextId, documentId)
              response <- Ok()
            yield response

  private def redirectToDefault(using Session[IO]) =
    for
      context <- contextWriteService.defaultContext
      response = Response[IO]()
                   .withStatus(Status.Found)
                   .withHeaders(Location(Uri.unsafeFromString(s"/$prefix/${context.id}")))
    yield response

  private def getContextOrNotFound(
    contextId: ContextId,
  )(using Session[IO])(fn: ContextInfo => IO[Response[IO]]): IO[Response[IO]] =
    contextReadService
      .getContext(contextId)
      .flatMap:
        case Some(context) => fn(context)
        case None          => NotFound()

object ContextController:
  def of()(using
    SessionResource,
    ContextReadService,
    ContextWriteService,
    ChatService[IO],
    IngestionService[IO],
    AppConfig,
  ): Resource[IO, ContextController] =
    for given Logger[IO] <- Slf4jLogger.create[IO].toResource
    yield ContextController()

  given QueryParamDecoder[QueryId] = QueryParamDecoder[String].emap: str =>
    ParseResult.fromTryCatchNonFatal("Could not parse the UUID")(QueryId(UUID.fromString(str)))

  object QueryIdMatcher extends QueryParamDecoderMatcher[QueryId]("queryId")

  object ContextIdVar:
    def unapply(str: String): Option[ContextId] =
      if str.isEmpty then None
      else scala.util.Try(UUID.fromString(str)).toOption.map(ContextId.apply)

  object DocumentIdVar:
    def unapply(str: String): Option[DocumentId] =
      if str.isEmpty then None
      else scala.util.Try(UUID.fromString(str)).toOption.map(DocumentId.apply)
