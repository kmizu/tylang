package tylang.types

import munit.FunSuite

class StructuralSubtypingTest extends FunSuite {
  
  implicit val ctx: TypeContext = TypeContext()
  
  test("structural type with single method") {
    val printable = StructuralType(Map(
      "print" -> FunctionType(List(), UnitType)
    ))
    
    // Test that a type with the exact same method is a subtype
    val samePrintable = StructuralType(Map(
      "print" -> FunctionType(List(), UnitType)
    ))
    
    assert(samePrintable.isSubtypeOf(printable))
    assert(printable.isSubtypeOf(samePrintable))
  }
  
  test("structural type width subtyping") {
    val minimal = StructuralType(Map(
      "toString" -> FunctionType(List(), StringType)
    ))
    
    val extended = StructuralType(Map(
      "toString" -> FunctionType(List(), StringType),
      "hashCode" -> FunctionType(List(), IntType),
      "equals" -> FunctionType(List(AnyType), BooleanType)
    ))
    
    // A type with more methods is a subtype of a type with fewer methods
    assert(extended.isSubtypeOf(minimal))
    assert(!minimal.isSubtypeOf(extended))
  }
  
  test("structural type depth subtyping - return type covariance") {
    val returnsAny = StructuralType(Map(
      "getValue" -> FunctionType(List(), AnyType)
    ))
    
    val returnsString = StructuralType(Map(
      "getValue" -> FunctionType(List(), StringType)
    ))
    
    // String <: Any, so method returning String is subtype of method returning Any
    assert(returnsString.isSubtypeOf(returnsAny))
    assert(!returnsAny.isSubtypeOf(returnsString))
  }
  
  test("structural type depth subtyping - parameter contravariance") {
    val takesString = StructuralType(Map(
      "process" -> FunctionType(List(StringType), UnitType)
    ))
    
    val takesAny = StructuralType(Map(
      "process" -> FunctionType(List(AnyType), UnitType)
    ))
    
    // Any :> String, so method taking Any is subtype of method taking String
    assert(takesAny.isSubtypeOf(takesString))
    assert(!takesString.isSubtypeOf(takesAny))
  }
  
  test("structural type with multiple parameters") {
    val twoParams = StructuralType(Map(
      "add" -> FunctionType(List(IntType, IntType), IntType)
    ))
    
    val differentParams = StructuralType(Map(
      "add" -> FunctionType(List(IntType, StringType), IntType)
    ))
    
    // Different parameter types mean no subtype relation
    assert(!twoParams.isSubtypeOf(differentParams))
    assert(!differentParams.isSubtypeOf(twoParams))
  }
  
  test("structural type with generic function") {
    // Create a structural type with a generic method signature
    val comparableStruct = StructuralType(Map(
      "compareTo" -> FunctionType(List(AnyType), IntType)
    ))
    
    val intComparable = StructuralType(Map(
      "compareTo" -> FunctionType(List(IntType), IntType)
    ))
    
    // The Any version is more general (contravariant in parameter)
    assert(comparableStruct.isSubtypeOf(intComparable))
    assert(!intComparable.isSubtypeOf(comparableStruct))
  }
  
  test("empty structural type") {
    val empty = StructuralType(Map.empty)
    val nonEmpty = StructuralType(Map(
      "method" -> FunctionType(List(), UnitType)
    ))
    
    // Any type is a subtype of the empty structural type
    assert(nonEmpty.isSubtypeOf(empty))
    assert(!empty.isSubtypeOf(nonEmpty))
    
    // Empty structural type is subtype of itself
    assert(empty.isSubtypeOf(empty))
  }
  
  test("structural type with overloaded methods not supported") {
    // TyLang doesn't support method overloading, so this is just documentation
    val type1 = StructuralType(Map(
      "process" -> FunctionType(List(IntType), UnitType)
    ))
    
    val type2 = StructuralType(Map(
      "process" -> FunctionType(List(StringType), UnitType)
    ))
    
    // These are incompatible types
    assert(!type1.isSubtypeOf(type2))
    assert(!type2.isSubtypeOf(type1))
  }
  
  test("complex structural type hierarchy") {
    val drawable = StructuralType(Map(
      "draw" -> FunctionType(List(), UnitType)
    ))
    
    val movable = StructuralType(Map(
      "move" -> FunctionType(List(IntType, IntType), UnitType)
    ))
    
    val shape = StructuralType(Map(
      "draw" -> FunctionType(List(), UnitType),
      "move" -> FunctionType(List(IntType, IntType), UnitType),
      "area" -> FunctionType(List(), DoubleType)
    ))
    
    // Shape implements both drawable and movable
    assert(shape.isSubtypeOf(drawable))
    assert(shape.isSubtypeOf(movable))
    
    // But drawable and movable are not related
    assert(!drawable.isSubtypeOf(movable))
    assert(!movable.isSubtypeOf(drawable))
  }
  
  test("structural type vs class type compatibility") {
    val toStringStruct = StructuralType(Map(
      "toString" -> FunctionType(List(), StringType)
    ))
    
    val stringClass = ClassType(
      name = "String",
      typeArguments = List(),
      superClass = Some(AnyType),
      traits = List(),
      members = Map(
        "toString" -> FunctionType(List(), StringType),
        "length" -> FunctionType(List(), IntType)
      )
    )
    
    // Class with matching methods is subtype of structural type
    assert(stringClass.isSubtypeOf(toStringStruct))
    
    // But structural type is not subtype of class
    assert(!toStringStruct.isSubtypeOf(stringClass))
  }
}