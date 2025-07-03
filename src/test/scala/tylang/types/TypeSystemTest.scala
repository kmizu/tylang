package tylang.types

import munit.FunSuite

class TypeSystemTest extends FunSuite {
  
  test("basic type equality") {
    assertEquals(IntType, IntType)
    assertEquals(StringType, StringType)
    assertEquals(BooleanType, BooleanType)
    assertEquals(DoubleType, DoubleType)
    assertEquals(UnitType, UnitType)
    assertEquals(AnyType, AnyType)
    
    assert(!IntType.equals(StringType))
    assert(!BooleanType.equals(DoubleType))
  }
  
  test("primitive type subtyping") {
    implicit val ctx: TypeContext = TypeContext()
    
    // All types are subtypes of Any
    assert(IntType.isSubtypeOf(AnyType))
    assert(StringType.isSubtypeOf(AnyType))
    assert(BooleanType.isSubtypeOf(AnyType))
    assert(DoubleType.isSubtypeOf(AnyType))
    assert(UnitType.isSubtypeOf(AnyType))
    
    // Reflexivity: every type is a subtype of itself
    assert(IntType.isSubtypeOf(IntType))
    assert(StringType.isSubtypeOf(StringType))
    assert(BooleanType.isSubtypeOf(BooleanType))
    
    // Non-subtype relationships
    assert(!IntType.isSubtypeOf(StringType))
    assert(!StringType.isSubtypeOf(BooleanType))
    assert(!BooleanType.isSubtypeOf(IntType))
  }
  
  test("nothing type subtyping") {
    implicit val ctx: TypeContext = TypeContext()
    
    // Nothing is a subtype of everything
    assert(NothingType.isSubtypeOf(IntType))
    assert(NothingType.isSubtypeOf(StringType))
    assert(NothingType.isSubtypeOf(BooleanType))
    assert(NothingType.isSubtypeOf(AnyType))
    assert(NothingType.isSubtypeOf(NothingType))
  }
  
  test("function type creation and equality") {
    val intToString = FunctionType(List(IntType), StringType)
    val intStringToBool = FunctionType(List(IntType, StringType), BooleanType)
    val unitToUnit = FunctionType(List.empty, UnitType)
    
    assertEquals(intToString.parameterTypes, List(IntType))
    assertEquals(intToString.returnType, StringType)
    
    assertEquals(intStringToBool.parameterTypes, List(IntType, StringType))
    assertEquals(intStringToBool.returnType, BooleanType)
    
    assertEquals(unitToUnit.parameterTypes, List.empty)
    assertEquals(unitToUnit.returnType, UnitType)
    
    assert(!intToString.equals(intStringToBool))
    assert(!intToString.equals(unitToUnit))
  }
  
  test("function type subtyping - contravariant parameters") {
    implicit val ctx: TypeContext = TypeContext()
    
    val anyToInt = FunctionType(List(AnyType), IntType)
    val stringToInt = FunctionType(List(StringType), IntType)
    
    // Function types are contravariant in parameters:
    // If String <: Any, then (Any => Int) <: (String => Int)
    assert(anyToInt.isSubtypeOf(stringToInt))
    assert(!stringToInt.isSubtypeOf(anyToInt))
  }
  
  test("function type subtyping - covariant return types") {
    implicit val ctx: TypeContext = TypeContext()
    
    val intToString = FunctionType(List(IntType), StringType)
    val intToAny = FunctionType(List(IntType), AnyType)
    
    // Function types are covariant in return type:
    // If String <: Any, then (Int => String) <: (Int => Any)
    assert(intToString.isSubtypeOf(intToAny))
    assert(!intToAny.isSubtypeOf(intToString))
  }
  
  test("list type creation and equality") {
    val intList = ListType(IntType)
    val stringList = ListType(StringType)
    val anyList = ListType(AnyType)
    
    assertEquals(intList.elementType, IntType)
    assertEquals(stringList.elementType, StringType)
    assertEquals(anyList.elementType, AnyType)
    
    assert(!intList.equals(stringList))
    assert(!stringList.equals(anyList))
  }
  
  test("list type subtyping - covariant elements") {
    implicit val ctx: TypeContext = TypeContext()
    
    val intList = ListType(IntType)
    val anyList = ListType(AnyType)
    
    // List types are covariant in element type:
    // If Int <: Any, then List[Int] <: List[Any]
    assert(intList.isSubtypeOf(anyList))
    assert(!anyList.isSubtypeOf(intList))
  }
  
  test("map type creation and subtyping") {
    implicit val ctx: TypeContext = TypeContext()
    
    val stringToInt = MapType(StringType, IntType)
    val anyToAny = MapType(AnyType, AnyType)
    
    assertEquals(stringToInt.keyType, StringType)
    assertEquals(stringToInt.valueType, IntType)
    
    // Map should be covariant in both key and value types
    assert(stringToInt.isSubtypeOf(anyToAny))
    assert(!anyToAny.isSubtypeOf(stringToInt))
  }
  
  test("set type creation and subtyping") {
    implicit val ctx: TypeContext = TypeContext()
    
    val intSet = SetType(IntType)
    val anySet = SetType(AnyType)
    
    assertEquals(intSet.elementType, IntType)
    assertEquals(anySet.elementType, AnyType)
    
    // Set should be covariant in element type
    assert(intSet.isSubtypeOf(anySet))
    assert(!anySet.isSubtypeOf(intSet))
  }
  
  test("structural type creation") {
    val members = Map(
      "toString" -> FunctionType(List.empty, StringType),
      "hashCode" -> FunctionType(List.empty, IntType)
    )
    
    val structType = StructuralType(members)
    
    assertEquals(structType.members.size, 2)
    assert(structType.members.contains("toString"))
    assert(structType.members.contains("hashCode"))
  }
  
  test("structural type subtyping") {
    implicit val ctx: TypeContext = TypeContext()
    
    val toStringOnly = StructuralType(Map(
      "toString" -> FunctionType(List.empty, StringType)
    ))
    
    val withHashCode = StructuralType(Map(
      "toString" -> FunctionType(List.empty, StringType),
      "hashCode" -> FunctionType(List.empty, IntType),
      "equals" -> FunctionType(List(AnyType), BooleanType)
    ))
    
    // Structural subtyping: if a type has all the methods of another type, 
    // it's a subtype (contravariant - fewer requirements = supertype)
    assert(withHashCode.isSubtypeOf(toStringOnly))
    assert(!toStringOnly.isSubtypeOf(withHashCode))
  }
  
  test("class type creation") {
    val stringClass = ClassType(
      name = "String",
      typeArguments = List.empty,
      superClass = Some(AnyType),
      traits = List.empty,
      members = Map("length" -> FunctionType(List.empty, IntType))
    )
    
    assertEquals(stringClass.name, "String")
    assertEquals(stringClass.typeArguments, List.empty)
    assertEquals(stringClass.superClass, Some(AnyType))
    assert(stringClass.members.contains("length"))
  }
  
  test("class type subtyping") {
    implicit val ctx: TypeContext = TypeContext()
    
    val stringClass = ClassType(
      name = "String", 
      typeArguments = List.empty,
      superClass = Some(AnyType),
      traits = List.empty,
      members = Map.empty
    )
    
    // String should be subtype of Any through superclass
    assert(stringClass.isSubtypeOf(AnyType))
    assert(!AnyType.isSubtypeOf(stringClass))
  }
  
  test("trait type creation") {
    val comparable = TraitType(
      name = "Comparable",
      typeArguments = List(IntType),
      superTraits = List.empty,
      members = Map("compare" -> FunctionType(List(IntType), IntType))
    )
    
    assertEquals(comparable.name, "Comparable")
    assertEquals(comparable.typeArguments, List(IntType))
    assert(comparable.members.contains("compare"))
  }
  
  test("object type creation") {
    val singleton = ObjectType(
      name = "MySingleton",
      superClass = Some(AnyType),
      traits = List.empty,
      members = Map("value" -> IntType)
    )
    
    assertEquals(singleton.name, "MySingleton")
    assertEquals(singleton.superClass, Some(AnyType))
    assert(singleton.members.contains("value"))
  }
  
  test("type variable creation") {
    val typeVar1 = TypeVariable("T", 1)
    val typeVar2 = TypeVariable("U", 2)
    val typeVar3 = TypeVariable("T", 3)
    
    assertEquals(typeVar1.name, "T")
    assertEquals(typeVar1.id, 1)
    assertEquals(typeVar2.name, "U")
    assertEquals(typeVar2.id, 2)
    
    // Different IDs make them different even with same name
    assert(!typeVar1.equals(typeVar3))
    assert(!typeVar1.equals(typeVar2))
  }
  
  test("type variable subtyping with constraints") {
    implicit val ctx: TypeContext = TypeContext()
    
    val typeVar = TypeVariable("T", 1)
    
    // Without constraint, only subtype of Any
    assert(typeVar.isSubtypeOf(AnyType))
    assert(!typeVar.isSubtypeOf(IntType))
    
    // With constraint
    val ctxWithConstraint = ctx.withConstraint(typeVar, IntType)
    assert(typeVar.isSubtypeOf(IntType)(ctxWithConstraint))
    assert(typeVar.isSubtypeOf(AnyType)(ctxWithConstraint))
  }
  
  test("type context operations") {
    val ctx = TypeContext()
    
    // Add type binding
    val ctx1 = ctx.withType("MyType", IntType)
    assertEquals(ctx1.getType("MyType"), Some(IntType))
    assertEquals(ctx1.getType("Unknown"), None)
    
    // Add type variable constraint
    val typeVar = TypeVariable("T", 1)
    val ctx2 = ctx1.withConstraint(typeVar, StringType)
    assertEquals(ctx2.getConstraint(typeVar), Some(StringType))
    
    // Add generic type definition
    val genericDef = GenericTypeDefinition("Box", List.empty, AnyType)
    val ctx3 = ctx2.withTypeDefinition("Box", genericDef)
    assertEquals(ctx3.getTypeDefinition("Box"), Some(genericDef))
  }
  
  test("type parameter variance") {
    val covariantParam = TypeParameter("T", Covariant)
    val contravariantParam = TypeParameter("U", Contravariant) 
    val invariantParam = TypeParameter("V", Invariant)
    
    assertEquals(covariantParam.variance, Covariant)
    assertEquals(contravariantParam.variance, Contravariant)
    assertEquals(invariantParam.variance, Invariant)
  }
  
  test("type parameter bounds checking") {
    implicit val ctx: TypeContext = TypeContext()
    
    val boundedParam = TypeParameter(
      name = "T",
      variance = Invariant,
      upperBound = Some(AnyType),
      lowerBound = Some(IntType)
    )
    
    // IntType satisfies both bounds
    assert(boundedParam.isWithinBounds(IntType))
    
    // StringType doesn't satisfy lower bound
    assert(!boundedParam.isWithinBounds(StringType))
    
    // No bounds = always valid
    val unboundedParam = TypeParameter("U", Invariant)
    assert(unboundedParam.isWithinBounds(IntType))
    assert(unboundedParam.isWithinBounds(StringType))
  }
}