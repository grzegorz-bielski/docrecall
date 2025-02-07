package ragster

import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.effect.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.syntax.*

import ragster.rag.*
import ragster.rag.ingestion.*
import ragster.rag.vectorstore.*
import ragster.home.*
import ragster.postgres.*
import ragster.chat.*
import ragster.integrations.slack.*
import ragster.inference.*

object Ragster extends ResourceApp.Forever:
  def run(args: List[String]): Resource[IO, Unit] =
    for
      given AppConfig   <- AppConfig.load.toResource
      _                 <- AppLogger.configure.toResource
      given Logger[IO]  <- Slf4jLogger.create[IO].toResource
      given SttpBackend <- SttpBackend.resource

      given PostgresClient[IO]        <- PostgresClient.of
      given ContextRepository[IO]     <- PostgresContextRepository.of.toResource
      given DocumentRepository[IO]    <- PostgresDocumentRepository.of.toResource
      given VectorStoreRepository[IO] <- PostgresVectorStore.of.toResource

      inferenceModule                 = InferenceModule.of
      given ChatCompletionService[IO] = inferenceModule.chatCompletionService
      given EmbeddingService[IO]      = inferenceModule.embeddingService

      given IngestionService[IO] <- ClickHouseIngestionService.of.toResource
      given ChatService[IO]      <- ChatServiceImpl.of()

      contextController <- ContextController.of()
      homeController     = HomeController()

      // integrations
      given SlackCommandsService[IO] <- SlackCommandsService.of
      slackBotController            <- SlackBotController.of

      // _ <- runInitialHealthChecks().toResource

      // state-changing side effects (!)
      // _ <- ClickHouseMigrator.migrate().toResource
      // _ <- Fixtures.loadFixtures().toResource
      _ <- ragster.Migrations.run.toResource

      // _ <- httpApp(
      //        controllers = Vector(
      //          contextController,
      //          homeController,
      //          slackBotController,
      //        ),
      //      )
    yield ()

  // private def runInitialHealthChecks()(using clickHouseClient: ClickHouseClient[IO], logger: Logger[IO]) =
  //   val healthChecks = Vector(
  //     "ClickHouse" -> clickHouseClient.healthCheck,
  //   )

  //   healthChecks
  //     .traverse: (name, check) =>
  //       check.attempt.flatMap:
  //         case Right(_) => info"$name is healthy"
  //         case Left(e)  =>
  //           logger.error(e)(s"$name is unhealthy, check your connection. Stopping the app") *> IO.raiseError(e)
  //     .void
