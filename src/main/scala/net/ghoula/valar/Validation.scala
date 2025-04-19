package net.ghoula.valar 

import scala.reflect.ClassTag

/** Provides user-friendly entry points for validation, particularly for complex types like unions.
  */
object Validation {

  /** Validates a value against a union type `A | B`. Delegates to
    * [[ValidationResult.validateUnion]]. Requires implicit validators and ClassTags for both `A`
    * and `B`.
    * @param value
    *   The value to validate.
    * @param va
    *   Implicit validator for type `A`.
    * @param vb
    *   Implicit validator for type `B`.
    * @param ctA
    *   Implicit ClassTag for type `A`.
    * @param ctB
    *   Implicit ClassTag for type `B`.
    * @tparam A
    *   The first type in the union.
    * @tparam B
    *   The second type in the union.
    * @return
    *   A [[ValidationResult[A | B]] via [[ValidationResult.validateUnion]].
    * @see
    *   [[ValidationResult.validateUnion]]
    */
  def validateUnion[A, B](
    value: Any
  )(using
    va: Validator[A],
    vb: Validator[B],
    ctA: ClassTag[A],
    ctB: ClassTag[B]
  ): ValidationResult[A | B] =
    ValidationResult.validateUnion[A, B](value)(using va, vb, ctA, ctB)

  /** Validates a value against a specific expected type `T`. Delegates to
    * [[ValidationResult.validateType]]. Requires an implicit validator and ClassTag for `T`.
    * @param value
    *   The value to validate.
    * @param validator
    *   Implicit validator for type `T`.
    * @param ct
    *   Implicit ClassTag for type `T`.
    * @tparam T
    *   The target type for validation.
    * @return
    *   A [[ValidationResult[T]] via [[ValidationResult.validateType]].
    * @see
    *   [[ValidationResult.validateType]]
    */
  def validateType[T](value: Any)(using validator: Validator[T], ct: ClassTag[T]): ValidationResult[T] =
    ValidationResult.validateType(value)(using validator, ct)
}
