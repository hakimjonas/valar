package net.ghoula.valar.internal // Corrected package

// Keep this internal to the library using package-private (default access within 'internal')

/** Typeclass for accumulating errors of type E. Allows defining how two instances of an error type
  * should be combined.
  * @tparam E
  *   The type of error to accumulate.
  */
private[valar] trait ErrorAccumulator[E] {

  /** Combines two error instances into a single instance.
    * @param e1
    *   The first error instance.
    * @param e2
    *   The second error instance.
    * @return
    *   The combined error instance.
    */
  def combine(e1: E, e2: E): E
}

/** Companion object for [[ErrorAccumulator]]. Provides default instances.
  */
object ErrorAccumulator {

  /** Default instance for Vector-based error accumulation (concatenation). Combines two vectors of
    * errors by concatenating them.
    * @tparam Err
    *   The type of individual errors within the vector.
    * @return
    *   An [[ErrorAccumulator]] instance for `Vector[Err]`.
    */
  given vectorAccumulator[Err]: ErrorAccumulator[Vector[Err]] with {
    def combine(e1: Vector[Err], e2: Vector[Err]): Vector[Err] = e1 ++ e2
  }
}
