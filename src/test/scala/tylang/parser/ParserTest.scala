package tylang.parser

import munit.FunSuite
import tylang.ast.*
import tylang.lexer.Lexer

class ParserTest extends FunSuite {
  
  def parseExpression(input: String): Expression = {
    Parser.parseExpression(input, "<test>")
  }
  
  def parseProgram(input: String): Program = {
    Parser.parse(input, "<test>")
  }
  
  test("parse integer literal") {
    val expr = parseExpression("42")
    expr match {
      case IntLiteral(value, _) =>
        assertEquals(value, 42)
      case _ => fail(s"Expected IntLiteral, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse double literal") {
    val expr = parseExpression("3.14")
    expr match {
      case DoubleLiteral(value, _) =>
        assertEquals(value, 3.14)
      case _ => fail(s"Expected DoubleLiteral, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse string literal") {
    val expr = parseExpression("\"hello world\"")
    expr match {
      case StringLiteral(value, _) =>
        assertEquals(value, "hello world")
      case _ => fail(s"Expected StringLiteral, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse boolean literals") {
    val trueExpr = parseExpression("true")
    trueExpr match {
      case BooleanLiteral(value, _) =>
        assertEquals(value, true)
      case _ => fail(s"Expected BooleanLiteral, got ${trueExpr.getClass.getSimpleName}")
    }
    
    val falseExpr = parseExpression("false")
    falseExpr match {
      case BooleanLiteral(value, _) =>
        assertEquals(value, false)
      case _ => fail(s"Expected BooleanLiteral, got ${falseExpr.getClass.getSimpleName}")
    }
  }
  
  test("parse identifier") {
    val expr = parseExpression("foo")
    expr match {
      case Identifier(name, _) =>
        assertEquals(name, "foo")
      case _ => fail(s"Expected Identifier, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse binary operations") {
    val expr = parseExpression("1 + 2")
    expr match {
      case BinaryOp(left, op, right, _) =>
        assertEquals(op, "+")
        assert(left.isInstanceOf[IntLiteral])
        assert(right.isInstanceOf[IntLiteral])
      case _ => fail(s"Expected BinaryOp, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse operator precedence") {
    val expr = parseExpression("1 + 2 * 3")
    expr match {
      case BinaryOp(IntLiteral(1, _), "+", BinaryOp(IntLiteral(2, _), "*", IntLiteral(3, _), _), _) =>
        // Correct: 1 + (2 * 3)
      case _ => fail(s"Incorrect operator precedence parsing")
    }
  }
  
  test("parse parentheses") {
    val expr = parseExpression("(1 + 2) * 3")
    expr match {
      case BinaryOp(BinaryOp(IntLiteral(1, _), "+", IntLiteral(2, _), _), "*", IntLiteral(3, _), _) =>
        // Correct: (1 + 2) * 3
      case _ => fail(s"Incorrect parentheses parsing")
    }
  }
  
  test("parse unary operations") {
    val negExpr = parseExpression("-42")
    negExpr match {
      case UnaryOp(op, IntLiteral(42, _), _) =>
        assertEquals(op, "-")
      case _ => fail(s"Expected UnaryOp, got ${negExpr.getClass.getSimpleName}")
    }
    
    val notExpr = parseExpression("!true")
    notExpr match {
      case UnaryOp(op, BooleanLiteral(true, _), _) =>
        assertEquals(op, "!")
      case _ => fail(s"Expected UnaryOp, got ${notExpr.getClass.getSimpleName}")
    }
  }
  
  test("parse if expression") {
    val expr = parseExpression("if (x > 0) x else -x")
    expr match {
      case IfExpression(condition, thenBranch, Some(elseBranch), _) =>
        assert(condition.isInstanceOf[BinaryOp])
        assert(thenBranch.isInstanceOf[Identifier])
        assert(elseBranch.isInstanceOf[UnaryOp])
      case _ => fail(s"Expected IfExpression, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse block expression") {
    val expr = parseExpression("{ val x = 1; x + 2 }")
    expr match {
      case Block(statements, _) =>
        assertEquals(statements.length, 2)
        assert(statements(0).isInstanceOf[VariableDeclaration])
        assert(statements(1).isInstanceOf[ExpressionStatement])
      case _ => fail(s"Expected Block, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse list literal") {
    val expr = parseExpression("[1, 2, 3]")
    expr match {
      case ListLiteral(elements, _) =>
        assertEquals(elements.length, 3)
        elements.foreach(e => assert(e.isInstanceOf[IntLiteral]))
      case _ => fail(s"Expected ListLiteral, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse function declaration") {
    val program = parseProgram("fun add(x: Int, y: Int): Int { x + y }")
    assertEquals(program.declarations.length, 1)
    
    program.declarations.head match {
      case FunctionDeclaration(name, typeParams, params, returnType, body, _) =>
        assertEquals(name, "add")
        assertEquals(typeParams.length, 0)
        assertEquals(params.length, 2)
        assertEquals(params(0).name, "x")
        assertEquals(params(1).name, "y")
        assert(returnType.isDefined)
        body match {
          case Block(statements, _) =>
            assertEquals(statements.length, 1)
            statements.head match {
              case ExpressionStatement(expr, _) =>
                assert(expr.isInstanceOf[BinaryOp])
              case _ => fail("Expected ExpressionStatement in block")
            }
          case _ => fail("Expected Block as function body")
        }
      case _ => fail("Expected FunctionDeclaration")
    }
  }
  
  test("parse single parameter function type without parentheses") {
    val program = parseProgram("""
      fun applyFunction(f: Int => Int, x: Int): Int { f(x) }
      fun transform<T, U>(f: T => U, value: T): U { f(value) }
    """)
    assertEquals(program.declarations.length, 2)
    
    // Check first function
    program.declarations(0) match {
      case FunctionDeclaration(name, _, params, _, _, _) =>
        assertEquals(name, "applyFunction")
        params(0).typeAnnotation match {
          case Some(FunctionType(paramTypes, returnType, _)) =>
            assertEquals(paramTypes.length, 1)
            paramTypes(0) match {
              case SimpleType("Int", _) => // Expected
              case other => fail(s"Expected SimpleType(Int), got: $other")
            }
            returnType match {
              case SimpleType("Int", _) => // Expected
              case other => fail(s"Expected SimpleType(Int), got: $other")
            }
          case other => fail(s"Expected FunctionType, got: $other")
        }
      case _ => fail("Expected FunctionDeclaration")
    }
    
    // Check second function (generic)
    program.declarations(1) match {
      case FunctionDeclaration(name, typeParams, params, _, _, _) =>
        assertEquals(name, "transform")
        assertEquals(typeParams.length, 2)
        params(0).typeAnnotation match {
          case Some(FunctionType(paramTypes, returnType, _)) =>
            assertEquals(paramTypes.length, 1)
            paramTypes(0) match {
              case SimpleType("T", _) => // Expected
              case other => fail(s"Expected SimpleType(T), got: $other")
            }
            returnType match {
              case SimpleType("U", _) => // Expected
              case other => fail(s"Expected SimpleType(U), got: $other")
            }
          case other => fail(s"Expected FunctionType, got: $other")
        }
      case _ => fail("Expected FunctionDeclaration")
    }
  }
  
  test("parse class declaration") {
    val program = parseProgram("""
      class Point(x: Int, y: Int) {
        fun distance(): Double { 0.0 }
      }
    """)
    assertEquals(program.declarations.length, 1)
    
    program.declarations.head match {
      case ClassDeclaration(name, typeParams, superClass, traits, constructor, members, _) =>
        assertEquals(name, "Point")
        assertEquals(typeParams.length, 0)
        assert(superClass.isEmpty)
        assertEquals(traits.length, 0)
        assert(constructor.isDefined)
        assertEquals(constructor.get.parameters.length, 2)
        assertEquals(members.length, 1)
        assert(members.head.isInstanceOf[MethodMember])
      case _ => fail("Expected ClassDeclaration")
    }
  }
  
  test("parse generic class with variance") {
    val program = parseProgram("class Box<+T> { }")
    assertEquals(program.declarations.length, 1)
    
    program.declarations.head match {
      case ClassDeclaration(name, typeParams, _, _, _, _, _) =>
        assertEquals(name, "Box")
        assertEquals(typeParams.length, 1)
        assertEquals(typeParams.head.name, "T")
        assert(typeParams.head.variance.isInstanceOf[Covariant.type])
      case _ => fail("Expected ClassDeclaration")
    }
  }
  
  test("parse trait declaration") {
    val program = parseProgram("""
      trait Comparable<T> {
        def compare(other: T): Int
      }
    """)
    assertEquals(program.declarations.length, 1)
    
    program.declarations.head match {
      case TraitDeclaration(name, typeParams, superTraits, members, _) =>
        assertEquals(name, "Comparable")
        assertEquals(typeParams.length, 1)
        assertEquals(typeParams.head.name, "T")
        assertEquals(superTraits.length, 0)
        assertEquals(members.length, 1)
        assert(members.head.isInstanceOf[AbstractMethodMember])
      case _ => fail("Expected TraitDeclaration")
    }
  }
  
  test("parse extension declaration") {
    val program = parseProgram("""extension String {
        fun reverse(): String { "" }
      }""")
    assertEquals(program.declarations.length, 1)
    
    program.declarations.head match {
      case ExtensionDeclaration(targetType, methods, _) =>
        assert(targetType.isInstanceOf[SimpleType])
        assertEquals(methods.length, 1)
        assertEquals(methods.head.name, "reverse")
      case _ => fail("Expected ExtensionDeclaration")
    }
  }
  
  test("parse variable declarations") {
    // Test parsing variable declarations as statements within a block expression
    val expr = parseExpression("{ val x = 42; var y: String = \"hello\" }")
    expr match {
      case Block(statements, _) =>
        assertEquals(statements.length, 2)
        
        statements(0) match {
          case VariableDeclaration(name, typeAnnotation, initializer, isMutable, _) =>
            assertEquals(name, "x")
            assert(typeAnnotation.isEmpty)
            assert(initializer.isDefined)
            assertEquals(isMutable, false)
          case _ => fail("Expected VariableDeclaration")
        }
        
        statements(1) match {
          case VariableDeclaration(name, typeAnnotation, initializer, isMutable, _) =>
            assertEquals(name, "y")
            assert(typeAnnotation.isDefined)
            assert(initializer.isDefined)
            assertEquals(isMutable, true)
          case _ => fail("Expected VariableDeclaration")
        }
      case _ => fail("Expected Block")
    }
  }
  
  test("parse lambda expression") {
    val expr = parseExpression("(x: Int, y: Int) => x + y")
    expr match {
      case Lambda(params, body, _) =>
        assertEquals(params.length, 2)
        assertEquals(params(0).name, "x")
        assertEquals(params(1).name, "y")
        assert(body.isInstanceOf[BinaryOp])
      case _ => fail(s"Expected Lambda, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse method call") {
    val expr = parseExpression("obj.method(1, 2)")
    expr match {
      case MethodCall(Some(receiver), methodName, args, _, _) =>
        assert(receiver.isInstanceOf[Identifier])
        assertEquals(methodName, "method")
        assertEquals(args.length, 2)
      case _ => fail(s"Expected MethodCall, got ${expr.getClass.getSimpleName}")
    }
  }
  
  test("parse field access") {
    val expr = parseExpression("obj.field")
    expr match {
      case FieldAccess(receiver, fieldName, _) =>
        assert(receiver.isInstanceOf[Identifier])
        assertEquals(fieldName, "field")
      case _ => fail(s"Expected FieldAccess, got ${expr.getClass.getSimpleName}")
    }
  }
}