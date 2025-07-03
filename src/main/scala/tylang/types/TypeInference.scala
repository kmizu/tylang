package tylang.types

import tylang.ast.*
import tylang.{TypeException, SourceLocation}
import scala.collection.mutable

class TypeInference {
  private var nextTypeVarId = 0
  private def freshTypeVar(): TypeVariable = {
    val id = nextTypeVarId
    nextTypeVarId += 1
    TypeVariable(s"T$id", id)
  }
  
  def inferType(expr: Expression)(implicit ctx: TypeContext): Type = {
    expr match {
      case IntLiteral(_, _) => IntType
      case DoubleLiteral(_, _) => DoubleType
      case StringLiteral(_, _) => StringType
      case BooleanLiteral(_, _) => BooleanType
      
      case Identifier(name, location) =>
        ctx.getType(name).getOrElse {
          throw TypeException(s"Undefined variable: $name", location)
        }
      
      case ThisExpression(location) =>
        ctx.getType("this").getOrElse {
          throw TypeException("'this' is not available in this context", location)
        }
      
      case BinaryOp(left, op, right, location) =>
        inferBinaryOpType(left, op, right, location)
      
      case UnaryOp(op, operand, location) =>
        inferUnaryOpType(op, operand, location)
      
      case MethodCall(receiver, methodName, args, typeArgs, location) =>
        inferMethodCallType(receiver, methodName, args, typeArgs, location)
      
      case FieldAccess(receiver, fieldName, location) =>
        inferFieldAccessType(receiver, fieldName, location)
      
      case Assignment(target, value, location) =>
        val targetType = inferType(target)
        val valueType = inferType(value)
        if (!valueType.isSubtypeOf(targetType)) {
          throw TypeException(s"Cannot assign ${valueType.name} to ${targetType.name}", location)
        }
        valueType
      
      case Block(statements, location) =>
        var currentCtx = ctx
        statements.foldLeft(UnitType: Type) { (_, stmt) =>
          stmt match {
            case ExpressionStatement(expr, _) => 
              inferType(expr)(currentCtx)
            case VariableDeclaration(name, typeAnnotation, initializer, _, _) =>
              val varType = typeAnnotation match {
                case Some(annotation) => resolveTypeAnnotation(annotation)
                case None => initializer match {
                  case Some(init) => inferType(init)(currentCtx)
                  case None => throw TypeException("Variable declaration must have type annotation or initializer", location)
                }
              }
              currentCtx = currentCtx.withType(name, varType)
              UnitType
            case Return(value, _) =>
              value.map(inferType(_)(currentCtx)).getOrElse(UnitType)
          }
        }
      
      case IfExpression(condition, thenBranch, elseBranch, location) =>
        val conditionType = inferType(condition)
        if (!conditionType.isSubtypeOf(BooleanType)) {
          throw TypeException(s"If condition must be Boolean, got ${conditionType.name}", location)
        }
        
        val thenType = inferType(thenBranch)
        elseBranch match {
          case Some(elseBranch) =>
            val elseType = inferType(elseBranch)
            unifyTypes(thenType, elseType, location)
          case None =>
            UnitType
        }
      
      case WhileExpression(condition, body, location) =>
        val conditionType = inferType(condition)
        if (!conditionType.isSubtypeOf(BooleanType)) {
          throw TypeException(s"While condition must be Boolean, got ${conditionType.name}", location)
        }
        inferType(body)
        UnitType
      
      case ListLiteral(elements, location) =>
        if (elements.isEmpty) {
          ListType(freshTypeVar())
        } else {
          val elementTypes = elements.map(inferType)
          val elementType = elementTypes.reduce((t1, t2) => unifyTypes(t1, t2, location))
          ListType(elementType)
        }
      
      case MapLiteral(pairs, location) =>
        if (pairs.isEmpty) {
          MapType(freshTypeVar(), freshTypeVar())
        } else {
          val keyTypes = pairs.map(p => inferType(p._1))
          val valueTypes = pairs.map(p => inferType(p._2))
          val keyType = keyTypes.reduce((t1, t2) => unifyTypes(t1, t2, location))
          val valueType = valueTypes.reduce((t1, t2) => unifyTypes(t1, t2, location))
          MapType(keyType, valueType)
        }
      
      case Lambda(parameters, body, location) =>
        val paramTypes = parameters.map { param =>
          param.typeAnnotation match {
            case Some(annotation) => resolveTypeAnnotation(annotation)
            case None => freshTypeVar()
          }
        }
        
        val lambdaCtx = parameters.zip(paramTypes).foldLeft(ctx) { case (ctx, (param, paramType)) =>
          ctx.withType(param.name, paramType)
        }
        
        val returnType = inferType(body)(lambdaCtx)
        tylang.types.FunctionType(paramTypes, returnType)
      
      case _ =>
        throw TypeException(s"Type inference not implemented for ${expr.getClass.getSimpleName}", expr.location)
    }
  }
  
  private def inferBinaryOpType(left: Expression, op: String, right: Expression, location: SourceLocation)(implicit ctx: TypeContext): Type = {
    val leftType = inferType(left)
    val rightType = inferType(right)
    
    op match {
      case "+" | "-" | "*" | "/" | "%" =>
        if (leftType.isInstanceOf[IntType.type] && rightType.isInstanceOf[IntType.type]) IntType
        else if (leftType.isInstanceOf[DoubleType.type] && rightType.isInstanceOf[DoubleType.type]) DoubleType
        else if ((leftType.isInstanceOf[IntType.type] && rightType.isInstanceOf[DoubleType.type]) || (leftType.isInstanceOf[DoubleType.type] && rightType.isInstanceOf[IntType.type])) DoubleType
        else if (leftType.isInstanceOf[StringType.type] && op == "+") StringType
        else if (rightType.isInstanceOf[StringType.type] && op == "+") StringType
        else throw TypeException(s"Cannot apply operator $op to ${leftType.name} and ${rightType.name}", location)
      
      case "==" | "!=" | "<" | ">" | "<=" | ">=" =>
        if (leftType.isSubtypeOf(rightType) || rightType.isSubtypeOf(leftType)) {
          BooleanType
        } else {
          throw TypeException(s"Cannot compare ${leftType.name} and ${rightType.name}", location)
        }
      
      case "&&" | "||" =>
        if (leftType.isSubtypeOf(BooleanType) && rightType.isSubtypeOf(BooleanType)) {
          BooleanType
        } else {
          throw TypeException(s"Logical operators require Boolean operands, got ${leftType.name} and ${rightType.name}", location)
        }
      
      case _ =>
        throw TypeException(s"Unknown binary operator: $op", location)
    }
  }
  
  private def inferUnaryOpType(op: String, operand: Expression, location: SourceLocation)(implicit ctx: TypeContext): Type = {
    val operandType = inferType(operand)
    
    op match {
      case "!" =>
        if (operandType.isSubtypeOf(BooleanType)) {
          BooleanType
        } else {
          throw TypeException(s"Logical not requires Boolean operand, got ${operandType.name}", location)
        }
      
      case "-" | "+" =>
        if (operandType.isInstanceOf[IntType.type]) IntType
        else if (operandType.isInstanceOf[DoubleType.type]) DoubleType
        else throw TypeException(s"Unary $op requires numeric operand, got ${operandType.name}", location)
      
      case _ =>
        throw TypeException(s"Unknown unary operator: $op", location)
    }
  }
  
  private def inferMethodCallType(receiver: Option[Expression], methodName: String, args: List[Expression], typeArgs: List[TypeAnnotation], location: SourceLocation)(implicit ctx: TypeContext): Type = {
    val argTypes = args.map(inferType)
    
    receiver match {
      case Some(recv) =>
        // Special case: if methodName is "apply" and receiver is an Identifier, 
        // treat this as a direct function call
        if (methodName == "apply" && recv.isInstanceOf[Identifier]) {
          val functionName = recv.asInstanceOf[Identifier].name
          ctx.getType(functionName) match {
            case Some(tylang.types.FunctionType(paramTypes, returnType)) =>
              if (argTypes.length != paramTypes.length) {
                throw TypeException(s"Function $functionName expects ${paramTypes.length} arguments, got ${argTypes.length}", location)
              }
              argTypes.zip(paramTypes).foreach { case (argType, paramType) =>
                if (!argType.isSubtypeOf(paramType)) {
                  throw TypeException(s"Argument type ${argType.name} does not match parameter type ${paramType.name}", location)
                }
              }
              return returnType
            case Some(otherType) =>
              // Fall through to normal method lookup if it's not a function
            case None =>
              // Fall through to normal method lookup if function not found
          }
        }
        
        val receiverType = inferType(recv)
        lookupMethod(receiverType, methodName, argTypes, location)
      
      case None =>
        // Top-level function call
        ctx.getType(methodName) match {
          case Some(tylang.types.FunctionType(paramTypes, returnType)) =>
            if (argTypes.length != paramTypes.length) {
              throw TypeException(s"Function $methodName expects ${paramTypes.length} arguments, got ${argTypes.length}", location)
            }
            argTypes.zip(paramTypes).foreach { case (argType, paramType) =>
              if (!argType.isSubtypeOf(paramType)) {
                throw TypeException(s"Argument type ${argType.name} does not match parameter type ${paramType.name}", location)
              }
            }
            returnType
          case Some(otherType) =>
            throw TypeException(s"$methodName is not a function, it has type ${otherType.name}", location)
          case None =>
            throw TypeException(s"Undefined function: $methodName", location)
        }
    }
  }
  
  private def inferFieldAccessType(receiver: Expression, fieldName: String, location: SourceLocation)(implicit ctx: TypeContext): Type = {
    val receiverType = inferType(receiver)
    lookupField(receiverType, fieldName, location)
  }
  
  private def lookupMethod(receiverType: Type, methodName: String, argTypes: List[Type], location: SourceLocation)(implicit ctx: TypeContext): Type = {
    receiverType match {
      case tylang.types.StructuralType(members) =>
        members.get(methodName) match {
          case Some(tylang.types.FunctionType(paramTypes, returnType)) =>
            if (argTypes.length != paramTypes.length) {
              throw TypeException(s"Method $methodName expects ${paramTypes.length} arguments, got ${argTypes.length}", location)
            }
            argTypes.zip(paramTypes).foreach { case (argType, paramType) =>
              if (!argType.isSubtypeOf(paramType)) {
                throw TypeException(s"Argument type ${argType.name} does not match parameter type ${paramType.name}", location)
              }
            }
            returnType
          case Some(otherType) =>
            throw TypeException(s"$methodName is not a method, it has type ${otherType.name}", location)
          case None =>
            throw TypeException(s"Method $methodName not found on type ${receiverType.name}", location)
        }
      
      case tylang.types.ClassType(_, _, _, _, members) =>
        members.get(methodName) match {
          case Some(methodType) => methodType // Simplified - should check arguments
          case None => throw TypeException(s"Method $methodName not found on type ${receiverType.name}", location)
        }
      
      case _ =>
        // Check for built-in methods or extension methods
        lookupBuiltinMethod(receiverType, methodName, argTypes, location)
    }
  }
  
  private def lookupField(receiverType: Type, fieldName: String, location: SourceLocation)(implicit ctx: TypeContext): Type = {
    receiverType match {
      case tylang.types.StructuralType(members) =>
        members.get(fieldName).getOrElse {
          throw TypeException(s"Field $fieldName not found on type ${receiverType.name}", location)
        }
      
      case tylang.types.ClassType(_, _, _, _, members) =>
        members.get(fieldName).getOrElse {
          throw TypeException(s"Field $fieldName not found on type ${receiverType.name}", location)
        }
      
      case _ =>
        throw TypeException(s"Type ${receiverType.name} does not have fields", location)
    }
  }
  
  private def lookupBuiltinMethod(receiverType: Type, methodName: String, argTypes: List[Type], location: SourceLocation)(implicit ctx: TypeContext): Type = {
    (receiverType, methodName) match {
      case (ListType(elementType), "size") if argTypes.isEmpty => IntType
      case (ListType(elementType), "get") if argTypes.length == 1 && argTypes.head.isSubtypeOf(IntType) => elementType
      case (ListType(elementType), "add") if argTypes.length == 1 && argTypes.head.isSubtypeOf(elementType) => ListType(elementType)
      case (receiverType, "length") if receiverType.isInstanceOf[StringType.type] && argTypes.isEmpty => IntType
      case (receiverType, "substring") if receiverType.isInstanceOf[StringType.type] && argTypes.length == 2 && argTypes.forall(_.isSubtypeOf(IntType)) => StringType
      case _ => throw TypeException(s"Method $methodName not found on type ${receiverType.name}", location)
    }
  }
  
  private def unifyTypes(t1: Type, t2: Type, location: SourceLocation)(implicit ctx: TypeContext): Type = {
    if (t1.isSubtypeOf(t2)) t2
    else if (t2.isSubtypeOf(t1)) t1
    else throw TypeException(s"Cannot unify types ${t1.name} and ${t2.name}", location)
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
          case _ => throw TypeException(s"Unknown generic type: $name", location)
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

object TypeInference {
  def apply(): TypeInference = new TypeInference()
}