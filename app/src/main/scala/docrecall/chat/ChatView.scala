package docrecall
package chat

import scalatags.Text.all.*
import scalatags.Text.tags2.article
import scala.concurrent.duration.{span as _, *}

object ChatView extends HtmxView:
  private val messagesViewId        = "chat-messages"
  private val eventSourceListenerId = "chat-event-source-listener"

  def view(chatPostUrl: String) =
    div(
      cls := "rounded-box pl-5 md:col-span-2 border border-bg-base-200 shadow-xl",
      div(
        h2(
          cls := "text-sm text-center font-bold pt-2 tracking-widest",
          "Workbench",
        ),
      ),
      messages(),
      chatForm(postUrl = chatPostUrl),
    )

  def chatForm(
    postUrl: String,
  ) =
    form(
      cls         := "mr-5 pb-5",
      `hx-post`   := postUrl,
      `hx-target` := s"#$messagesViewId",
      `hx-swap`   := "beforeend scroll:bottom",
    )(
      div(
        input(
          cls         := "input input-bordered w-full p-5",
          `type`      := "text",
          name        := "content",
          placeholder := "Type a query to the chatbot",
        ),
      ),
    )

  def messages()                     =
    div(
      cls := "py-5 pr-5 md:h-[calc(100dvh-16rem)] md:overflow-y-scroll",
      id  := messagesViewId,
    )(
      div(
        cls := "chat chat-start",
        div(cls := "chat-bubble", "Hello, how can I help you?"),
      ),
    )

  def responseMessage(
    queryId: QueryId,
    query: ChatQuery,
    sseUrl: String,
    queryCloseEvent: String,
    queryResponseEvent: String*,
  ) =
    val chatBubbleId = s"chat-bubble-$queryId"

    div(
      div(
        cls           := "chat chat-end",
        div(cls := "chat-bubble chat-bubble-primary", query.content),
      ),
      div(
        id            := eventSourceListenerId,
        `hx-ext`      := "sse",
        `sse-connect` := sseUrl,
        `sse-swap`    := queryResponseEvent.mkString(","),
        `sse-close`   := queryCloseEvent,
        `hx-swap`     := "beforeend scroll:bottom",
        `hx-target`   := s"#$chatBubbleId",
      ),
      div(
        cls           := "chat chat-start",
        div(
          cls := "chat-bubble",
          id  := chatBubbleId,
        )(),
      ),
    )

  def responseClearEventSourceListener() =
    // clear the event source listener, ensuring that the browser won't be reconnecting in case of any issues
    // sse-close is not always enough
    div(id := eventSourceListenerId, `hx-swap-oob` := "true")

  def responseRetrievalChunk(
    metadata: RetrievalMetadata,
  ) =
    div(
      cls := "pb-5",
      h3(
        // cls := "",
        "Document fragments:",
      ),
      ul(
        metadata.retrievedEmbeddings.toVector.map: (docId, retrieved) =>
          li(
            cls := "py-2",
            div(
              cls := "bg-base-200 collapse",
              input(`type` := "checkbox", cls := "peer w-full h-full"),
              article(
                cls        := "collapse-title bg-primary text-primary-content peer-checked:bg-base-200",
                docId.toString,
              ),
              ul(
                cls        := "collapse-content bg-primary text-primary-content peer-checked:bg-base-200",
                retrieved.map: embedding =>
                  li(
                    cls := "text-sm tracking-widest",
                    div(cls := "divider"),
                    p(
                      span(cls := "font-bold", "Index: "),
                      span(embedding.fragmentIndex.toString),
                    ),
                    p(
                      span(cls := "font-bold", "Matched Index: "),
                      span(embedding.matchedFragmentIndex.toString),
                    ),
                    p(
                      span(cls := "font-bold", "RRF Score: "),
                      span(embedding.rrfScore.toString),
                    ),
                    p(
                      span(cls := "font-bold", "Semantic Score: "),
                      span(embedding.semanticScore.toString),
                    ),
                    p(
                      span(cls := "font-bold", "Full Text Score: "),
                      span(embedding.fullTextScore.toString),
                    ),
                    p(
                      span(cls := "font-bold", "Chunk: "),
                      span(sanitizeChunk(embedding.chunk.text)),
                    ),
                  ),
              ),
            ),
          ),
      ),
    )
  def responseChunk(content: String) =
    span(sanitizeChunk(content))

  private def sanitizeChunk(input: String) =
    // TODO: this could be potentially dangerous, use a proper HTML sanitizer
    raw(
      input
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#x27;")
        .replaceAll("/", "&#x2F;")
        .replaceAll("\n", br().render),
    )
