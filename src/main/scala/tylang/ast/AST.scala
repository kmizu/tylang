package tylang.ast

import tylang.SourceLocation
import tylang.types.Type

sealed trait ASTNode {
  def location: SourceLocation
}

// Base types
sealed trait Expression extends ASTNode {
  var inferredType: Option[Type] = None
}

sealed trait Statement extends ASTNode

sealed trait Declaration extends ASTNode

sealed trait TypeAnnotation extends ASTNode

// Expressions
case class IntLiteral(value: Int, location: SourceLocation) extends Expression

case class DoubleLiteral(value: Double, location: SourceLocation) extends Expression

case class StringLiteral(value: String, location: SourceLocation) extends Expression

case class BooleanLiteral(value: Boolean, location: SourceLocation) extends Expression

case class Identifier(name: String, location: SourceLocation) extends Expression

case class ThisExpression(location: SourceLocation) extends Expression

case class BinaryOp(
  left: Expression,
  operator: String,
  right: Expression,
  location: SourceLocation
) extends Expression

case class UnaryOp(
  operator: String,
  operand: Expression,
  location: SourceLocation
) extends Expression

case class MethodCall(
  receiver: Option[Expression],
  methodName: String,
  arguments: List[Expression],
  typeArguments: List[TypeAnnotation] = List.empty,
  location: SourceLocation
) extends Expression

case class FieldAccess(
  receiver: Expression,
  fieldName: String,
  location: SourceLocation
) extends Expression

case class Assignment(
  target: Expression,
  value: Expression,
  location: SourceLocation
) extends Expression

case class Block(
  statements: List[Statement],
  location: SourceLocation
) extends Expression

case class IfExpression(
  condition: Expression,
  thenBranch: Expression,
  elseBranch: Option[Expression],
  location: SourceLocation
) extends Expression

case class WhileExpression(
  condition: Expression,
  body: Expression,
  location: SourceLocation
) extends Expression

case class ListLiteral(
  elements: List[Expression],
  location: SourceLocation
) extends Expression

case class MapLiteral(
  pairs: List[(Expression, Expression)],
  location: SourceLocation
) extends Expression

case class Lambda(
  parameters: List[Parameter],
  body: Expression,
  location: SourceLocation
) extends Expression

case class MatchExpression(
  expr: Expression,
  cases: List[MatchCase],
  location: SourceLocation
) extends Expression

// Statements
case class ExpressionStatement(
  expression: Expression,
  location: SourceLocation
) extends Statement

case class VariableDeclaration(
  name: String,
  typeAnnotation: Option[TypeAnnotation],
  initializer: Option[Expression],
  isMutable: Boolean,
  location: SourceLocation
) extends Statement

case class Return(
  value: Option[Expression],
  location: SourceLocation
) extends Statement

// Declarations
case class FunctionDeclaration(
  name: String,
  typeParameters: List[TypeParameter],
  parameters: List[Parameter],
  returnType: Option[TypeAnnotation],
  body: Expression,
  location: SourceLocation
) extends Declaration

case class ClassDeclaration(
  name: String,
  typeParameters: List[TypeParameter],
  superClass: Option[TypeAnnotation],
  traits: List[TypeAnnotation],
  constructor: Option[Constructor],
  members: List[ClassMember],
  location: SourceLocation
) extends Declaration

case class TraitDeclaration(
  name: String,
  typeParameters: List[TypeParameter],
  superTraits: List[TypeAnnotation],
  members: List[TraitMember],
  location: SourceLocation
) extends Declaration

case class ObjectDeclaration(
  name: String,
  superClass: Option[TypeAnnotation],
  traits: List[TypeAnnotation],
  members: List[ClassMember],
  location: SourceLocation
) extends Declaration

case class ExtensionDeclaration(
  targetType: TypeAnnotation,
  methods: List[FunctionDeclaration],
  location: SourceLocation
) extends Declaration

// Supporting types
case class Parameter(
  name: String,
  typeAnnotation: Option[TypeAnnotation],
  defaultValue: Option[Expression],
  location: SourceLocation
) extends ASTNode

case class TypeParameter(
  name: String,
  variance: Variance,
  upperBound: Option[TypeAnnotation],
  lowerBound: Option[TypeAnnotation],
  location: SourceLocation
) extends ASTNode

case class Constructor(
  parameters: List[Parameter],
  body: Option[Expression],
  location: SourceLocation
) extends ASTNode

sealed trait ClassMember extends ASTNode
case class MethodMember(method: FunctionDeclaration) extends ClassMember {
  def location: SourceLocation = method.location
}
case class FieldMember(field: VariableDeclaration) extends ClassMember {
  def location: SourceLocation = field.location
}

sealed trait TraitMember extends ASTNode
case class AbstractMethodMember(
  name: String,
  typeParameters: List[TypeParameter],
  parameters: List[Parameter],
  returnType: Option[TypeAnnotation],
  location: SourceLocation
) extends TraitMember
case class ConcreteMethodMember(method: FunctionDeclaration) extends TraitMember {
  def location: SourceLocation = method.location
}

case class MatchCase(
  pattern: Pattern,
  guard: Option[Expression],
  body: Expression,
  location: SourceLocation
) extends ASTNode

sealed trait Pattern extends ASTNode
case class IdentifierPattern(name: String, location: SourceLocation) extends Pattern
case class LiteralPattern(literal: Expression, location: SourceLocation) extends Pattern
case class WildcardPattern(location: SourceLocation) extends Pattern
case class TypePattern(typeAnnotation: TypeAnnotation, location: SourceLocation) extends Pattern

// Type annotations
case class SimpleType(name: String, location: SourceLocation) extends TypeAnnotation

case class GenericType(
  name: String,
  typeArguments: List[TypeAnnotation],
  location: SourceLocation
) extends TypeAnnotation

case class FunctionType(
  parameterTypes: List[TypeAnnotation],
  returnType: TypeAnnotation,
  location: SourceLocation
) extends TypeAnnotation

case class StructuralType(
  members: List[StructuralMember],
  location: SourceLocation
) extends TypeAnnotation

case class StructuralMember(
  name: String,
  typeAnnotation: TypeAnnotation,
  location: SourceLocation
) extends ASTNode

// Variance
sealed trait Variance
case object Invariant extends Variance
case object Covariant extends Variance
case object Contravariant extends Variance

// Program
case class Program(
  declarations: List[Declaration],
  location: SourceLocation
) extends ASTNode