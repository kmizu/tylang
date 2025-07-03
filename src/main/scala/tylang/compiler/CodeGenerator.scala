package tylang.compiler

import tylang.ast.*
import tylang.types.{Type, TypeContext}
import tylang.ast.{TypeParameter as AstTypeParameter}
import tylang.{CompileException, SourceLocation}
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import java.io.{FileOutputStream, File}
import scala.collection.mutable

class CodeGenerator {
  private val classWriters = mutable.Map[String, ClassWriter]()
  private val methodWriters = mutable.Stack[MethodVisitor]()
  private val localVariables = mutable.Map[String, Int]()
  private val localVariableTypes = mutable.Map[String, Option[TypeAnnotation]]()
  private var nextLocalIndex = 0
  private var currentClassName: Option[String] = None
  private var currentTypeContext: tylang.types.TypeContext = tylang.types.TypeContext()
  private val classFields = mutable.Map[String, Map[String, Option[TypeAnnotation]]]() // className -> (fieldName -> type)
  private val classMethods = mutable.Map[String, Set[String]]() // className -> method names
  private val topLevelFunctions = mutable.Map[String, (List[tylang.types.Type], tylang.types.Type)]() // functionName -> (paramTypes, returnType)
  
  def generateProgram(program: Program, outputDir: String): Unit = {
    // Generate bytecode for each declaration
    program.declarations.foreach { decl =>
      generateDeclaration(decl, outputDir)
    }
    
    // Write class files
    classWriters.foreach { case (className, cw) =>
      val classFile = new File(outputDir, s"$className.class")
      classFile.getParentFile.mkdirs()
      val fos = new FileOutputStream(classFile)
      try {
        fos.write(cw.toByteArray)
      } finally {
        fos.close()
      }
    }
  }
  
  private def generateDeclaration(decl: Declaration, outputDir: String): Unit = {
    decl match {
      case FunctionDeclaration(name, typeParams, params, returnType, body, location) =>
        generateTopLevelFunction(name, typeParams, params, returnType, body, location)
      
      case ClassDeclaration(name, typeParams, superClass, traits, constructor, members, location) =>
        generateClass(name, typeParams, superClass, traits, constructor, members, location)
      
      case TraitDeclaration(name, typeParams, superTraits, members, location) =>
        generateTrait(name, typeParams, superTraits, members, location)
      
      case ObjectDeclaration(name, superClass, traits, members, location) =>
        generateObject(name, superClass, traits, members, location)
      
      case ExtensionDeclaration(targetType, methods, location) =>
        generateExtension(targetType, methods, location)
    }
  }
  
  private def generateTopLevelFunction(
    name: String,
    typeParams: List[AstTypeParameter],
    params: List[Parameter],
    returnType: Option[TypeAnnotation],
    body: Expression,
    location: SourceLocation
  ): Unit = {
    // Track this function with its signature
    val inference = tylang.types.TypeInference()
    val ctx = tylang.types.TypeContext()
    val paramTypes = params.map { param =>
      param.typeAnnotation.map(convertTypeAnnotationToType).getOrElse(tylang.types.AnyType)
    }
    val returnTypeResolved = returnType.map(convertTypeAnnotationToType).getOrElse {
      // Infer return type from body if not specified
      var inferCtx = ctx
      params.foreach { param =>
        param.typeAnnotation.foreach { typeAnn =>
          inferCtx = inferCtx.withType(param.name, convertTypeAnnotationToType(typeAnn))
        }
      }
      inference.inferType(body)(inferCtx)
    }
    topLevelFunctions(name) = (paramTypes, returnTypeResolved)
    val className = s"${name}$$"
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
    
    cw.visit(V11, ACC_PUBLIC + ACC_FINAL, className, null, "java/lang/Object", null)
    
    // Generate static method
    val paramDescriptor = params.map(p => getTypeDescriptor(p.typeAnnotation)).mkString
    val returnDescriptor = returnType.map(ta => getTypeDescriptor(Some(ta))).getOrElse("V")
    val methodDescriptor = s"($paramDescriptor)$returnDescriptor"
    
    val mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, name, methodDescriptor, null, null)
    methodWriters.push(mv)
    
    // Set up local variables
    localVariables.clear()
    localVariableTypes.clear()
    nextLocalIndex = 0
    currentTypeContext = tylang.types.TypeContext()
    params.zipWithIndex.foreach { case (param, index) =>
      localVariables(param.name) = index
      localVariableTypes(param.name) = param.typeAnnotation
      param.typeAnnotation.foreach { typeAnn =>
        currentTypeContext = currentTypeContext.withType(param.name, convertTypeAnnotationToType(typeAnn))
      }
      nextLocalIndex = index + 1
    }
    
    mv.visitCode()
    
    // Generate body
    generateExpression(body)
    
    // Return instruction
    returnType match {
      case Some(_) => mv.visitInsn(getReturnOpcode(returnType))
      case None => mv.visitInsn(RETURN)
    }
    
    mv.visitMaxs(0, 0)
    mv.visitEnd()
    methodWriters.pop()
    
    cw.visitEnd()
    classWriters(className) = cw
  }
  
  private def generateClass(
    name: String,
    typeParams: List[AstTypeParameter],
    superClass: Option[TypeAnnotation],
    traits: List[TypeAnnotation],
    constructor: Option[Constructor],
    members: List[ClassMember],
    location: SourceLocation
  ): Unit = {
    currentClassName = Some(name)
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
    
    val superName = superClass.map(getInternalName).getOrElse("java/lang/Object")
    val interfaces = traits.map(getInternalName).toArray
    
    cw.visit(V11, ACC_PUBLIC, name, null, superName, interfaces)
    
    // Collect field information and generate fields
    val fieldMap = mutable.Map[String, Option[TypeAnnotation]]()
    val explicitFields = mutable.Set[String]()
    
    members.foreach {
      case FieldMember(field) =>
        fieldMap(field.name) = field.typeAnnotation
        explicitFields += field.name
        val fieldDescriptor = getTypeDescriptor(field.typeAnnotation)
        val fieldAccess = if (field.isMutable) ACC_PRIVATE else ACC_PRIVATE + ACC_FINAL
        cw.visitField(fieldAccess, field.name, fieldDescriptor, null, null)
      case _ => // Skip methods for now
    }
    
    // Generate fields for constructor parameters (only if not already defined)
    constructor.foreach { constr =>
      constr.parameters.foreach { param =>
        fieldMap(param.name) = param.typeAnnotation
        if (!explicitFields.contains(param.name)) {
          val fieldDescriptor = getTypeDescriptor(param.typeAnnotation)
          cw.visitField(ACC_PRIVATE + ACC_FINAL, param.name, fieldDescriptor, null, null)
        }
      }
    }
    
    classFields(name) = fieldMap.toMap
    
    // Generate constructor
    constructor match {
      case Some(constr) =>
        generateConstructor(cw, constr, superName)
      case None =>
        generateDefaultConstructor(cw, superName)
    }
    
    // Generate methods
    members.foreach {
      case MethodMember(method) =>
        generateMethod(cw, method)
      case _ => // Skip fields
    }
    
    cw.visitEnd()
    classWriters(name) = cw
  }
  
  private def generateTrait(
    name: String,
    typeParams: List[AstTypeParameter],
    superTraits: List[TypeAnnotation],
    members: List[TraitMember],
    location: SourceLocation
  ): Unit = {
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
    
    val interfaces = superTraits.map(getInternalName).toArray
    
    cw.visit(V11, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE, name, null, "java/lang/Object", interfaces)
    
    // Generate methods
    members.foreach {
      case ConcreteMethodMember(method) =>
        generateMethod(cw, method, isDefault = true)
      case AbstractMethodMember(methodName, methodTypeParams, methodParams, methodReturnType, methodLocation) =>
        generateAbstractMethod(cw, methodName, methodTypeParams, methodParams, methodReturnType, methodLocation)
    }
    
    cw.visitEnd()
    classWriters(name) = cw
  }
  
  private def generateObject(
    name: String,
    superClass: Option[TypeAnnotation],
    traits: List[TypeAnnotation],
    members: List[ClassMember],
    location: SourceLocation
  ): Unit = {
    currentClassName = Some(name)
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
    
    val superName = superClass.map(getInternalName).getOrElse("java/lang/Object")
    val interfaces = traits.map(getInternalName).toArray
    
    cw.visit(V11, ACC_PUBLIC + ACC_FINAL, name, null, superName, interfaces)
    
    // Singleton instance field
    cw.visitField(ACC_PUBLIC + ACC_STATIC + ACC_FINAL, "INSTANCE", s"L$name;", null, null)
    
    // Static initializer
    val clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)
    clinit.visitCode()
    clinit.visitTypeInsn(NEW, name)
    clinit.visitInsn(DUP)
    clinit.visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V", false)
    clinit.visitFieldInsn(PUTSTATIC, name, "INSTANCE", s"L$name;")
    clinit.visitInsn(RETURN)
    clinit.visitMaxs(0, 0)
    clinit.visitEnd()
    
    // Private constructor
    val init = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
    init.visitCode()
    init.visitVarInsn(ALOAD, 0)
    init.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false)
    init.visitInsn(RETURN)
    init.visitMaxs(0, 0)
    init.visitEnd()
    
    // Track method names for this object
    val methodNames = members.collect {
      case MethodMember(method) => method.name
    }.toSet
    classMethods(name) = methodNames
    
    // Generate methods
    members.foreach {
      case MethodMember(method) =>
        generateMethod(cw, method)
      case FieldMember(field) =>
        val fieldDescriptor = getTypeDescriptor(field.typeAnnotation)
        cw.visitField(ACC_PRIVATE + ACC_FINAL, field.name, fieldDescriptor, null, null)
    }
    
    cw.visitEnd()
    classWriters(name) = cw
  }
  
  private def generateExtension(
    targetType: TypeAnnotation,
    methods: List[FunctionDeclaration],
    location: SourceLocation
  ): Unit = {
    val className = s"${getInternalName(targetType)}$$Extension"
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
    
    cw.visit(V11, ACC_PUBLIC + ACC_FINAL, className, null, "java/lang/Object", null)
    
    // Generate each extension method as a static method
    methods.foreach { method =>
      val targetDescriptor = getTypeDescriptor(Some(targetType))
      val paramDescriptor = targetDescriptor + method.parameters.map(p => getTypeDescriptor(p.typeAnnotation)).mkString
      val returnDescriptor = method.returnType.map(ta => getTypeDescriptor(Some(ta))).getOrElse("V")
      val methodDescriptor = s"($paramDescriptor)$returnDescriptor"
      
      val mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, method.name, methodDescriptor, null, null)
      methodWriters.push(mv)
      
      // Set up local variables (including implicit 'this')
      localVariables.clear()
      localVariableTypes.clear()
      localVariables("this") = 0
      localVariableTypes("this") = Some(targetType)
      nextLocalIndex = 1
      
      method.parameters.zipWithIndex.foreach { case (param, index) =>
        localVariables(param.name) = index + 1
        localVariableTypes(param.name) = param.typeAnnotation
        nextLocalIndex = index + 2
      }
      
      mv.visitCode()
      generateExpression(method.body)
      
      method.returnType match {
        case Some(_) => mv.visitInsn(getReturnOpcode(method.returnType))
        case None => mv.visitInsn(RETURN)
      }
      
      mv.visitMaxs(0, 0)
      mv.visitEnd()
      methodWriters.pop()
    }
    
    cw.visitEnd()
    classWriters(className) = cw
  }
  
  private def generateConstructor(cw: ClassWriter, constructor: Constructor, superName: String): Unit = {
    val paramDescriptor = constructor.parameters.map(p => getTypeDescriptor(p.typeAnnotation)).mkString
    val mv = cw.visitMethod(ACC_PUBLIC, "<init>", s"($paramDescriptor)V", null, null)
    
    methodWriters.push(mv)
    
    // Set up local variables
    localVariables.clear()
    localVariableTypes.clear()
    localVariables("this") = 0
    localVariableTypes("this") = None // 'this' is a reference type
    nextLocalIndex = 1
    
    constructor.parameters.zipWithIndex.foreach { case (param, index) =>
      localVariables(param.name) = index + 1
      localVariableTypes(param.name) = param.typeAnnotation
      nextLocalIndex = index + 2
    }
    
    mv.visitCode()
    
    // Call super constructor
    mv.visitVarInsn(ALOAD, 0)
    mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false)
    
    // Initialize fields with constructor parameters
    constructor.parameters.foreach { param =>
      mv.visitVarInsn(ALOAD, 0)
      mv.visitVarInsn(getLoadOpcode(param.typeAnnotation), localVariables(param.name))
      val className = currentClassName.getOrElse("java/lang/Object")
      mv.visitFieldInsn(PUTFIELD, className, param.name, getTypeDescriptor(param.typeAnnotation))
    }
    
    // Execute constructor body
    constructor.body.foreach(generateExpression)
    
    mv.visitInsn(RETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()
    methodWriters.pop()
  }
  
  private def generateDefaultConstructor(cw: ClassWriter, superName: String): Unit = {
    val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(ALOAD, 0)
    mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false)
    mv.visitInsn(RETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()
  }
  
  private def generateMethod(cw: ClassWriter, method: FunctionDeclaration, isDefault: Boolean = false): Unit = {
    val paramDescriptor = method.parameters.map(p => getTypeDescriptor(p.typeAnnotation)).mkString
    val returnDescriptor = method.returnType.map(ta => getTypeDescriptor(Some(ta))).getOrElse("V")
    val methodDescriptor = s"($paramDescriptor)$returnDescriptor"
    
    val access = ACC_PUBLIC // Default methods not implemented yet
    val mv = cw.visitMethod(access, method.name, methodDescriptor, null, null)
    
    methodWriters.push(mv)
    
    // Set up local variables
    localVariables.clear()
    localVariableTypes.clear()
    localVariables("this") = 0
    localVariableTypes("this") = None // 'this' is a reference type
    nextLocalIndex = 1
    
    method.parameters.zipWithIndex.foreach { case (param, index) =>
      localVariables(param.name) = index + 1
      localVariableTypes(param.name) = param.typeAnnotation
      nextLocalIndex = index + 2
    }
    
    mv.visitCode()
    generateExpression(method.body)
    
    method.returnType match {
      case Some(_) => mv.visitInsn(getReturnOpcode(method.returnType))
      case None => mv.visitInsn(RETURN)
    }
    
    mv.visitMaxs(0, 0)
    mv.visitEnd()
    methodWriters.pop()
  }
  
  private def generateAbstractMethod(
    cw: ClassWriter,
    methodName: String,
    methodTypeParams: List[AstTypeParameter],
    methodParams: List[Parameter],
    methodReturnType: Option[TypeAnnotation],
    methodLocation: SourceLocation
  ): Unit = {
    val paramDescriptor = methodParams.map(p => getTypeDescriptor(p.typeAnnotation)).mkString
    val returnDescriptor = methodReturnType.map(ta => getTypeDescriptor(Some(ta))).getOrElse("V")
    val methodDescriptor = s"($paramDescriptor)$returnDescriptor"
    
    val mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, methodName, methodDescriptor, null, null)
    mv.visitEnd()
  }
  
  private def generateExpression(expr: Expression): Unit = {
    val mv = methodWriters.top
    
    expr match {
      case IntLiteral(value, _) =>
        mv.visitLdcInsn(value)
      
      case DoubleLiteral(value, _) =>
        mv.visitLdcInsn(value)
      
      case StringLiteral(value, _) =>
        mv.visitLdcInsn(value)
      
      case BooleanLiteral(value, _) =>
        mv.visitInsn(if (value) ICONST_1 else ICONST_0)
      
      case Identifier(name, location) =>
        localVariables.get(name) match {
          case Some(index) => 
            val typeAnnotation = localVariableTypes.get(name).flatten
            mv.visitVarInsn(getLoadOpcode(typeAnnotation), index)
          case None => 
            // Check if this is a method reference in the current class/object
            if (localVariables.contains("this") && currentClassName.isDefined) {
              val className = currentClassName.get
              val isMethod = classMethods.get(className).exists(_.contains(name))
              
              if (isMethod) {
                // This is a method name referenced without calling it
                // For now, throw an error as we don't support method references yet
                throw CompileException(s"Method references not yet supported: $name. Did you mean to call $name()?", location)
              } else {
                // Try as field access on 'this'
                val fieldType = classFields.get(className).flatMap(_.get(name)).flatten
                mv.visitVarInsn(ALOAD, 0) // Load 'this'
                mv.visitFieldInsn(GETFIELD, className, name, getTypeDescriptor(fieldType))
              }
            } else if (topLevelFunctions.contains(name)) {
              // This is a top-level function reference
              generateFunctionReference(name, location)
            } else {
              throw CompileException(s"Undefined variable: $name", location)
            }
        }
      
      case ThisExpression(location) =>
        // Load 'this' from local variable (type-aware for extension methods)
        localVariables.get("this") match {
          case Some(index) => 
            val typeAnnotation = localVariableTypes.get("this").flatten
            mv.visitVarInsn(getLoadOpcode(typeAnnotation), index)
          case None => throw CompileException("'this' is not available in this context", location)
        }
      
      case BinaryOp(left, op, right, location) =>
        generateBinaryOp(left, op, right, location)
      
      case UnaryOp(op, operand, location) =>
        generateUnaryOp(op, operand, location)
      
      case MethodCall(receiver, methodName, args, typeArgs, location) =>
        generateMethodCall(receiver, methodName, args, typeArgs, location)
      
      case FieldAccess(receiver, fieldName, location) =>
        generateFieldAccess(receiver, fieldName, location)
      
      case Assignment(target, value, location) =>
        generateAssignment(target, value, location)
      
      case Block(statements, location) =>
        generateBlock(statements, location)
      
      case IfExpression(condition, thenBranch, elseBranch, location) =>
        generateIfExpression(condition, thenBranch, elseBranch, location)
      
      case WhileExpression(condition, body, location) =>
        generateWhileExpression(condition, body, location)
      
      case ListLiteral(elements, location) =>
        generateListLiteral(elements, location)
      
      case Lambda(parameters, body, location) =>
        generateLambda(parameters, body, location)
      
      case _ =>
        throw CompileException(s"Code generation not implemented for ${expr.getClass.getSimpleName}", expr.location)
    }
  }
  
  private def generateBinaryOp(left: Expression, op: String, right: Expression, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    generateExpression(left)
    generateExpression(right)
    
    op match {
      case "+" => mv.visitInsn(IADD) // Simplified - should handle different types
      case "-" => mv.visitInsn(ISUB)
      case "*" => mv.visitInsn(IMUL)
      case "/" => mv.visitInsn(IDIV)
      case "%" => mv.visitInsn(IREM)
      case "==" => 
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IF_ICMPNE, falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitLabel(endLabel)
      case "!=" =>
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IF_ICMPEQ, falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitLabel(endLabel)
      case "<" =>
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IF_ICMPGE, falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitLabel(endLabel)
      case ">" =>
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IF_ICMPLE, falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitLabel(endLabel)
      case "<=" =>
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IF_ICMPGT, falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitLabel(endLabel)
      case ">=" =>
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IF_ICMPLT, falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitLabel(endLabel)
      case _ => throw CompileException(s"Unsupported binary operator: $op", location)
    }
  }
  
  private def generateUnaryOp(op: String, operand: Expression, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    generateExpression(operand)
    
    op match {
      case "-" => mv.visitInsn(INEG)
      case "!" => 
        val falseLabel = new Label()
        val endLabel = new Label()
        mv.visitJumpInsn(IFEQ, falseLabel)
        mv.visitInsn(ICONST_0)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(falseLabel)
        mv.visitInsn(ICONST_1)
        mv.visitLabel(endLabel)
      case _ => throw CompileException(s"Unsupported unary operator: $op", location)
    }
  }
  
  private def generateMethodCall(
    receiver: Option[Expression],
    methodName: String,
    args: List[Expression],
    typeArgs: List[TypeAnnotation],
    location: SourceLocation
  ): Unit = {
    val mv = methodWriters.top
    
    receiver match {
      case Some(recv) =>
        // Special case: if methodName is "apply" and receiver is an Identifier,
        // treat this as a direct function call
        if (methodName == "apply" && recv.isInstanceOf[Identifier]) {
          val functionName = recv.asInstanceOf[Identifier].name
          if (topLevelFunctions.contains(functionName)) {
            // Generate static function call
            args.foreach(generateExpression)
            val targetClassName = s"${functionName}$$"
            val (paramTypes, returnType) = topLevelFunctions(functionName)
            val argDescriptor = args.map(getArgumentDescriptor).mkString
            val returnDescriptor = getTypeDescriptorFromType(returnType)
            val descriptor = s"($argDescriptor)$returnDescriptor"
            mv.visitMethodInsn(INVOKESTATIC, targetClassName, functionName, descriptor, false)
            return
          } else if (localVariables.contains("this") && currentClassName.isDefined) {
            // Check if this is an object method call
            val className = currentClassName.get
            val isMethod = classMethods.get(className).exists(_.contains(functionName))
            if (isMethod) {
              // Generate object method call
              args.foreach(generateExpression)
              mv.visitVarInsn(ALOAD, 0) // Load 'this'
              val argDescriptor = args.map(getArgumentDescriptor).mkString
              val returnDescriptor = "I" // Should be computed from method signature
              val descriptor = s"($argDescriptor)$returnDescriptor"
              mv.visitMethodInsn(INVOKEVIRTUAL, className, functionName, descriptor, false)
              return
            }
          }
        }
        
        generateExpression(recv)
        
        // Check if this is a function call (method name is "apply")
        if (methodName == "apply") {
          // This is a function invocation
          // Infer the receiver type to determine the interface
          val inference = tylang.types.TypeInference()
          var ctx = tylang.types.TypeContext()
          
          // Add local variables to context
          localVariables.foreach { case (name, _) =>
            localVariableTypes.get(name).flatten.foreach { typeAnn =>
              ctx = ctx.withType(name, convertTypeAnnotationToType(typeAnn))
            }
          }
          
          val receiverType = inference.inferType(recv)(ctx)
          
          receiverType match {
            case tylang.types.FunctionType(paramTypes, returnType) =>
              // Generate arguments
              args.foreach(generateExpression)
              
              // Determine the functional interface to use
              val parameters = paramTypes.zipWithIndex.map { case (typ, idx) =>
                val typeAnnotation = typ match {
                  case _: tylang.types.IntType.type => Some(SimpleType("Int", location))
                  case _: tylang.types.DoubleType.type => Some(SimpleType("Double", location))
                  case _: tylang.types.StringType.type => Some(SimpleType("String", location))
                  case _: tylang.types.BooleanType.type => Some(SimpleType("Boolean", location))
                  case _ => None
                }
                Parameter(s"arg$idx", typeAnnotation, None, location)
              }
              
              val (functionalInterface, interfaceMethodName, interfaceMethodDesc) = 
                determineFunctionalInterface(parameters, returnType)
              
              // Generate the interface method call
              mv.visitMethodInsn(INVOKEINTERFACE, functionalInterface, interfaceMethodName, interfaceMethodDesc, true)
              
              // Add cast/unboxing if needed for primitive return types
              if (interfaceMethodDesc.endsWith(")Ljava/lang/Object;")) {
                returnType match {
                  case _: tylang.types.IntType.type =>
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Integer")
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
                  case _: tylang.types.DoubleType.type =>
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Double")
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false)
                  case _: tylang.types.BooleanType.type =>
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean")
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
                  case _ => // No cast needed for reference types
                }
              }
            case _ =>
              // Fall back to generic method call
              args.foreach(generateExpression)
              val argDescriptor = args.map(getArgumentDescriptor).mkString
              val descriptor = s"($argDescriptor)Ljava/lang/Object;"
              mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", methodName, descriptor, false)
          }
        } else {
          // Regular method call
          args.foreach(generateExpression)
          val argDescriptor = args.map(getArgumentDescriptor).mkString
          val descriptor = s"($argDescriptor)Ljava/lang/Object;"
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", methodName, descriptor, false)
        }
      
      case None =>
        args.foreach(generateExpression)
        // Static method call - check if it's a top-level function
        if (topLevelFunctions.contains(methodName)) {
          val targetClassName = s"${methodName}$$"
          val (paramTypes, returnType) = topLevelFunctions(methodName)
          val argDescriptor = args.map(getArgumentDescriptor).mkString
          val returnDescriptor = getTypeDescriptorFromType(returnType)
          val descriptor = s"($argDescriptor)$returnDescriptor"
          mv.visitMethodInsn(INVOKESTATIC, targetClassName, methodName, descriptor, false)
        } else {
          // Method call without receiver in object context - call on 'this' instance
          if (localVariables.contains("this")) {
            mv.visitVarInsn(ALOAD, 0) // Load 'this'
            val argDescriptor = args.map(getArgumentDescriptor).mkString
            val descriptor = s"($argDescriptor)I" // Simplified - should compute return type
            mv.visitMethodInsn(INVOKEVIRTUAL, getCurrentClassName, methodName, descriptor, false)
          } else {
            mv.visitMethodInsn(INVOKESTATIC, getCurrentClassName, methodName, "()V", false)
          }
        }
    }
  }
  
  private def getArgumentDescriptor(expr: Expression): String = {
    // Infer the type of the expression using current context
    val inference = tylang.types.TypeInference()
    val exprType = inference.inferType(expr)(currentTypeContext)
    getTypeDescriptorFromType(exprType)
  }
  
  private def generateFieldAccess(receiver: Expression, fieldName: String, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    generateExpression(receiver)
    val className = currentClassName.getOrElse("java/lang/Object")
    val fieldType = classFields.get(className).flatMap(_.get(fieldName)).flatten
    mv.visitFieldInsn(GETFIELD, className, fieldName, getTypeDescriptor(fieldType))
  }
  
  private def generateAssignment(target: Expression, value: Expression, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    target match {
      case Identifier(name, _) =>
        generateExpression(value)
        val typeAnnotation = localVariableTypes.get(name).flatten
        mv.visitVarInsn(getStoreOpcode(typeAnnotation), localVariables(name))
      
      case FieldAccess(receiver, fieldName, _) =>
        generateExpression(receiver)
        generateExpression(value)
        val className = currentClassName.getOrElse("java/lang/Object")
        val fieldType = classFields.get(className).flatMap(_.get(fieldName)).flatten
        mv.visitFieldInsn(PUTFIELD, className, fieldName, getTypeDescriptor(fieldType))
      
      case _ =>
        throw CompileException("Invalid assignment target", location)
    }
  }
  
  private def generateBlock(statements: List[Statement], location: SourceLocation): Unit = {
    statements.foreach {
      case ExpressionStatement(expr, _) => generateExpression(expr)
      case VariableDeclaration(name, typeAnnotation, initializer, isMutable, _) =>
        localVariables(name) = nextLocalIndex
        localVariableTypes(name) = typeAnnotation
        
        // Infer type if needed and update context
        val varType = typeAnnotation match {
          case Some(ta) => convertTypeAnnotationToType(ta)
          case None => 
            // Infer from initializer
            initializer.map { expr =>
              val inference = tylang.types.TypeInference()
              inference.inferType(expr)(currentTypeContext)
            }.getOrElse(tylang.types.AnyType)
        }
        currentTypeContext = currentTypeContext.withType(name, varType)
        
        initializer.foreach { expr =>
          generateExpression(expr)
          methodWriters.top.visitVarInsn(getStoreOpcode(typeAnnotation), nextLocalIndex)
        }
        nextLocalIndex += 1
      case Return(value, _) =>
        value.foreach(generateExpression)
        methodWriters.top.visitInsn(ARETURN)
    }
  }
  
  private def generateIfExpression(
    condition: Expression,
    thenBranch: Expression,
    elseBranch: Option[Expression],
    location: SourceLocation
  ): Unit = {
    val mv = methodWriters.top
    val elseLabel = new Label()
    val endLabel = new Label()
    
    generateExpression(condition)
    mv.visitJumpInsn(IFEQ, elseLabel)
    
    generateExpression(thenBranch)
    mv.visitJumpInsn(GOTO, endLabel)
    
    mv.visitLabel(elseLabel)
    elseBranch.foreach(generateExpression)
    
    mv.visitLabel(endLabel)
  }
  
  private def generateWhileExpression(condition: Expression, body: Expression, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    val loopStart = new Label()
    val loopEnd = new Label()
    
    mv.visitLabel(loopStart)
    generateExpression(condition)
    mv.visitJumpInsn(IFEQ, loopEnd)
    
    generateExpression(body)
    mv.visitJumpInsn(GOTO, loopStart)
    
    mv.visitLabel(loopEnd)
  }
  
  private def generateListLiteral(elements: List[Expression], location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    // Create ArrayList
    mv.visitTypeInsn(NEW, "java/util/ArrayList")
    mv.visitInsn(DUP)
    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
    
    // Add elements
    elements.foreach { element =>
      mv.visitInsn(DUP)
      generateExpression(element)
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true)
      mv.visitInsn(POP)
    }
  }
  
  private def generateLambda(parameters: List[Parameter], body: Expression, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    // Generate the lambda as a static method
    val lambdaMethodName = s"lambda$$lambdaCounter"
    lambdaCounter += 1
    
    // Get parameter and return types
    val paramDescriptors = parameters.map { param =>
      getTypeDescriptor(param.typeAnnotation)
    }.mkString("")
    
    // Infer return type from body
    val inference = tylang.types.TypeInference()
    // Start with current context (has access to closure variables)
    var ctx = currentTypeContext
    parameters.foreach { param =>
      param.typeAnnotation.foreach { typeAnn =>
        ctx = ctx.withType(param.name, convertTypeAnnotationToType(typeAnn))
      }
    }
    val returnType = inference.inferType(body)(ctx)
    val returnDescriptor = getTypeDescriptorFromType(returnType)
    
    // Create the lambda method
    val lambdaMethodDescriptor = s"($paramDescriptors)$returnDescriptor"
    val currentClass = currentClassName.getOrElse("TyLangGenerated")
    val classWriter = classWriters.getOrElse(currentClass, {
      val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
      cw.visit(V11, ACC_PUBLIC + ACC_FINAL, currentClass, null, "java/lang/Object", null)
      classWriters(currentClass) = cw
      cw
    })
    
    val lambdaMethod = classWriter.visitMethod(
      ACC_PUBLIC | ACC_STATIC,
      lambdaMethodName,
      lambdaMethodDescriptor,
      null,
      null
    )
    lambdaMethod.visitCode()
    
    // Save current state
    val savedMv = methodWriters.top
    val savedLocals = localVariables.clone()
    val savedTypes = localVariableTypes.clone()
    val savedNextLocal = nextLocalIndex
    val savedContext = currentTypeContext
    
    // Set up lambda method context
    methodWriters.push(lambdaMethod)
    localVariables.clear()
    localVariableTypes.clear()
    nextLocalIndex = 0
    
    // Lambda has access to closure but parameters shadow them
    currentTypeContext = savedContext
    
    // Add parameters to local variables
    parameters.foreach { param =>
      localVariables(param.name) = nextLocalIndex
      localVariableTypes(param.name) = param.typeAnnotation
      param.typeAnnotation.foreach { typeAnn =>
        currentTypeContext = currentTypeContext.withType(param.name, convertTypeAnnotationToType(typeAnn))
      }
      nextLocalIndex += getTypeSize(param.typeAnnotation)
    }
    
    // Generate lambda body
    generateExpression(body)
    
    // Add appropriate return instruction
    returnType match {
      case _: tylang.types.IntType.type => lambdaMethod.visitInsn(IRETURN)
      case _: tylang.types.DoubleType.type => lambdaMethod.visitInsn(DRETURN)
      case _: tylang.types.BooleanType.type => lambdaMethod.visitInsn(IRETURN)
      case _: tylang.types.UnitType.type => lambdaMethod.visitInsn(RETURN)
      case _ => lambdaMethod.visitInsn(ARETURN)
    }
    
    lambdaMethod.visitMaxs(0, 0)
    lambdaMethod.visitEnd()
    
    // Restore state
    methodWriters.pop()
    localVariables.clear()
    localVariables ++= savedLocals
    localVariableTypes.clear()
    localVariableTypes ++= savedTypes
    nextLocalIndex = savedNextLocal
    currentTypeContext = savedContext
    
    // Now generate code to create the function object using invokedynamic
    generateLambdaInvokeDynamic(parameters, returnType, currentClass, lambdaMethodName, lambdaMethodDescriptor)
  }
  
  private def generateLambdaInvokeDynamic(
    parameters: List[Parameter], 
    returnType: tylang.types.Type,
    currentClass: String,
    lambdaMethodName: String,
    lambdaMethodDescriptor: String
  ): Unit = {
    val mv = methodWriters.top
    
    // Determine the functional interface to use
    val (functionalInterface, interfaceMethodName, interfaceMethodDesc) = 
      determineFunctionalInterface(parameters, returnType)
    
    // Create bootstrap method handle for LambdaMetafactory
    val bootstrapMethodHandle = new Handle(
      Opcodes.H_INVOKESTATIC,
      "java/lang/invoke/LambdaMetafactory",
      "metafactory",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
      false
    )
    
    // Create the lambda implementation method handle
    val implMethodHandle = new Handle(
      Opcodes.H_INVOKESTATIC,
      currentClass,
      lambdaMethodName,
      lambdaMethodDescriptor,
      false
    )
    
    // Generate invokedynamic
    mv.visitInvokeDynamicInsn(
      interfaceMethodName,
      s"()L$functionalInterface;",
      bootstrapMethodHandle,
      Type.getType(interfaceMethodDesc),
      implMethodHandle,
      Type.getType(lambdaMethodDescriptor)
    )
  }
  
  private def determineFunctionalInterface(
    parameters: List[Parameter], 
    returnType: tylang.types.Type
  ): (String, String, String) = {
    // Use Java's built-in functional interfaces
    (parameters.length, parameters.map(_.typeAnnotation), returnType) match {
      case (0, _, _) =>
        ("java/util/function/Supplier", "get", "()Ljava/lang/Object;")
      case (1, List(Some(SimpleType("Int", _))), _: tylang.types.IntType.type) =>
        ("java/util/function/IntUnaryOperator", "applyAsInt", "(I)I")
      case (1, List(Some(SimpleType("Int", _))), _: tylang.types.DoubleType.type) =>
        ("java/util/function/IntToDoubleFunction", "applyAsDouble", "(I)D")
      case (1, List(Some(SimpleType("Int", _))), _) =>
        ("java/util/function/IntFunction", "apply", "(I)Ljava/lang/Object;")
      case (1, _, _: tylang.types.IntType.type) =>
        ("java/util/function/ToIntFunction", "applyAsInt", "(Ljava/lang/Object;)I")
      case (1, _, _) =>
        ("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")
      case (2, List(Some(SimpleType("Int", _)), Some(SimpleType("Int", _))), _: tylang.types.IntType.type) =>
        ("java/util/function/IntBinaryOperator", "applyAsInt", "(II)I")
      case (2, _, _) =>
        ("java/util/function/BiFunction", "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
      case _ => 
        throw CompileException(s"Lambdas with ${parameters.length} parameters not yet supported", SourceLocation("<lambda>", (0, 0), ""))
    }
  }
  
  private def generateFunctionReference(functionName: String, location: SourceLocation): Unit = {
    val mv = methodWriters.top
    
    // Get function signature from topLevelFunctions
    val (paramTypes, returnType) = topLevelFunctions.get(functionName) match {
      case Some(signature) => signature
      case None => throw CompileException(s"Unknown function: $functionName", location)
    }
    
    // Create method reference using invokedynamic
    val targetClassName = s"${functionName}$$"
    val methodDescriptor = s"(${paramTypes.map(getTypeDescriptorFromType).mkString})${getTypeDescriptorFromType(returnType)}"
    
    // Convert parameter types to Parameter list for determineFunctionalInterface
    val parameters = paramTypes.zipWithIndex.map { case (typ, idx) =>
      val typeAnnotation = typ match {
        case _: tylang.types.IntType.type => Some(SimpleType("Int", location))
        case _: tylang.types.DoubleType.type => Some(SimpleType("Double", location))
        case _: tylang.types.StringType.type => Some(SimpleType("String", location))
        case _: tylang.types.BooleanType.type => Some(SimpleType("Boolean", location))
        case _ => None
      }
      Parameter(s"arg$idx", typeAnnotation, None, location)
    }
    
    val (functionalInterface, interfaceMethodName, interfaceMethodDesc) = 
      determineFunctionalInterface(parameters, returnType)
    
    // Create bootstrap method handle for LambdaMetafactory
    val bootstrapMethodHandle = new Handle(
      Opcodes.H_INVOKESTATIC,
      "java/lang/invoke/LambdaMetafactory",
      "metafactory",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
      false
    )
    
    // Create the method reference handle
    val implMethodHandle = new Handle(
      Opcodes.H_INVOKESTATIC,
      targetClassName,
      functionName,
      methodDescriptor,
      false
    )
    
    // Generate invokedynamic
    mv.visitInvokeDynamicInsn(
      interfaceMethodName,
      s"()L$functionalInterface;",
      bootstrapMethodHandle,
      Type.getType(interfaceMethodDesc),
      implMethodHandle,
      Type.getType(methodDescriptor)
    )
  }
  
  private def getTypeSize(typeAnnotation: Option[TypeAnnotation]): Int = {
    typeAnnotation match {
      case Some(SimpleType("Double", _)) => 2
      case Some(SimpleType("Long", _)) => 2
      case _ => 1
    }
  }
  
  private var lambdaCounter = 0
  
  private def convertTypeAnnotationToType(typeAnn: TypeAnnotation): tylang.types.Type = {
    typeAnn match {
      case SimpleType("Int", _) => tylang.types.IntType
      case SimpleType("Double", _) => tylang.types.DoubleType
      case SimpleType("String", _) => tylang.types.StringType
      case SimpleType("Boolean", _) => tylang.types.BooleanType
      case SimpleType("Unit", _) => tylang.types.UnitType
      case FunctionType(paramTypes, returnType, _) =>
        val convertedParamTypes = paramTypes.map(convertTypeAnnotationToType)
        val convertedReturnType = convertTypeAnnotationToType(returnType)
        tylang.types.FunctionType(convertedParamTypes, convertedReturnType)
      case _ => tylang.types.AnyType
    }
  }
  
  private def getTypeDescriptorFromType(typ: tylang.types.Type): String = {
    typ match {
      case _: tylang.types.IntType.type => "I"
      case _: tylang.types.DoubleType.type => "D"
      case _: tylang.types.BooleanType.type => "Z"
      case _: tylang.types.StringType.type => "Ljava/lang/String;"
      case _: tylang.types.UnitType.type => "V"
      case _ => "Ljava/lang/Object;"
    }
  }
  
  // Helper methods
  private def getTypeDescriptor(typeAnnotation: Option[TypeAnnotation]): String = {
    typeAnnotation match {
      case Some(SimpleType("Int", _)) => "I"
      case Some(SimpleType("Double", _)) => "D"
      case Some(SimpleType("String", _)) => "Ljava/lang/String;"
      case Some(SimpleType("Boolean", _)) => "Z"
      case Some(SimpleType("Unit", _)) => "V"
      case Some(SimpleType(name, _)) => s"L$name;"
      case Some(GenericType("List", _, _)) => "Ljava/util/List;"
      case Some(GenericType("Map", _, _)) => "Ljava/util/Map;"
      case Some(GenericType("Set", _, _)) => "Ljava/util/Set;"
      case None => "Ljava/lang/Object;"
      case _ => "Ljava/lang/Object;"
    }
  }
  
  private def getInternalName(typeAnnotation: TypeAnnotation): String = {
    typeAnnotation match {
      case SimpleType(name, _) => name
      case GenericType(name, _, _) => name
      case _ => "java/lang/Object"
    }
  }
  
  private def getLoadOpcode(typeAnnotation: Option[TypeAnnotation]): Int = {
    typeAnnotation match {
      case Some(SimpleType("Int", _)) => ILOAD
      case Some(SimpleType("Double", _)) => DLOAD
      case Some(SimpleType("Boolean", _)) => ILOAD
      case _ => ALOAD
    }
  }
  
  private def getStoreOpcode(typeAnnotation: Option[TypeAnnotation]): Int = {
    typeAnnotation match {
      case Some(SimpleType("Int", _)) => ISTORE
      case Some(SimpleType("Double", _)) => DSTORE
      case Some(SimpleType("Boolean", _)) => ISTORE
      case _ => ASTORE
    }
  }
  
  private def getReturnOpcode(returnType: Option[TypeAnnotation]): Int = {
    returnType match {
      case Some(SimpleType("Int", _)) => IRETURN
      case Some(SimpleType("Double", _)) => DRETURN
      case Some(SimpleType("Boolean", _)) => IRETURN
      case Some(SimpleType("Unit", _)) => RETURN
      case _ => ARETURN
    }
  }
  
  private def getCurrentClassName: String = {
    classWriters.keys.lastOption.getOrElse("Unknown")
  }
}

object CodeGenerator {
  def apply(): CodeGenerator = new CodeGenerator()
}