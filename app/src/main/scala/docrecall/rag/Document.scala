package docrecall
package rag

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

object Document:
  final case class Ingested(
    info: Info,
    fragments: Vector[Fragment],
  )

  final case class Info(
    id: DocumentId,
    contextId: ContextId,
    name: DocumentName,
    description: String,
    `type`: String,
    metadata: Metadata,
  )

  // aka Document Token
  final case class Fragment(
    index: Long,
    chunk: Chunk,
  )
