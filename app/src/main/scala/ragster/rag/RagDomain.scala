package ragster

import java.util.UUID
import cats.effect.IO
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import skunk.codec.all.*
import skunk.*
import cats.instances.bigInt

import ragster.common.SkunkJson.*

opaque type DocumentName <: String = String
object DocumentName:
  inline def apply(value: String): DocumentName = value

  given JsonValueCodec[DocumentName] = JsonCodecMaker.make
  given pgCodec: Codec[DocumentName] = text

opaque type DocumentVersion <: Int = Int
object DocumentVersion:
  inline def apply(value: Int): DocumentVersion = value

  given JsonValueCodec[DocumentVersion] = JsonCodecMaker.make
  given pgCodec: Codec[DocumentVersion] = int4

extension (underlying: DocumentVersion) def next: DocumentVersion = DocumentVersion(underlying + 1)

opaque type DocumentId = UUID
object DocumentId:
  inline def apply(value: UUID): DocumentId = value
  def of: IO[DocumentId]                    = IO.randomUUID

  given pgCodec: Codec[DocumentId] = uuid

  given JsonValueCodec[DocumentId] = JsonCodecMaker.make

final case class Chunk(
  text: String,
  index: Long,
  metadata: Metadata = Metadata.empty,
):
  def toEmbeddingInput: String =
    metadata.toEmbeddingInput ++ s"\n$text"

opaque type Metadata = Map[String, String]
object Metadata:
  def empty: Metadata = Map.empty

  given JsonValueCodec[Metadata] = JsonCodecMaker.make
  given pgCodec: Codec[Metadata] = jsonb

extension (underlying: Metadata)
  def toEmbeddingInput: String =
    underlying.map((k, v) => s"$k: $v").mkString("\n")
