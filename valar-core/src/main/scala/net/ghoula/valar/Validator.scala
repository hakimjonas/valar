package net.ghoula.valar

import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.ArraySeq
import scala.deriving.Mirror
import scala.language.reflectiveCalls
import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.{validateUnion, given}
import net.ghoula.valar.internal.MacroHelper

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

  /** Validates that an Int is non-negative (>= 0). Uses [[ValidationHelpers.nonNegativeInt]]. */
  given nonNegativeIntValidator: Validator[Int] with {
    def validate(i: Int): ValidationResult[Int] = nonNegativeInt(i)
  }

  /** Validates that a Float is finite (not NaN or infinite). Uses
    * [[ValidationHelpers.finiteFloat]].
    */
  given finiteFloatValidator: Validator[Float] with {
    def validate(f: Float): ValidationResult[Float] = finiteFloat(f)
  }

  /** Validates that a Double is finite (not NaN or infinite). Uses
    * [[ValidationHelpers.finiteDouble]].
    */
  given finiteDoubleValidator: Validator[Double] with {
    def validate(d: Double): ValidationResult[Double] = finiteDouble(d)
  }

  /** Validates that a String is non-empty. Uses [[ValidationHelpers.nonEmpty]]. */
  given nonEmptyStringValidator: Validator[String] with {
    def validate(s: String): ValidationResult[String] = nonEmpty(s)
  }

  /** Default validator for `Option[A]`. If the option is `Some(a)`, it validates the inner `a`
    * using the implicit `Validator[A]`. If the option is `None`, it is considered `Valid`.
    * Accumulates errors from the inner validation if `Some`.
    *
    * @tparam A
    *   the inner type of the Option.
    * @param v
    *   the implicit validator for the inner type `A`.
    * @return
    *   A `Validator[Option[A]]`.
    */
  given optionValidator[A](using v: Validator[A]): Validator[Option[A]] with {
    def validate(opt: Option[A]): ValidationResult[Option[A]] =
      optional(opt)(using v)
  }

  /** Validates a `List[A]` by validating each element using the implicit `Validator[A]`.
    * Accumulates all errors found in invalid elements.
    * @tparam A
    *   the element type.
    * @param v
    *   the implicit validator for the element type `A`.
    * @return
    *   A `Validator[List[A]]`.
    */
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

  /** Validates a `Seq[A]` by validating each element using the implicit `Validator[A]`. Accumulates
    * all errors found in invalid elements.
    *
    * @tparam A
    *   the element type.
    * @param v
    *   the implicit validator for the element type `A`.
    * @return
    *   A `Validator[Seq[A]]`.
    */
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

  /** Validates a `Vector[A]` by validating each element using the implicit `Validator[A]`.
    * Accumulates all errors found in invalid elements.
    *
    * @tparam A
    *   the element type.
    * @param v
    *   the implicit validator for the element type `A`.
    * @return
    *   A `Validator[Vector[A]]`.
    */
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

  /** Validates a `Set[A]` by validating each element using the implicit `Validator[A]`. Accumulates
    * all errors found in invalid elements.
    *
    * @note
    *   The order of accumulated errors from a Set is not guaranteed due to its unordered nature.
    * @tparam A
    *   the element type.
    * @param v
    *   the implicit validator for the element type `A`.
    * @return
    *   A `Validator[Set[A]]`.
    */
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

  /** Validates a `Map[K, V]` by validating each key with `Validator[K]` and each value with
    * `Validator[V]`. Accumulates all errors from invalid keys and values. Errors are annotated with
    * context indicating whether they originated from a 'key' or a 'value'.
    *
    * @tparam K
    *   the key type.
    * @tparam V
    *   the value type.
    * @param vk
    *   the implicit validator for the key type `K`.
    * @param vv
    *   the implicit validator for the value type `V`.
    * @return
    *   A `Validator[Map[K, V]]`.
    */
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

  /** Helper method for validating iterable collections and building results.
    *
    * This method provides a generic implementation for validating any iterable collection of
    * elements. It applies the provided validator to each element, accumulates any validation
    * errors, and constructs a new collection of the same type containing only valid elements if all
    * validations succeed.
    *
    * @tparam A
    *   the element type to be validated
    * @tparam C
    *   the collection type constructor (e.g., Array, Vector)
    * @param xs
    *   the iterable collection of elements to validate
    * @param builder
    *   a function that constructs a collection of type C from a Vector of valid elements
    * @param v
    *   the implicit validator for type A
    * @return
    *   a ValidationResult containing either the valid collection or accumulated errors
    */
  private def validateIterable[A, C[_]](
    xs: Iterable[A],
    builder: Vector[A] => C[A]
  )(using v: Validator[A]): ValidationResult[C[A]] = {
    val resultsIterator = xs.iterator.map(v.validate)
    val initial = (Vector.empty[ValidationError], Vector.empty[A])
    val (errors, validValues) = resultsIterator.foldLeft(initial) {
      case ((currentErrors, currentValidValues), result) =>
        result match {
          case ValidationResult.Valid(a) => (currentErrors, currentValidValues :+ a)
          case ValidationResult.Invalid(e2) => (currentErrors ++ e2, currentValidValues)
        }
    }
    if (errors.isEmpty) ValidationResult.Valid(builder(validValues))
    else ValidationResult.Invalid(errors)
  }

  /** Validates an `Array[A]` by validating each element using the implicit `Validator[A]`.
    * Accumulates all errors found in invalid elements.
    *
    * @tparam A
    *   the element type.
    * @param v
    *   the implicit validator for the element type `A`.
    * @param ct
    *   implicit ClassTag required for creating the resulting Array.
    * @return
    *   A `Validator[Array[A]]`.
    */
  given arrayValidator[A](using v: Validator[A], ct: ClassTag[A]): Validator[Array[A]] with {
    def validate(xs: Array[A]): ValidationResult[Array[A]] =
      validateIterable(xs, (validValues: Vector[A]) => validValues.toArray)
  }

  /** Validates an `ArraySeq[A]` by validating each element using the implicit `Validator[A]`.
    * Accumulates all errors found in invalid elements.
    *
    * @tparam A
    *   the element type.
    * @param v
    *   the implicit validator for the element type `A`.
    * @param ct
    *   implicit ClassTag required for the underlying Array.
    * @return
    *   A `Validator[ArraySeq[A]]`.
    */
  given arraySeqValidator[A](using v: Validator[A], ct: ClassTag[A]): Validator[ArraySeq[A]] with {
    def validate(xs: ArraySeq[A]): ValidationResult[ArraySeq[A]] =
      validateIterable(xs, (validValues: Vector[A]) => ArraySeq.unsafeWrapArray(validValues.toArray))
  }

  /** Validates an intersection type `A & B` by applying both `Validator[A]` and `Validator[B]`. The
    * result is `Valid` only if *both* underlying validators succeed. If either or both fail, their
    * errors are accumulated using `zip`.
    *
    * @tparam A
    *   the first type in the intersection.
    * @tparam B
    *   the second type in the intersection.
    * @param va
    *   the implicit validator for type `A`.
    * @param vb
    *   the implicit validator for type `B`.
    * @return
    *   A `Validator[A & B]`.
    */
  given intersectionValidator[A, B](using va: Validator[A], vb: Validator[B]): Validator[A & B] with {
    def validate(ab: A & B): ValidationResult[A & B] =
      va.validate(ab).zip(vb.validate(ab)).map(_ => ab)
  }

  /** Validates a union type `A | B`. It attempts to validate the input value first as type `A` and
    * then as type `B`. The result is `Valid` if *either* validation succeeds (preferring the result
    * for `A` if both succeed). If both underlying validations fail, it returns an `Invalid` result
    * containing a summary error wrapping the errors from both attempts. Delegates to
    * [[ValidationResult.validateUnion]].
    *
    * @tparam A
    *   the first type in the union.
    * @tparam B
    *   the second type in the union.
    * @param va
    *   the implicit validator for type `A`.
    * @param vb
    *   the implicit validator for type `B`.
    * @param ctA
    *   implicit ClassTag required for runtime type checking for `A`.
    * @param ctB
    *   implicit ClassTag required for runtime type checking for `B`.
    * @return
    *   A `Validator[A | B]`.
    */
  given unionValidator[A, B](using va: Validator[A], vb: Validator[B], ctA: ClassTag[A], ctB: ClassTag[B]): Validator[
    A | B
  ] with {
    def validate(value: A | B): ValidationResult[A | B] = validateUnion[A, B](value)(using va, vb, ctA, ctB)
  }

  /** This section provides "pass-through" `given` instances that always return `Valid`. They are
    * marked as `inline` to allow the compiler to eliminate the validation overhead, making them
    * zero-cost abstractions when used by the `deriveValidatorMacro`.
    */

  /** Pass-through validator for Boolean values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given booleanValidator: Validator[Boolean] with {
    def validate(b: Boolean): ValidationResult[Boolean] = ValidationResult.Valid(b)
  }

  /** Pass-through validator for Byte values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given byteValidator: Validator[Byte] with {
    def validate(b: Byte): ValidationResult[Byte] = ValidationResult.Valid(b)
  }

  /** Pass-through validator for Short values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given shortValidator: Validator[Short] with {
    def validate(s: Short): ValidationResult[Short] = ValidationResult.Valid(s)
  }

  /** Pass-through validator for Long values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given longValidator: Validator[Long] with {
    def validate(l: Long): ValidationResult[Long] = ValidationResult.Valid(l)
  }

  /** Pass-through validator for Char values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given charValidator: Validator[Char] with {
    def validate(c: Char): ValidationResult[Char] = ValidationResult.Valid(c)
  }

  /** Pass-through validator for Unit values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given unitValidator: Validator[Unit] with {
    def validate(u: Unit): ValidationResult[Unit] = ValidationResult.Valid(u)
  }

  /** Pass-through validator for BigInt values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given bigIntValidator: Validator[BigInt] with {
    def validate(bi: BigInt): ValidationResult[BigInt] = ValidationResult.Valid(bi)
  }

  /** Pass-through validator for BigDecimal values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given bigDecimalValidator: Validator[BigDecimal] with {
    def validate(bd: BigDecimal): ValidationResult[BigDecimal] = ValidationResult.Valid(bd)
  }

  /** Pass-through validator for Symbol values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given symbolValidator: Validator[Symbol] with {
    def validate(s: Symbol): ValidationResult[Symbol] = ValidationResult.Valid(s)
  }

  /** Pass-through validator for UUID values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given uuidValidator: Validator[UUID] with {
    def validate(v: UUID): ValidationResult[UUID] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for Instant values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given instantValidator: Validator[Instant] with {
    def validate(v: Instant): ValidationResult[Instant] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for LocalDate values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given localDateValidator: Validator[LocalDate] with {
    def validate(v: LocalDate): ValidationResult[LocalDate] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for LocalTime values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given localTimeValidator: Validator[LocalTime] with {
    def validate(v: LocalTime): ValidationResult[LocalTime] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for LocalDateTime values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given localDateTimeValidator: Validator[LocalDateTime] with {
    def validate(v: LocalDateTime): ValidationResult[LocalDateTime] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for ZonedDateTime values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given zonedDateTimeValidator: Validator[ZonedDateTime] with {
    def validate(v: ZonedDateTime): ValidationResult[ZonedDateTime] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for Duration values.
    *
    * Always returns a valid result without additional validation. Marked as `inline` for compiler
    * optimization.
    */
  inline given durationValidator: Validator[Duration] with {
    def validate(v: Duration): ValidationResult[Duration] = ValidationResult.Valid(v)
  }

  /** Automatically derives a `Validator` for case classes using Scala 3 macros.
    *
    * Derivation is recursive, validating each field using implicitly available validators. Errors
    * from nested fields are aggregated and annotated with clear field context.
    *
    * @tparam T
    *   case class type to derive validator for
    * @param m
    *   implicit Scala 3 Mirror for reflection
    * @return
    *   Validator[T] automatically derived validator instance
    */
  inline def deriveValidatorMacro[T](using m: Mirror.ProductOf[T]): Validator[T] =
    ${ deriveValidatorMacroImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  /** Implementation of the `deriveValidatorMacro` method.
    *
    * This macro implementation generates a validator for a product type (case class) by:
    *   1. Summoning validators for each field.
    *   2. Extracting field names from the mirror.
    *   3. Determining which fields are Options - for null-safety handling.
    *   4. Generating code that validates each field and accumulates errors.
    *
    * @tparam T
    *   the product type (case class) for which to derive a validator
    * @tparam Elems
    *   tuple type representing the types of all fields in T
    * @tparam Labels
    *   tuple type representing the names of all fields in T
    * @param m
    *   expression containing the product mirror for type T
    * @param q
    *   the Quotes context for macro expansion
    * @return
    *   an expression that constructs a Validator[T]
    */
  private def deriveValidatorMacroImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[Validator[T]] = {
    import q.reflect.*

    val fieldValidators: List[Expr[Validator[Any]]] = summonValidators[Elems]
    val fieldLabels: List[String] = getLabels[Labels]
    val isOptionList: List[Boolean] = getIsOptionFlags[Elems]

    val validatorsExpr: Expr[Seq[Validator[Any]]] = Expr.ofSeq(fieldValidators)
    val fieldLabelsExpr: Expr[List[String]] = Expr(fieldLabels)
    val isOptionListExpr: Expr[List[Boolean]] = Expr(isOptionList)

    '{
      new Validator[T] {
        def validate(a: T): ValidationResult[T] = {
          a match {
            case product: Product =>
              val validators = ${ validatorsExpr }
              val labels = ${ fieldLabelsExpr }
              val isOptionFlags = ${ isOptionListExpr }

              val results = product.productIterator.zipWithIndex.map { case (fieldValue, i) =>
                val label = labels(i)
                val isOption = isOptionFlags(i)

                if (Option(fieldValue).isEmpty && !isOption) {
                  ValidationResult.invalid(
                    ValidationError(
                      message = s"Field '$label' must not be null.",
                      fieldPath = List(label),
                      expected = Some("non-null value"),
                      actual = Some("null")
                    )
                  )
                } else {
                  val validator = validators(i)
                  validator.validate(fieldValue) match {
                    case ValidationResult.Valid(v) => ValidationResult.Valid(v)
                    case ValidationResult.Invalid(errs) =>
                      val fieldTypeName = Option(fieldValue).map(_.getClass.getSimpleName).getOrElse("null")
                      ValidationResult.Invalid(errs.map(_.annotateField(label, fieldTypeName)))
                  }
                }
              }.toList

              val allErrors = results.collect { case ValidationResult.Invalid(e) => e }.flatten.toVector
              if (allErrors.isEmpty) {
                val validValues = results.collect { case ValidationResult.Valid(v) => v }
                ValidationResult.Valid($m.fromProduct(Tuple.fromArray(validValues.toArray)))
              } else {
                ValidationResult.Invalid(allErrors)
              }
          }
        }
      }
    }
  }

  /** Summons validators for each element type in a tuple.
    *
    * This helper method is used by the macro implementation to get validator instances for each
    * field in a product type. It recursively processes the tuple of element types, summoning a
    * validator for each type and converting it to Validator[Any] for uniform handling.
    *
    * @tparam Elems
    *   tuple type representing the types for which to summon validators
    * @param q
    *   the Quotes context for macro expansion
    * @return
    *   a list of expressions, each constructing a Validator[Any]
    */
  private def summonValidators[Elems <: Tuple: Type](using q: Quotes): List[Expr[Validator[Any]]] = {
    import q.reflect.*
    Type.of[Elems] match {
      case '[EmptyTuple] => Nil
      case '[h *: t] =>
        val validatorExpr = Expr.summon[Validator[h]].getOrElse {
          report.errorAndAbort(s"Could not find a given Validator for type ${Type.show[h]}")
        }
        '{ MacroHelper.upcastTo[Validator[Any]](${ validatorExpr }) } :: summonValidators[t]
    }
  }

  /** Extracts field names from a tuple type of string literals.
    *
    * This helper method is used by the macro implementation to obtain the names of fields in a
    * product type. It recursively processes the tuple of string literal types, extracting each name
    * as a String.
    *
    * @tparam Labels
    *   tuple type of string literals representing field names
    * @param q
    *   the Quotes context for macro expansion
    * @return
    *   a list of field names as strings
    */
  private def getLabels[Labels <: Tuple: Type](using q: Quotes): List[String] = {
    import q.reflect.*
    def loop(tpe: TypeRepr): List[String] = tpe.dealias match {
      case AppliedType(_, List(head, tail)) =>
        head match {
          case ConstantType(StringConstant(label)) => label :: loop(tail)
          case _ => report.errorAndAbort(s"Macro error: Expected a literal string for a label, but got ${head.show}")
        }
      case t if t =:= TypeRepr.of[EmptyTuple] => Nil
      case _ => report.errorAndAbort(s"Macro error: The labels tuple was not structured as expected: ${tpe.show}")
    }

    loop(TypeRepr.of[Labels])
  }

  /** Determines which fields in a product type are Options.
    *
    * This helper method is used by the macro implementation to identify which fields in a product
    * type are Option types. This information is used for null-safety handling during validation.
    *
    * @tparam Elems
    *   tuple type representing the types of all fields
    * @param q
    *   the Quotes context for macro expansion
    * @return
    *   a list of booleans indicating whether each field is an Option type
    */
  private def getIsOptionFlags[Elems <: Tuple: Type](using q: Quotes): List[Boolean] = {
    import q.reflect.*
    Type.of[Elems] match {
      case '[EmptyTuple] => Nil
      case '[h *: t] =>
        (TypeRepr.of[h] <:< TypeRepr.of[Option[Any]]) :: getIsOptionFlags[t]
    }
  }
}
