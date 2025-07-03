package tylang.lexer

import tylang.SourceLocation

sealed trait Token {
  def location: SourceLocation
  def text: String
}

object Token {
  // Literals
  case class IntLiteral(value: Int, location: SourceLocation, text: String) extends Token
  case class DoubleLiteral(value: Double, location: SourceLocation, text: String) extends Token
  case class StringLiteral(value: String, location: SourceLocation, text: String) extends Token
  case class BooleanLiteral(value: Boolean, location: SourceLocation, text: String) extends Token
  
  // Identifiers
  case class Identifier(name: String, location: SourceLocation, text: String) extends Token
  
  // Keywords
  case class Keyword(name: String, location: SourceLocation, text: String) extends Token
  
  // Operators
  case class Operator(symbol: String, location: SourceLocation, text: String) extends Token
  
  // Delimiters
  case class Delimiter(symbol: String, location: SourceLocation, text: String) extends Token
  
  // Special tokens
  case class Newline(location: SourceLocation, text: String) extends Token
  case class Whitespace(location: SourceLocation, text: String) extends Token
  case class Comment(content: String, location: SourceLocation, text: String) extends Token
  case class EOF(location: SourceLocation) extends Token { val text = "" }
  
  // Keywords set
  val keywords = Set(
    "fun", "class", "trait", "object", "val", "var", "def", "extension",
    "if", "else", "while", "for", "match", "case", "try", "catch", "finally",
    "import", "package", "extends", "with", "override", "abstract", "final",
    "private", "protected", "public", "sealed", "implicit", "explicit",
    "true", "false", "null", "this", "super", "new", "return", "throw",
    "Int", "Double", "String", "Boolean", "Unit", "Any", "AnyRef", "Nothing"
  )
  
  // Operators
  val operators = Set(
    "+", "-", "*", "/", "%", "**",
    "==", "!=", "<", ">", "<=", ">=",
    "&&", "||", "!",
    "=", "+=", "-=", "*=", "/=", "%=",
    "=>", "->", "<-", "<:",  ">:",
    ".", "::", ":::", "++", "--"
  )
  
  // Delimiters
  val delimiters = Set(
    "(", ")", "[", "]", "{", "}", ",", ";", ":", "_"
  )
}