package ragster

import cats.effect.*
import cats.syntax.all.*
import dumbo.*
import org.typelevel.otel4s.trace.Tracer.Implicits.noop

object Migrations:
  // https://github.com/rolang/dumbo
  def run: IO[Unit] =
    Dumbo
      .withResourcesIn[IO]("db/migration")
      .apply(
        ConnectionConfig(
          host = "localhost",
          port = 5432,
          user = "user",
          password = "password".some,
          database = "ragster",
          ssl = ConnectionConfig.SSL.None,
        ),
      )
      .runMigration
      .flatMap: result =>
        IO.println(s"Migration result: $result")
