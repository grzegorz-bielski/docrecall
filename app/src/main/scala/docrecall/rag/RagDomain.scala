package docrecall

import java.util.UUID
import cats.effect.IO
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import skunk.codec.all.*
import skunk.*
import cats.instances.bigInt
import unindent.*

import docrecall.common.SkunkJson.*

opaque type DocumentName <: String = String
object DocumentName:
  inline def apply(value: String): DocumentName = value

  given JsonValueCodec[DocumentName] = JsonCodecMaker.make
  given pgCodec: Codec[DocumentName] = text

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

  override def toString: String = 
    i"""
    Chunk(
      text:
        ```````
        $text
        ```````,
      index: $index,
      metadata: $metadata,
    )
    """

opaque type Metadata = Map[String, String]
object Metadata:
  def empty: Metadata = Map.empty

  given JsonValueCodec[Metadata] = JsonCodecMaker.make
  given pgCodec: Codec[Metadata] = jsonb

extension (underlying: Metadata)
  def toEmbeddingInput: String =
    underlying.map((k, v) => s"$k: $v").mkString("\n")
