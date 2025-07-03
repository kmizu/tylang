package tylang.lexer

import tylang.{SourceLocation, ParseException}
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

class Lexer(input: String, filename: String = "<input>") {
  private val lines = input.split('\n')
  private var pos = 0
  private var line = 1
  private var col = 1
  
  private def currentChar: Char = 
    if (pos >= input.length) '\u0000' else input(pos)
  
  private def peek(offset: Int = 1): Char = 
    if (pos + offset >= input.length) '\u0000' else input(pos + offset)
  
  private def advance(): Unit = {
    if (pos < input.length && input(pos) == '\n') {
      line += 1
      col = 1
    } else {
      col += 1
    }
    pos += 1
  }
  
  private def currentLocation: SourceLocation = {
    val currentLine = if (line <= lines.length) lines(line - 1) else ""
    SourceLocation(filename, (line, col), currentLine)
  }
  
  private def skipWhitespace(): Unit = {
    while (currentChar.isWhitespace && currentChar != '\n') {
      advance()
    }
  }
  
  private def readNumber(): Token = {
    val start = pos
    val startLocation = currentLocation
    val sb = new StringBuilder
    
    while (currentChar.isDigit) {
      sb.append(currentChar)
      advance()
    }
    
    if (currentChar == '.' && peek().isDigit) {
      sb.append(currentChar)
      advance()
      while (currentChar.isDigit) {
        sb.append(currentChar)
        advance()
      }
      val text = sb.toString()
      Token.DoubleLiteral(text.toDouble, startLocation, text)
    } else {
      val text = sb.toString()
      Token.IntLiteral(text.toInt, startLocation, text)
    }
  }
  
  private def readString(): Token = {
    val startLocation = currentLocation
    val sb = new StringBuilder
    advance() // Skip opening quote
    
    while (currentChar != '"' && currentChar != '\u0000') {
      if (currentChar == '\\') {
        advance()
        currentChar match {
          case 'n' => sb.append('\n')
          case 't' => sb.append('\t')
          case 'r' => sb.append('\r')
          case '\\' => sb.append('\\')
          case '"' => sb.append('"')
          case c => sb.append(c)
        }
      } else {
        sb.append(currentChar)
      }
      advance()
    }
    
    if (currentChar == '"') {
      advance() // Skip closing quote
      val value = sb.toString()
      val rawText = input.substring(startLocation.position._2 - 1, pos)
      Token.StringLiteral(value, startLocation, rawText)
    } else {
      throw ParseException("Unterminated string literal", startLocation)
    }
  }
  
  private def readIdentifier(): Token = {
    val startLocation = currentLocation
    val sb = new StringBuilder
    
    while (currentChar.isLetterOrDigit || currentChar == '_') {
      sb.append(currentChar)
      advance()
    }
    
    val text = sb.toString()
    if (Token.keywords.contains(text)) {
      if (text == "true") Token.BooleanLiteral(true, startLocation, text)
      else if (text == "false") Token.BooleanLiteral(false, startLocation, text)
      else Token.Keyword(text, startLocation, text)
    } else {
      Token.Identifier(text, startLocation, text)
    }
  }
  
  private def readOperator(): Token = {
    val startLocation = currentLocation
    val sb = new StringBuilder
    
    // Try to match longest operator first
    val candidates = Token.operators.filter(_.startsWith(currentChar.toString))
    var matched = ""
    
    candidates.toList.sortBy(-_.length).foreach { op =>
      if (matched.isEmpty && pos + op.length <= input.length && 
          input.substring(pos, pos + op.length) == op) {
        matched = op
      }
    }
    
    if (matched.nonEmpty) {
      matched.foreach(_ => advance())
      Token.Operator(matched, startLocation, matched)
    } else {
      val char = currentChar.toString
      advance()
      Token.Operator(char, startLocation, char)
    }
  }
  
  private def readLineComment(): Token = {
    val startLocation = currentLocation
    val sb = new StringBuilder
    advance() // Skip first /
    advance() // Skip second /
    
    while (currentChar != '\n' && currentChar != '\u0000') {
      sb.append(currentChar)
      advance()
    }
    
    Token.Comment(sb.toString(), startLocation, s"//${sb.toString()}")
  }
  
  private def readBlockComment(): Token = {
    val startLocation = currentLocation
    val sb = new StringBuilder
    advance() // Skip /
    advance() // Skip *
    
    var done = false
    while (!done) {
      if (currentChar == '*' && peek() == '/') {
        advance() // Skip *
        advance() // Skip /
        done = true
      } else if (currentChar == '\u0000') {
        throw ParseException("Unterminated block comment", startLocation)
      } else {
        sb.append(currentChar)
        advance()
      }
    }
    
    Token.Comment(sb.toString(), startLocation, s"/*${sb.toString()}*/")
  }
  
  def nextToken(): Token = {
    skipWhitespace()
    
    if (currentChar == '\u0000') {
      return Token.EOF(currentLocation)
    }
    
    currentChar match {
      case '\n' =>
        val location = currentLocation
        advance()
        Token.Newline(location, "\n")
        
      case c if c.isDigit =>
        readNumber()
        
      case '"' =>
        readString()
        
      case c if c.isLetter || c == '_' =>
        readIdentifier()
        
      case '/' if peek() == '/' =>
        readLineComment()
        
      case '/' if peek() == '*' =>
        readBlockComment()
        
      case c if Token.delimiters.contains(c.toString) =>
        val location = currentLocation
        val text = c.toString
        advance()
        Token.Delimiter(text, location, text)
        
      case _ =>
        readOperator()
    }
  }
  
  def tokenize(): List[Token] = {
    val tokens = ListBuffer[Token]()
    
    var done = false
    while (!done) {
      val token = nextToken()
      tokens += token
      if (token.isInstanceOf[Token.EOF]) done = true
    }
    
    tokens.toList
  }
}