package net.ghoula.valar

import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.ArraySeq
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag

import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.{validateUnion, given}
import net.ghoula.valar.internal.{Derivation, SyncEffect, ValidationLogic}

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

  // ... keep all the existing given instances exactly as they are ...

  /** Pass-through validator for Int. For constraints, use [[ValidationHelpers.nonNegativeInt]]. */
  inline given intValidator: Validator[Int] with {
    def validate(i: Int): ValidationResult[Int] = ValidationResult.Valid(i)
  }

  /** Pass-through validator for Float. For constraints, use [[ValidationHelpers.finiteFloat]]. */
  inline given floatValidator: Validator[Float] with {
    def validate(f: Float): ValidationResult[Float] = ValidationResult.Valid(f)
  }

  /** Pass-through validator for Double. For constraints, use [[ValidationHelpers.finiteDouble]]. */
  inline given doubleValidator: Validator[Double] with {
    def validate(d: Double): ValidationResult[Double] = ValidationResult.Valid(d)
  }

  /** Pass-through validator for String. For constraints, use [[ValidationHelpers.nonEmpty]]. */
  inline given stringValidator: Validator[String] with {
    def validate(s: String): ValidationResult[String] = ValidationResult.Valid(s)
  }

  /** Default validator for `Option[A]`. */
  given optionValidator[A](using v: Validator[A]): Validator[Option[A]] with {
    def validate(opt: Option[A]): ValidationResult[Option[A]] =
      optional(opt)(using v)
  }

  /** Validates a `List[A]` by validating each element. */
  given listValidator[A](using v: Validator[A], config: ValidationConfig): Validator[List[A]] with {
    def validate(xs: List[A]): ValidationResult[List[A]] =
      ValidationLogic.validateCollection[[X] =>> X, A, List[A]](xs, identity, "List")(v.validate)(using
        SyncEffect,
        config
      )
  }

  /** Validates a `Seq[A]` by validating each element. */
  given seqValidator[A](using v: Validator[A], config: ValidationConfig): Validator[Seq[A]] with {
    def validate(xs: Seq[A]): ValidationResult[Seq[A]] =
      ValidationLogic.validateCollection[[X] =>> X, A, Seq[A]](xs, _.toSeq, "Seq")(v.validate)(using SyncEffect, config)
  }

  /** Validates a `Vector[A]` by validating each element. */
  given vectorValidator[A](using v: Validator[A], config: ValidationConfig): Validator[Vector[A]] with {
    def validate(xs: Vector[A]): ValidationResult[Vector[A]] =
      ValidationLogic.validateCollection[[X] =>> X, A, Vector[A]](xs, _.toVector, "Vector")(v.validate)(using
        SyncEffect,
        config
      )
  }

  /** Validates a `Set[A]` by validating each element. */
  given setValidator[A](using v: Validator[A], config: ValidationConfig): Validator[Set[A]] with {
    def validate(xs: Set[A]): ValidationResult[Set[A]] =
      ValidationLogic.validateCollection[[X] =>> X, A, Set[A]](xs, _.toSet, "Set")(v.validate)(using SyncEffect, config)
  }

  /** Validates a `Map[K, V]` by validating each key and value. */
  given mapValidator[K, V](using vk: Validator[K], vv: Validator[V], config: ValidationConfig): Validator[Map[K, V]]
  with {
    def validate(m: Map[K, V]): ValidationResult[Map[K, V]] =
      ValidationLogic.validateMap[[X] =>> X, K, V](m)(vk.validate, vv.validate)(using SyncEffect, config)
  }

  /** Validates an `Array[A]` by validating each element. */
  given arrayValidator[A](using v: Validator[A], ct: ClassTag[A], config: ValidationConfig): Validator[Array[A]] with {
    def validate(xs: Array[A]): ValidationResult[Array[A]] =
      ValidationLogic.validateCollection[[X] =>> X, A, Array[A]](xs, _.toArray, "Array")(v.validate)(using
        SyncEffect,
        config
      )
  }

  /** Validates an `ArraySeq[A]` by validating each element. */
  given arraySeqValidator[A](using v: Validator[A], ct: ClassTag[A], config: ValidationConfig): Validator[ArraySeq[A]]
  with {
    def validate(xs: ArraySeq[A]): ValidationResult[ArraySeq[A]] =
      ValidationLogic.validateCollection[[X] =>> X, A, ArraySeq[A]](
        xs,
        l => ArraySeq.unsafeWrapArray(l.toArray),
        "ArraySeq"
      )(v.validate)(using SyncEffect, config)
  }

  /** Validates an intersection type `A & B`. */
  given intersectionValidator[A, B](using va: Validator[A], vb: Validator[B]): Validator[A & B] with {
    def validate(ab: A & B): ValidationResult[A & B] =
      va.validate(ab).zip(vb.validate(ab)).map(_ => ab)
  }

  /** Validates a union type `A | B`. */
  given unionValidator[A, B](using
    va: Validator[A],
    vb: Validator[B],
    ctA: ClassTag[A],
    ctB: ClassTag[B]
  ): Validator[A | B] with {
    def validate(value: A | B): ValidationResult[A | B] = validateUnion[A, B](value)(using va, vb, ctA, ctB)
  }

  /** This section provides "pass-through" `given` instances that always return `Valid`. */
  inline given booleanValidator: Validator[Boolean] with {
    def validate(b: Boolean): ValidationResult[Boolean] = ValidationResult.Valid(b)
  }
  inline given byteValidator: Validator[Byte] with {
    def validate(b: Byte): ValidationResult[Byte] = ValidationResult.Valid(b)
  }
  inline given shortValidator: Validator[Short] with {
    def validate(s: Short): ValidationResult[Short] = ValidationResult.Valid(s)
  }
  inline given longValidator: Validator[Long] with {
    def validate(l: Long): ValidationResult[Long] = ValidationResult.Valid(l)
  }
  inline given charValidator: Validator[Char] with {
    def validate(c: Char): ValidationResult[Char] = ValidationResult.Valid(c)
  }
  inline given unitValidator: Validator[Unit] with {
    def validate(u: Unit): ValidationResult[Unit] = ValidationResult.Valid(u)
  }
  inline given bigIntValidator: Validator[BigInt] with {
    def validate(bi: BigInt): ValidationResult[BigInt] = ValidationResult.Valid(bi)
  }
  inline given bigDecimalValidator: Validator[BigDecimal] with {
    def validate(bd: BigDecimal): ValidationResult[BigDecimal] = ValidationResult.Valid(bd)
  }
  inline given symbolValidator: Validator[Symbol] with {
    def validate(s: Symbol): ValidationResult[Symbol] = ValidationResult.Valid(s)
  }
  inline given uuidValidator: Validator[UUID] with {
    def validate(v: UUID): ValidationResult[UUID] = ValidationResult.Valid(v)
  }
  inline given instantValidator: Validator[Instant] with {
    def validate(v: Instant): ValidationResult[Instant] = ValidationResult.Valid(v)
  }
  inline given localDateValidator: Validator[LocalDate] with {
    def validate(v: LocalDate): ValidationResult[LocalDate] = ValidationResult.Valid(v)
  }
  inline given localTimeValidator: Validator[LocalTime] with {
    def validate(v: LocalTime): ValidationResult[LocalTime] = ValidationResult.Valid(v)
  }
  inline given localDateTimeValidator: Validator[LocalDateTime] with {
    def validate(v: LocalDateTime): ValidationResult[LocalDateTime] = ValidationResult.Valid(v)
  }
  inline given zonedDateTimeValidator: Validator[ZonedDateTime] with {
    def validate(v: ZonedDateTime): ValidationResult[ZonedDateTime] = ValidationResult.Valid(v)
  }
  inline given durationValidator: Validator[Duration] with {
    def validate(v: Duration): ValidationResult[Duration] = ValidationResult.Valid(v)
  }

  /** Automatically derives a `Validator` for case classes using Scala 3 macros. */
  inline def derive[T](using m: Mirror.ProductOf[T]): Validator[T] =
    ${ deriveImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  /** Macro implementation for deriving a `Validator`. */
  private def deriveImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[Validator[T]] =
    Derivation.deriveSyncValidatorImpl[T, Elems, Labels](m)
}
