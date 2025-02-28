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
    *
    * @param chunk
    *   The chunk of text that was embedded
    * @param value
    *   The embedding vector
    * @param documentId
    *   The document ID that the chunk belongs to
    * @param contextId
    *   The context ID that the chunk belongs to
    * @param fragmentIndex
    *   The fragment index that the chunk belongs to
    * @param matchedFragmentIndex
    *   The fragment index that was matched on in the the query. Could be different from the fragment index of the
    *   chunk, due to surrounding chunk lookup.
    * @param semanticScore
    *   The cosine distance from the dense embeddings vector search. 0 - Exact match, 1 - No correlation, 2 - Opposite
    *   correlation. `Cosine Distance` = 1 — `Cosine Similarity`
    * @param fullTextScore
    *   The BM25 score of the match from the full text search.
    * @param rrfScore
    *   The RRF hybrid score of the match
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
