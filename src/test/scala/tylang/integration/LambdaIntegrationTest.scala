package tylang.integration

import munit.FunSuite
import tylang.lexer.Lexer
import tylang.parser.Parser
import tylang.types.{TypeChecker, TypeInference, TypeContext}
import tylang.compiler.CodeGenerator
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import scala.util.{Try, Success, Failure}

class LambdaIntegrationTest extends FunSuite {
  def compileAndRun(code: String, mainClass: String = "main$"): Try[Any] = {
    val tempDir = Files.createTempDirectory("tylang-test-").toFile
    tempDir.deleteOnExit()
    
    val result = Try {
      // Lex
      val lexer = new Lexer(code)
      val tokens = lexer.tokenize()
      
      // Parse
      val parser = new Parser(tokens)
      val program = parser.parseProgram()
      
      // Type check
      val typeChecker = new TypeChecker()
      typeChecker.checkProgram(program)
      
      // Generate code
      val generator = new CodeGenerator()
      generator.generateProgram(program, tempDir.getAbsolutePath)
      
      // Load and run
      val classLoader = new URLClassLoader(Array(tempDir.toURI.toURL))
      val clazz = classLoader.loadClass(mainClass)
      val method = clazz.getMethod("main")
      method.invoke(null).asInstanceOf[Int]
    }
    
    // Clean up
    tempDir.listFiles().foreach(_.delete())
    tempDir.delete()
    
    result
  }
  
  test("lambda expression with function call") {
    val code = """
    fun twice(f: Int => Int, x: Int): Int {
      f(f(x))
    }
    
    fun main(): Int {
      twice((x: Int) => x * 2, 3)
    }
    """
    
    compileAndRun(code) match {
      case Success(result) => 
        assertEquals(result, 12) // 3 * 2 * 2 = 12
      case Failure(ex) => 
        fail(s"Failed to compile and run lambda test: ${ex.getMessage}")
    }
  }
  
  test("lambda expression with different types") {
    val code = """
    fun applyToString(f: String => Int, s: String): Int {
      f(s)
    }
    
    fun main(): Int {
      applyToString((s: String) => 42, "hello")
    }
    """
    
    compileAndRun(code) match {
      case Success(result) => 
        assertEquals(result, 42)
      case Failure(ex) => 
        fail(s"Failed to compile and run lambda test: ${ex.getMessage}")
    }
  }
  
  test("lambda expression with no parameters") {
    val code = """
    fun execute(f: () => Int): Int {
      f()
    }
    
    fun main(): Int {
      execute(() => 99)
    }
    """
    
    compileAndRun(code) match {
      case Success(result) => 
        assertEquals(result, 99)
      case Failure(ex) => 
        fail(s"Failed to compile and run lambda test: ${ex.getMessage}")
    }
  }
  
  test("lambda expression with multiple parameters") {
    val code = """
    fun combine(f: (Int, Int) => Int, x: Int, y: Int): Int {
      f(x, y)
    }
    
    fun main(): Int {
      combine((a: Int, b: Int) => a + b, 10, 20)
    }
    """
    
    compileAndRun(code) match {
      case Success(result) => 
        assertEquals(result, 30)
      case Failure(ex) => 
        fail(s"Failed to compile and run lambda test: ${ex.getMessage}")
    }
  }
}