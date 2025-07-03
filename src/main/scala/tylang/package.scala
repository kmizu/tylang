package object tylang {
  // Common types and utilities
  type Position = (Int, Int) // (line, column)
  
  case class SourceLocation(
    filename: String,
    position: Position,
    line: String
  )
  
  // Exception types
  class TyLangException(message: String, cause: Throwable = null) extends Exception(message, cause)
  class ParseException(message: String, location: SourceLocation) extends TyLangException(s"Parse error at ${location.filename}:${location.position._1}:${location.position._2}: $message")
  class TypeException(message: String, location: SourceLocation) extends TyLangException(s"Type error at ${location.filename}:${location.position._1}:${location.position._2}: $message")
  class CompileException(message: String, location: SourceLocation) extends TyLangException(s"Compile error at ${location.filename}:${location.position._1}:${location.position._2}: $message")
  
  // Language version
  val VERSION = "0.1.0-SNAPSHOT"
}