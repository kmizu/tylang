package tylang.lexer

import munit.FunSuite
import tylang.SourceLocation

class LexerTest extends FunSuite {
  
  def testLocation(line: Int, col: Int, text: String = ""): SourceLocation = 
    SourceLocation("<test>", (line, col), text)
  
  test("tokenize integer literals") {
    val lexer = new Lexer("123 456", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 3) // 123, 456, EOF
    
    tokens(0) match {
      case Token.IntLiteral(value, _, text) =>
        assertEquals(value, 123)
        assertEquals(text, "123")
      case _ => fail("Expected IntLiteral")
    }
    
    tokens(1) match {
      case Token.IntLiteral(value, _, text) =>
        assertEquals(value, 456)
        assertEquals(text, "456")
      case _ => fail("Expected IntLiteral")
    }
    
    assert(tokens(2).isInstanceOf[Token.EOF])
  }
  
  test("tokenize double literals") {
    val lexer = new Lexer("3.14 0.5", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 3) // 3.14, 0.5, EOF
    
    tokens(0) match {
      case Token.DoubleLiteral(value, _, text) =>
        assertEquals(value, 3.14)
        assertEquals(text, "3.14")
      case _ => fail("Expected DoubleLiteral")
    }
    
    tokens(1) match {
      case Token.DoubleLiteral(value, _, text) =>
        assertEquals(value, 0.5)
        assertEquals(text, "0.5")
      case _ => fail("Expected DoubleLiteral")
    }
  }
  
  test("tokenize string literals") {
    val lexer = new Lexer("\"hello\" \"world\\n\"", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 3) // "hello", "world\n", EOF
    
    tokens(0) match {
      case Token.StringLiteral(value, _, text) =>
        assertEquals(value, "hello")
        assertEquals(text, "\"hello\"")
      case _ => fail("Expected StringLiteral")
    }
    
    tokens(1) match {
      case Token.StringLiteral(value, _, text) =>
        assertEquals(value, "world\n")
        assertEquals(text, "\"world\\n\"")
      case _ => fail("Expected StringLiteral")
    }
  }
  
  test("tokenize boolean literals") {
    val lexer = new Lexer("true false", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 3) // true, false, EOF
    
    tokens(0) match {
      case Token.BooleanLiteral(value, _, text) =>
        assertEquals(value, true)
        assertEquals(text, "true")
      case _ => fail("Expected BooleanLiteral")
    }
    
    tokens(1) match {
      case Token.BooleanLiteral(value, _, text) =>
        assertEquals(value, false)
        assertEquals(text, "false")
      case _ => fail("Expected BooleanLiteral")
    }
  }
  
  test("tokenize identifiers") {
    val lexer = new Lexer("foo bar_baz test123", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 4) // foo, bar_baz, test123, EOF
    
    tokens(0) match {
      case Token.Identifier(name, _, text) =>
        assertEquals(name, "foo")
        assertEquals(text, "foo")
      case _ => fail("Expected Identifier")
    }
    
    tokens(1) match {
      case Token.Identifier(name, _, text) =>
        assertEquals(name, "bar_baz")
        assertEquals(text, "bar_baz")
      case _ => fail("Expected Identifier")
    }
    
    tokens(2) match {
      case Token.Identifier(name, _, text) =>
        assertEquals(name, "test123")
        assertEquals(text, "test123")
      case _ => fail("Expected Identifier")
    }
  }
  
  test("tokenize keywords") {
    val lexer = new Lexer("fun class if else", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 5) // fun, class, if, else, EOF
    
    tokens(0) match {
      case Token.Keyword(name, _, text) =>
        assertEquals(name, "fun")
        assertEquals(text, "fun")
      case _ => fail("Expected Keyword")
    }
    
    tokens(1) match {
      case Token.Keyword(name, _, text) =>
        assertEquals(name, "class")
        assertEquals(text, "class")
      case _ => fail("Expected Keyword")
    }
    
    tokens(2) match {
      case Token.Keyword(name, _, text) =>
        assertEquals(name, "if")
        assertEquals(text, "if")
      case _ => fail("Expected Keyword")
    }
    
    tokens(3) match {
      case Token.Keyword(name, _, text) =>
        assertEquals(name, "else")
        assertEquals(text, "else")
      case _ => fail("Expected Keyword")
    }
  }
  
  test("tokenize operators") {
    val lexer = new Lexer("+ - * / == != <= >=", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 9) // 8 operators + EOF
    
    val expectedOps = List("+", "-", "*", "/", "==", "!=", "<=", ">=")
    
    expectedOps.zipWithIndex.foreach { case (expectedOp, index) =>
      tokens(index) match {
        case Token.Operator(symbol, _, text) =>
          assertEquals(symbol, expectedOp)
          assertEquals(text, expectedOp)
        case _ => fail(s"Expected Operator '$expectedOp' at index $index")
      }
    }
  }
  
  test("tokenize delimiters") {
    val lexer = new Lexer("( ) [ ] { } , ; :", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 10) // 9 delimiters + EOF
    
    val expectedDelims = List("(", ")", "[", "]", "{", "}", ",", ";", ":")
    
    expectedDelims.zipWithIndex.foreach { case (expectedDelim, index) =>
      tokens(index) match {
        case Token.Delimiter(symbol, _, text) =>
          assertEquals(symbol, expectedDelim)
          assertEquals(text, expectedDelim)
        case _ => fail(s"Expected Delimiter '$expectedDelim' at index $index")
      }
    }
  }
  
  test("tokenize line comments") {
    val lexer = new Lexer("// this is a comment\n123", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 4) // comment, newline, 123, EOF
    
    tokens(0) match {
      case Token.Comment(content, _, text) =>
        assertEquals(content, " this is a comment")
        assertEquals(text, "// this is a comment")
      case _ => fail("Expected Comment")
    }
    
    tokens(1) match {
      case Token.Newline(_, text) =>
        assertEquals(text, "\n")
      case _ => fail("Expected Newline")
    }
    
    tokens(2) match {
      case Token.IntLiteral(value, _, _) =>
        assertEquals(value, 123)
      case _ => fail("Expected IntLiteral")
    }
  }
  
  test("tokenize block comments") {
    val lexer = new Lexer("/* this is a\n   block comment */\n456", "<test>")
    val tokens = lexer.tokenize()
    
    assertEquals(tokens.length, 4) // comment, newline, 456, EOF
    
    tokens(0) match {
      case Token.Comment(content, _, text) =>
        assertEquals(content, " this is a\n   block comment ")
        assertEquals(text, "/* this is a\n   block comment */")
      case _ => fail("Expected Comment")
    }
    
    tokens(2) match {
      case Token.IntLiteral(value, _, _) =>
        assertEquals(value, 456)
      case _ => fail("Expected IntLiteral")
    }
  }
  
  test("tokenize complex expression") {
    val lexer = new Lexer("fun add(x: Int, y: Int): Int { x + y }", "<test>")
    val tokens = lexer.tokenize()
    
    val expectedTokens = List(
      "fun", "add", "(", "x", ":", "Int", ",", "y", ":", "Int", ")", ":", "Int", "{", "x", "+", "y", "}"
    )
    
    // Filter out whitespace and comments for easier testing
    val filteredTokens = tokens.filterNot(t => t.isInstanceOf[Token.Whitespace] || t.isInstanceOf[Token.Comment] || t.isInstanceOf[Token.EOF])
    
    expectedTokens.zipWithIndex.foreach { case (expected, index) =>
      val token = filteredTokens(index)
      val actualText = token.text
      assertEquals(actualText, expected, s"Token at index $index should be '$expected' but was '$actualText'")
    }
  }
  
  test("handle newlines correctly") {
    val lexer = new Lexer("foo\nbar\n\nbaz", "<test>")
    val tokens = lexer.tokenize()
    
    val filteredTokens = tokens.filterNot(t => t.isInstanceOf[Token.Whitespace] || t.isInstanceOf[Token.Comment] || t.isInstanceOf[Token.EOF])
    
    // Should have: foo, newline, bar, newline, newline, baz
    assertEquals(filteredTokens.length, 6)
    
    // Check that line positions are tracked correctly
    filteredTokens(0) match {
      case Token.Identifier(name, location, _) =>
        assertEquals(name, "foo")
        assertEquals(location.position._1, 1) // line 1
      case _ => fail("Expected Identifier 'foo'")
    }
    
    filteredTokens(2) match {
      case Token.Identifier(name, location, _) =>
        assertEquals(name, "bar")
        assertEquals(location.position._1, 2) // line 2
      case _ => fail("Expected Identifier 'bar'")
    }
    
    filteredTokens(5) match {
      case Token.Identifier(name, location, _) =>
        assertEquals(name, "baz")
        assertEquals(location.position._1, 4) // line 4
      case _ => fail("Expected Identifier 'baz'")
    }
  }
}