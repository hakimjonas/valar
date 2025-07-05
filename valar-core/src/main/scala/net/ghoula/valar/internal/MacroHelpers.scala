package net.ghoula.valar.internal

import scala.annotation.unused
import scala.quoted.{Expr, Quotes, Type}

import net.ghoula.valar.Validator

/** Internal utilities for type casting operations required by Valar's macro system.
  *
  * These functions exist to handle three specific scenarios where unsafe casting is unavoidable:
  *   1. **Macro compilation**: Bridging between compile-time types and runtime values
  *   2. **Union type validation**: Working around type erasure when validating `A | B` types
  *   3. **Generic tuple handling**: Converting between `Any` and specific tuple types in derived
  *      validators
  *
  * '''Safety Contract''': All functions in this object perform unchecked type casts using
  * `asInstanceOf`. They should only be used when the macro logic or type system guarantees type
  * compatibility. Incorrect usage will result in `ClassCastException` at runtime.
  *
  * '''Naming Convention''': Functions are prefixed with "upcast" to emphasize that they're casting
  * from a more general type to a more specific one (e.g., `Any` to `T`).
  */
object MacroHelpers {

  /** Casts a value from `Any` to a specific type `T`.
    *
    * This is the primary casting function used throughout Valar's macro system. It's typically used
    * when macro logic has determined the correct type at compile time, but the runtime value is
    * typed as `Any` due to type erasure.
    *
    * @param x
    *   The value to cast (typically from macro-generated code)
    * @tparam T
    *   The target type (must be correct or will throw at runtime)
    * @return
    *   The value `x` cast to type `T`
    * @throws ClassCastException
    *   if `x` is not of type `T`
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
  def upcastTo[T](x: Any): T = x.asInstanceOf[T]

  /** Internal casting utilities for specific macro contexts. */
  private[internal] object Upcast {

    /** Generic casting between any two types.
      *
      * More dangerous than `upcastTo` as it doesn't require the source type to be `Any`. Only used
      * internally where macro logic guarantees type relationships.
      */
    @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
    def apply[T, U](x: T): U = x.asInstanceOf[U]

    /** Casts a typed validator to accept `Any` input.
      *
      * Used in union type validation where we need to apply a `Validator[A]` to a value of an
      * unknown type. The macro ensures the value is actually of type `A` before this cast.
      */
    @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
    def validator[A](v: Validator[A]): Validator[Any] =
      v.asInstanceOf[Validator[Any]]
  }
  import Upcast.{apply as upcastApply, validator as upcastValidatorHelper}

  /** Casts a quoted expression within the macro context.
    *
    * Used when macro code needs to change the type of `quoted.Expr` from `Any` to a specific type
    * `T`. The macro system guarantees type safety through compile-time analysis.
    *
    * @param expr
    *   The expression to cast
    * @tparam T
    *   The target type (with implicit `Type` evidence)
    * @return
    *   The expression cast to `Expr[T]`
    */
  def castExpr[T: Type](expr: Expr[Any])(using @unused quotes: Quotes): Expr[T] =
    upcastApply[Expr[Any], Expr[T]](expr)

  /** Alias for `upcastTo` with a more descriptive name for runtime value casting.
    *
    * @param value
    *   The runtime value to cast
    * @tparam T
    *   The target type
    * @return
    *   The value cast to type `T`
    */
  def castValue[T](value: Any): T = upcastTo[T](value)

  /** Internal helper for validator casting in derived instances. */
  private inline def upcastValidatorInternal[A](v: Validator[A]): Validator[Any] =
    upcastValidatorHelper(v)

  /** Casts a validator for use in union type validation.
    *
    * This is specifically used in `validateUnion` where we need to apply validators of different
    * types to the same input value. The union validation logic ensures the cast is safe by checking
    * type compatibility before applying the validator.
    *
    * @param v
    *   The validator to cast
    * @tparam A
    *   The original validator type
    * @return
    *   A validator that accepts `Any` input
    */
  inline def upcastUnionValidator[A](v: Validator[A]): Validator[Any] =
    upcastValidatorInternal(v)
}
