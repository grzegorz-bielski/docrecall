package docrecall
package chat

import fs2.{Chunk as _, io as _, *}
import cats.*
import cats.effect.*
import cats.effect.std.*
import cats.syntax.all.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*
import scala.concurrent.duration.{span as _, *}
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.*
import java.util.UUID
import java.time.Duration

import docrecall.rag.vectorstore.*
import docrecall.rag.retrieval.*
import docrecall.rag.*
import docrecall.postgres.*

trait ChatService[F[_]]:
  /** Starts the query processing, which usually runs in the background.
    *
    * @param input
    *   The input for the query.
    */
  def processQuery(input: ChatService.Input): F[Unit]

  /** Subscribe to the query responses started in `processQuery`.
    *
    * @param queryId
    *   The query id to subscribe to.
    * @return
    *   A stream of responses.
    */
  def subscribeToQueryResponses(queryId: QueryId): Stream[F, ChatService.Response]

  /** Asks a question and waits for the response.
    *
    * @param input
    *   The input for the query.
    * @return
    *   The response content.
    */
  final def ask(input: ChatService.Input)(using Concurrent[F]): F[String] =
    processQuery(input) *>
      subscribeToQueryResponses(input.queryId)
        .collectWhile:
          case ChatService.Response.Partial(_, content) => content
        .compile
        .string

object ChatService:
  final case class Input(
    contextId: ContextId,
    query: ChatQuery,
    queryId: QueryId,
    promptTemplate: PromptTemplate,
    retrievalSettings: RetrievalSettings,
    chatModel: Model,
    embeddingsModel: Model,
  )

  extension (info: ContextInfo)
    def toChatInput(query: ChatQuery, queryId: QueryId): Input =
      info
        .into[Input]
        .withFieldRenamed(_.id, _.contextId)
        .withFieldConst(_.query, query)
        .withFieldConst(_.queryId, queryId)
        .transform

  trait WithQueryId:
    def queryId: QueryId

  enum ResponseType:
    case Partial, Retrieval, Finished

  enum Response(val eventType: ResponseType) extends WithQueryId:
    case Partial(queryId: QueryId, content: String) extends Response(ResponseType.Partial)

    case Retrieval(queryId: QueryId, metadata: RetrievalMetadata) extends Response(ResponseType.Retrieval)

    case Finished(queryId: QueryId) extends Response(ResponseType.Finished)

  final class Supervised[F[_]: Apply](
    chatService: ChatService[F],
    supervisor: Supervisor[F],
  ) extends ChatService[F]:
    def processQuery(input: ChatService.Input): F[Unit] =
      supervisor.supervise(chatService.processQuery(input)).void

    export chatService.subscribeToQueryResponses

  object Supervised:
    def of[F[_]: Concurrent](chatService: ChatService[F]): Resource[F, ChatService[F]] =
      Supervisor[F].map(Supervised(chatService, _))

final class ChatServiceImpl(pubSub: PubSub[IO])(using
  logger: Logger[IO],
  chatCompletionService: ChatCompletionService[IO],
  contextReadService: ContextReadService,
) extends ChatService[IO]:
  import ChatService.*
  import ChatServiceImpl.*

  def processQuery(input: ChatService.Input): IO[Unit] =
    import input.*

    for
      _                   <- info"Processing the response for queryId: $queryId has started."
      processingStarted   <- IO.realTimeInstant
      retrievedEmbeddings <- contextReadService.retrieve(
                               RetrievalService.Input(
                                 query = query.content,
                                 contextId = contextId,
                                 embeddingsModel = embeddingsModel,
                                 retrievalSettings = retrievalSettings,
                               ),
                             )

      prompt =
        Prompt(
          query = query.content,
          // TODO: move it into a tool / LLM function call ?
          queryContext = retrievedEmbeddings.map(_.chunk.toEmbeddingInput).mkString("\n").some,
          template = promptTemplate,
        )

      metadataStream = Stream
                         .emit(
                           Response.Retrieval(
                             queryId = queryId,
                             metadata = RetrievalMetadata.of(retrievedEmbeddings),
                           ),
                         )
                         .covary[IO]

      chatResponseStream = chatCompletionService
                             .chatCompletion(prompt, model = chatModel)
                             .map: chatMsg =>
                               Response.Partial(
                                 queryId = queryId,
                                 content = chatMsg.contentDeltas,
                               )
                             .onComplete:
                               Stream(Response.Finished(queryId = queryId))

      _ <- (metadataStream ++ chatResponseStream)
             .evalTap: chatMsg =>
               debug"Received chat message: $chatMsg"
             .evalTap(pubSub.publish)
             .compile
             .drain

      processingEnded   <- IO.realTimeInstant
      processingDuration = Duration.between(processingStarted, processingEnded)
      _                 <-
        info"Processing the response for queryId: $queryId has been completed. (took: ${processingDuration.getSeconds} s)"
    yield ()

  def subscribeToQueryResponses(queryId: QueryId): Stream[IO, Response] =
    // TODO: keep in-progress queries in state and reject requests for non-existent ones?
    Stream.eval(info"Waiting for query completion: $queryId") *>
      pubSub
        .subscribe(queryId)
        .timeout(5.minutes)

object ChatServiceImpl:
  def of()(using
    SessionResource,
    ChatCompletionService[IO],
    ContextReadService,
    // PostgresEmbeddingsRepository,
    // EmbeddingService[IO],
  ): Resource[IO, ChatService[IO]] =
    for
      given Logger[IO] <- Slf4jLogger.create[IO].toResource
      pubSub           <- PubSub.resource[IO]

      chatService <- ChatService.Supervised.of(ChatServiceImpl(pubSub))
    yield chatService
