package tylang.types

import tylang.ast.*
import tylang.{TypeException, SourceLocation}

class TypeChecker {
  private val inference = TypeInference()
  
  def checkProgram(program: Program): Unit = {
    implicit val ctx = createInitialContext()
    
    // First pass: collect all type definitions
    val ctxWithTypes = program.declarations.foldLeft(ctx) { (ctx, decl) =>
      decl match {
        case ClassDeclaration(name, typeParams, superClass, traits, constructor, members, location) =>
          val classType = createClassType(name, typeParams, superClass, traits, members, location)
          ctx.withType(name, classType)
        
        case TraitDeclaration(name, typeParams, superTraits, members, location) =>
          val traitType = createTraitType(name, typeParams, superTraits, members, location)
          ctx.withType(name, traitType)
        
        case ObjectDeclaration(name, superClass, traits, members, location) =>
          val objectType = createObjectType(name, superClass, traits, members, location)
          ctx.withType(name, objectType)
        
        case FunctionDeclaration(name, typeParams, params, returnType, body, location) =>
          val functionType = createFunctionType(typeParams, params, returnType, location)
          ctx.withType(name, functionType)
        
        case _ => ctx
      }
    }
    
    // Second pass: check all declarations
    program.declarations.foreach { decl =>
      checkDeclaration(decl)(ctxWithTypes)
    }
  }
  
  def checkDeclaration(decl: Declaration)(implicit ctx: TypeContext): Unit = {
    decl match {
      case FunctionDeclaration(name, typeParams, params, returnType, body, location) =>
        checkFunctionDeclaration(name, typeParams, params, returnType, body, location)
      
      case ClassDeclaration(name, typeParams, superClass, traits, constructor, members, location) =>
        checkClassDeclaration(name, typeParams, superClass, traits, constructor, members, location)
      
      case TraitDeclaration(name, typeParams, superTraits, members, location) =>
        checkTraitDeclaration(name, typeParams, superTraits, members, location)
      
      case ObjectDeclaration(name, superClass, traits, members, location) =>
        checkObjectDeclaration(name, superClass, traits, members, location)
      
      case ExtensionDeclaration(targetType, methods, location) =>
        checkExtensionDeclaration(targetType, methods, location)
    }
  }
  
  private def checkFunctionDeclaration(
    name: String,
    typeParams: List[TypeParameter],
    params: List[Parameter],
    returnType: Option[TypeAnnotation],
    body: Expression,
    location: SourceLocation
  )(implicit ctx: TypeContext): Unit = {
    
    // Create context with type parameters
    val ctxWithTypeParams = typeParams.foldLeft(ctx) { (ctx, typeParam) =>
      ctx.withType(typeParam.name, TypeVariable(typeParam.name, 0))
    }
    
    // Create function type for recursive calls
    val paramTypes = params.map { param =>
      param.typeAnnotation match {
        case Some(annotation) => resolveTypeAnnotation(annotation)(ctxWithTypeParams)
        case None => throw TypeException(s"Function parameter ${param.name} must have type annotation", location)
      }
    }
    
    val expectedReturnType = returnType match {
      case Some(annotation) => resolveTypeAnnotation(annotation)(ctxWithTypeParams)
      case None => TypeVariable(s"${name}_return", 0) // Placeholder for inference
    }
    
    val functionType = tylang.types.FunctionType(paramTypes, expectedReturnType)
    
    // Add function to context for recursive calls
    val ctxWithFunction = ctxWithTypeParams.withType(name, functionType)
    
    // Create context with parameters
    val ctxWithParams = params.zip(paramTypes).foldLeft(ctxWithFunction) { case (ctx, (param, paramType)) =>
      ctx.withType(param.name, paramType)
    }
    
    // Check body
    val bodyType = inference.inferType(body)(ctxWithParams)
    
    // Check return type
    returnType match {
      case Some(annotation) =>
        val expectedReturnType = resolveTypeAnnotation(annotation)(ctx)
        if (!bodyType.isSubtypeOf(expectedReturnType)) {
          throw TypeException(s"Function body type ${bodyType.name} does not match declared return type ${expectedReturnType.name}", location)
        }
      case None =>
        // Return type is inferred from body
    }
    
    // Check type parameter bounds
    typeParams.foreach { typeParam =>
      typeParam.upperBound.foreach { bound =>
        val boundType = resolveTypeAnnotation(bound)(ctx)
        // Check that bound is valid
      }
      typeParam.lowerBound.foreach { bound =>
        val boundType = resolveTypeAnnotation(bound)(ctx)
        // Check that bound is valid
      }
    }
  }
  
  private def checkClassDeclaration(
    name: String,
    typeParams: List[TypeParameter],
    superClass: Option[TypeAnnotation],
    traits: List[TypeAnnotation],
    constructor: Option[Constructor],
    members: List[ClassMember],
    location: SourceLocation
  )(implicit ctx: TypeContext): Unit = {
    
    // Create context with type parameters
    val ctxWithTypeParams = typeParams.foldLeft(ctx) { (ctx, typeParam) =>
      ctx.withType(typeParam.name, TypeVariable(typeParam.name, 0))
    }
    
    // Check superclass
    superClass.foreach { superAnnotation =>
      val superType = resolveTypeAnnotation(superAnnotation)(ctxWithTypeParams)
      superType match {
        case _: ClassType => // OK
        case _ => throw TypeException(s"Class can only extend other classes, not ${superType.name}", location)
      }
    }
    
    // Check traits
    traits.foreach { traitAnnotation =>
      val traitType = resolveTypeAnnotation(traitAnnotation)(ctxWithTypeParams)
      traitType match {
        case _: TraitType => // OK
        case _ => throw TypeException(s"Class can only implement traits, not ${traitType.name}", location)
      }
    }
    
    // Create context with 'this' type
    val thisType = ClassType(name, typeParams.map(tp => TypeVariable(tp.name, 0)), superClass.map(resolveTypeAnnotation(_)(ctxWithTypeParams)), traits.map(resolveTypeAnnotation(_)(ctxWithTypeParams)), Map.empty)
    val ctxWithThis = ctxWithTypeParams.withType("this", thisType)
    
    // Add constructor parameters to context for class members
    val ctxWithConstructorParams = constructor match {
      case Some(constr) =>
        constr.parameters.foldLeft(ctxWithThis) { (ctx, param) =>
          val paramType = param.typeAnnotation match {
            case Some(annotation) => resolveTypeAnnotation(annotation)(ctx)
            case None => throw TypeException(s"Constructor parameter ${param.name} must have type annotation", location)
          }
          ctx.withType(param.name, paramType)
        }
      case None => ctxWithThis
    }
    
    // Check constructor
    constructor.foreach { constr =>
      constr.body.foreach { body =>
        inference.inferType(body)(ctxWithConstructorParams)
      }
    }
    
    // Add all class methods to context for mutual references
    val ctxWithMethods = members.foldLeft(ctxWithConstructorParams) {
      case (ctx, MethodMember(method)) =>
        val functionType = createFunctionType(method.typeParameters, method.parameters, method.returnType, method.location)(ctx)
        ctx.withType(method.name, functionType)
      case (ctx, _) => ctx
    }
    
    // Check members with all methods and constructor parameters available
    members.foreach {
      case MethodMember(method) =>
        checkDeclaration(method)(ctxWithMethods)
      case FieldMember(field) =>
        checkVariableDeclaration(field)(ctxWithMethods)
    }
  }
  
  private def checkTraitDeclaration(
    name: String,
    typeParams: List[TypeParameter],
    superTraits: List[TypeAnnotation],
    members: List[TraitMember],
    location: SourceLocation
  )(implicit ctx: TypeContext): Unit = {
    
    // Create context with type parameters
    val ctxWithTypeParams = typeParams.foldLeft(ctx) { (ctx, typeParam) =>
      ctx.withType(typeParam.name, TypeVariable(typeParam.name, 0))
    }
    
    // Check super traits
    superTraits.foreach { traitAnnotation =>
      val traitType = resolveTypeAnnotation(traitAnnotation)(ctxWithTypeParams)
      traitType match {
        case _: TraitType => // OK
        case _ => throw TypeException(s"Trait can only extend other traits, not ${traitType.name}", location)
      }
    }
    
    // Create context with 'this' type
    val thisType = TraitType(name, typeParams.map(tp => TypeVariable(tp.name, 0)), superTraits.map(resolveTypeAnnotation(_)(ctxWithTypeParams)), Map.empty)
    val ctxWithThis = ctxWithTypeParams.withType("this", thisType)
    
    // Check members
    members.foreach {
      case ConcreteMethodMember(method) =>
        checkDeclaration(method)(ctxWithThis)
      case AbstractMethodMember(methodName, methodTypeParams, methodParams, methodReturnType, methodLocation) =>
        // Abstract methods don't have bodies, just check signatures
        val ctxWithMethodTypeParams = methodTypeParams.foldLeft(ctxWithThis) { (ctx, typeParam) =>
          ctx.withType(typeParam.name, TypeVariable(typeParam.name, 0))
        }
        
        methodParams.foreach { param =>
          param.typeAnnotation match {
            case Some(annotation) => resolveTypeAnnotation(annotation)(ctxWithMethodTypeParams)
            case None => throw TypeException(s"Abstract method parameter ${param.name} must have type annotation", methodLocation)
          }
        }
        
        methodReturnType.foreach { returnType =>
          resolveTypeAnnotation(returnType)(ctxWithMethodTypeParams)
        }
    }
  }
  
  private def checkObjectDeclaration(
    name: String,
    superClass: Option[TypeAnnotation],
    traits: List[TypeAnnotation],
    members: List[ClassMember],
    location: SourceLocation
  )(implicit ctx: TypeContext): Unit = {
    
    // Check superclass
    superClass.foreach { superAnnotation =>
      val superType = resolveTypeAnnotation(superAnnotation)(ctx)
      superType match {
        case _: ClassType => // OK
        case _ => throw TypeException(s"Object can only extend classes, not ${superType.name}", location)
      }
    }
    
    // Check traits
    traits.foreach { traitAnnotation =>
      val traitType = resolveTypeAnnotation(traitAnnotation)(ctx)
      traitType match {
        case _: TraitType => // OK
        case _ => throw TypeException(s"Object can only implement traits, not ${traitType.name}", location)
      }
    }
    
    // Create context with 'this' type and all object methods
    val thisType = ObjectType(name, superClass.map(resolveTypeAnnotation(_)(ctx)), traits.map(resolveTypeAnnotation(_)(ctx)), Map.empty)
    val ctxWithThis = ctx.withType("this", thisType)
    
    // Add all object methods to context for mutual references
    val ctxWithMethods = members.foldLeft(ctxWithThis) {
      case (ctx, MethodMember(method)) =>
        val functionType = createFunctionType(method.typeParameters, method.parameters, method.returnType, method.location)(ctx)
        ctx.withType(method.name, functionType)
      case (ctx, _) => ctx
    }
    
    // Check members with all methods available
    members.foreach {
      case MethodMember(method) =>
        checkDeclaration(method)(ctxWithMethods)
      case FieldMember(field) =>
        checkVariableDeclaration(field)(ctxWithMethods)
    }
  }
  
  private def checkExtensionDeclaration(
    targetType: TypeAnnotation,
    methods: List[FunctionDeclaration],
    location: SourceLocation
  )(implicit ctx: TypeContext): Unit = {
    
    val resolvedTargetType = resolveTypeAnnotation(targetType)(ctx)
    
    // Check each extension method
    methods.foreach { method =>
      // Extension methods have an implicit 'this' parameter of the target type
      val ctxWithThis = ctx.withType("this", resolvedTargetType)
      checkDeclaration(method)(ctxWithThis)
    }
  }
  
  private def checkVariableDeclaration(varDecl: VariableDeclaration)(implicit ctx: TypeContext): Unit = {
    val declaredType = varDecl.typeAnnotation.map(resolveTypeAnnotation(_)(ctx))
    
    varDecl.initializer.foreach { init =>
      val initType = inference.inferType(init)(ctx)
      declaredType.foreach { expected =>
        if (!initType.isSubtypeOf(expected)) {
          throw TypeException(s"Variable initializer type ${initType.name} does not match declared type ${expected.name}", varDecl.location)
        }
      }
    }
  }
  
  private def createInitialContext(): TypeContext = {
    TypeContext(
      types = Map(
        "Int" -> IntType,
        "Double" -> DoubleType,
        "String" -> StringType,
        "Boolean" -> BooleanType,
        "Unit" -> UnitType,
        "Any" -> AnyType
      )
    )
  }
  
  private def createClassType(
    name: String,
    typeParams: List[TypeParameter],
    superClass: Option[TypeAnnotation],
    traits: List[TypeAnnotation],
    members: List[ClassMember],
    location: SourceLocation
  )(implicit ctx: TypeContext): ClassType = {
    val memberTypes = members.collect {
      case MethodMember(method) => method.name -> createFunctionType(method.typeParameters, method.parameters, method.returnType, method.location)
      case FieldMember(field) => field.name -> (field.typeAnnotation match {
        case Some(annotation) => resolveTypeAnnotation(annotation)
        case None => throw TypeException(s"Field ${field.name} must have type annotation", location)
      })
    }.toMap
    
    ClassType(
      name,
      typeParams.map(tp => TypeVariable(tp.name, 0)),
      superClass.map(resolveTypeAnnotation(_)),
      traits.map(resolveTypeAnnotation(_)),
      memberTypes
    )
  }
  
  private def createTraitType(
    name: String,
    typeParams: List[TypeParameter],
    superTraits: List[TypeAnnotation],
    members: List[TraitMember],
    location: SourceLocation
  )(implicit ctx: TypeContext): TraitType = {
    val memberTypes = members.collect {
      case ConcreteMethodMember(method) => method.name -> createFunctionType(method.typeParameters, method.parameters, method.returnType, method.location)
      case AbstractMethodMember(methodName, methodTypeParams, methodParams, methodReturnType, methodLocation) =>
        val paramTypes = methodParams.map { param =>
          param.typeAnnotation match {
            case Some(annotation) => resolveTypeAnnotation(annotation)
            case None => throw TypeException(s"Method parameter ${param.name} must have type annotation", methodLocation)
          }
        }
        val returnType = methodReturnType match {
          case Some(annotation) => resolveTypeAnnotation(annotation)
          case None => UnitType
        }
        methodName -> tylang.types.FunctionType(paramTypes, returnType)
    }.toMap
    
    TraitType(
      name,
      typeParams.map(tp => TypeVariable(tp.name, 0)),
      superTraits.map(resolveTypeAnnotation(_)),
      memberTypes
    )
  }
  
  private def createObjectType(
    name: String,
    superClass: Option[TypeAnnotation],
    traits: List[TypeAnnotation],
    members: List[ClassMember],
    location: SourceLocation
  )(implicit ctx: TypeContext): ObjectType = {
    val memberTypes = members.collect {
      case MethodMember(method) => method.name -> createFunctionType(method.typeParameters, method.parameters, method.returnType, method.location)
      case FieldMember(field) => field.name -> (field.typeAnnotation match {
        case Some(annotation) => resolveTypeAnnotation(annotation)
        case None => throw TypeException(s"Field ${field.name} must have type annotation", location)
      })
    }.toMap
    
    ObjectType(
      name,
      superClass.map(resolveTypeAnnotation(_)),
      traits.map(resolveTypeAnnotation(_)),
      memberTypes
    )
  }
  
  private def createFunctionType(
    typeParams: List[TypeParameter],
    params: List[Parameter],
    returnType: Option[TypeAnnotation],
    location: SourceLocation
  )(implicit ctx: TypeContext): tylang.types.FunctionType = {
    val paramTypes = params.map { param =>
      param.typeAnnotation match {
        case Some(annotation) => resolveTypeAnnotation(annotation)
        case None => throw TypeException(s"Function parameter ${param.name} must have type annotation", location)
      }
    }
    
    val retType = returnType match {
      case Some(annotation) => resolveTypeAnnotation(annotation)
      case None => UnitType
    }
    
    tylang.types.FunctionType(paramTypes, retType)
  }
  
  private def resolveTypeAnnotation(annotation: TypeAnnotation)(implicit ctx: TypeContext): Type = {
    annotation match {
      case SimpleType(name, location) =>
        name match {
          case "Int" => IntType
          case "Double" => DoubleType
          case "String" => StringType
          case "Boolean" => BooleanType
          case "Unit" => UnitType
          case "Any" => AnyType
          case _ => ctx.getType(name).getOrElse {
            throw TypeException(s"Unknown type: $name", location)
          }
        }
      
      case GenericType(name, typeArgs, location) =>
        val resolvedArgs = typeArgs.map(resolveTypeAnnotation)
        name match {
          case "List" if resolvedArgs.length == 1 => ListType(resolvedArgs.head)
          case "Map" if resolvedArgs.length == 2 => MapType(resolvedArgs.head, resolvedArgs.tail.head)
          case "Set" if resolvedArgs.length == 1 => SetType(resolvedArgs.head)
          case _ => 
            ctx.getTypeDefinition(name) match {
              case Some(definition) => definition.instantiate(resolvedArgs)
              case None => throw TypeException(s"Unknown generic type: $name", location)
            }
        }
      
      case FunctionType(paramTypes, returnType, location) =>
        val resolvedParamTypes = paramTypes.map(resolveTypeAnnotation)
        val resolvedReturnType = resolveTypeAnnotation(returnType)
        tylang.types.FunctionType(resolvedParamTypes, resolvedReturnType)
      
      case StructuralType(members, location) =>
        val resolvedMembers = members.map { member =>
          member.name -> resolveTypeAnnotation(member.typeAnnotation)
        }.toMap
        tylang.types.StructuralType(resolvedMembers)
    }
  }
}

object TypeChecker {
  def apply(): TypeChecker = new TypeChecker()
}