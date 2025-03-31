package docrecall
package inference

import cats.effect.*
import sttp.openai.OpenAI
import sttp.model.Uri.*

import docrecall.chat.*
import docrecall.rag.*

trait InferenceModule[F[_]]:
  def chatCompletionService: ChatCompletionService[F]
  def embeddingService: EmbeddingService[F]

object InferenceModule:
  def of(using AppConfig, SttpBackend): InferenceModule[IO]  =
    AppConfig.get.inferenceEngine match
      case InferenceEngine.OpenAIProtocolLike(url, authToken) =>
        given OpenAI = OpenAI(authToken.getOrElse("docrecall"), uri"$url")

        new InferenceModule[IO]:
          def chatCompletionService = SttpOpenAIChatCompletionService()
          def embeddingService      = SttpOpenAIEmbeddingService()
