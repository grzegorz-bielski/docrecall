package docrecall
package home

import org.http4s.{scalatags as _, h2 as _, *}
import cats.effect.*
import cats.syntax.all.*
import scalatags.Text.all.*
import scalatags.Text.TypedTag
import org.http4s.headers.Location
import org.http4s.implicits.*

class HomeController(using AppConfig) extends TopLevelHtmxController:
  def prefix = "/"
  def routes = IO:
    HttpRoutes.of[IO]:
      case GET -> Root =>
        Response[IO]()
          .withStatus(Status.Found)
          .withHeaders(Location(uri"/contexts"))
          .pure[IO]
          
