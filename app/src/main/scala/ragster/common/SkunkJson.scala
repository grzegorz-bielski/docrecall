package ragster
package common

import com.github.plokhotnyuk.jsoniter_scala.core.*
import skunk.*
import skunk.data.Type

/** skunk and jsoniter-scala integration layer
  */
object SkunkJson:
  /** Construct a codec for `A`, coded as Json, mapped to the `json` schema type. */
  def json[A: JsonValueCodec]: Codec[A] = codecOf[A](Type.json)

  /** Construct a codec for `A`, coded as Json, mapped to the `jsonb` schema type. */
  def jsonb[A: JsonValueCodec]: Codec[A] = codecOf[A](Type.jsonb)

  private def codecOf[A: JsonValueCodec](oid: Type): Codec[A] =
    Codec.simple(
      encode = _.asJsonUnsafe(),
      decode = _.parseToJson,
      oid = oid,
    )
