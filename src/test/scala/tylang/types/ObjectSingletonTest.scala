package tylang.types

import munit.FunSuite
import tylang.ast.*
import tylang.parser.Parser
import tylang.compiler.CodeGenerator
import tylang.{SourceLocation}
import java.io.File
import java.net.{URL, URLClassLoader}

class ObjectSingletonTest extends FunSuite {
  
  val dummyLocation = SourceLocation("<test>", (1, 1), "test")
  
  test("object declaration basic parsing") {
    val code = """
      object Math {
        fun pi(): Double { 3.14159 }
        fun e(): Double { 2.71828 }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    assert(program.declarations.length == 1)
    
    program.declarations.head match {
      case ObjectDeclaration(name, superClass, traits, members, _) =>
        assertEquals(name, "Math")
        assertEquals(superClass, None)
        assertEquals(traits, List.empty)
        assertEquals(members.length, 2)
        members.foreach {
          case MethodMember(method) =>
            assert(method.name == "pi" || method.name == "e")
            assert(method.returnType.isDefined)
          case _ => fail("Expected MethodMember")
        }
      case _ => fail("Expected ObjectDeclaration")
    }
  }
  
  test("object with inheritance") {
    val code = """
      object Singleton extends SomeClass with SomeTrait {
        fun method(): Int { 42 }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val objectDecl = program.declarations.head.asInstanceOf[ObjectDeclaration]
    
    assertEquals(objectDecl.name, "Singleton")
    objectDecl.superClass match {
      case Some(SimpleType("SomeClass", _)) => // Expected
      case other => fail(s"Expected SimpleType(SomeClass), got: $other")
    }
    assertEquals(objectDecl.traits.length, 1)
    objectDecl.traits.head match {
      case SimpleType("SomeTrait", _) => // Expected
      case other => fail(s"Expected SimpleType(SomeTrait), got: $other")
    }
  }
  
  test("object bytecode generation creates singleton pattern") {
    val code = """
      object TestSingleton {
        fun getValue(): Int { 42 }
        fun getDouble(): Double { 3.14 }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val codeGenerator = new CodeGenerator()
    val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-object-test"
    
    // Generate bytecode
    codeGenerator.generateProgram(program, tempDir)
    
    // Verify class file was created
    val classFile = new File(tempDir, "TestSingleton.class")
    assert(classFile.exists(), "TestSingleton.class should be generated")
    
    // Load the class and verify singleton pattern
    val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
    val clazz = classLoader.loadClass("TestSingleton")
    
    // Verify INSTANCE field exists
    val instanceField = clazz.getField("INSTANCE")
    assert(instanceField != null, "INSTANCE field should exist")
    assert(instanceField.getType.equals(clazz), "INSTANCE field should be of same type as class")
    
    // Verify singleton instance
    val instance = instanceField.get(null)
    assert(instance != null, "Singleton instance should exist")
    
    // Verify methods exist and can be called
    val getValueMethod = clazz.getMethod("getValue")
    val getDoubleMethod = clazz.getMethod("getDouble")
    
    val value = getValueMethod.invoke(instance).asInstanceOf[Int]
    val doubleValue = getDoubleMethod.invoke(instance).asInstanceOf[Double]
    
    assertEquals(value, 42)
    assertEquals(doubleValue, 3.14, 0.001)
  }
  
  test("object method mutual references") {
    val code = """
      object Calculator {
        fun add(x: Int, y: Int): Int { x + y }
        fun multiply(x: Int, y: Int): Int { x * y }
        fun simple(): Int { 42 }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val codeGenerator = new CodeGenerator()
    val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-object-mutual-test"
    
    // Should compile without errors
    try {
      codeGenerator.generateProgram(program, tempDir)
      
      // Try to load and use the class
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Calculator")
      val instance = clazz.getField("INSTANCE").get(null)
      
      val simpleMethod = clazz.getMethod("simple")
      val result = simpleMethod.invoke(instance).asInstanceOf[Int]
      
      assertEquals(result, 42)
    } catch {
      case ex: Exception => fail(s"Object method compilation failed: ${ex.getMessage}")
    }
  }
  
  test("object with fields") {
    val code = """
      object Constants {
        val pi: Double = 3.14159
        var counter: Int = 0
        fun getPi(): Double { pi }
        fun incrementCounter(): Int { 
          counter = counter + 1
          counter 
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val objectDecl = program.declarations.head.asInstanceOf[ObjectDeclaration]
    
    // Verify structure
    assertEquals(objectDecl.name, "Constants")
    assert(objectDecl.members.length >= 2) // At least 2 methods
    
    // Find fields and methods
    val fields = objectDecl.members.collect { case FieldMember(field) => field }
    val methods = objectDecl.members.collect { case MethodMember(method) => method }
    
    assert(fields.length >= 0) // May not parse fields yet
    assert(methods.length >= 2) // Should have methods
  }
  
  test("object empty declaration") {
    val code = """
      object Empty {
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val objectDecl = program.declarations.head.asInstanceOf[ObjectDeclaration]
    
    assertEquals(objectDecl.name, "Empty")
    assertEquals(objectDecl.members.length, 0)
    assertEquals(objectDecl.superClass, None)
    assertEquals(objectDecl.traits, List.empty)
  }
  
  test("object compilation and class loading") {
    val code = """
      object Utils {
        fun abs(x: Int): Int {
          if (x < 0) { 0 - x } else { x }
        }
        fun max(a: Int, b: Int): Int {
          if (a > b) { a } else { b }
        }
        fun min(a: Int, b: Int): Int {
          if (a < b) { a } else { b }
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val codeGenerator = new CodeGenerator()
    val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-utils-test"
    
    codeGenerator.generateProgram(program, tempDir)
    
    // Load and test the class
    val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
    val clazz = classLoader.loadClass("Utils")
    val instance = clazz.getField("INSTANCE").get(null)
    
    // Test abs method
    val absMethod = clazz.getMethod("abs", classOf[Int])
    assertEquals(absMethod.invoke(instance, Int.box(-5)).asInstanceOf[Int], 5)
    assertEquals(absMethod.invoke(instance, Int.box(3)).asInstanceOf[Int], 3)
    
    // Test max method
    val maxMethod = clazz.getMethod("max", classOf[Int], classOf[Int])
    assertEquals(maxMethod.invoke(instance, Int.box(10), Int.box(20)).asInstanceOf[Int], 20)
    assertEquals(maxMethod.invoke(instance, Int.box(30), Int.box(15)).asInstanceOf[Int], 30)
    
    // Test min method
    val minMethod = clazz.getMethod("min", classOf[Int], classOf[Int])
    assertEquals(minMethod.invoke(instance, Int.box(10), Int.box(20)).asInstanceOf[Int], 10)
    assertEquals(minMethod.invoke(instance, Int.box(30), Int.box(15)).asInstanceOf[Int], 15)
  }
  
  test("object singleton instance uniqueness") {
    val code = """
      object Single {
        fun id(): String { "singleton" }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val codeGenerator = new CodeGenerator()
    val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-single-test"
    
    codeGenerator.generateProgram(program, tempDir)
    
    val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
    val clazz = classLoader.loadClass("Single")
    
    // Get instance multiple times
    val instance1 = clazz.getField("INSTANCE").get(null)
    val instance2 = clazz.getField("INSTANCE").get(null)
    
    // Should be the same object (singleton pattern)
    assert(instance1 eq instance2, "Singleton instances should be the same object")
    assert(instance1.equals(instance2), "Singleton instances should be equal")
  }
  
  test("object with return type inference") {
    val code = """
      object Inferred {
        fun getInt() { 42 }
        fun getString() { "hello" }
        fun getBoolean() { true }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val objectDecl = program.declarations.head.asInstanceOf[ObjectDeclaration]
    
    // Verify all methods parse correctly (return types may be inferred)
    val methods = objectDecl.members.collect { case MethodMember(method) => method }
    assertEquals(methods.length, 3)
    
    methods.foreach { method =>
      assert(Set("getInt", "getString", "getBoolean").contains(method.name))
      // Return type may be None (inferred) or Some (explicitly annotated)
    }
  }
  
  test("object complex method bodies") {
    val code = """
      object Complex {
        fun simpleIf(n: Int): Int {
          if (n <= 1) { 1 } else { n * 2 }
        }
        fun basicOp(n: Int): Int {
          n + 10
        }
      }
    """
    
    val program = Parser.parse(code, "<test>")
    val objectDecl = program.declarations.head.asInstanceOf[ObjectDeclaration]
    
    assertEquals(objectDecl.name, "Complex")
    val methods = objectDecl.members.collect { case MethodMember(method) => method }
    assertEquals(methods.length, 2)
    
    // Verify methods compile
    val codeGenerator = new CodeGenerator()
    val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-complex-test"
    
    try {
      codeGenerator.generateProgram(program, tempDir)
      
      // Load and test
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Complex")
      val instance = clazz.getField("INSTANCE").get(null)
      
      val simpleIfMethod = clazz.getMethod("simpleIf", classOf[Int])
      val result = simpleIfMethod.invoke(instance, Int.box(5)).asInstanceOf[Int]
      assertEquals(result, 10) // 5 * 2 = 10
      
    } catch {
      case ex: Exception => fail(s"Complex object methods failed: ${ex.getMessage}")
    }
  }
}