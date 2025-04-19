package net.ghoula.valar.internal // Corrected package

import net.ghoula.valar.Validator

import scala.quoted.{Expr, Quotes, Type} // Corrected import path

// Keep this internal to the library using package-private (default access within 'internal')

/** Internal helper methods for macros, primarily for type casting which is inherently unsafe but
  * necessary when bridging compile-time types and runtime values in macros or dealing with
  * unavoidable type erasure limitations (like in `validateUnion`). Use with extreme caution, only
  * when type compatibility is guaranteed by macro logic.
  */
object MacroHelpers {

  /** Unsafely casts a value `x` to type `T`. Should only be used within macro implementations where
    * type correctness is guaranteed by the macro logic, or where `Any` must be cast back.
    * Suppressed warnings for `asInstanceOf`.
    * @param x
    *   The value to cast.
    * @tparam T
    *   The target type.
    * @return
    *   The value `x` cast to type `T`.
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
  def upcastTo[T](x: Any): T = x.asInstanceOf[T]

  /** Internal helper for type casting within macros. Marked `private`. */
  private[internal] object Upcast {

    /** Unsafely casts `x` of type `T` to type `U`. */
    @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
    def apply[T, U](x: T): U = x.asInstanceOf[U]

    /** Unsafely casts a `Validator[A]` to `Validator[Any]`. */
    @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
    def validator[A](v: Validator[A]): Validator[Any] =
      v.asInstanceOf[Validator[Any]]
  }
  // Use aliases for clarity when importing from private object
  import Upcast.{apply as upcastApply, validator as upcastValidatorHelper}

  /** Casts a `quoted.Expr[Any]` to `quoted.Expr[T]` within a macro context. */
  def castExpr[T: Type](expr: Expr[Any])(using Quotes): Expr[T] =
    upcastApply[Expr[Any], Expr[T]](expr)

  /** Casts a runtime value `value` to type `T`. Equivalent to `upcastTo`. */
  def castValue[T](value: Any): T = upcastTo[T](value)

  /** Private inline helper to cast a `Validator[A]` to `Validator[Any]`. */
  private inline def upcastValidatorInternal[A](v: Validator[A]): Validator[Any] =
    upcastValidatorHelper(v)

  /** Inline helper specifically for casting validators in union/intersection contexts. */
  inline def upcastUnionValidator[A](v: Validator[A]): Validator[Any] =
    upcastValidatorInternal(v) // Renamed internal call
}
