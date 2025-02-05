package ragster
package inference

import cats.effect.*
import sttp.openai.OpenAI
import sttp.model.Uri.*

import ragster.chat.*
import ragster.rag.*

trait InferenceModule[F[_]]:
  def chatCompletionService: ChatCompletionService[F]
  def embeddingService: EmbeddingService[F]

object InferenceModule:
  def of(using AppConfig, SttpBackend): InferenceModule[IO]  =
    AppConfig.get.inferenceEngine match
      case InferenceEngine.OpenAIOllama(url) =>
        given OpenAI = OpenAI("ollama", uri"$url")

        new InferenceModule[IO]:
          def chatCompletionService = SttpOpenAIChatCompletionService()
          def embeddingService      = SttpOpenAIEmbeddingService()
