package tylang.repl

import munit.FunSuite
import tylang.ast.*
import tylang.parser.Parser
import tylang.compiler.CodeGenerator
import tylang.types.{TypeChecker, TypeContext}
import tylang.{SourceLocation}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream, File}
import java.net.{URL, URLClassLoader}
import scala.util.{Try, Success, Failure}

class REPLTest extends FunSuite {
  
  val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-repl-test"
  
  override def beforeEach(context: BeforeEach): Unit = {
    // Clean up temp directory
    val dir = new File(tempDir)
    if (dir.exists()) {
      dir.listFiles().foreach(_.delete())
    } else {
      dir.mkdirs()
    }
  }
  
  // Test actual REPL functionality by simulating complete workflows
  
  test("compile and execute simple arithmetic expression") {
    val repl = new REPL()
    
    // Initialize temp directory
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    // Process expression "3 + 4 * 2"
    val processMethod = repl.getClass.getDeclaredMethod("processExpression", classOf[String])
    processMethod.setAccessible(true)
    
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream))
    
    try {
      processMethod.invoke(repl, "3 + 4 * 2")
      val output = outputStream.toString
      
      // Should contain result showing 11 (not 14 due to operator precedence)
      assert(output.contains("res1: 11"), s"Expected 'res1: 11' in output, got: $output")
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("compile and execute function declaration then call it") {
    val repl = new REPL()
    
    // Initialize temp directory
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    // First define a function
    val processInputMethod = repl.getClass.getDeclaredMethod("processInput", classOf[String])
    processInputMethod.setAccessible(true)
    
    val outputStream1 = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream1))
    
    try {
      processInputMethod.invoke(repl, "fun square(x: Int): Int { x * x }")
      val output1 = outputStream1.toString
      assert(output1.contains("Function 'square' defined"), s"Function definition failed: $output1")
    } finally {
      System.setOut(originalOut)
    }
    
    // Then call the function with an expression
    val outputStream2 = new ByteArrayOutputStream()
    System.setOut(new PrintStream(outputStream2))
    
    try {
      processInputMethod.invoke(repl, "square(5)")
      val output2 = outputStream2.toString
      assert(output2.contains("res1: 25"), s"Function call failed: $output2")
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("compile and execute class declaration") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    val processInputMethod = repl.getClass.getDeclaredMethod("processInput", classOf[String])
    processInputMethod.setAccessible(true)
    
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream))
    
    try {
      val classCode = """class Point(x: Int, y: Int) {
        fun getX(): Int { x }
        fun getY(): Int { y }
        fun distance(): Double { 0.0 }
      }"""
      
      processInputMethod.invoke(repl, classCode)
      val output = outputStream.toString
      assert(output.contains("Class 'Point' defined"), s"Class definition failed: $output")
      
      // Verify class file was actually generated
      val classFile = new File(repl.getTempDir, "Point.class")
      assert(classFile.exists(), "Point.class file should be generated")
      
      // Try to load the class
      val classLoader = new URLClassLoader(Array(new File(repl.getTempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Point")
      assert(clazz != null, "Point class should be loadable")
      assert(!clazz.isInterface(), "Point should be a class, not interface")
      
      // Verify constructor exists
      val constructor = clazz.getConstructor(classOf[Int], classOf[Int])
      assert(constructor != null, "Point constructor should exist")
      
      // Verify methods exist
      val getXMethod = clazz.getMethod("getX")
      val getYMethod = clazz.getMethod("getY")
      val distanceMethod = clazz.getMethod("distance")
      assert(getXMethod != null, "getX method should exist")
      assert(getYMethod != null, "getY method should exist")
      assert(distanceMethod != null, "distance method should exist")
      
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("compile and execute object declaration") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    val processInputMethod = repl.getClass.getDeclaredMethod("processInput", classOf[String])
    processInputMethod.setAccessible(true)
    
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream))
    
    try {
      val objectCode = """object Math {
        fun pi(): Double { 3.14159 }
        fun e(): Double { 2.71828 }
      }"""
      
      processInputMethod.invoke(repl, objectCode)
      val output = outputStream.toString
      assert(output.contains("Object 'Math' defined"), s"Object definition failed: $output")
      
      // Verify class file was generated
      val classFile = new File(repl.getTempDir, "Math.class")
      assert(classFile.exists(), "Math.class file should be generated")
      
      // Try to load the class and verify singleton pattern
      val classLoader = new URLClassLoader(Array(new File(repl.getTempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Math")
      
      // Verify INSTANCE field exists
      val instanceField = clazz.getField("INSTANCE")
      assert(instanceField != null, "INSTANCE field should exist")
      assert(instanceField.getType.equals(clazz), "INSTANCE field should be of same type as class")
      
      // Verify singleton instance
      val instance = instanceField.get(null)
      assert(instance != null, "Singleton instance should exist")
      
      // Verify methods exist and work
      val piMethod = clazz.getMethod("pi")
      val pi = piMethod.invoke(instance).asInstanceOf[Double]
      assertEquals(pi, 3.14159, 0.0001)
      
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("compile and execute trait declaration") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    val processInputMethod = repl.getClass.getDeclaredMethod("processInput", classOf[String])
    processInputMethod.setAccessible(true)
    
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream))
    
    try {
      val traitCode = """trait Drawable {
        def draw(): Unit
      }"""
      
      processInputMethod.invoke(repl, traitCode)
      val output = outputStream.toString
      assert(output.contains("Trait 'Drawable' defined"), s"Trait definition failed: $output")
      
      // Verify class file was generated
      val classFile = new File(repl.getTempDir, "Drawable.class")
      assert(classFile.exists(), "Drawable.class file should be generated")
      
      // Try to load the class and verify it's an interface
      val classLoader = new URLClassLoader(Array(new File(repl.getTempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Drawable")
      assert(clazz.isInterface(), "Drawable should be an interface")
      
      // Verify abstract method exists
      val drawMethod = clazz.getMethod("draw")
      assert(drawMethod != null, "draw method should exist")
      
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("handle parse errors gracefully") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    val processInputMethod = repl.getClass.getDeclaredMethod("processInput", classOf[String])
    processInputMethod.setAccessible(true)
    
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream))
    
    try {
      // Invalid syntax should produce parse error
      processInputMethod.invoke(repl, "fun invalid syntax here")
      val output = outputStream.toString
      assert(output.contains("Parse error") || output.contains("Error"), s"Expected error message, got: $output")
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("handle type errors gracefully") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    val processInputMethod = repl.getClass.getDeclaredMethod("processInput", classOf[String])
    processInputMethod.setAccessible(true)
    
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(new PrintStream(outputStream))
    
    try {
      // This should cause a type error - adding int and string
      processInputMethod.invoke(repl, "fun badType(): Int { \"string\" + 1 }")
      val output = outputStream.toString
      // Should contain some kind of error message
      assert(output.toLowerCase.contains("error"), s"Expected error message, got: $output")
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("multiline isIncomplete detection works correctly") {
    val repl = new REPL()
    
    val method = repl.getClass.getDeclaredMethod("isIncomplete", classOf[String])
    method.setAccessible(true)
    
    // Test various incomplete patterns
    assert(method.invoke(repl, "fun add(x: Int").asInstanceOf[Boolean], "Unclosed parenthesis should be incomplete")
    assert(method.invoke(repl, "class Point {").asInstanceOf[Boolean], "Unclosed brace should be incomplete")
    assert(method.invoke(repl, "if (x > 0) {").asInstanceOf[Boolean], "Unclosed if block should be incomplete")
    assert(method.invoke(repl, "val x = [1, 2").asInstanceOf[Boolean], "Unclosed bracket should be incomplete")
    assert(method.invoke(repl, "\"unclosed string").asInstanceOf[Boolean], "Unclosed string should be incomplete")
    
    // Test complete patterns
    assert(!method.invoke(repl, "val x = 42").asInstanceOf[Boolean], "Simple assignment should be complete")
    assert(!method.invoke(repl, "1 + 2 * 3").asInstanceOf[Boolean], "Arithmetic should be complete")
    assert(!method.invoke(repl, "true && false").asInstanceOf[Boolean], "Boolean expression should be complete")
    assert(!method.invoke(repl, "fun add(x: Int, y: Int): Int { x + y }").asInstanceOf[Boolean], "Complete function should be complete")
  }
  
  test("session counter increments correctly") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    val sessionCounterField = repl.getClass.getDeclaredField("sessionCounter")
    sessionCounterField.setAccessible(true)
    
    val processExpressionMethod = repl.getClass.getDeclaredMethod("processExpression", classOf[String])
    processExpressionMethod.setAccessible(true)
    
    assertEquals(sessionCounterField.getInt(repl), 0)
    
    val originalOut = System.out
    val outputStream = new ByteArrayOutputStream()
    System.setOut(new PrintStream(outputStream))
    
    try {
      processExpressionMethod.invoke(repl, "1")
      assertEquals(sessionCounterField.getInt(repl), 1)
      
      processExpressionMethod.invoke(repl, "2")
      assertEquals(sessionCounterField.getInt(repl), 2)
      
      processExpressionMethod.invoke(repl, "3")
      assertEquals(sessionCounterField.getInt(repl), 3)
    } finally {
      System.setOut(originalOut)
    }
  }
  
  test("reset clears all session state") {
    val repl = new REPL()
    
    val initMethod = repl.getClass.getDeclaredMethod("initTempDir")
    initMethod.setAccessible(true)
    initMethod.invoke(repl)
    
    // Set up some state
    val sessionCounterField = repl.getClass.getDeclaredField("sessionCounter")
    sessionCounterField.setAccessible(true)
    sessionCounterField.setInt(repl, 10)
    
    val definedFunctionsField = repl.getClass.getDeclaredField("definedFunctions")
    definedFunctionsField.setAccessible(true)
    val definedFunctions = definedFunctionsField.get(repl).asInstanceOf[scala.collection.mutable.Set[String]]
    definedFunctions += "testFunc1"
    definedFunctions += "testFunc2"
    
    val definedClassesField = repl.getClass.getDeclaredField("definedClasses")
    definedClassesField.setAccessible(true)
    val definedClasses = definedClassesField.get(repl).asInstanceOf[scala.collection.mutable.Set[String]]
    definedClasses += "TestClass1"
    definedClasses += "TestClass2"
    
    // Create some files in temp directory
    new File(repl.getTempDir, "test1.class").createNewFile()
    new File(repl.getTempDir, "test2.class").createNewFile()
    
    // Verify state is set
    assertEquals(sessionCounterField.getInt(repl), 10)
    assertEquals(definedFunctions.size, 2)
    assertEquals(definedClasses.size, 2)
    assertEquals(new File(repl.getTempDir).listFiles().length, 2)
    
    // Reset
    val resetMethod = repl.getClass.getDeclaredMethod("reset")
    resetMethod.setAccessible(true)
    
    val originalOut = System.out
    val outputStream = new ByteArrayOutputStream()
    System.setOut(new PrintStream(outputStream))
    
    try {
      resetMethod.invoke(repl)
      val output = outputStream.toString
      assert(output.contains("REPL session reset"), s"Expected reset message, got: $output")
    } finally {
      System.setOut(originalOut)
    }
    
    // Verify state is cleared
    assertEquals(sessionCounterField.getInt(repl), 0)
    assertEquals(definedFunctions.size, 0)
    assertEquals(definedClasses.size, 0)
    assertEquals(new File(repl.getTempDir).listFiles().length, 0)
  }
}