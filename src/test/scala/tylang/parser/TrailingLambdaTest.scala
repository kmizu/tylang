package tylang.parser

import munit.FunSuite
import tylang.ast.*
import tylang.lexer.Lexer
import tylang.SourceLocation

class TrailingLambdaTest extends FunSuite {
  
  def parseExpression(input: String): Expression = {
    Parser.parseExpression(input)
  }
  
  test("trailing lambda with parentheses - single parameter") {
    val expr = parseExpression("foo(a){x => x * 2}")
    
    expr match {
      case MethodCall(Some(Identifier("foo", _)), "apply", args, _, _) =>
        assertEquals(args.length, 2)
        assert(args(0).isInstanceOf[Identifier])
        args(1) match {
          case Lambda(params, body, _) =>
            assertEquals(params.length, 1)
            assertEquals(params(0).name, "x")
            assert(params(0).typeAnnotation.isEmpty) // Type inferred
            assert(body.isInstanceOf[BinaryOp])
          case _ => fail("Expected lambda as second argument")
        }
      case _ => fail(s"Expected method call with receiver, got ${expr.getClass}: $expr")
    }
  }
  
  test("trailing lambda without parentheses - single parameter") {
    val expr = parseExpression("foo{x => x * 2}")
    
    expr match {
      case MethodCall(None, "foo", args, _, _) =>
        assertEquals(args.length, 1)
        args(0) match {
          case Lambda(params, body, _) =>
            assertEquals(params.length, 1)
            assertEquals(params(0).name, "x")
            assert(params(0).typeAnnotation.isEmpty)
            assert(body.isInstanceOf[BinaryOp])
          case _ => fail("Expected lambda as argument")
        }
      case _ => fail(s"Expected method call, got ${expr.getClass}")
    }
  }
  
  test("trailing lambda with typed parameter") {
    val expr = parseExpression("foo{x: Int => x * 2}")
    
    expr match {
      case MethodCall(None, "foo", args, _, _) =>
        assertEquals(args.length, 1)
        args(0) match {
          case Lambda(params, body, _) =>
            assertEquals(params.length, 1)
            assertEquals(params(0).name, "x")
            assert(params(0).typeAnnotation.isDefined)
            params(0).typeAnnotation.get match {
              case SimpleType("Int", _) => // OK
              case _ => fail("Expected Int type annotation")
            }
          case _ => fail("Expected lambda as argument")
        }
      case _ => fail(s"Expected method call, got ${expr.getClass}")
    }
  }
  
  test("trailing lambda with multiple parameters") {
    val expr = parseExpression("foo{(x, y) => x + y}")
    
    expr match {
      case MethodCall(None, "foo", args, _, _) =>
        assertEquals(args.length, 1)
        args(0) match {
          case Lambda(params, body, _) =>
            assertEquals(params.length, 2)
            assertEquals(params(0).name, "x")
            assertEquals(params(1).name, "y")
            assert(params(0).typeAnnotation.isEmpty)
            assert(params(1).typeAnnotation.isEmpty)
          case _ => fail("Expected lambda as argument")
        }
      case _ => fail(s"Expected method call, got ${expr.getClass}")
    }
  }
  
  test("trailing lambda with no parameters") {
    val expr = parseExpression("foo{ => 42}")
    
    expr match {
      case MethodCall(None, "foo", args, _, _) =>
        assertEquals(args.length, 1)
        args(0) match {
          case Lambda(params, body, _) =>
            assertEquals(params.length, 0)
            body match {
              case IntLiteral(42, _) => // OK
              case _ => fail("Expected literal 42")
            }
          case _ => fail("Expected lambda as argument")
        }
      case _ => fail(s"Expected method call, got ${expr.getClass}")
    }
  }
  
  test("trailing lambda on method call") {
    val expr = parseExpression("list.map{x => x * 2}")
    
    expr match {
      case MethodCall(Some(receiver), "map", args, _, _) =>
        assert(receiver.isInstanceOf[Identifier])
        assertEquals(args.length, 1)
        assert(args(0).isInstanceOf[Lambda])
      case _ => fail(s"Expected method call, got ${expr.getClass}")
    }
  }
  
  test("trailing lambda with regular arguments") {
    val expr = parseExpression("fold(0, list){acc, x => acc + x}")
    
    expr match {
      case MethodCall(Some(Identifier("fold", _)), "apply", args, _, _) =>
        assertEquals(args.length, 3)
        assert(args(0).isInstanceOf[IntLiteral])
        assert(args(1).isInstanceOf[Identifier])
        args(2) match {
          case Lambda(params, _, _) =>
            assertEquals(params.length, 2)
            assertEquals(params(0).name, "acc")
            assertEquals(params(1).name, "x")
          case _ => fail("Expected lambda as third argument")
        }
      case _ => fail(s"Expected method call, got ${expr.getClass}")
    }
  }
}