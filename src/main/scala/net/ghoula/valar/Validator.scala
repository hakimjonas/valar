package net.ghoula.valar

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.{validateUnion, given}
import net.ghoula.valar.internal.MacroHelpers

import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID
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

  /** Validates that an Int is non-negative (>= 0). Uses [[ValidationHelpers.positiveInt]]. */
  given positiveIntValidator: Validator[Int] with {
    def validate(i: Int): ValidationResult[Int] = positiveInt(i)
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

  /** Pass-through validator for Boolean. Always returns Valid. */
  given booleanValidator: Validator[Boolean] with {
    def validate(b: Boolean): ValidationResult[Boolean] = ValidationResult.Valid(b)
  }

  /** Pass-through validator for Byte. Always returns Valid. */
  given byteValidator: Validator[Byte] with {
    def validate(b: Byte): ValidationResult[Byte] = ValidationResult.Valid(b)
  }

  /** Pass-through validator for Short. Always returns Valid. */
  given shortValidator: Validator[Short] with {
    def validate(s: Short): ValidationResult[Short] = ValidationResult.Valid(s)
  }

  /** Pass-through validator for Long. Always returns Valid. */
  given longValidator: Validator[Long] with {
    def validate(l: Long): ValidationResult[Long] = ValidationResult.Valid(l)
  }

  /** Pass-through validator for Char. Always returns Valid. */
  given charValidator: Validator[Char] with {
    def validate(c: Char): ValidationResult[Char] = ValidationResult.Valid(c)
  }

  /** Pass-through validator for Unit. Always returns Valid. */
  given unitValidator: Validator[Unit] with {
    def validate(u: Unit): ValidationResult[Unit] = ValidationResult.Valid(u)
  }

  /** Pass-through validator for BigInt. Always returns Valid. */
  given bigIntValidator: Validator[BigInt] with {
    // No standard constraints like finiteness apply easily.
    def validate(bi: BigInt): ValidationResult[BigInt] = ValidationResult.Valid(bi)
  }

  /** Pass-through validator for BigDecimal. Always returns Valid.
    *
    * @note
    *   Scala's `BigDecimal` often relies on a `MathContext,` and representing constraints like
    *   finiteness or precision is complex for a default validator. Therefore, this default
    *   validator is pass-through. Users needing specific precision, range, or other checks for
    *   `BigDecimal` should define a custom `Validator[BigDecimal]` instance.
    * @return
    *   A Validator[BigDecimal] that always returns Valid.
    */
  given bigDecimalValidator: Validator[BigDecimal] with {
    def validate(bd: BigDecimal): ValidationResult[BigDecimal] = ValidationResult.Valid(bd)
  }

  /** Pass-through validator for Symbol. Always returns Valid. */
  given symbolValidator: Validator[Symbol] with {
    def validate(s: Symbol): ValidationResult[Symbol] = ValidationResult.Valid(s)
  }

  /** ==Default Validators for Common Java Types==
    *
    * This section provides default, pass-through `Validator` instances for Java types that are
    * frequently encountered in Scala data models, particularly within case classes used with
    * `deriveValidatorMacro`.
    *
    * @note
    *   Rationale for Inclusion and Behavior:
    *   - **Ubiquity: ** These types (`java.util.UUID`, core `java.time.*`) are chosen because of
    *     their extremely common usage in Scala applications.
    *   - **Derivation Support: ** Providing these instances prevents compilation errors when
    *     deriving validators for case classes containing these types, reducing boilerplate for the
    *     user.
    *   - **Pass-Through Logic: ** These validators are simple "pass-through" validators (they
    *     always return `ValidationResult.Valid(value)`). They do not impose any validation rules
    *     beyond what the type system enforces.
    *   - **Extensibility: ** Users requiring specific validation logic for these types (e.g.,
    *     checking the UUID version, ensuring an `Instant` is in the past) should define their own
    *     custom `given Validator[...]` instance, which will take precedence over these defaults
    *     according to implicit resolution rules.
    */

  /** Pass-through validator for java.util.UUID. Always returns Valid. */
  given uuidValidator: Validator[UUID] with {
    def validate(v: UUID): ValidationResult[UUID] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for java.time.Instant. Always returns Valid. */
  given instantValidator: Validator[Instant] with {
    def validate(v: Instant): ValidationResult[Instant] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for java.time.LocalDate. Always returns Valid. */
  given localDateValidator: Validator[LocalDate] with {
    def validate(v: LocalDate): ValidationResult[LocalDate] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for java.time.LocalTime. Always returns Valid. */
  given localTimeValidator: Validator[LocalTime] with {
    def validate(v: LocalTime): ValidationResult[LocalTime] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for java.time.LocalDateTime. Always returns Valid. */
  given localDateTimeValidator: Validator[LocalDateTime] with {
    def validate(v: LocalDateTime): ValidationResult[LocalDateTime] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for java.time.ZonedDateTime. Always returns Valid. */
  given zonedDateTimeValidator: Validator[ZonedDateTime] with {
    def validate(v: ZonedDateTime): ValidationResult[ZonedDateTime] = ValidationResult.Valid(v)
  }

  /** Pass-through validator for java.time.Duration. Always returns Valid. */
  given durationValidator: Validator[Duration] with {
    def validate(v: Duration): ValidationResult[Duration] = ValidationResult.Valid(v)
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
