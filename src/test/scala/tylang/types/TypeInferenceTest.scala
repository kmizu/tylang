package tylang.types

import munit.FunSuite
import tylang.ast._
import tylang.{TypeException, SourceLocation}

class TypeInferenceTest extends FunSuite {
  
  private val dummyLocation = SourceLocation("test.ty", (0, 0), "")
  
  test("infer literal types") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Integer literal
    val intLit = IntLiteral(42, dummyLocation)
    assertEquals(inference.inferType(intLit), tylang.types.IntType)
    
    // Double literal
    val doubleLit = DoubleLiteral(3.14, dummyLocation)
    assertEquals(inference.inferType(doubleLit), tylang.types.DoubleType)
    
    // String literal
    val stringLit = StringLiteral("hello", dummyLocation)
    assertEquals(inference.inferType(stringLit), tylang.types.StringType)
    
    // Boolean literal
    val boolLit = BooleanLiteral(true, dummyLocation)
    assertEquals(inference.inferType(boolLit), tylang.types.BooleanType)
  }
  
  test("infer variable types from context") {
    val ctx = TypeContext()
      .withType("x", tylang.types.IntType)
      .withType("name", tylang.types.StringType)
      .withType("flag", tylang.types.BooleanType)
    
    val inference = TypeInference()
    
    val xRef = Identifier("x", dummyLocation)
    assertEquals(inference.inferType(xRef)(ctx), tylang.types.IntType)
    
    val nameRef = Identifier("name", dummyLocation)
    assertEquals(inference.inferType(nameRef)(ctx), tylang.types.StringType)
    
    val flagRef = Identifier("flag", dummyLocation)
    assertEquals(inference.inferType(flagRef)(ctx), tylang.types.BooleanType)
  }
  
  test("infer binary operation types") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Arithmetic operations return Int
    val addition = BinaryOp(IntLiteral(1, dummyLocation), "+", IntLiteral(2, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(addition), tylang.types.IntType)
    
    val multiplication = BinaryOp(IntLiteral(3, dummyLocation), "*", IntLiteral(4, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(multiplication), tylang.types.IntType)
    
    // Comparison operations return Boolean
    val comparison = BinaryOp(IntLiteral(1, dummyLocation), "<", IntLiteral(2, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(comparison), tylang.types.BooleanType)
    
    val equality = BinaryOp(IntLiteral(1, dummyLocation), "==", IntLiteral(1, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(equality), tylang.types.BooleanType)
    
    // Logical operations return Boolean
    val logicalAnd = BinaryOp(BooleanLiteral(true, dummyLocation), "&&", BooleanLiteral(false, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(logicalAnd), tylang.types.BooleanType)
  }
  
  test("infer unary operation types") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Negation returns Int
    val negation = UnaryOp("-", IntLiteral(5, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(negation), tylang.types.IntType)
    
    // Logical not returns Boolean
    val not = UnaryOp("!", BooleanLiteral(true, dummyLocation), dummyLocation)
    assertEquals(inference.inferType(not), tylang.types.BooleanType)
  }
  
  test("infer if expression type") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Both branches return same type
    val ifExpr = IfExpression(
      condition = BooleanLiteral(true, dummyLocation),
      thenBranch = IntLiteral(1, dummyLocation),
      elseBranch = Some(IntLiteral(2, dummyLocation)),
      location = dummyLocation
    )
    assertEquals(inference.inferType(ifExpr), tylang.types.IntType)
    
    // If without else returns Unit
    val ifNoElse = IfExpression(
      condition = BooleanLiteral(true, dummyLocation),
      thenBranch = IntLiteral(1, dummyLocation),
      elseBranch = None,
      location = dummyLocation
    )
    assertEquals(inference.inferType(ifNoElse), tylang.types.UnitType)
  }
  
  test("infer block expression type") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Empty block returns Unit
    val emptyBlock = Block(List(), dummyLocation)
    assertEquals(inference.inferType(emptyBlock), tylang.types.UnitType)
    
    // Block returns type of last expression
    val block = Block(
      List(
        ExpressionStatement(IntLiteral(1, dummyLocation), dummyLocation),
        ExpressionStatement(StringLiteral("hello", dummyLocation), dummyLocation)
      ),
      dummyLocation
    )
    assertEquals(inference.inferType(block), tylang.types.StringType)
  }
  
  test("infer method call type") {
    val ctx = TypeContext()
      .withType("toString", tylang.types.FunctionType(List(), tylang.types.StringType))
      .withType("add", tylang.types.FunctionType(List(tylang.types.IntType, tylang.types.IntType), tylang.types.IntType))
    
    val inference = TypeInference()
    
    // No-arg method
    val toStringCall = MethodCall(None, "toString", List(), List(), dummyLocation)
    assertEquals(inference.inferType(toStringCall)(ctx), tylang.types.StringType)
    
    // Method with arguments
    val addCall = MethodCall(
      None, 
      "add", 
      List(IntLiteral(1, dummyLocation), IntLiteral(2, dummyLocation)), 
      List(), 
      dummyLocation
    )
    assertEquals(inference.inferType(addCall)(ctx), tylang.types.IntType)
  }
  
  test("infer lambda expression type") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Lambda with explicit parameter types
    val lambda = Lambda(
      parameters = List(
        Parameter("x", Some(SimpleType("Int", dummyLocation)), None, dummyLocation),
        Parameter("y", Some(SimpleType("Int", dummyLocation)), None, dummyLocation)
      ),
      body = BinaryOp(Identifier("x", dummyLocation), "+", Identifier("y", dummyLocation), dummyLocation),
      location = dummyLocation
    )
    
    val lambdaType = inference.inferType(lambda)
    assert(lambdaType.isInstanceOf[tylang.types.FunctionType])
    
    val funcType = lambdaType.asInstanceOf[tylang.types.FunctionType]
    assertEquals(funcType.parameterTypes.length, 2)
    assertEquals(funcType.returnType, tylang.types.IntType)
  }
  
  test("infer field access type") {
    val pointType = tylang.types.ClassType(
      name = "Point",
      typeArguments = List(),
      superClass = Some(tylang.types.AnyType),
      traits = List(),
      members = Map(
        "x" -> tylang.types.IntType,
        "y" -> tylang.types.IntType
      )
    )
    
    val ctx = TypeContext()
      .withType("point", pointType)
    
    val inference = TypeInference()
    
    // Accessing field returns field type
    val fieldAccess = FieldAccess(
      receiver = Identifier("point", dummyLocation),
      fieldName = "x",
      location = dummyLocation
    )
    
    assertEquals(inference.inferType(fieldAccess)(ctx), tylang.types.IntType)
  }
  
  test("infer list literal type") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Empty list creates a type variable (can be unified later)
    val emptyList = ListLiteral(List(), dummyLocation)
    val emptyListType = inference.inferType(emptyList)
    assert(emptyListType.isInstanceOf[tylang.types.ListType])
    assert(emptyListType.asInstanceOf[tylang.types.ListType].elementType.isInstanceOf[tylang.types.TypeVariable])
    
    // List with elements infers element type
    val intList = ListLiteral(
      List(IntLiteral(1, dummyLocation), IntLiteral(2, dummyLocation)), 
      dummyLocation
    )
    assertEquals(inference.inferType(intList), tylang.types.ListType(tylang.types.IntType))
  }
  
  test("infer type with type variables") {
    val typeVar = tylang.types.TypeVariable("T", 1)
    val ctx = TypeContext()
      .withType("value", typeVar)
      .withConstraint(typeVar, tylang.types.StringType)
    
    val inference = TypeInference()
    
    val valueRef = Identifier("value", dummyLocation)
    val inferredType = inference.inferType(valueRef)(ctx)
    
    // Type variable should resolve to its constraint
    assertEquals(inferredType, typeVar)
  }
  
  test("type inference error handling") {
    implicit val ctx: TypeContext = TypeContext()
    val inference = TypeInference()
    
    // Undefined variable
    val undefined = Identifier("undefined", dummyLocation)
    intercept[TypeException] {
      inference.inferType(undefined)
    }
    
    // Type mismatch in binary operation (string concatenation with non-string left side)
    val mismatch = BinaryOp(IntLiteral(1, dummyLocation), "*", StringLiteral("hello", dummyLocation), dummyLocation)
    intercept[TypeException] {
      inference.inferType(mismatch)
    }
  }
}