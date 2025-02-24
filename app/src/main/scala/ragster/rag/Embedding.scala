package ragster
package rag

import unindent.*

object Embedding:
  /** Embedding to be stored and indexed in the vector store
    */
  final case class Index(
    chunk: Chunk,
    value: Vector[Float],
    documentId: DocumentId,
    contextId: ContextId,
    fragmentIndex: Long,
  )

  /** Embedding retrieved from the vector store
    */
  final case class Retrieved(
    chunk: Chunk,
    value: Vector[Float],
    documentId: DocumentId,
    contextId: ContextId,
    fragmentIndex: Long,
    matchedFragmentIndex: Long,
    semanticScore: Double,
    fullTextScore: Float,
    rrfScore: BigDecimal,
  )

  /** Embedding from the user query
    */
  final case class Query(
    chunk: Chunk,
    contextId: ContextId,
    value: Vector[Float],
  )
