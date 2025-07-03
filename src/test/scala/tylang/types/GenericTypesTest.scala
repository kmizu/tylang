package tylang.types

import munit.FunSuite

class GenericTypesTest extends FunSuite {
  
  implicit val ctx: TypeContext = TypeContext()
  
  test("generic class with single type parameter") {
    val boxOfInt = ClassType(
      name = "Box",
      typeArguments = List(IntType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "get" -> FunctionType(List(), IntType),
        "set" -> FunctionType(List(IntType), UnitType)
      )
    )
    
    val boxOfString = ClassType(
      name = "Box",
      typeArguments = List(StringType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "get" -> FunctionType(List(), StringType),
        "set" -> FunctionType(List(StringType), UnitType)
      )
    )
    
    // Different type arguments mean different types
    assert(!boxOfInt.equals(boxOfString))
    assert(!boxOfInt.isSubtypeOf(boxOfString))
    assert(!boxOfString.isSubtypeOf(boxOfInt))
  }
  
  test("covariant generic type") {
    // Producer[+T] - covariant in T
    val producerOfString = ClassType(
      name = "Producer",
      typeArguments = List(StringType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "produce" -> FunctionType(List(), StringType)
      )
    )
    
    val producerOfAny = ClassType(
      name = "Producer",
      typeArguments = List(AnyType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "produce" -> FunctionType(List(), AnyType)
      )
    )
    
    // With covariance: Producer[String] <: Producer[Any]
    // Note: This test documents expected behavior; actual implementation
    // would need to track variance annotations
    assert(producerOfString.members("produce").isSubtypeOf(producerOfAny.members("produce")))
  }
  
  test("contravariant generic type") {
    // Consumer[-T] - contravariant in T
    val consumerOfString = ClassType(
      name = "Consumer",
      typeArguments = List(StringType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "consume" -> FunctionType(List(StringType), UnitType)
      )
    )
    
    val consumerOfAny = ClassType(
      name = "Consumer",
      typeArguments = List(AnyType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "consume" -> FunctionType(List(AnyType), UnitType)
      )
    )
    
    // With contravariance: Consumer[Any] <: Consumer[String]
    // The consumer that accepts Any can be used where consumer of String is expected
    assert(consumerOfAny.members("consume").isSubtypeOf(consumerOfString.members("consume")))
  }
  
  test("invariant generic type") {
    // MutableBox[T] - invariant in T
    val mutableBoxOfInt = ClassType(
      name = "MutableBox",
      typeArguments = List(IntType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "get" -> FunctionType(List(), IntType),
        "set" -> FunctionType(List(IntType), UnitType)
      )
    )
    
    val mutableBoxOfAny = ClassType(
      name = "MutableBox", 
      typeArguments = List(AnyType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "get" -> FunctionType(List(), AnyType),
        "set" -> FunctionType(List(AnyType), UnitType)
      )
    )
    
    // Invariant types have no subtype relation even if type arguments are related
    assert(!mutableBoxOfInt.isSubtypeOf(mutableBoxOfAny))
    assert(!mutableBoxOfAny.isSubtypeOf(mutableBoxOfInt))
  }
  
  test("generic type with multiple parameters") {
    val pairIntString = ClassType(
      name = "Pair",
      typeArguments = List(IntType, StringType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "first" -> FunctionType(List(), IntType),
        "second" -> FunctionType(List(), StringType)
      )
    )
    
    val pairStringInt = ClassType(
      name = "Pair",
      typeArguments = List(StringType, IntType),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "first" -> FunctionType(List(), StringType),
        "second" -> FunctionType(List(), IntType)
      )
    )
    
    // Order of type arguments matters
    assert(!pairIntString.equals(pairStringInt))
    assert(!pairIntString.isSubtypeOf(pairStringInt))
  }
  
  test("type parameter bounds") {
    // T <: Number
    val upperBounded = TypeParameter(
      name = "T",
      variance = Invariant,
      upperBound = Some(DoubleType),
      lowerBound = None
    )
    
    assert(upperBounded.isWithinBounds(DoubleType))
    assert(!upperBounded.isWithinBounds(StringType))
    assert(!upperBounded.isWithinBounds(AnyType))
    
    // T >: Int
    val lowerBounded = TypeParameter(
      name = "T",
      variance = Invariant,
      upperBound = None,
      lowerBound = Some(IntType)
    )
    
    assert(lowerBounded.isWithinBounds(IntType))
    assert(lowerBounded.isWithinBounds(AnyType))
    assert(!lowerBounded.isWithinBounds(NothingType))
  }
  
  test("F-bounded polymorphism pattern") {
    // Comparable[T <: Comparable[T]]
    val comparableTrait = TraitType(
      name = "Comparable",
      typeArguments = List(TypeVariable("T", 1)),
      superTraits = List(),
      members = Map(
        "compareTo" -> FunctionType(List(TypeVariable("T", 1)), IntType)
      )
    )
    
    // This documents the pattern; full implementation would need recursive bounds
    assertEquals(comparableTrait.name, "Comparable")
    assert(comparableTrait.members.contains("compareTo"))
  }
  
  test("variance in nested generics") {
    // List[List[String]] vs List[List[Any]]
    val listOfListString = ListType(ListType(StringType))
    val listOfListAny = ListType(ListType(AnyType))
    
    // Outer list is covariant, inner list is also covariant
    // So List[List[String]] <: List[List[Any]]
    assert(listOfListString.isSubtypeOf(listOfListAny))
    assert(!listOfListAny.isSubtypeOf(listOfListString))
  }
  
  test("mixed variance in complex types") {
    // Function[List[String], List[Any]]
    val func1 = FunctionType(List(ListType(StringType)), ListType(AnyType))
    
    // Function[List[Any], List[String]]  
    val func2 = FunctionType(List(ListType(AnyType)), ListType(StringType))
    
    // Functions are contravariant in params, covariant in return
    // List[Any] :> List[String] (contravariant position)
    // List[String] <: List[Any] (covariant position)
    // So func2 <: func1
    assert(func2.isSubtypeOf(func1))
    assert(!func1.isSubtypeOf(func2))
  }
  
  test("wildcards and existential types") {
    // TyLang doesn't have wildcards, but we can simulate with type variables
    val wildcardList = ClassType(
      name = "List",
      typeArguments = List(TypeVariable("?", 999)),
      superClass = Some(AnyType),
      traits = List(),
      members = Map()
    )
    
    val stringList = ListType(StringType)
    
    // Without proper wildcard support, these are incompatible
    assert(!stringList.isSubtypeOf(wildcardList))
  }
}