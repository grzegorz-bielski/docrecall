package docrecall

import cats.effect.*
import cats.effect.std.*
import cats.syntax.all.*
import scribe.Level
import com.comcast.ip4s.*

final case class AppConfig(
  host: Host,
  port: Port,
  env: EnvType,
  logLevel: Level,
  logPath: Option[String],
  loadFixtures: Boolean,
  maxEntitySizeInBytes: Long,
  inferenceEngine: InferenceEngine,
  postgres: PostgresConfig,
  slack: SlackBotConfig,
):
  def isDev = env == EnvType.Local

object AppConfig:
  inline def get(using appConfig: AppConfig): AppConfig = appConfig

  def load: IO[AppConfig] =
    for
      env                <- Env[IO].get("ENV").map(_.flatMap(EnvType.fromString).getOrElse(EnvType.Local))
      logLevel           <- Env[IO].get("LOG_LEVEL").map(_.flatMap(Level.get).getOrElse(Level.Info))
      path               <- Env[IO].get("LOG_PATH")
      slackSigningSecret <- Env[IO].get("SLACK_SIGNING_SECRET").map(_.getOrElse("<not_provided>"))
    yield AppConfig(
      host = ipv4"0.0.0.0",
      port = port"8081",
      env = env,
      logLevel = logLevel,
      logPath = path,
      loadFixtures = true,
      maxEntitySizeInBytes = 1073741824L, // 1GiB
      inferenceEngine = InferenceEngine.OpenAIProtocolLike(
        url = "http://localhost:4000", // litellm
        authToken = None,
      ),
      postgres = PostgresConfig(
        host = "localhost",
        port = 5432,
        username = "user",
        password = "password",
        database = "docrecall",
        maxConcurrentSessions = 10,
      ),
      slack = SlackBotConfig(
        signingSecret = slackSigningSecret,
      ),
    )

enum EnvType:
  case Local, Prod

object EnvType:
  def fromString(str: String): Option[EnvType] =
    EnvType.values.find(_.toString.equalsIgnoreCase(str))

enum InferenceEngine:
  case OpenAIProtocolLike(
    url: String,
    authToken: Option[String] = None,
  )

final case class PostgresConfig(
  host: String,
  port: Int,
  username: String,
  password: String,
  database: String,
  maxConcurrentSessions: Int,
)

final case class SlackBotConfig(
  signingSecret: String,
  maxConcurrentSessions: Int = 10,
  maxSessions: Int = 100,
)
