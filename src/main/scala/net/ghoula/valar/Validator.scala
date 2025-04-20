package net.ghoula.valar

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.given
import net.ghoula.valar.internal.MacroHelpers

import scala.collection.immutable.ArraySeq
import scala.compiletime.{constValueTuple, summonInline}
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag

/** A typeclass for defining custom validation logic for type `A`.
  *
  * Validators encapsulate the validation rules for a specific type and produce structured
  * `ValidationResult`s.
  *
  * @tparam A
  *   the type to be validated
  */
trait Validator[A] {

  /** Validate an instance of type `A`.
    *
    * @param a
    *   the instance to validate
    * @return
    *   ValidationResult[A] representing validation success or accumulated errors
    */
  def validate(a: A): ValidationResult[A]
}

/** Companion object for the [[Validator]] typeclass. */
object Validator {

  /** Summons an implicit [[Validator]] instance for type `A`. */
  def apply[A](using v: Validator[A]): Validator[A] = v

  given positiveIntValidator: Validator[Int] with {
    def validate(i: Int): ValidationResult[Int] = positiveInt(i)
  }
  given finiteFloatValidator: Validator[Float] with {
    def validate(f: Float): ValidationResult[Float] = finiteFloat(f)
  }
  given finiteDoubleValidator: Validator[Double] with {
    def validate(d: Double): ValidationResult[Double] = finiteDouble(d)
  }
  given nonEmptyStringValidator: Validator[String] with {
    def validate(s: String): ValidationResult[String] = nonEmpty(s)
  }
  given default[A]: Validator[A] with {
    def validate(a: A): ValidationResult[A] = ValidationResult.Valid(a)
  }
  given listValidator[A](using v: Validator[A]): Validator[List[A]] with {
    def validate(xs: List[A]): ValidationResult[List[A]] = {
      val results = xs.map(v.validate)
      val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], List.empty[A])) {
        case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
        case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
      }
      if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
    }
  }
  given seqValidator[A](using v: Validator[A]): Validator[Seq[A]] with {
    def validate(xs: Seq[A]): ValidationResult[Seq[A]] = {
      val results = xs.map(v.validate)
      val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Seq.empty[A])) {
        case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
        case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
      }
      if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
    }
  }
  given vectorValidator[A](using v: Validator[A]): Validator[Vector[A]] with {
    def validate(xs: Vector[A]): ValidationResult[Vector[A]] = {
      val results = xs.map(v.validate)
      val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Vector.empty[A])) {
        case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
        case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
      }
      if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
    }
  }
  given setValidator[A](using v: Validator[A]): Validator[Set[A]] with {
    def validate(xs: Set[A]): ValidationResult[Set[A]] = {
      val results = xs.map(v.validate)
      val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Set.empty[A])) {
        case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals + a)
        case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
      }
      if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
    }
  }
  given mapValidator[K, V](using vk: Validator[K], vv: Validator[V]): Validator[Map[K, V]] with {
    def validate(m: Map[K, V]): ValidationResult[Map[K, V]] = {
      val results = m.map { case (k, v) =>
        val validatedKey: ValidationResult[K] = vk.validate(k) match {
          case ValidationResult.Valid(kk) => ValidationResult.Valid(kk)
          case ValidationResult.Invalid(es) =>
            ValidationResult.Invalid(
              es.map(e => e.annotateField("key", k.getClass.getSimpleName))
            )
        }
        val validatedValue: ValidationResult[V] = vv.validate(v) match {
          case ValidationResult.Valid(vv) => ValidationResult.Valid(vv)
          case ValidationResult.Invalid(es) =>
            ValidationResult.Invalid(
              es.map(e => e.annotateField("value", v.getClass.getSimpleName))
            )
        }
        validatedKey.zip(validatedValue)
      }
      val (errors, validPairs) = results.foldLeft((Vector.empty[ValidationError], Map.empty[K, V])) {
        case ((errs, acc), ValidationResult.Valid(pair)) => (errs, acc + pair)
        case ((errs, acc), ValidationResult.Invalid(e2)) => (errs ++ e2, acc)
      }
      if errors.isEmpty then ValidationResult.Valid(validPairs) else ValidationResult.Invalid(errors)
    }
  }
  given arrayValidator[A](using v: Validator[A], ct: ClassTag[A]): Validator[Array[A]] with {
    def validate(xs: Array[A]): ValidationResult[Array[A]] = { /* ... unchanged, optimized version ... */
      val resultsIterator: Iterator[ValidationResult[A]] = xs.iterator.map(v.validate)
      val initial = (Vector.empty[ValidationError], Vector.empty[A])
      val (errors, validValues) = resultsIterator.foldLeft(initial) {
        case ((currentErrors, currentValidValues), result) =>
          result match {
            case ValidationResult.Valid(a) => (currentErrors, currentValidValues :+ a)
            case ValidationResult.Invalid(e2) => (currentErrors ++ e2, currentValidValues)
          }
      }
      if (errors.isEmpty) ValidationResult.Valid(validValues.toArray)
      else ValidationResult.Invalid(errors)
    }
  }
  given arraySeqValidator[A](using v: Validator[A], ct: ClassTag[A]): Validator[ArraySeq[A]] with {
    def validate(xs: ArraySeq[A]): ValidationResult[ArraySeq[A]] = {
      val results = xs.map(v.validate)
      val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Vector.empty[A])) {
        case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
        case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
      }
      if errors.isEmpty then ValidationResult.Valid(ArraySeq.unsafeWrapArray(validValues.toArray))
      else ValidationResult.Invalid(errors)
    }
  }
  given intersectionValidator[A, B](using va: Validator[A], vb: Validator[B]): Validator[A & B] with {
    def validate(ab: A & B): ValidationResult[A & B] =
      va.validate(ab).zip(vb.validate(ab)).map(_ => ab)
  }
  given unionValidator[A, B](using va: Validator[A], vb: Validator[B], ctA: ClassTag[A], ctB: ClassTag[B]): Validator[
    A | B
  ] with {
    def validate(value: A | B): ValidationResult[A | B] = Validation.validateUnion[A, B](value)(using va, vb, ctA, ctB)
  }

  /** Automatically derives a `Validator` for case classes using Scala 3 macros.
    *
    * Derivation is recursive, validating each field using implicitly available validators. Errors
    * from nested fields are aggregated and annotated with clear field context.
    *
    * Example usage:
    * {{{
    *   case class User(name: String, age: Int)
    *   given Validator[User] = deriveValidatorMacro
    * }}}
    *
    * @tparam T
    *   case class type to derive validator for
    * @param m
    *   implicit Scala 3 Mirror for reflection
    * @return
    *   Validator[T] automatically derived validator instance
    */
  inline def deriveValidatorMacro[T <: Product](using m: Mirror.ProductOf[T]): Validator[T] =
    ${ deriveValidatorMacroImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  private def deriveValidatorMacroImpl[T <: Product: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[Validator[T]] = {
    import q.reflect.*
    if !(TypeRepr.of[Elems] <:< TypeRepr.of[Tuple]) then
      report.errorAndAbort(s"deriveValidatorMacro: Expected Elems to be a Tuple type, but got ${Type.show[Elems]}")
    '{
      new Validator[T] {
        def validate(a: T): ValidationResult[T] = {
          val elems: Elems = MacroHelpers.upcastTo[Elems](Tuple.fromProduct(a))
          val labels: Labels = constValueTuple[Labels]
          val validatedElemsResult: ValidationResult[Elems] = ${
            validateTupleWithLabelsMacro[Elems, Labels]('{ elems }, '{ labels })
          }
          validatedElemsResult.map { validatedElems => $m.fromProduct(validatedElems) }
        }
      }
    }
  }

  private def validateTupleWithLabelsMacro[Elems <: Tuple: Type, Labels <: Tuple: Type](
    values: Expr[Elems],
    labels: Expr[Labels]
  )(using q: Quotes): Expr[ValidationResult[Elems]] = {
    import q.reflect.*
    (TypeRepr.of[Elems].dealias, TypeRepr.of[Labels].dealias) match {
      case (elemsType, _) if elemsType <:< TypeRepr.of[EmptyTuple] =>
        '{ ValidationResult.Valid(MacroHelpers.upcastTo[Elems](EmptyTuple)) }
      case (elemsRepr, labelsRepr) =>
        elemsRepr.asType match {
          case '[h *: tElems] =>
            labelsRepr.asType match {
              case '[String *: tLabels] =>
                val headTypeRepr: TypeRepr = TypeRepr.of[h]
                val typeSymbol: Symbol = headTypeRepr.typeSymbol
                val fieldTypeNameString: String = typeSymbol.name
                val fieldTypeNameExpr: Expr[String] = Expr(fieldTypeNameString)
                val isOption = TypeRepr.of[h] <:< TypeRepr.of[Option[Any]]
                val isOptionExpr: Expr[Boolean] = Expr(isOption)
                '{
                  val rawHead = $values.head
                  if (Option(MacroHelpers.upcastTo[Any](rawHead)).isEmpty && !${ isOptionExpr }) {
                    val headLabelNull: String = Option(MacroHelpers.upcastTo[String]($labels.head)).getOrElse("unknown")
                    val nullError = ValidationErrors.ValidationError(
                      message = s"Field '$headLabelNull' must not be null.",
                      fieldPath = List(headLabelNull),
                      expected = Some("non-null value"),
                      actual = Some("null")
                    )
                    ValidationResult.invalid(nullError)
                  } else {
                    val head: h = MacroHelpers.castValue[h](rawHead)
                    val tail: tElems = MacroHelpers.upcastTo[tElems]($values.tail)
                    val headLabel: String = Option(MacroHelpers.upcastTo[String]($labels.head)).getOrElse("unknown")
                    val tailLabels: tLabels = Option(MacroHelpers.upcastTo[tLabels]($labels.tail))
                      .getOrElse(MacroHelpers.upcastTo[tLabels](EmptyTuple))
                    val fieldTypeNameValue: String = ${ fieldTypeNameExpr }
                    val headValidation: ValidationResult[h] =
                      summonInline[Validator[h]].validate(head) match {
                        case ValidationResult.Valid(v) => ValidationResult.Valid(v)
                        case ValidationResult.Invalid(errs) =>
                          ValidationResult.Invalid(
                            errs.map(e => e.annotateField(headLabel, fieldTypeNameValue))
                          )
                      }
                    val tailValidation: ValidationResult[tElems] = ${
                      validateTupleWithLabelsMacro[tElems, tLabels]('{ tail }, '{ tailLabels })
                    }
                    headValidation.zip(tailValidation).map { case (hValidated, tValidated) =>
                      MacroHelpers.upcastTo[Elems](hValidated *: tValidated)
                    }
                  }
                }
              case _ => report.errorAndAbort("Labels tuple...")
            }
          case _ => report.errorAndAbort("Unsupported elements tuple type...")
        }
    }
  }
}
