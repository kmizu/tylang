package tylang.compiler

import munit.FunSuite
import tylang.ast.*
import tylang.parser.Parser
import tylang.{SourceLocation, CompileException}
import java.io.{File, ByteArrayOutputStream, PrintStream}
import java.net.{URL, URLClassLoader}
import java.lang.reflect.Method
import scala.util.{Try, Success, Failure}

class CodeGeneratorTest extends FunSuite {
  
  val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-test"
  
  def testLocation: SourceLocation = SourceLocation("<test>", (1, 1), "")
  
  override def beforeEach(context: BeforeEach): Unit = {
    // Clean up temp directory
    val dir = new File(tempDir)
    if (dir.exists()) {
      dir.listFiles().foreach(_.delete())
    } else {
      dir.mkdirs()
    }
  }
  
  test("generate simple function with integer return") {
    val program = Program(List(
      FunctionDeclaration(
        name = "add",
        typeParameters = List.empty,
        parameters = List(
          Parameter("x", Some(SimpleType("Int", testLocation)), None, testLocation),
          Parameter("y", Some(SimpleType("Int", testLocation)), None, testLocation)
        ),
        returnType = Some(SimpleType("Int", testLocation)),
        body = BinaryOp(
          Identifier("x", testLocation),
          "+",
          Identifier("y", testLocation),
          testLocation
        ),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    // Verify class file was created
    val classFile = new File(tempDir, "add$.class")
    assert(classFile.exists(), "Class file should be generated")
    
    // Try to load and invoke the generated method
    val result = loadAndInvokeStaticMethod("add$", "add", Array(classOf[Int], classOf[Int]), Array(Int.box(5), Int.box(3)))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 8)
      case Failure(ex) => fail(s"Failed to invoke generated method: ${ex.getMessage}")
    }
  }
  
  test("generate function with boolean operations") {
    val program = Program(List(
      FunctionDeclaration(
        name = "isEqual",
        typeParameters = List.empty,
        parameters = List(
          Parameter("a", Some(SimpleType("Int", testLocation)), None, testLocation),
          Parameter("b", Some(SimpleType("Int", testLocation)), None, testLocation)
        ),
        returnType = Some(SimpleType("Boolean", testLocation)),
        body = BinaryOp(
          Identifier("a", testLocation),
          "==",
          Identifier("b", testLocation),
          testLocation
        ),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "isEqual$.class")
    assert(classFile.exists())
    
    val result = loadAndInvokeStaticMethod("isEqual$", "isEqual", Array(classOf[Int], classOf[Int]), Array(Int.box(5), Int.box(5)))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[Boolean], true)
      case Failure(ex) => fail(s"Failed to invoke generated method: ${ex.getMessage}")
    }
  }
  
  test("generate function with string literal") {
    val program = Program(List(
      FunctionDeclaration(
        name = "greeting",
        typeParameters = List.empty,
        parameters = List.empty,
        returnType = Some(SimpleType("String", testLocation)),
        body = StringLiteral("Hello, World!", testLocation),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "greeting$.class")
    assert(classFile.exists())
    
    val result = loadAndInvokeStaticMethod("greeting$", "greeting", Array.empty, Array.empty)
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[String], "Hello, World!")
      case Failure(ex) => fail(s"Failed to invoke generated method: ${ex.getMessage}")
    }
  }
  
  test("generate function with unary operations") {
    val program = Program(List(
      FunctionDeclaration(
        name = "negate",
        typeParameters = List.empty,
        parameters = List(
          Parameter("x", Some(SimpleType("Int", testLocation)), None, testLocation)
        ),
        returnType = Some(SimpleType("Int", testLocation)),
        body = UnaryOp("-", Identifier("x", testLocation), testLocation),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "negate$.class")
    assert(classFile.exists())
    
    val result = loadAndInvokeStaticMethod("negate$", "negate", Array(classOf[Int]), Array(Int.box(42)))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], -42)
      case Failure(ex) => fail(s"Failed to invoke generated method: ${ex.getMessage}")
    }
  }
  
  test("generate function with block expression") {
    val program = Program(List(
      FunctionDeclaration(
        name = "calculate",
        typeParameters = List.empty,
        parameters = List(
          Parameter("x", Some(SimpleType("Int", testLocation)), None, testLocation)
        ),
        returnType = Some(SimpleType("Int", testLocation)),
        body = Block(List(
          VariableDeclaration("y", Some(SimpleType("Int", testLocation)), Some(IntLiteral(10, testLocation)), false, testLocation),
          ExpressionStatement(BinaryOp(
            Identifier("x", testLocation),
            "+",
            Identifier("y", testLocation),
            testLocation
          ), testLocation)
        ), testLocation),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "calculate$.class")
    assert(classFile.exists())
    
    val result = loadAndInvokeStaticMethod("calculate$", "calculate", Array(classOf[Int]), Array(Int.box(5)))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 15)
      case Failure(ex) => fail(s"Failed to invoke generated method: ${ex.getMessage}")
    }
  }
  
  test("generate simple class") {
    val program = Program(List(
      ClassDeclaration(
        name = "Person",
        typeParameters = List.empty,
        superClass = None,
        traits = List.empty,
        constructor = Some(Constructor(
          parameters = List(
            Parameter("name", Some(SimpleType("String", testLocation)), None, testLocation),
            Parameter("age", Some(SimpleType("Int", testLocation)), None, testLocation)
          ),
          body = None,
          location = testLocation
        )),
        members = List(
          FieldMember(VariableDeclaration("name", Some(SimpleType("String", testLocation)), None, false, testLocation)),
          FieldMember(VariableDeclaration("age", Some(SimpleType("Int", testLocation)), None, false, testLocation)),
          MethodMember(FunctionDeclaration(
            name = "getName",
            typeParameters = List.empty,
            parameters = List.empty,
            returnType = Some(SimpleType("String", testLocation)),
            body = Identifier("name", testLocation),
            location = testLocation
          ))
        ),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "Person.class")
    assert(classFile.exists(), "Person class file should be generated")
    
    // Try to load and instantiate the class
    val result = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Person")
      val constructor = clazz.getConstructor(classOf[String], classOf[Int])
      val instance = constructor.newInstance("Alice", Int.box(30))
      
      val getNameMethod = clazz.getMethod("getName")
      val name = getNameMethod.invoke(instance)
      name.asInstanceOf[String]
    }
    
    result match {
      case Success(name) => assertEquals(name, "Alice")
      case Failure(ex) => fail(s"Failed to test generated class: ${ex.getMessage}")
    }
  }
  
  test("generate object (singleton)") {
    val program = Program(List(
      ObjectDeclaration(
        name = "Utils",
        superClass = None,
        traits = List.empty,
        members = List(
          MethodMember(FunctionDeclaration(
            name = "pi",
            typeParameters = List.empty,
            parameters = List.empty,
            returnType = Some(SimpleType("Double", testLocation)),
            body = DoubleLiteral(3.14159, testLocation),
            location = testLocation
          ))
        ),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "Utils.class")
    assert(classFile.exists(), "Utils object class file should be generated")
    
    // Test singleton pattern
    val result = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Utils")
      val instanceField = clazz.getField("INSTANCE")
      val instance = instanceField.get(null)
      
      val piMethod = clazz.getMethod("pi")
      val pi = piMethod.invoke(instance)
      pi.asInstanceOf[Double]
    }
    
    result match {
      case Success(pi) => assertEquals(pi, 3.14159, 0.0001)
      case Failure(ex) => fail(s"Failed to test generated object: ${ex.getMessage}")
    }
  }
  
  test("generate trait (interface)") {
    val program = Program(List(
      TraitDeclaration(
        name = "Drawable",
        typeParameters = List.empty,
        superTraits = List.empty,
        members = List(
          AbstractMethodMember(
            name = "draw",
            typeParameters = List.empty,
            parameters = List.empty,
            returnType = Some(SimpleType("Unit", testLocation)),
            location = testLocation
          )
        ),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "Drawable.class")
    assert(classFile.exists(), "Drawable trait class file should be generated")
    
    // Verify it's an interface
    val result = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Drawable")
      clazz.isInterface()
    }
    
    result match {
      case Success(isInterface) => assert(isInterface, "Generated trait should be an interface")
      case Failure(ex) => fail(s"Failed to test generated trait: ${ex.getMessage}")
    }
  }
  
  test("generate extension methods") {
    val program = Program(List(
      ExtensionDeclaration(
        targetType = SimpleType("String", testLocation),
        methods = List(
          FunctionDeclaration(
            name = "reverse",
            typeParameters = List.empty,
            parameters = List.empty,
            returnType = Some(SimpleType("String", testLocation)),
            body = StringLiteral("reversed", testLocation), // Simplified for testing
            location = testLocation
          )
        ),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "String$Extension.class")
    assert(classFile.exists(), "Extension class file should be generated")
    
    val result = loadAndInvokeStaticMethod("String$Extension", "reverse", Array(classOf[String]), Array("hello"))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[String], "reversed")
      case Failure(ex) => fail(s"Failed to invoke extension method: ${ex.getMessage}")
    }
  }
  
  test("parse and generate from source code") {
    val sourceCode = """
      fun add_one(n: Int): Int {
        n + 1
      }
    """
    
    val program = Parser.parse(sourceCode.trim, "<test>")
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "add_one$.class")
    assert(classFile.exists(), "add_one class file should be generated")
    
    // Verify the generated method works
    val result = loadAndInvokeStaticMethod("add_one$", "add_one", Array(classOf[Int]), Array(Int.box(41)))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 42)
      case Failure(ex) => fail(s"Failed to invoke generated method: ${ex.getMessage}")
    }
  }
  
  test("lambda expression generation") {
    val program = Program(List(
      FunctionDeclaration(
        name = "test",
        typeParameters = List.empty,
        parameters = List.empty,
        returnType = Some(SimpleType("Unit", testLocation)),
        body = Lambda(List.empty, IntLiteral(42, testLocation), testLocation),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    generator.generateProgram(program, tempDir)
    
    val classFile = new File(tempDir, "test$.class")
    assert(classFile.exists(), "test class file should be generated")
  }
  
  test("error handling - undefined variable") {
    val program = Program(List(
      FunctionDeclaration(
        name = "test",
        typeParameters = List.empty,
        parameters = List.empty,
        returnType = Some(SimpleType("Int", testLocation)),
        body = Identifier("undefinedVar", testLocation),
        location = testLocation
      )
    ), testLocation)
    
    val generator = new CodeGenerator()
    
    intercept[CompileException] {
      generator.generateProgram(program, tempDir)
    }
  }
  
  // Helper method to load and invoke static methods
  private def loadAndInvokeStaticMethod(
    className: String,
    methodName: String,
    paramTypes: Array[Class[_]],
    args: Array[Object]
  ): Try[Object] = {
    Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass(className)
      val method = clazz.getMethod(methodName, paramTypes: _*)
      method.invoke(null, args: _*)
    }
  }
}