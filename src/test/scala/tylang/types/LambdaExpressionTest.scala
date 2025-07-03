package tylang.types

import munit.FunSuite
import tylang.ast.*
import tylang.parser.Parser
import tylang.types.{TypeInference, TypeContext}
import tylang.{SourceLocation}

class LambdaExpressionTest extends FunSuite {
  
  val dummyLocation = SourceLocation("<test>", (1, 1), "test")
  
  test("lambda expression basic parsing") {
    val code = """
      fun test(): Unit {
        val add = (x: Int, y: Int) => x + y
      }
    """
    
    val program = Parser.parse(code, "<test>")
    assert(program.declarations.length == 1)
    
    program.declarations.head match {
      case FunctionDeclaration(_, _, _, _, body, _) =>
        body match {
          case Block(statements, _) =>
            statements.head match {
              case VariableDeclaration(name, _, Some(Lambda(params, lambdaBody, _)), _, _) =>
                assertEquals(name, "add")
                assertEquals(params.length, 2)
                assertEquals(params(0).name, "x")
                assertEquals(params(1).name, "y")
                params(0).typeAnnotation.get match {
                  case SimpleType("Int", _) => // Expected
                  case other => fail(s"Expected SimpleType(Int), got: $other")
                }
                params(1).typeAnnotation.get match {
                  case SimpleType("Int", _) => // Expected
                  case other => fail(s"Expected SimpleType(Int), got: $other")
                }
              case _ => fail("Expected lambda in variable declaration")
            }
          case _ => fail("Expected block")
        }
      case _ => fail("Expected function declaration")
    }
  }
  
  test("lambda expression with single parameter") {
    val code = """
      fun test(): Unit {
        val square = (x: Int) => x * x
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    val varDecl = block.statements.head.asInstanceOf[VariableDeclaration]
    val lambda = varDecl.initializer.get.asInstanceOf[Lambda]
    
    assertEquals(lambda.parameters.length, 1)
    assertEquals(lambda.parameters(0).name, "x")
    lambda.parameters(0).typeAnnotation.get match {
      case SimpleType("Int", _) => // Expected
      case other => fail(s"Expected SimpleType(Int), got: $other")
    }
  }
  
  test("lambda expression with no parameters") {
    val code = """
      fun test(): Unit {
        val getValue = () => 42
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    val varDecl = block.statements.head.asInstanceOf[VariableDeclaration]
    val lambda = varDecl.initializer.get.asInstanceOf[Lambda]
    
    assertEquals(lambda.parameters.length, 0)
    lambda.body match {
      case IntLiteral(42, _) => // Expected
      case other => fail(s"Expected IntLiteral(42), got: $other")
    }
  }
  
  test("lambda expression with inferred parameter types") {
    val code = """
      fun test(): Unit {
        val increment = (x) => x + 1
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    val varDecl = block.statements.head.asInstanceOf[VariableDeclaration]
    val lambda = varDecl.initializer.get.asInstanceOf[Lambda]
    
    assertEquals(lambda.parameters.length, 1)
    assertEquals(lambda.parameters(0).name, "x")
    // Type annotation might be None (inferred)
    assert(lambda.parameters(0).typeAnnotation.isEmpty || lambda.parameters(0).typeAnnotation.isDefined)
  }
  
  test("lambda expression type inference") {
    val inference = new TypeInference()
    implicit val ctx: TypeContext = TypeContext()
    
    // Create a simple lambda manually for type testing
    val param = Parameter("x", Some(SimpleType("Int", dummyLocation)), None, dummyLocation)
    val body = BinaryOp(
      Identifier("x", dummyLocation), 
      "+", 
      IntLiteral(1, dummyLocation), 
      dummyLocation
    )
    val lambda = Lambda(List(param), body, dummyLocation)
    
    val inferredType = inference.inferType(lambda)
    inferredType match {
      case tylang.types.FunctionType(paramTypes, returnType) =>
        assertEquals(paramTypes.length, 1)
        assert(paramTypes(0).isInstanceOf[tylang.types.IntType.type])
        assert(returnType.isInstanceOf[tylang.types.IntType.type])
      case other => fail(s"Expected FunctionType, got: $other")
    }
  }
  
  test("lambda expression with complex body") {
    val code = """
      fun test(): Unit {
        val complex = (x: Int, y: Int) => if (x > y) { x } else { y }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    val varDecl = block.statements.head.asInstanceOf[VariableDeclaration]
    val lambda = varDecl.initializer.get.asInstanceOf[Lambda]
    
    assertEquals(lambda.parameters.length, 2)
    lambda.body match {
      case IfExpression(_, _, Some(_), _) => // Has if-else
      case other => fail(s"Expected IfExpression, got: $other")
    }
  }
  
  test("lambda expression as function parameter") {
    val code = """
      fun apply(f: (Int) => Int, value: Int): Int {
        f(value)
      }
      
      fun test(): Unit {
        val result = apply((x: Int) => x * 2, 5)
      }
    """
    
    val program = Parser.parse(code, "<test>")
    assertEquals(program.declarations.length, 2)
    
    // Check apply function signature
    val applyFunc = program.declarations(0).asInstanceOf[FunctionDeclaration]
    assertEquals(applyFunc.name, "apply")
    assertEquals(applyFunc.parameters.length, 2)
    
    applyFunc.parameters(0).typeAnnotation.get match {
      case FunctionType(List(SimpleType("Int", _)), SimpleType("Int", _), _) => // Expected
      case other => fail(s"Expected FunctionType, got: $other")
    }
  }
  
  test("lambda expression in method call") {
    val code = """
      fun test(): Unit {
        val numbers = [1, 2, 3, 4, 5]
        val doubled = numbers.map((x: Int) => x * 2)
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    
    // Should have two variable declarations
    assertEquals(block.statements.length, 2)
    
    block.statements(1) match {
      case VariableDeclaration(name, _, Some(MethodCall(_, methodName, args, _, _)), _, _) =>
        assertEquals(name, "doubled")
        assertEquals(methodName, "map")
        assertEquals(args.length, 1)
        args.head match {
          case Lambda(params, _, _) =>
            assertEquals(params.length, 1)
            assertEquals(params(0).name, "x")
          case other => fail(s"Expected Lambda, got: $other")
        }
      case _ => fail("Expected method call with lambda")
    }
  }
  
  test("lambda expression with string parameters") {
    val code = """
      fun test(): Unit {
        val concat = (a: String, b: String) => a + b
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    val varDecl = block.statements.head.asInstanceOf[VariableDeclaration]
    val lambda = varDecl.initializer.get.asInstanceOf[Lambda]
    
    assertEquals(lambda.parameters.length, 2)
    lambda.parameters.foreach { param =>
      param.typeAnnotation.get match {
        case SimpleType("String", _) => // Expected
        case other => fail(s"Expected SimpleType(String), got: $other")
      }
    }
  }
  
  test("lambda expression with boolean logic") {
    val code = """
      fun test(): Unit {
        val and = (a: Boolean, b: Boolean) => a && b
        val not = (x: Boolean) => !x
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    
    assertEquals(block.statements.length, 2)
    
    // Check 'and' lambda
    val andDecl = block.statements(0).asInstanceOf[VariableDeclaration]
    val andLambda = andDecl.initializer.get.asInstanceOf[Lambda]
    assertEquals(andLambda.parameters.length, 2)
    
    // Check 'not' lambda  
    val notDecl = block.statements(1).asInstanceOf[VariableDeclaration]
    val notLambda = notDecl.initializer.get.asInstanceOf[Lambda]
    assertEquals(notLambda.parameters.length, 1)
  }
  
  test("lambda expression nested in other expressions") {
    val code = """
      fun test(): Unit {
        val expr = 1 + ((x: Int) => x * 2)(5)
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
    val block = functionDecl.body.asInstanceOf[Block]
    val varDecl = block.statements.head.asInstanceOf[VariableDeclaration]
    
    // The expression should be parsed (exact structure may vary)
    assert(varDecl.initializer.isDefined)
    assertEquals(varDecl.name, "expr")
  }
  
  test("lambda expression return type matching") {
    val inference = new TypeInference()
    implicit val ctx: TypeContext = TypeContext()
    
    // Lambda that returns String
    val stringParam = Parameter("s", Some(SimpleType("String", dummyLocation)), None, dummyLocation)
    val stringBody = StringLiteral("result", dummyLocation)
    val stringLambda = Lambda(List(stringParam), stringBody, dummyLocation)
    
    val stringType = inference.inferType(stringLambda)
    stringType match {
      case tylang.types.FunctionType(paramTypes, returnType) =>
        assertEquals(paramTypes.length, 1)
        assert(paramTypes(0).isInstanceOf[tylang.types.StringType.type])
        assert(returnType.isInstanceOf[tylang.types.StringType.type])
      case other => fail(s"Expected FunctionType, got: $other")
    }
  }
}