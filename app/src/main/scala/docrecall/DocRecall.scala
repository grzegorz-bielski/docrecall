package docrecall

import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.effect.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*
import skunk.*

import docrecall.rag.*
import docrecall.rag.ingestion.*
import docrecall.rag.vectorstore.*
import docrecall.home.*
import docrecall.postgres.*
import docrecall.chat.*
import docrecall.integrations.slack.*
import docrecall.inference.*

object DocRecall extends ResourceApp.Forever:
  def run(args: List[String]): Resource[IO, Unit] =
    for
      given AppConfig   <- AppConfig.load.toResource
      _                 <- AppLogger.configure.toResource
      given Logger[IO]  <- Slf4jLogger.create[IO].toResource
      given SttpBackend <- SttpBackend.resource

      given SessionResource              <- PostgresSessionPool.of
      given PostgresContextRepository    <- PostgresContextRepository.of.toResource
      given PostgresDocumentRepository   <- PostgresDocumentRepository.of.toResource
      given PostgresEmbeddingsRepository <- PostgresEmbeddingsRepository.of.toResource

      inferenceModule                 = InferenceModule.of
      given ChatCompletionService[IO] = inferenceModule.chatCompletionService
      given EmbeddingService[IO]      = inferenceModule.embeddingService

      given IngestionService[IO] <- PostgresIngestionService.of.toResource
      given ChatService[IO]      <- ChatServiceImpl.of()

      contextController <- ContextController.of()
      homeController     = HomeController()

      // integrations
      given SlackCommandsService[IO] <- SlackCommandsService.of
      slackBotController             <- SlackBotController.of

      // state-changing side effects (!)
      _ <- docrecall.Migrations.run.toResource
      _ <- Fixtures.loadFixtures().toResource

      _ <- httpApp(
             controllers = Vector(
               contextController,
               homeController,
               slackBotController,
             ),
           )
    yield ()
