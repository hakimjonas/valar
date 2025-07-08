package net.ghoula.valar.internal

/** Internal utility for type casting operations required by Valar's macro system.
  *
  * This function exists to handle specific, unavoidable casting scenarios in macros while
  * suppressing linter warnings for `asInstanceOf`.
  *
  * '''Safety Contract''': This function should only be used when macro logic guarantees type
  * compatibility. Incorrect usage will result in a `ClassCastException` at runtime.
  */
object MacroHelper {

  /** Casts a value to a specific type `T` when the type is guaranteed by macro logic.
    *
    * @param x
    *   The value to cast.
    * @tparam T
    *   The target type.
    * @return
    *   The value `x` cast to type `T`.
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
  inline def upcastTo[T](x: Any): T = x.asInstanceOf[T]

}
