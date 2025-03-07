package docrecall

import scalatags.Text.all.*
import scalatags.Text.tags2.{title as headTitle, details, summary}

import docrecall.viewpartials.*

import docrecall.common.*

object RootLayoutView extends HtmxView:
  private lazy val drawerId = "navbar-drawer"

  def view(contexts: Vector[ContextInfo], contextUrl: ContextId => String, children: scalatags.Text.Modifier*)(using
    AppConfig,
  ) =
    val headContent =
      Vector(
        headTitle("docrecall"),
        meta(charset := "UTF-8"),
        meta(
          name       := "viewport",
          content    := "width=device-width, initial-scale=1.0",
        ),
        // tailwind-generated styles
        link(
          rel        := "stylesheet",
          href       := "/static/generated.css",
        ),
        script(
          src        := "/static/bundle.js",
          defer,
        ),
      ) ++ devSetup

    doctype("html")(
      html(
        data("theme") := "cmyk",
        lang          := "en",
        head(headContent*),
        body(
          cls := "min-h-screen mx-auto",
        )(
          div(
            cls := "drawer lg:drawer-open",
          )(
            input(
              id     := drawerId,
              `type` := "checkbox",
              cls    := "drawer-toggle",
            ),
            div(
              cls    := "drawer-content",
              navbar,
              children,
            ),
            sidebar(contexts, contextUrl),
          ),
        ),
      ),
    )

  private def devSetup(using appConfig: AppConfig): Vector[Modifier] =
    if appConfig.isDev then
      // this is so we can add new tailwind classes directly in the browser during development
      Vector(
        // link(
        //   rel  := "stylesheet",
        //   href := "https://cdn.jsdelivr.net/npm/daisyui@4.12.14/dist/full.min.css",
        // ),
        // script(
        //   src  := "https://cdn.tailwindcss.com",
        // ),
      )
    else Vector.empty

  lazy val appName =
    h3(
      cls := "text-2xl font-bold",
      "docrecall 📄",
    )

  lazy val navbar =
    div(
      cls := "navbar bg-neutral text-neutral-content",
      div(
        cls := "lg:hidden flex items-center",
        label(
          `for` := drawerId,
          cls   := "drawer-button btn btn-square btn-ghost",
          IconsView.menuIcon(),
        ),
        appName,
      ),
    )

  def sidebar(
    contexts: Vector[ContextInfo],
    contextUrl: ContextId => String,
  ) =
    div(
      cls := "drawer-side",
      label(
        `for`              := drawerId,
        cls                := "drawer-overlay",
        attr("aria-label") := "close sidebar",
      ),
      div(
      ),
      div(
        div(
          cls := "navbar bg-neutral text-neutral-content",
          div(
            cls := "hidden lg:block",
            appName,
          ),
        ),
        ul(
          cls := "menu bg-base-200 text-base-content w-80 p-4 min-h-[calc(100vh-4rem)]" // 4rem is the height of the navbar,
        )(
          li(
            cls := "menu-title",
            "Contexts",
          ),
          ul(
            contexts.map: context =>
              li(
                appLink(
                  path  = contextUrl(context.id),
                  child = context.name,
                ),
              ),
          ),
          li(
            cls := "mt-auto",
            appLink(
              path  = "/contexts/new",
              child = "Create new",
              attrs = 
                cls := "btn btn-primary",
            ),
          )
        ),
      ),
    )
