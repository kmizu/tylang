package tylang.parser

import tylang.lexer.{Token, Lexer}
import tylang.ast.*
import tylang.{SourceLocation, ParseException}
import scala.collection.mutable.ListBuffer

class Parser(tokens: List[Token]) {
  private var pos = 0
  private val tokenList = tokens.filterNot(_.isInstanceOf[Token.Comment])
  
  private def currentToken: Token = 
    if (pos >= tokenList.length) tokenList.last else tokenList(pos)
  
  private def peek(offset: Int = 1): Token = 
    if (pos + offset >= tokenList.length) tokenList.last else tokenList(pos + offset)
  
  private def advance(): Token = {
    val current = currentToken
    if (pos < tokenList.length - 1) pos += 1
    current
  }
  
  private def peekToken: Option[Token] = {
    if (pos < tokenList.length - 1) Some(tokenList(pos + 1))
    else None
  }
  
  private def expect(predicate: Token => Boolean, message: String): Token = {
    val token = currentToken
    if (predicate(token)) {
      advance()
      token
    } else {
      throw ParseException(s"$message, got ${token.text}", token.location)
    }
  }
  
  private def expectKeyword(keyword: String): Token = 
    expect(
      token => token.isInstanceOf[Token.Keyword] && token.text == keyword,
      s"Expected '$keyword'"
    )
  
  private def expectOperator(operator: String): Token = 
    expect(
      token => token.isInstanceOf[Token.Operator] && token.text == operator,
      s"Expected '$operator'"
    )
  
  private def expectDelimiter(delimiter: String): Token = 
    expect(
      token => token.isInstanceOf[Token.Delimiter] && token.text == delimiter,
      s"Expected '$delimiter'"
    )
  
  private def expectIdentifier(): Token = 
    expect(token => token.isInstanceOf[Token.Identifier], "Expected identifier")
  
  private def isAtEnd: Boolean = currentToken.isInstanceOf[Token.EOF]
  
  private def skipNewlines(): Unit = {
    while (currentToken.isInstanceOf[Token.Newline] && !isAtEnd) {
      advance()
    }
  }
  
  // Main parsing method
  def parseProgram(): Program = {
    val declarations = ListBuffer[Declaration]()
    skipNewlines()
    
    while (!isAtEnd) {
      declarations += parseDeclaration()
      skipNewlines()
    }
    
    Program(declarations.toList, currentToken.location)
  }
  
  // Parse declarations
  private def parseDeclaration(): Declaration = {
    skipNewlines()
    currentToken match {
      case Token.Keyword("fun", location, _) =>
        parseFunctionDeclaration()
      case Token.Keyword("class", location, _) =>
        parseClassDeclaration()
      case Token.Keyword("trait", location, _) =>
        parseTraitDeclaration()
      case Token.Keyword("object", location, _) =>
        parseObjectDeclaration()
      case Token.Keyword("extension", location, _) =>
        parseExtensionDeclaration()
      case _ =>
        throw ParseException("Expected declaration", currentToken.location)
    }
  }
  
  private def parseFunctionDeclaration(): FunctionDeclaration = {
    val startToken = expectKeyword("fun")
    val name = expectIdentifier().text
    
    // Parse type parameters
    val typeParameters = if (currentToken.text == "<") {
      expectOperator("<")
      val params = parseTypeParameterList()
      expectOperator(">")
      params
    } else List.empty
    
    // Parse parameters
    expectDelimiter("(")
    val parameters = parseParameterList()
    expectDelimiter(")")
    
    // Parse return type
    val returnType = if (currentToken.text == ":") {
      expectDelimiter(":")
      Some(parseTypeAnnotation())
    } else None
    
    // Parse body
    val body = parseExpression()
    
    FunctionDeclaration(name, typeParameters, parameters, returnType, body, startToken.location)
  }
  
  private def parseClassDeclaration(): ClassDeclaration = {
    val startToken = expectKeyword("class")
    val name = expectIdentifier().text
    
    // Parse type parameters
    val typeParameters = if (currentToken.text == "<") {
      expectOperator("<")
      val params = parseTypeParameterList()
      expectOperator(">")
      params
    } else List.empty
    
    // Parse superclass and traits
    val superClass = if (currentToken.text == "extends") {
      expectKeyword("extends")
      Some(parseTypeAnnotation())
    } else None
    
    val traits = if (currentToken.text == "with") {
      expectKeyword("with")
      parseTypeAnnotationList()
    } else List.empty
    
    // Parse constructor
    val constructor = if (currentToken.text == "(") {
      expectDelimiter("(")
      val params = parseParameterList()
      expectDelimiter(")")
      Some(Constructor(params, None, currentToken.location))
    } else None
    
    // Parse body
    val members = if (currentToken.text == "{") {
      expectDelimiter("{")
      val memberList = ListBuffer[ClassMember]()
      skipNewlines()
      
      while (currentToken.text != "}") {
        memberList += parseClassMember()
        skipNewlines()
      }
      
      expectDelimiter("}")
      memberList.toList
    } else List.empty
    
    ClassDeclaration(name, typeParameters, superClass, traits, constructor, members, startToken.location)
  }
  
  private def parseTraitDeclaration(): TraitDeclaration = {
    val startToken = expectKeyword("trait")
    val name = expectIdentifier().text
    
    // Parse type parameters
    val typeParameters = if (currentToken.text == "<") {
      expectOperator("<")
      val params = parseTypeParameterList()
      expectOperator(">")
      params
    } else List.empty
    
    // Parse super traits
    val superTraits = if (currentToken.text == "extends") {
      expectKeyword("extends")
      parseTypeAnnotationList()
    } else List.empty
    
    // Parse body
    val members = if (currentToken.text == "{") {
      expectDelimiter("{")
      val memberList = ListBuffer[TraitMember]()
      skipNewlines()
      
      while (currentToken.text != "}") {
        memberList += parseTraitMember()
        skipNewlines()
      }
      
      expectDelimiter("}")
      memberList.toList
    } else List.empty
    
    TraitDeclaration(name, typeParameters, superTraits, members, startToken.location)
  }
  
  private def parseObjectDeclaration(): ObjectDeclaration = {
    val startToken = expectKeyword("object")
    val name = expectIdentifier().text
    
    // Parse superclass and traits
    val superClass = if (currentToken.text == "extends") {
      expectKeyword("extends")
      Some(parseTypeAnnotation())
    } else None
    
    val traits = if (currentToken.text == "with") {
      expectKeyword("with")
      parseTypeAnnotationList()
    } else List.empty
    
    // Parse body
    val members = if (currentToken.text == "{") {
      expectDelimiter("{")
      val memberList = ListBuffer[ClassMember]()
      skipNewlines()
      
      while (currentToken.text != "}") {
        memberList += parseClassMember()
        skipNewlines()
      }
      
      expectDelimiter("}")
      memberList.toList
    } else List.empty
    
    ObjectDeclaration(name, superClass, traits, members, startToken.location)
  }
  
  private def parseExtensionDeclaration(): ExtensionDeclaration = {
    val startToken = expectKeyword("extension")
    val targetType = parseTypeAnnotation()
    
    expectDelimiter("{")
    val methods = ListBuffer[FunctionDeclaration]()
    skipNewlines()
    
    while (currentToken.text != "}") {
      methods += parseFunctionDeclaration()
      skipNewlines()
    }
    
    expectDelimiter("}")
    
    ExtensionDeclaration(targetType, methods.toList, startToken.location)
  }
  
  // Parse expressions
  private def parseExpression(): Expression = {
    parseAssignmentExpression()
  }
  
  private def parseAssignmentExpression(): Expression = {
    val left = parseLogicalOrExpression()
    
    if (currentToken.text == "=") {
      val operator = advance()
      val right = parseAssignmentExpression()
      Assignment(left, right, operator.location)
    } else {
      left
    }
  }
  
  private def parseLogicalOrExpression(): Expression = {
    var left = parseLogicalAndExpression()
    
    while (currentToken.text == "||") {
      val operator = advance()
      val right = parseLogicalAndExpression()
      left = BinaryOp(left, operator.text, right, operator.location)
    }
    
    left
  }
  
  private def parseLogicalAndExpression(): Expression = {
    var left = parseEqualityExpression()
    
    while (currentToken.text == "&&") {
      val operator = advance()
      val right = parseEqualityExpression()
      left = BinaryOp(left, operator.text, right, operator.location)
    }
    
    left
  }
  
  private def parseEqualityExpression(): Expression = {
    var left = parseRelationalExpression()
    
    while (currentToken.text == "==" || currentToken.text == "!=") {
      val operator = advance()
      val right = parseRelationalExpression()
      left = BinaryOp(left, operator.text, right, operator.location)
    }
    
    left
  }
  
  private def parseRelationalExpression(): Expression = {
    var left = parseAdditiveExpression()
    
    while (Set("<", ">", "<=", ">=").contains(currentToken.text)) {
      val operator = advance()
      val right = parseAdditiveExpression()
      left = BinaryOp(left, operator.text, right, operator.location)
    }
    
    left
  }
  
  private def parseAdditiveExpression(): Expression = {
    var left = parseMultiplicativeExpression()
    
    while (currentToken.text == "+" || currentToken.text == "-") {
      val operator = advance()
      val right = parseMultiplicativeExpression()
      left = BinaryOp(left, operator.text, right, operator.location)
    }
    
    left
  }
  
  private def parseMultiplicativeExpression(): Expression = {
    var left = parseUnaryExpression()
    
    while (Set("*", "/", "%").contains(currentToken.text)) {
      val operator = advance()
      val right = parseUnaryExpression()
      left = BinaryOp(left, operator.text, right, operator.location)
    }
    
    left
  }
  
  private def parseUnaryExpression(): Expression = {
    if (Set("!", "-", "+").contains(currentToken.text)) {
      val operator = advance()
      val operand = parseUnaryExpression()
      UnaryOp(operator.text, operand, operator.location)
    } else {
      parsePostfixExpression()
    }
  }
  
  private def parsePostfixExpression(): Expression = {
    var expr = parsePrimaryExpression()
    
    while (true) {
      currentToken match {
        case Token.Delimiter("(", location, _) =>
          advance()
          var args = parseArgumentList()
          expectDelimiter(")")
          
          // Check for trailing lambda syntax: foo(a){x => x * 2}
          if (currentToken.text == "{") {
            val lambdaLocation = currentToken.location
            advance() // consume '{'
            val lambda = parseTrailingLambda(lambdaLocation)
            args = args :+ lambda
          }
          
          expr = MethodCall(Some(expr), "apply", args, List.empty, location)
          
        case Token.Delimiter("{", location, _) if expr.isInstanceOf[Identifier] =>
          // Handle trailing lambda without parentheses: foo{x => x * 2}
          advance() // consume '{'
          val lambda = parseTrailingLambda(location)
          val identifier = expr.asInstanceOf[Identifier]
          expr = MethodCall(None, identifier.name, List(lambda), List.empty, identifier.location)
          
        case Token.Operator(".", location, _) =>
          advance()
          val fieldName = expectIdentifier().text
          if (currentToken.text == "(") {
            advance()
            var args = parseArgumentList()
            expectDelimiter(")")
            
            // Check for trailing lambda syntax: foo.bar(a){x => x * 2}
            if (currentToken.text == "{") {
              val lambdaLocation = currentToken.location
              advance() // consume '{'
              val lambda = parseTrailingLambda(lambdaLocation)
              args = args :+ lambda
            }
            
            expr = MethodCall(Some(expr), fieldName, args, List.empty, location)
          } else if (currentToken.text == "{") {
            // Handle trailing lambda on method call without parentheses: list.map{x => x * 2}
            val lambdaLocation = currentToken.location
            advance() // consume '{'
            val lambda = parseTrailingLambda(lambdaLocation)
            expr = MethodCall(Some(expr), fieldName, List(lambda), List.empty, location)
          } else {
            expr = FieldAccess(expr, fieldName, location)
          }
          
        case _ => return expr
      }
    }
    
    expr
  }
  
  private def parsePrimaryExpression(): Expression = {
    currentToken match {
      case Token.IntLiteral(value, location, _) =>
        advance()
        IntLiteral(value, location)
        
      case Token.DoubleLiteral(value, location, _) =>
        advance()
        DoubleLiteral(value, location)
        
      case Token.StringLiteral(value, location, _) =>
        advance()
        StringLiteral(value, location)
        
      case Token.BooleanLiteral(value, location, _) =>
        advance()
        BooleanLiteral(value, location)
        
      case Token.Identifier(name, location, _) =>
        advance()
        Identifier(name, location)
        
      case Token.Delimiter("(", location, _) =>
        advance()
        // Check if this is a lambda expression
        if (isLambdaExpression()) {
          parseLambdaExpression(location)
        } else {
          val expr = parseExpression()
          expectDelimiter(")")
          expr
        }
        
      case Token.Delimiter("{", location, _) =>
        parseBlockExpression()
        
      case Token.Keyword("if", location, _) =>
        parseIfExpression()
        
      case Token.Keyword("while", location, _) =>
        parseWhileExpression()
        
      case Token.Delimiter("[", location, _) =>
        parseListLiteral()
        
      case Token.Keyword("this", location, _) =>
        advance()
        ThisExpression(location)
        
      case _ =>
        throw ParseException(s"Unexpected token: ${currentToken.text}", currentToken.location)
    }
  }
  
  private def parseBlockExpression(): Block = {
    val startToken = expectDelimiter("{")
    val statements = ListBuffer[Statement]()
    skipNewlines()
    
    while (currentToken.text != "}") {
      statements += parseStatement()
      
      // Optional semicolon
      if (currentToken.text == ";") {
        advance()
      }
      
      skipNewlines()
    }
    
    expectDelimiter("}")
    Block(statements.toList, startToken.location)
  }
  
  private def parseIfExpression(): IfExpression = {
    val startToken = expectKeyword("if")
    expectDelimiter("(")
    val condition = parseExpression()
    expectDelimiter(")")
    val thenBranch = parseExpression()
    
    val elseBranch = if (currentToken.text == "else") {
      expectKeyword("else")
      Some(parseExpression())
    } else None
    
    IfExpression(condition, thenBranch, elseBranch, startToken.location)
  }
  
  private def parseWhileExpression(): WhileExpression = {
    val startToken = expectKeyword("while")
    expectDelimiter("(")
    val condition = parseExpression()
    expectDelimiter(")")
    val body = parseExpression()
    
    WhileExpression(condition, body, startToken.location)
  }
  
  private def parseListLiteral(): ListLiteral = {
    val startToken = expectDelimiter("[")
    val elements = parseArgumentList()
    expectDelimiter("]")
    
    ListLiteral(elements, startToken.location)
  }
  
  private def parseStatement(): Statement = {
    currentToken match {
      case Token.Keyword("val", _, _) | Token.Keyword("var", _, _) =>
        parseVariableDeclaration()
      case Token.Keyword("return", location, _) =>
        parseReturnStatement()
      case _ =>
        ExpressionStatement(parseExpression(), currentToken.location)
    }
  }
  
  private def parseVariableDeclaration(): VariableDeclaration = {
    val isMutable = currentToken.text == "var"
    val startToken = advance()
    val name = expectIdentifier().text
    
    val typeAnnotation = if (currentToken.text == ":") {
      expectDelimiter(":")
      Some(parseTypeAnnotation())
    } else None
    
    val initializer = if (currentToken.text == "=") {
      expectOperator("=")
      Some(parseExpression())
    } else None
    
    VariableDeclaration(name, typeAnnotation, initializer, isMutable, startToken.location)
  }
  
  private def parseReturnStatement(): Return = {
    val startToken = expectKeyword("return")
    val value = if (currentToken.isInstanceOf[Token.Newline] || currentToken.text == "}") {
      None
    } else {
      Some(parseExpression())
    }
    
    Return(value, startToken.location)
  }
  
  // Helper methods
  private def parseParameterList(): List[Parameter] = {
    val params = ListBuffer[Parameter]()
    skipNewlines()
    
    if (currentToken.text != ")") {
      params += parseParameter()
      skipNewlines()
      while (currentToken.text == ",") {
        advance()
        params += parseParameter()
        skipNewlines()
      }
    }
    
    params.toList
  }
  
  private def parseParameter(): Parameter = {
    val name = expectIdentifier().text
    val typeAnnotation = if (currentToken.text == ":") {
      expectDelimiter(":")
      Some(parseTypeAnnotation())
    } else None
    
    val defaultValue = if (currentToken.text == "=") {
      expectOperator("=")
      Some(parseExpression())
    } else None
    
    Parameter(name, typeAnnotation, defaultValue, currentToken.location)
  }
  
  private def parseArgumentList(): List[Expression] = {
    val args = ListBuffer[Expression]()
    skipNewlines()
    
    if (currentToken.text != ")" && currentToken.text != "]") {
      args += parseExpression()
      skipNewlines()
      while (currentToken.text == ",") {
        advance()
        args += parseExpression()
        skipNewlines()
      }
    }
    
    args.toList
  }
  
  private def parseTypeParameterList(): List[TypeParameter] = {
    val params = ListBuffer[TypeParameter]()
    skipNewlines()
    
    if (currentToken.text != ">") {
      params += parseTypeParameter()
      skipNewlines()
      while (currentToken.text == ",") {
        advance()
        params += parseTypeParameter()
        skipNewlines()
      }
    }
    
    params.toList
  }
  
  private def parseTypeParameter(): TypeParameter = {
    val variance = currentToken.text match {
      case "+" => advance(); Covariant
      case "-" => advance(); Contravariant
      case _ => Invariant
    }
    
    val name = expectIdentifier().text
    
    val upperBound = if (currentToken.text == "<:") {
      expectOperator("<:")
      Some(parseTypeAnnotation())
    } else None
    
    val lowerBound = if (currentToken.text == ">:") {
      expectOperator(">:")
      Some(parseTypeAnnotation())
    } else None
    
    TypeParameter(name, variance, upperBound, lowerBound, currentToken.location)
  }
  
  private def parseTypeAnnotation(): TypeAnnotation = {
    parseTypeAnnotationWithContext(allowFunctionType = true)
  }
  
  private def parseTypeAnnotationWithContext(allowFunctionType: Boolean): TypeAnnotation = {
    val firstType = parsePrimaryTypeAnnotation()
    
    // Check if this is a function type without parentheses (single parameter)
    if (allowFunctionType && currentToken.text == "=>") {
      val location = firstType.location
      advance() // consume "=>"
      val returnType = parseTypeAnnotation()
      FunctionType(List(firstType), returnType, location)
    } else {
      firstType
    }
  }
  
  private def parsePrimaryTypeAnnotation(): TypeAnnotation = {
    currentToken match {
      case Token.Identifier(name, location, _) =>
        advance()
        if (currentToken.text == "<") {
          expectOperator("<")
          val args = parseTypeAnnotationList()
          expectOperator(">")
          GenericType(name, args, location)
        } else {
          SimpleType(name, location)
        }
      case Token.Keyword(name, location, _) if Set("Int", "Double", "String", "Boolean", "Unit", "Any").contains(name) =>
        advance()
        if (currentToken.text == "<") {
          expectOperator("<")
          val args = parseTypeAnnotationList()
          expectOperator(">")
          GenericType(name, args, location)
        } else {
          SimpleType(name, location)
        }
      case Token.Delimiter("(", location, _) =>
        advance()
        val paramTypes = parseTypeAnnotationList()
        expectDelimiter(")")
        expectOperator("=>")
        val returnType = parseTypeAnnotation()
        FunctionType(paramTypes, returnType, location)
      case _ =>
        throw ParseException(s"Expected type annotation, got ${currentToken.text}", currentToken.location)
    }
  }
  
  private def parseTypeAnnotationList(): List[TypeAnnotation] = {
    val types = ListBuffer[TypeAnnotation]()
    skipNewlines()
    
    if (currentToken.text != ">" && currentToken.text != ")") {
      types += parseTypeAnnotation()
      skipNewlines()
      while (currentToken.text == ",") {
        advance()
        types += parseTypeAnnotation()
        skipNewlines()
      }
    }
    
    types.toList
  }
  
  private def parseClassMember(): ClassMember = {
    currentToken match {
      case Token.Keyword("fun", _, _) =>
        MethodMember(parseFunctionDeclaration())
      case Token.Keyword("val", _, _) | Token.Keyword("var", _, _) =>
        FieldMember(parseVariableDeclaration())
      case _ =>
        throw ParseException(s"Expected class member, got ${currentToken.text}", currentToken.location)
    }
  }
  
  private def parseTraitMember(): TraitMember = {
    currentToken match {
      case Token.Keyword("fun", _, _) =>
        ConcreteMethodMember(parseFunctionDeclaration())
      case Token.Keyword("def", location, _) =>
        advance()
        val name = expectIdentifier().text
        
        val typeParameters = if (currentToken.text == "<") {
          expectOperator("<")
          val params = parseTypeParameterList()
          expectOperator(">")
          params
        } else List.empty
        
        expectDelimiter("(")
        val parameters = parseParameterList()
        expectDelimiter(")")
        
        val returnType = if (currentToken.text == ":") {
          expectDelimiter(":")
          Some(parseTypeAnnotation())
        } else None
        
        AbstractMethodMember(name, typeParameters, parameters, returnType, location)
      case _ =>
        throw ParseException(s"Expected trait member, got ${currentToken.text}", currentToken.location)
    }
  }
  
  private def isLambdaExpression(): Boolean = {
    // Look ahead to see if this is a lambda parameter list
    var lookahead = 0
    var parenCount = 1
    
    while (parenCount > 0 && pos + lookahead < tokenList.length) {
      val token = tokenList(pos + lookahead)
      token match {
        case Token.Delimiter("(", _, _) => parenCount += 1
        case Token.Delimiter(")", _, _) => parenCount -= 1
        case _ =>
      }
      lookahead += 1
    }
    
    // Check if we have => after the closing paren
    if (pos + lookahead < tokenList.length) {
      tokenList(pos + lookahead) match {
        case Token.Operator("=>", _, _) => true
        case _ => false
      }
    } else {
      false
    }
  }
  
  private def parseLambdaExpression(startLocation: SourceLocation): Lambda = {
    val parameters = parseParameterList()
    expectDelimiter(")")
    expectOperator("=>")
    val body = parseExpression()
    Lambda(parameters, body, startLocation)
  }
  
  private def parseTrailingLambda(startLocation: SourceLocation): Lambda = {
    // Parse lambda with syntax: { x => x * 2 } or { (x, y) => x + y }
    val parameters = if (currentToken.text == "(") {
      // Multiple parameters: { (x, y) => x + y }
      advance() // consume '('
      val params = parseParameterList()
      expectDelimiter(")")
      params
    } else if (currentToken.text == "=>") {
      // No parameters: { => 42 }
      List.empty
    } else if (currentToken.isInstanceOf[Token.Identifier]) {
      // Parse one or more parameters
      val params = ListBuffer[Parameter]()
      
      // Parse first parameter
      var name = expectIdentifier().text
      var typeAnnotation: Option[TypeAnnotation] = None
      
      if (currentToken.text == ":") {
        advance() // consume ':'
        typeAnnotation = Some(parseTypeAnnotationWithContext(allowFunctionType = false))
      }
      
      params += Parameter(name, typeAnnotation, None, startLocation)
      
      // Check for more parameters
      while (currentToken.text == ",") {
        advance() // consume ','
        name = expectIdentifier().text
        typeAnnotation = None
        
        if (currentToken.text == ":") {
          advance() // consume ':'
          typeAnnotation = Some(parseTypeAnnotationWithContext(allowFunctionType = false))
        }
        
        params += Parameter(name, typeAnnotation, None, currentToken.location)
      }
      
      params.toList
    } else {
      throw ParseException(s"Expected parameter or '=>' in lambda, got ${currentToken.text}", currentToken.location)
    }
    
    expectOperator("=>")
    val body = parseExpression()
    expectDelimiter("}")
    
    Lambda(parameters, body, startLocation)
  }
}

object Parser {
  def parse(input: String, filename: String = "<input>"): Program = {
    val lexer = new Lexer(input, filename)
    val tokens = lexer.tokenize()
    val parser = new Parser(tokens)
    parser.parseProgram()
  }
  
  def parseExpression(input: String, filename: String = "<input>"): Expression = {
    val lexer = new Lexer(input, filename)
    val tokens = lexer.tokenize()
    val parser = new Parser(tokens)
    parser.parseExpression()
  }
}