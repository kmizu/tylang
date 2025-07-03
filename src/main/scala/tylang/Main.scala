package tylang

import tylang.repl.REPL
import tylang.parser.Parser
import tylang.compiler.CodeGenerator
import tylang.types.{TypeChecker, TypeContext}
import java.io.{File, FileInputStream}
import scala.io.Source
import scala.util.{Try, Success, Failure}

object Main {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case Nil =>
        // Start REPL
        val repl = new REPL()
        repl.start()
        
      case "--version" :: Nil =>
        println("TyLang v0.1.0 - A statically typed language with structural subtyping")
        
      case "--help" :: Nil =>
        printHelp()
        
      case files =>
        // Compile files
        files.foreach(compileFile)
    }
  }
  
  private def printHelp(): Unit = {
    println("TyLang Compiler and REPL")
    println("Usage:")
    println("  tylang                 - Start interactive REPL")
    println("  tylang file.ty         - Compile file")
    println("  tylang --help          - Show this help")
    println("  tylang --version       - Show version")
    println()
    println("REPL Commands:")
    println("  :help, :h     - Show help")
    println("  :quit, :q     - Exit")
    println("  :reset        - Reset session")
    println("  :list         - List definitions")
  }
  
  private def compileFile(filename: String): Unit = {
    val file = new File(filename)
    if (!file.exists()) {
      println(s"Error: File not found: $filename")
      return
    }
    
    try {
      // Read source code
      val source = Source.fromFile(file)
      val sourceCode = source.mkString
      source.close()
      
      println(s"Compiling $filename...")
      
      // Parse
      val program = Parser.parse(sourceCode, filename)
      
      // Type check
      val typeChecker = new TypeChecker()
      typeChecker.checkProgram(program)
      
      // Generate bytecode
      val outputDir = file.getParent match {
        case null => "."
        case parent => parent
      }
      
      val codeGenerator = new CodeGenerator()
      codeGenerator.generateProgram(program, outputDir)
      
      println(s"Successfully compiled $filename")
      
    } catch {
      case ex: tylang.ParseException =>
        println(s"Parse error in $filename: ${ex.getMessage}")
      case ex: tylang.CompileException =>
        println(s"Compile error in $filename: ${ex.getMessage}")
      case ex: Exception =>
        println(s"Error compiling $filename: ${ex.getMessage}")
    }
  }
}