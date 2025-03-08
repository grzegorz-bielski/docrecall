package docrecall
package home

import org.http4s.{scalatags as _, h2 as _, *}
import cats.effect.*
import cats.syntax.all.*
import scalatags.Text.all.*
import scalatags.Text.TypedTag
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.syntax.*

import docrecall.postgres.*

class HomeController(using
  logger: Logger[IO],
  contextWriteService: ContextWriteService,
  sessionResource: SessionResource,
  appConfig: AppConfig,
) extends TopLevelHtmxController:
  def prefix = "/"
  def routes = IO:
    HttpRoutes.of[IO]:
      case GET -> Root =>
        sessionResource.useGiven:
          for
            context <- contextWriteService.defaultContext

            response = Response[IO]()
                         .withStatus(Status.Found)
                         .withHeaders(Location(Uri.unsafeFromString(s"/$prefix/${context.id}")))
          yield response
