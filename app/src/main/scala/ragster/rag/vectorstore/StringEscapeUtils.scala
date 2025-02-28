package docrecall
package rag
package vectorstore

private[vectorstore] object StringEscapeUtils:
  def toTantivySearchString(str: String): String =
    val sb = StringBuilder()

    str.foreach: c =>
      staticEscapeMappings.get(c) match
        case Some(escaped) => sb.append(escaped)
        case None          => sb.append(c)

    sb.result()

  // see: https://quickwit.io/docs/reference/query-language#escaping-special-characters
  lazy val staticEscapeMappings = Map(
    '+'  -> "\\+",
    '\'' -> "\\'",
    '^'  -> "\\^",
    ':'  -> "\\:",
    '{'  -> "\\{",
    '}'  -> "\\}",
    '"'  -> "\\\"",
    '['  -> "\\[",
    ']'  -> "\\]",
    '('  -> "\\(",
    ')'  -> "\\)",
    '<'  -> "\\<",
    '>'  -> "\\>",
    '~'  -> "\\~",
    '\\' -> "\\\\",
    '!'  -> "\\!",
    '*'  -> "\\*",
    ' '  -> "\\ ",
  )
