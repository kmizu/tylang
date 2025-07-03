package tylang.types

sealed trait Type {
  def name: String
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean
  def isSupertypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other.isSubtypeOf(this)
}

// Basic types
case object IntType extends Type {
  def name = "Int"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[IntType.type] || other.isInstanceOf[AnyType.type]
}

case object DoubleType extends Type {
  def name = "Double"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[DoubleType.type] || other.isInstanceOf[AnyType.type]
}

case object StringType extends Type {
  def name = "String"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[StringType.type] || other.isInstanceOf[AnyType.type]
}

case object BooleanType extends Type {
  def name = "Boolean"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[BooleanType.type] || other.isInstanceOf[AnyType.type]
}

case object UnitType extends Type {
  def name = "Unit"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[UnitType.type] || other.isInstanceOf[AnyType.type]
}

case object AnyType extends Type {
  def name = "Any"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[AnyType.type]
}

case object NothingType extends Type {
  def name = "Nothing"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = true
}

case object NullType extends Type {
  def name = "Null"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = 
    other.isInstanceOf[NullType.type] || other.isInstanceOf[AnyType.type] || other.isInstanceOf[ReferenceType]
}

// Collection types
case class ListType(elementType: Type) extends Type {
  def name = s"List[${elementType.name}]"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case ListType(otherElementType) => elementType.isSubtypeOf(otherElementType)
    case _ if other.isInstanceOf[AnyType.type] => true
    case _ => false
  }
}

case class MapType(keyType: Type, valueType: Type) extends Type {
  def name = s"Map[${keyType.name}, ${valueType.name}]"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case MapType(otherKeyType, otherValueType) => 
      keyType.isSubtypeOf(otherKeyType) && valueType.isSubtypeOf(otherValueType)
    case _ if other.isInstanceOf[AnyType.type] => true
    case _ => false
  }
}

case class SetType(elementType: Type) extends Type {
  def name = s"Set[${elementType.name}]"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case SetType(otherElementType) => elementType.isSubtypeOf(otherElementType)
    case _ if other.isInstanceOf[AnyType.type] => true
    case _ => false
  }
}

// Function types
case class FunctionType(parameterTypes: List[Type], returnType: Type) extends Type {
  def name = s"(${parameterTypes.map(_.name).mkString(", ")}) => ${returnType.name}"
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case FunctionType(otherParamTypes, otherReturnType) =>
      parameterTypes.length == otherParamTypes.length &&
      otherParamTypes.zip(parameterTypes).forall { case (otherParam, thisParam) => otherParam.isSubtypeOf(thisParam) } &&
      returnType.isSubtypeOf(otherReturnType)
    case _ if other.isInstanceOf[AnyType.type] => true
    case _ => false
  }
}

// Generic types
case class GenericType(name: String, typeArguments: List[Type], definition: GenericTypeDefinition) extends Type {
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case GenericType(otherName, otherTypeArgs, otherDefinition) if name == otherName =>
      if (typeArguments.length != otherTypeArgs.length) false
      else {
        typeArguments.zip(otherTypeArgs).zip(definition.typeParameters).forall {
          case ((thisArg, otherArg), typeParam) =>
            if (typeParam.variance.isInstanceOf[Covariant.type]) thisArg.isSubtypeOf(otherArg)
            else if (typeParam.variance.isInstanceOf[Contravariant.type]) otherArg.isSubtypeOf(thisArg)
            else false // Simplified invariant check
        }
      }
    case _ if other.isInstanceOf[AnyType.type] => true
    case _ => false
  }
}

// Type parameters
case class TypeParameter(
  name: String,
  variance: Variance,
  upperBound: Option[Type] = None,
  lowerBound: Option[Type] = None
) {
  def isWithinBounds(t: Type)(implicit ctx: TypeContext): Boolean = {
    val upperOk = upperBound.forall(bound => t.isSubtypeOf(bound))
    val lowerOk = lowerBound.forall(bound => bound.isSubtypeOf(t))
    upperOk && lowerOk
  }
}

sealed trait Variance
case object Covariant extends Variance
case object Contravariant extends Variance
case object Invariant extends Variance

// Structural types
case class StructuralType(members: Map[String, Type]) extends Type {
  def name = s"{ ${members.map { case (name, t) => s"$name: ${t.name}" }.mkString(", ")} }"
  
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case StructuralType(otherMembers) =>
      // Structural subtyping: this type must have all members of the other type
      otherMembers.forall { case (memberName, memberType) =>
        members.get(memberName).exists(_.isSubtypeOf(memberType))
      }
    case _ if other.isInstanceOf[AnyType.type] => true
    case _ => false
  }
}

// Method signatures for structural types
case class MethodSignature(
  name: String,
  typeParameters: List[TypeParameter],
  parameterTypes: List[Type],
  returnType: Type
) {
  def isSubtypeOf(other: MethodSignature)(implicit ctx: TypeContext): Boolean = {
    name == other.name &&
    typeParameters.length == other.typeParameters.length &&
    parameterTypes.length == other.parameterTypes.length &&
    other.parameterTypes.zip(parameterTypes).forall { case (otherParam, thisParam) => otherParam.isSubtypeOf(thisParam) } &&
    returnType.isSubtypeOf(other.returnType)
  }
}

// Reference types (classes, traits, objects)
sealed trait ReferenceType extends Type

case class ClassType(
  name: String,
  typeArguments: List[Type],
  superClass: Option[Type],
  traits: List[Type],
  members: Map[String, Type]
) extends ReferenceType {
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case ClassType(otherName, otherTypeArgs, _, _, _) if name == otherName =>
      // Same class with potentially different type arguments
      // For now, we implement invariant semantics (exact type match required)
      typeArguments.length == otherTypeArgs.length &&
      typeArguments.zip(otherTypeArgs).forall { case (thisArg, otherArg) =>
        thisArg.name == otherArg.name // Invariant: types must be exactly the same
      }
    case StructuralType(otherMembers) =>
      // Check if this class structurally matches the structural type
      otherMembers.forall { case (memberName, memberType) =>
        members.get(memberName).exists(_.isSubtypeOf(memberType))
      }
    case otherType =>
      // Check superclass and traits
      superClass.exists(_.isSubtypeOf(otherType)) ||
      traits.exists(_.isSubtypeOf(otherType)) ||
      otherType.isInstanceOf[AnyType.type]
  }
}

case class TraitType(
  name: String,
  typeArguments: List[Type],
  superTraits: List[Type],
  members: Map[String, Type]
) extends ReferenceType {
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case TraitType(otherName, otherTypeArgs, _, _) if name == otherName =>
      typeArguments.zip(otherTypeArgs).forall { case (thisArg, otherArg) =>
        thisArg.isSubtypeOf(otherArg)
      }
    case otherType =>
      superTraits.exists(_.isSubtypeOf(otherType)) ||
      otherType.isInstanceOf[AnyType.type]
  }
}

case class ObjectType(
  name: String,
  superClass: Option[Type],
  traits: List[Type],
  members: Map[String, Type]
) extends ReferenceType {
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = other match {
    case ObjectType(otherName, _, _, _) if name == otherName => true
    case otherType =>
      superClass.exists(_.isSubtypeOf(otherType)) ||
      traits.exists(_.isSubtypeOf(otherType)) ||
      otherType.isInstanceOf[AnyType.type]
  }
}

// Generic type definitions
case class GenericTypeDefinition(
  name: String,
  typeParameters: List[TypeParameter],
  baseType: Type
) {
  def instantiate(typeArguments: List[Type]): Type = {
    require(typeArguments.length == typeParameters.length)
    // Substitute type arguments - simplified implementation
    baseType
  }
}

// Type variables for inference
case class TypeVariable(name: String, id: Int) extends Type {
  def isSubtypeOf(other: Type)(implicit ctx: TypeContext): Boolean = {
    ctx.getConstraint(this) match {
      case Some(constraint) => constraint.isSubtypeOf(other)
      case None => other.isInstanceOf[AnyType.type]
    }
  }
}

// Type context for inference and checking
case class TypeContext(
  types: Map[String, Type] = Map.empty,
  constraints: Map[TypeVariable, Type] = Map.empty,
  typeDefinitions: Map[String, GenericTypeDefinition] = Map.empty
) {
  def withType(name: String, t: Type): TypeContext = 
    copy(types = types + (name -> t))
  
  def withConstraint(tv: TypeVariable, t: Type): TypeContext = 
    copy(constraints = constraints + (tv -> t))
  
  def withTypeDefinition(name: String, definition: GenericTypeDefinition): TypeContext = 
    copy(typeDefinitions = typeDefinitions + (name -> definition))
  
  def getType(name: String): Option[Type] = types.get(name)
  def getConstraint(tv: TypeVariable): Option[Type] = constraints.get(tv)
  def getTypeDefinition(name: String): Option[GenericTypeDefinition] = typeDefinitions.get(name)
}