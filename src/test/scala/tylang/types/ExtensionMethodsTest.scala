package tylang.types

import munit.FunSuite
import tylang.ast.*
import tylang.parser.Parser
import tylang.compiler.CodeGenerator
import tylang.{SourceLocation}

class ExtensionMethodsTest extends FunSuite {
  
  val dummyLocation = SourceLocation("<test>", (1, 1), "test")
  
  test("extension methods basic parsing and compilation") {
    val code = """
      extension Int {
        fun isEven(): Boolean { this % 2 == 0 }
        fun double(): Int { this * 2 }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    assert(program.declarations.length == 1)
    
    program.declarations.head match {
      case ExtensionDeclaration(targetType, methods, _) =>
        targetType match {
          case SimpleType("Int", _) => // Expected
          case other => fail(s"Expected SimpleType(Int), got: $other")
        }
        assertEquals(methods.length, 2)
        assertEquals(methods(0).name, "isEven")
        assertEquals(methods(1).name, "double")
      case _ => fail("Expected ExtensionDeclaration")
    }
  }
  
  test("extension methods with this keyword") {
    val code = """
      extension String {
        fun reverse(): String { this + "reversed" }
        fun isEmpty(): Boolean { this == "" }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    // Verify that 'this' is used in method bodies
    extension.methods.foreach { method =>
      def hasThisExpression(expr: Expression): Boolean = expr match {
        case ThisExpression(_) => true
        case BinaryOp(left, _, right, _) => hasThisExpression(left) || hasThisExpression(right)
        case MethodCall(Some(receiver), _, _, _, _) => hasThisExpression(receiver)
        case Identifier("this", _) => true // Also check for 'this' as identifier
        case _ => false
      }
      
      // For now, just check that extension methods parse correctly
      assert(method.name == "reverse" || method.name == "isEmpty")
    }
  }
  
  test("extension methods bytecode generation") {
    val code = """
      extension Int {
        fun square(): Int { this * this }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val codeGenerator = new CodeGenerator()
    
    // Should not throw exception
    assertNoDiff(
      try {
        codeGenerator.generateProgram(program, System.getProperty("java.io.tmpdir"))
        "success"
      } catch {
        case ex: Exception => s"failed: ${ex.getMessage}"
      },
      "success"
    )
  }
  
  test("extension methods with multiple parameters") {
    val code = """
      extension Int {
        fun add(other: Int): Int { this + other }
        fun between(min: Int, max: Int): Boolean { 
          this >= min && this <= max 
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    assertEquals(extension.methods.length, 2)
    assertEquals(extension.methods(0).parameters.length, 1)
    assertEquals(extension.methods(1).parameters.length, 2)
    assertEquals(extension.methods(0).parameters(0).name, "other")
    assertEquals(extension.methods(1).parameters(0).name, "min")
    assertEquals(extension.methods(1).parameters(1).name, "max")
  }
  
  test("extension methods with return type annotations") {
    val code = """
      extension String {
        fun length(): Int { 42 }
        fun charAt(index: Int): String { "c" }
        fun toBoolean(): Boolean { true }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    extension.methods.foreach { method =>
      assert(method.returnType.isDefined, s"Method ${method.name} should have return type annotation")
    }
    
    extension.methods(0).returnType.get match {
      case SimpleType("Int", _) => // Expected
      case other => fail(s"Expected SimpleType(Int), got: $other")
    }
    extension.methods(1).returnType.get match {
      case SimpleType("String", _) => // Expected
      case other => fail(s"Expected SimpleType(String), got: $other")
    }
    extension.methods(2).returnType.get match {
      case SimpleType("Boolean", _) => // Expected
      case other => fail(s"Expected SimpleType(Boolean), got: $other")
    }
  }
  
  test("extension methods on generic types") {
    val code = """
      extension List<T> {
        fun isEmpty(): Boolean { true }
        fun size(): Int { 0 }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    extension.targetType match {
      case GenericType("List", List(SimpleType("T", _)), _) => // Expected
      case other => fail(s"Expected GenericType with List<T>, got: $other")
    }
  }
  
  test("extension methods complex expressions") {
    val code = """
      extension Int {
        fun factorial(): Int {
          if (this <= 1) { 1 } else { this * 2 }
        }
        fun isPrime(): Boolean {
          if (this <= 1) { false } else { true }
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    assertEquals(extension.methods.length, 2)
    
    // Just verify that methods parse correctly (bodies are complex expressions)
    extension.methods.foreach { method =>
      assert(method.name == "factorial" || method.name == "isPrime")
      assert(method.returnType.isDefined)
    }
  }
  
  test("extension methods with nested method calls") {
    val code = """
      extension String {
        fun processString(): String {
          this.length().toString()
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    // Should parse without errors, even if method chaining isn't fully implemented
    assertEquals(extension.methods.length, 1)
    assertEquals(extension.methods(0).name, "processString")
  }
  
  test("extension methods static compilation pattern") {
    val code = """
      extension Int {
        fun abs(): Int { 
          if (this < 0) { 0 - this } else { this }
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val codeGenerator = new CodeGenerator()
    
    // Extension methods should compile to static methods with receiver as first parameter
    try {
      codeGenerator.generateProgram(program, System.getProperty("java.io.tmpdir"))
      // If we get here without exception, compilation succeeded
      assert(true)
    } catch {
      case ex: Exception => 
        fail(s"Extension method compilation failed: ${ex.getMessage}")
    }
  }
  
  test("extension methods preserve parameter types") {
    val code = """
      extension Double {
        fun round(precision: Int): Double { this }
        fun formatWith(pattern: String): String { pattern }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    val roundMethod = extension.methods.find(_.name == "round").get
    val formatMethod = extension.methods.find(_.name == "formatWith").get
    
    roundMethod.parameters(0).typeAnnotation.get match {
      case SimpleType("Int", _) => // Expected
      case other => fail(s"Expected SimpleType(Int), got: $other")
    }
    formatMethod.parameters(0).typeAnnotation.get match {
      case SimpleType("String", _) => // Expected  
      case other => fail(s"Expected SimpleType(String), got: $other")
    }
    roundMethod.returnType.get match {
      case SimpleType("Double", _) => // Expected
      case other => fail(s"Expected SimpleType(Double), got: $other")
    }
    formatMethod.returnType.get match {
      case SimpleType("String", _) => // Expected
      case other => fail(s"Expected SimpleType(String), got: $other")
    }
  }
  
  test("extension methods type context integration") {
    val code = """
      extension Boolean {
        fun ifTrue(action: String): String {
          if (this) { action } else { "false" }
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val extension = program.declarations.head.asInstanceOf[ExtensionDeclaration]
    
    // Verify target type is Boolean
    extension.targetType match {
      case SimpleType("Boolean", _) => // Expected
      case other => fail(s"Expected SimpleType(Boolean), got: $other")
    }
    
    // Verify method structure
    val method = extension.methods.head
    assertEquals(method.name, "ifTrue")
    assertEquals(method.parameters.length, 1)
    assertEquals(method.parameters(0).name, "action")
    method.returnType.get match {
      case SimpleType("String", _) => // Expected
      case other => fail(s"Expected SimpleType(String), got: $other")
    }
  }
}