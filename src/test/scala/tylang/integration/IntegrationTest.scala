package tylang.integration

import munit.FunSuite
import tylang.Main
import java.io.{ByteArrayOutputStream, PrintStream, File}
import java.net.URLClassLoader
import scala.util.{Try, Success, Failure}

class IntegrationTest extends FunSuite {
  
  val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-integration-test"
  
  override def beforeEach(context: BeforeEach): Unit = {
    // Clean up temp directory
    val dir = new File(tempDir)
    if (dir.exists()) {
      dir.listFiles().foreach(_.delete())
    } else {
      dir.mkdirs()
    }
  }
  
  def captureOutput(operation: => Unit): String = {
    val outputStream = new ByteArrayOutputStream()
    val originalOut = System.out
    val originalErr = System.err
    System.setOut(new PrintStream(outputStream))
    System.setErr(new PrintStream(outputStream))
    try {
      operation
      val result = outputStream.toString
      println(s"DEBUG: Captured output: '$result'")
      result
    } finally {
      System.setOut(originalOut)
      System.setErr(originalErr)
    }
  }
  
  def writeTestFile(filename: String, content: String): String = {
    val file = new File(tempDir, filename)
    val writer = new java.io.FileWriter(file)
    try {
      writer.write(content)
      file.getAbsolutePath
    } finally {
      writer.close()
    }
  }
  
  def loadAndInvokeStaticMethod(
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
  
  test("compile and execute simple function") {
    val sourceCode = """
      fun add(x: Int, y: Int): Int {
        x + y
      }
    """
    
    val filePath = writeTestFile("test_add.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    println(s"DEBUG: Output for simple function: '$output'")
    // Check compilation succeeded by verifying class file was generated
    assert(new File(tempDir, "add$.class").exists(), "add$.class should be generated")
    
    // Test the generated function
    val result = loadAndInvokeStaticMethod("add$", "add", Array(classOf[Int], classOf[Int]), Array(Int.box(5), Int.box(3)))
    result match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 8)
      case Failure(ex) => fail(s"Failed to invoke generated function: ${ex.getMessage}")
    }
  }
  
  test("compile and execute recursive function") {
    val sourceCode = """
      fun factorial(n: Int): Int {
        if (n <= 1) {
          1
        } else {
          n * factorial(n - 1)
        }
      }
    """
    
    val filePath = writeTestFile("test_factorial.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation succeeded by verifying class file was generated
    assert(new File(tempDir, "factorial$.class").exists(), "factorial$.class should be generated")
    
    // Test factorial function
    val result5 = loadAndInvokeStaticMethod("factorial$", "factorial", Array(classOf[Int]), Array(Int.box(5)))
    result5 match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 120)
      case Failure(ex) => fail(s"Failed to invoke factorial(5): ${ex.getMessage}")
    }
    
    val result0 = loadAndInvokeStaticMethod("factorial$", "factorial", Array(classOf[Int]), Array(Int.box(0)))
    result0 match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 1)
      case Failure(ex) => fail(s"Failed to invoke factorial(0): ${ex.getMessage}")
    }
  }
  
  test("compile and execute class with methods") {
    val sourceCode = """
      class Point(x: Int, y: Int) {
        fun getX(): Int { x }
        fun getY(): Int { y }
      }
    """
    
    val filePath = writeTestFile("test_class.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation succeeded by verifying class file was generated
    assert(new File(tempDir, "Point.class").exists(), "Point.class should be generated")
    
    // Test the generated class
    val result = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Point")
      val constructor = clazz.getConstructor(classOf[Int], classOf[Int])
      val instance = constructor.newInstance(Int.box(10), Int.box(20))
      
      val getX = clazz.getMethod("getX")
      val getY = clazz.getMethod("getY")
      
      val x = getX.invoke(instance).asInstanceOf[Int]
      val y = getY.invoke(instance).asInstanceOf[Int]
      
      (x, y)
    }
    
    result match {
      case Success((x, y)) =>
        assertEquals(x, 10)
        assertEquals(y, 20)
      case Failure(ex) => fail(s"Failed to test Point class: ${ex.getMessage}")
    }
  }
  
  test("compile and execute object (singleton)") {
    val sourceCode = """
      object Math {
        fun pi(): Double { 3.14159 }
        fun square(x: Int): Int { x * x }
      }
    """
    
    val filePath = writeTestFile("test_object.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation succeeded by verifying class file was generated
    assert(new File(tempDir, "Math.class").exists(), "Math.class should be generated")
    
    // Test the generated object
    val result = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Math")
      val instanceField = clazz.getField("INSTANCE")
      val instance = instanceField.get(null)
      
      val pi = clazz.getMethod("pi")
      val square = clazz.getMethod("square", classOf[Int])
      
      val piValue = pi.invoke(instance).asInstanceOf[Double]
      val squareValue = square.invoke(instance, Int.box(7)).asInstanceOf[Int]
      
      (piValue, squareValue)
    }
    
    result match {
      case Success((piValue, squareValue)) =>
        assertEquals(piValue, 3.14159, 0.0001)
        assertEquals(squareValue, 49)
      case Failure(ex) => fail(s"Failed to test Math object: ${ex.getMessage}")
    }
  }
  
  test("compile complete sample program") {
    val sourceCode = """
      fun add(x: Int, y: Int): Int {
        x + y
      }
      
      fun factorial(n: Int): Int {
        if (n <= 1) {
          1
        } else {
          n * factorial(n - 1)
        }
      }
      
      class Point(x: Int, y: Int) {
        fun getX(): Int { x }
        fun getY(): Int { y }
      }
      
      object Math {
        fun pi(): Double { 3.14159 }
        fun square(x: Int): Int { x * x }
      }
    """
    
    val filePath = writeTestFile("test_complete.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation succeeded by verifying all class files were generated
    // This replaces checking for "Successfully compiled" output message
    
    // Verify all generated class files exist
    assert(new File(tempDir, "add$.class").exists(), "add$.class should exist")
    assert(new File(tempDir, "factorial$.class").exists(), "factorial$.class should exist") 
    assert(new File(tempDir, "Point.class").exists(), "Point.class should exist")
    assert(new File(tempDir, "Math.class").exists(), "Math.class should exist")
  }
  
  test("compile and execute recursive functions") {
    val sourceCode = """
      fun add(x: Int, y: Int): Int {
        x + y
      }
      
      fun factorial(n: Int): Int {
        if (n <= 1) {
          1
        } else {
          n * factorial(n - 1)
        }
      }
    """
    
    val filePath = writeTestFile("test_recursive.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation by testing if class files were generated and functions work
    // (Output capture may not work in test environment)
    
    // Verify class file was generated
    assert(new File(tempDir, "add$.class").exists(), "add$.class should be generated")
    
    // Test the generated functions
    val addResult = loadAndInvokeStaticMethod("add$", "add", Array(classOf[Int], classOf[Int]), Array(Int.box(5), Int.box(3)))
    addResult match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 8)
      case Failure(ex) => fail(s"Failed to invoke add function: ${ex.getMessage}")
    }
    
    val factResult = loadAndInvokeStaticMethod("factorial$", "factorial", Array(classOf[Int]), Array(Int.box(5)))
    factResult match {
      case Success(value) => assertEquals(value.asInstanceOf[Int], 120)
      case Failure(ex) => fail(s"Failed to invoke factorial function: ${ex.getMessage}")
    }
  }
  
  test("compile and execute class and object together") {
    val sourceCode = """
      class Point(x: Int, y: Int) {
        fun getX(): Int { x }
        fun getY(): Int { y }
      }
      
      object PointUtils {
        fun origin(): Int { 0 }
        fun getOriginX(): Int { 
          origin()
        }
      }
    """
    
    val filePath = writeTestFile("test_class_object.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation by testing if class files were generated and functions work
    // (Output capture may not work in test environment)
    
    // Test Point class
    val pointResult = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Point")
      val constructor = clazz.getConstructor(classOf[Int], classOf[Int])
      val instance = constructor.newInstance(Int.box(5), Int.box(10))
      
      val getX = clazz.getMethod("getX")
      val getY = clazz.getMethod("getY")
      
      val x = getX.invoke(instance).asInstanceOf[Int]
      val y = getY.invoke(instance).asInstanceOf[Int]
      
      (x, y)
    }
    
    pointResult match {
      case Success((x, y)) =>
        assertEquals(x, 5)
        assertEquals(y, 10)
      case Failure(ex) => fail(s"Failed to test Point class: ${ex.getMessage}")
    }
    
    // Test PointUtils object
    val utilsResult = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("PointUtils")
      val instanceField = clazz.getField("INSTANCE")
      val instance = instanceField.get(null)
      
      val origin = clazz.getMethod("origin")
      val getOriginX = clazz.getMethod("getOriginX")
      
      val originValue = origin.invoke(instance).asInstanceOf[Int]
      val originXValue = getOriginX.invoke(instance).asInstanceOf[Int]
      
      (originValue, originXValue)
    }
    
    utilsResult match {
      case Success((origin, originX)) =>
        assertEquals(origin, 0)
        assertEquals(originX, 0)
      case Failure(ex) => fail(s"Failed to test PointUtils object: ${ex.getMessage}")
    }
  }
  
  test("compile and execute extension methods with this keyword") {
    val sourceCode = """
      extension String {
        fun reverse(): String {
          "reversed"
        }
        fun length(): Int {
          42
        }
      }
      
      extension Int {
        fun isEven(): Boolean {
          this % 2 == 0
        }
        fun double(): Int {
          this * 2
        }
      }
    """
    
    val filePath = writeTestFile("test_extensions.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation by testing if class files were generated and functions work
    // (Output capture may not work in test environment)
    
    // Test String extensions
    val stringResult = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("String$Extension")
      
      val reverse = clazz.getMethod("reverse", classOf[String])
      val length = clazz.getMethod("length", classOf[String])
      
      val reverseResult = reverse.invoke(null, "hello").asInstanceOf[String]
      val lengthResult = length.invoke(null, "test").asInstanceOf[Int]
      
      (reverseResult, lengthResult)
    }
    
    stringResult match {
      case Success((rev, len)) =>
        assertEquals(rev, "reversed")
        assertEquals(len, 42)
      case Failure(ex) => fail(s"Failed to test String extensions: ${ex.getMessage}")
    }
    
    // Test Int extensions
    val intResult = Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Int$Extension")
      
      val isEven = clazz.getMethod("isEven", classOf[Int])
      val double = clazz.getMethod("double", classOf[Int])
      
      val evenResult = isEven.invoke(null, Int.box(8)).asInstanceOf[Boolean]
      val doubleResult = double.invoke(null, Int.box(21)).asInstanceOf[Int]
      
      (evenResult, doubleResult)
    }
    
    intResult match {
      case Success((even, doubled)) =>
        assertEquals(even, true)
        assertEquals(doubled, 42)
      case Failure(ex) => fail(s"Failed to test Int extensions: ${ex.getMessage}")
    }
  }
  
  test("compile complex sample with all features combined") {
    val sourceCode = """
      class Point(x: Int, y: Int) {
        fun getX(): Int { x }
        fun getY(): Int { y }
        fun addX(dx: Int): Int {
          x + dx
        }
      }
      
      object MathUtils {
        fun abs(n: Int): Int {
          if (n < 0) {
            0 - n
          } else {
            n
          }
        }
        fun square(n: Int): Int {
          n * n
        }
      }
      
      extension Int {
        fun isEven(): Boolean {
          this % 2 == 0
        }
        fun double(): Int {
          this * 2
        }
      }
    """
    
    val filePath = writeTestFile("test_complete_complex.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // Check compilation by testing if class files were generated and functions work
    // (Output capture may not work in test environment)
    
    // Verify all generated class files exist
    assert(new File(tempDir, "Point.class").exists(), "Point.class should exist")
    assert(new File(tempDir, "MathUtils.class").exists(), "MathUtils.class should exist")
    assert(new File(tempDir, "Int$Extension.class").exists(), "Int$Extension.class should exist")
    
    // Test Point class
    val pointResult = loadAndInvokePointMethods()
    pointResult match {
      case Success((x, y, addXResult)) =>
        assertEquals(x, 5)
        assertEquals(y, 10)
        assertEquals(addXResult, 8)
      case Failure(ex) => fail(s"Failed to test Point class: ${ex.getMessage}")
    }
    
    // Test MathUtils object
    val mathResult = loadAndInvokeMathUtils()
    mathResult match {
      case Success((absResult, squareResult)) =>
        assertEquals(absResult, 7)
        assertEquals(squareResult, 16)
      case Failure(ex) => fail(s"Failed to test MathUtils object: ${ex.getMessage}")
    }
    
    // Test Int extensions
    val intExtResult = loadAndInvokeIntExtensions()
    intExtResult match {
      case Success((evenResult, doubleResult)) =>
        assertEquals(evenResult, true)
        assertEquals(doubleResult, 42)
      case Failure(ex) => fail(s"Failed to test Int extensions: ${ex.getMessage}")
    }
  }
  
  test("handle compilation errors gracefully") {
    val sourceCode = """
      fun broken(x: Int): Int {
        undefined_variable + x
      }
    """
    
    val filePath = writeTestFile("test_error.ty", sourceCode.trim)
    
    val output = captureOutput {
      Main.main(Array(filePath))
    }
    
    // For error cases, we expect compilation to fail, so no .class files should be generated
    assert(!new File(tempDir, "broken$.class").exists(), "Should not generate class file for broken code")
  }
  
  // Helper methods for complex testing
  private def loadAndInvokePointMethods(): Try[(Int, Int, Int)] = {
    Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Point")
      val constructor = clazz.getConstructor(classOf[Int], classOf[Int])
      val instance = constructor.newInstance(Int.box(5), Int.box(10))
      
      val getX = clazz.getMethod("getX")
      val getY = clazz.getMethod("getY")
      val addX = clazz.getMethod("addX", classOf[Int])
      
      val x = getX.invoke(instance).asInstanceOf[Int]
      val y = getY.invoke(instance).asInstanceOf[Int]
      val addXResult = addX.invoke(instance, Int.box(3)).asInstanceOf[Int]
      
      (x, y, addXResult)
    }
  }
  
  private def loadAndInvokeMathUtils(): Try[(Int, Int)] = {
    Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("MathUtils")
      val instanceField = clazz.getField("INSTANCE")
      val instance = instanceField.get(null)
      
      val abs = clazz.getMethod("abs", classOf[Int])
      val square = clazz.getMethod("square", classOf[Int])
      
      val absResult = abs.invoke(instance, Int.box(-7)).asInstanceOf[Int]
      val squareResult = square.invoke(instance, Int.box(4)).asInstanceOf[Int]
      
      (absResult, squareResult)
    }
  }
  
  private def loadAndInvokeIntExtensions(): Try[(Boolean, Int)] = {
    Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val clazz = classLoader.loadClass("Int$Extension")
      
      val isEven = clazz.getMethod("isEven", classOf[Int])
      val double = clazz.getMethod("double", classOf[Int])
      
      val evenResult = isEven.invoke(null, Int.box(8)).asInstanceOf[Boolean]
      val doubleResult = double.invoke(null, Int.box(21)).asInstanceOf[Int]
      
      (evenResult, doubleResult)
    }
  }
}