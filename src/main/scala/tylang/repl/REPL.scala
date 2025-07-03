package tylang.repl

import tylang.lexer.{Lexer, Token}
import tylang.parser.Parser
import tylang.compiler.CodeGenerator
import tylang.types.{TypeChecker, TypeContext}
import tylang.ast.*
import tylang.{ParseException, CompileException}
import org.jline.reader.{LineReader, LineReaderBuilder, EndOfFileException, UserInterruptException}
import org.jline.terminal.{Terminal, TerminalBuilder}
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.reader.impl.DefaultParser
import org.jline.reader.ParsedLine
import java.io.{File, ByteArrayOutputStream, PrintStream}
import java.net.{URL, URLClassLoader}
import scala.util.{Try, Success, Failure}
import scala.collection.mutable

class REPL {
  private val tempDir = System.getProperty("java.io.tmpdir") + "/tylang-repl"
  private val codeGenerator = new CodeGenerator()
  private var typeContext = TypeContext()
  private var sessionCounter = 0
  private val definedFunctions = mutable.Set[String]()
  private val definedClasses = mutable.Set[String]()
  
  // JLine3 setup
  private val terminal: Terminal = TerminalBuilder.builder()
    .system(true)
    .build()
    
  private val completer = new StringsCompleter(
    "fun", "class", "trait", "object", "val", "var", "def", "extension",
    "if", "else", "while", "for", "match", "case", "try", "catch", "finally",
    "import", "package", "extends", "with", "override", "abstract", "final",
    "true", "false", "null", "this", "super", "new", "return", "throw",
    "Int", "Double", "String", "Boolean", "Unit", "Any", "AnyRef", "Nothing"
  )
  
  private val parser = new DefaultParser() {
    override def isEscapeChar(ch: Char): Boolean = ch == '\\'
    
    override def parse(line: String, cursor: Int, context: org.jline.reader.Parser.ParseContext): ParsedLine = {
      // Check if the line is incomplete (needs continuation)
      if (isIncomplete(line)) {
        throw new org.jline.reader.EOFError(-1, cursor, "Incomplete input")
      }
      super.parse(line, cursor, context)
    }
  }
  
  private val reader: LineReader = LineReaderBuilder.builder()
    .terminal(terminal)
    .completer(completer)
    .parser(parser)
    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "  | ")
    .variable(LineReader.INDENTATION, 2)
    .build()
  
  // Initialize temp directory
  private def initTempDir(): Unit = {
    val dir = new File(tempDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    // Clean up previous session files
    dir.listFiles().foreach(_.delete())
  }
  
  def start(): Unit = {
    initTempDir()
    printWelcome()
    
    var running = true
    while (running) {
      try {
        val input = reader.readLine("tylang> ")
        
        input.trim match {
          case "" => // Empty input, continue
          case ":quit" | ":q" => 
            running = false
          case ":help" | ":h" => 
            printHelp()
          case ":reset" => 
            reset()
          case ":list" => 
            listDefinitions()
          case line if line.startsWith(":") =>
            println(s"Unknown command: $line. Type :help for available commands.")
          case line =>
            processInput(line)
        }
      } catch {
        case _: UserInterruptException =>
          // Ctrl+C pressed
          println("\nUse :quit to exit")
        case _: EndOfFileException =>
          // Ctrl+D pressed
          println("\nGoodbye!")
          running = false
        case ex: Exception =>
          println(s"Unexpected error: ${ex.getMessage}")
      }
    }
    
    terminal.close()
  }
  
  private def printWelcome(): Unit = {
    println("Welcome to TyLang REPL!")
    println("Type :help for available commands, :quit to exit")
    println("=" * 50)
  }
  
  private def printHelp(): Unit = {
    println("Available commands:")
    println("  :help, :h     - Show this help message")
    println("  :quit, :q     - Exit the REPL")
    println("  :reset        - Reset the REPL session")
    println("  :list         - List defined functions and classes")
    println()
    println("You can enter:")
    println("  - Function declarations: fun add(x: Int, y: Int): Int { x + y }")
    println("  - Class declarations: class Point(x: Int, y: Int) { ... }")
    println("  - Expressions: 1 + 2 * 3")
    println("  - Variable declarations: val x = 42")
  }
  
  private def reset(): Unit = {
    sessionCounter = 0
    definedFunctions.clear()
    definedClasses.clear()
    typeContext = TypeContext()
    // Clean up temp directory
    val dir = new File(tempDir)
    if (dir.exists()) {
      dir.listFiles().foreach(_.delete())
    }
    System.out.print("REPL session reset.")
    System.out.flush()
  }
  
  private def listDefinitions(): Unit = {
    if (definedFunctions.nonEmpty) {
      println("Defined functions:")
      definedFunctions.foreach(name => println(s"  $name"))
    }
    if (definedClasses.nonEmpty) {
      println("Defined classes:")
      definedClasses.foreach(name => println(s"  $name"))
    }
    if (definedFunctions.isEmpty && definedClasses.isEmpty) {
      println("No definitions in current session.")
    }
  }
  
  def getTempDir: String = tempDir
  
  def isIncomplete(line: String): Boolean = {
    // Simple heuristic for incomplete input
    val trimmed = line.trim
    
    // Check for unclosed braces, parentheses, brackets
    var braces = 0
    var parens = 0
    var brackets = 0
    var inString = false
    var inComment = false
    var i = 0
    
    while (i < trimmed.length) {
      val ch = trimmed(i)
      
      if (inComment) {
        if (ch == '\n') inComment = false
      } else if (inString) {
        if (ch == '"' && (i == 0 || trimmed(i-1) != '\\')) inString = false
      } else {
        ch match {
          case '"' => inString = true
          case '/' if i + 1 < trimmed.length && trimmed(i + 1) == '/' => inComment = true
          case '{' => braces += 1
          case '}' => braces -= 1
          case '(' => parens += 1
          case ')' => parens -= 1
          case '[' => brackets += 1
          case ']' => brackets -= 1
          case _ =>
        }
      }
      i += 1
    }
    
    // Also check for keywords that typically span multiple lines
    val needsContinuation = trimmed.endsWith("{") || 
                          braces > 0 || parens > 0 || brackets > 0 || inString ||
                          trimmed.matches(".*\\b(fun|class|trait|object|if|while|for)\\s*\\([^)]*$") ||
                          trimmed.matches(".*\\b(fun|class|trait|object)\\s+\\w+\\s*$")
    
    needsContinuation
  }
  
  private def processInput(input: String): Unit = {
    try {
      // Check if input is only comments or whitespace
      val lexer = new tylang.lexer.Lexer(input, "<repl>")
      val tokens = lexer.tokenize()
      val nonCommentTokens = tokens.filterNot(t => 
        t.isInstanceOf[tylang.lexer.Token.Comment] || 
        t.isInstanceOf[tylang.lexer.Token.Whitespace] ||
        t.isInstanceOf[tylang.lexer.Token.Newline] ||
        t.isInstanceOf[tylang.lexer.Token.EOF]
      )
      
      if (nonCommentTokens.isEmpty) {
        // Input contains only comments/whitespace, do nothing
        return
      }
      
      // Try to parse as a complete program
      val program = Parser.parse(input, "<repl>")
      
      program.declarations match {
        case List(decl) => processDeclaration(decl)
        case multiple if multiple.nonEmpty => 
          // Multiple declarations
          multiple.foreach(processDeclaration)
        case _ =>
          // Try to parse as expression
          processExpression(input)
      }
    } catch {
      case ex: ParseException =>
        // Try parsing as expression if declaration parsing failed
        try {
          processExpression(input)
        } catch {
          case _: ParseException =>
            System.out.print(s"Parse error: ${ex.getMessage}")
            System.out.flush()
        }
      case ex: Exception =>
        System.out.print(s"Error: ${ex.getMessage}")
        System.out.flush()
    }
  }
  
  private def processDeclaration(decl: Declaration): Unit = {
    try {
      val program = Program(List(decl), decl.location)
      
      // Type check
      val typeChecker = new TypeChecker()
      typeChecker.checkProgram(program)
      
      // Generate bytecode
      codeGenerator.generateProgram(program, tempDir)
      
      decl match {
        case fd@FunctionDeclaration(name, _, params, returnType, body, _) =>
          definedFunctions += name
          // Add function to type context
          val paramTypes = params.map(p => p.typeAnnotation match {
            case Some(tylang.ast.SimpleType("Int", _)) => tylang.types.IntType
            case Some(tylang.ast.SimpleType("Double", _)) => tylang.types.DoubleType
            case Some(tylang.ast.SimpleType("String", _)) => tylang.types.StringType
            case Some(tylang.ast.SimpleType("Boolean", _)) => tylang.types.BooleanType
            case _ => tylang.types.AnyType
          })
          val retType = returnType match {
            case Some(tylang.ast.SimpleType("Int", _)) => tylang.types.IntType
            case Some(tylang.ast.SimpleType("Double", _)) => tylang.types.DoubleType
            case Some(tylang.ast.SimpleType("String", _)) => tylang.types.StringType
            case Some(tylang.ast.SimpleType("Boolean", _)) => tylang.types.BooleanType
            case None => 
              // Infer return type
              val inference = tylang.types.TypeInference()
              implicit val ctx: tylang.types.TypeContext = typeContext
              inference.inferType(body)
            case _ => tylang.types.AnyType
          }
          typeContext = typeContext.withType(name, tylang.types.FunctionType(paramTypes, retType))
          System.out.print(s"Function '$name' defined.")
          System.out.flush()
        case ClassDeclaration(name, _, _, _, _, _, _) =>
          definedClasses += name
          System.out.print(s"Class '$name' defined.")
          System.out.flush()
        case TraitDeclaration(name, _, _, _, _) =>
          definedClasses += name
          System.out.print(s"Trait '$name' defined.")
          System.out.flush()
        case ObjectDeclaration(name, _, _, _, _) =>
          definedClasses += name
          System.out.print(s"Object '$name' defined.")
          System.out.flush()
        case ExtensionDeclaration(_, methods, _) =>
          methods.foreach(method => definedFunctions += method.name)
          System.out.print(s"Extension methods defined: ${methods.map(_.name).mkString(", ")}")
          System.out.flush()
      }
    } catch {
      case ex: CompileException =>
        System.out.print(s"Compile error: ${ex.getMessage}")
        System.out.flush()
      case ex: Exception =>
        System.out.print(s"Error: ${ex.getMessage}")
        System.out.flush()
    }
  }
  
  private def processExpression(input: String): Unit = {
    try {
      // Create a temporary function to wrap the expression
      sessionCounter += 1
      val functionName = s"eval$sessionCounter"
      
      // Parse as a function that returns the expression (no explicit return type)
      val functionCode = s"fun $functionName() { $input }"
      val program = Parser.parse(functionCode, "<repl>")
      
      // Get the function declaration and infer its return type
      val functionDecl = program.declarations.head.asInstanceOf[FunctionDeclaration]
      
      // Type check with current context
      val typeChecker = new TypeChecker()
      val inference = tylang.types.TypeInference()
      implicit val ctx: tylang.types.TypeContext = typeContext
      typeChecker.checkDeclaration(functionDecl)
      val inferredReturnType = inference.inferType(functionDecl.body)
      
      // Create a new function declaration with explicit return type
      val returnTypeAnnotation = inferredReturnType match {
        case _: tylang.types.IntType.type => Some(tylang.ast.SimpleType("Int", functionDecl.location))
        case _: tylang.types.DoubleType.type => Some(tylang.ast.SimpleType("Double", functionDecl.location))
        case _: tylang.types.StringType.type => Some(tylang.ast.SimpleType("String", functionDecl.location))
        case _: tylang.types.BooleanType.type => Some(tylang.ast.SimpleType("Boolean", functionDecl.location))
        case _ => None
      }
      
      val updatedFunction = functionDecl.copy(returnType = returnTypeAnnotation)
      val updatedProgram = program.copy(declarations = List(updatedFunction))
      
      // Generate bytecode
      codeGenerator.generateProgram(updatedProgram, tempDir)
      
      // Execute and show result
      executeFunction(functionName, Array.empty, Array.empty) match {
        case Success(result) =>
          System.out.print(s"res$sessionCounter: ${formatResult(result)}")
          System.out.flush()
        case Failure(ex) =>
          System.out.print(s"Runtime error: ${ex.getMessage}")
          System.out.flush()
      }
    } catch {
      case ex: ParseException =>
        System.out.print(s"Parse error: ${ex.getMessage}")
        System.out.flush()
      case ex: CompileException =>
        System.out.print(s"Compile error: ${ex.getMessage}")
        System.out.flush()
      case ex: Exception =>
        System.out.print(s"Error: ${ex.getMessage}")
        System.out.flush()
    }
  }
  
  private def executeFunction(functionName: String, paramTypes: Array[Class[_]], args: Array[Object]): Try[Object] = {
    Try {
      val classLoader = new URLClassLoader(Array(new File(tempDir).toURI.toURL))
      val className = s"${functionName}$$"
      val clazz = classLoader.loadClass(className)
      val method = clazz.getMethod(functionName, paramTypes: _*)
      method.invoke(null, args: _*)
    }
  }
  
  private def formatResult(result: Any): String = {
    Option(result) match {
      case None => "null"
      case Some(s: String) => s"\"$s\""
      case Some(i: Int) => i.toString
      case Some(d: Double) => d.toString
      case Some(b: Boolean) => b.toString
      case Some(other) => other.toString
    }
  }
}

object REPL {
  def apply(): REPL = new REPL()
  
  def main(args: Array[String]): Unit = {
    val repl = new REPL()
    repl.start()
  }
}