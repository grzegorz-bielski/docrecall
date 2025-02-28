package ragster
package rag
package vectorstore

import StringEscapeUtils.*

class StringEscapeUtilsSpec extends munit.FunSuite:
  test("`toTantivySearchString` should escape a string with all special characters"):
    staticEscapeMappings.toVector.foreach: (char, expected) =>
      assertEquals(toTantivySearchString(s"$char"), expected)
