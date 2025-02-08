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
    semanticRank: Double,
    fullTextRank: Double,
    rrfRank: Double,
  ):
    override def toString: String = 
      i"""
      Retrieved(
        chunk: $chunk,
        value: [...],
        documentId: $documentId, 
        contextId: $contextId, 
        fragmentIndex: $fragmentIndex, 
        semanticRank: $semanticRank,
        fullTextRank: $fullTextRank,
        rrfRank: $rrfRank
      """"

  /** Embedding from the user query
    */
  final case class Query(
    chunk: Chunk,
    contextId: ContextId,
    value: Vector[Float],
  )
