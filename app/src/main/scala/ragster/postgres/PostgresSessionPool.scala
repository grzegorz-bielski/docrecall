package ragster
package postgres

import cats.effect.*
import cats.syntax.all.*
import skunk.*
import org.typelevel.otel4s.trace.Tracer.Implicits.noop

type SessionResource = Resource[IO, Session[IO]]

object PostgresSessionPool:
  def of(using appConfig: AppConfig): SessionPool[IO] =
    val config = appConfig.postgres

    Session
      .pooled[IO](
        host = config.host,
        port = config.port,
        user = config.username,
        password = config.password.some,
        database = config.database,
        max = config.maxConcurrentSessions,
      )
