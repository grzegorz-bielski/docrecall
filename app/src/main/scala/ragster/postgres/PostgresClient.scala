package ragster
package postgres

import cats.effect.*
import cats.syntax.all.*
import skunk.*
import org.typelevel.otel4s.trace.Tracer.Implicits.noop

trait PostgresClient[F[_]]:
  def withSession[A](fn: Session[F] => F[A]): F[A]

object PostgresClient:
  def of(using appConfig: AppConfig): Resource[IO, PostgresClient[IO]] =
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
      .map: poolResource => 
       new PostgresClient[IO]:
          def withSession[A](fn: Session[IO] => IO[A]): IO[A] = 
            poolResource.use(fn)
