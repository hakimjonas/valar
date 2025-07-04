package net.ghoula.valar

/** A public typeclass for accumulating errors of type E.
  *
  * This typeclass defines a strategy for how two instances of an error type should be combined. It
  * is a key component for both the core library's error handling and the testing extensions.
  *
  * @tparam E
  *   The type of error to accumulate.
  */
trait ErrorAccumulator[E] {

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

/** Companion object for [[ErrorAccumulator]]. Provides default instances for common collections.
  */
object ErrorAccumulator {

  /** Default instance for `Vector`. Combines two vectors by concatenation. This is the primary
    * accumulator used by Valar's core.
    */
  given vectorAccumulator[Err]: ErrorAccumulator[Vector[Err]] with {
    def combine(e1: Vector[Err], e2: Vector[Err]): Vector[Err] = e1 ++ e2
  }

  /** Instance for `List`. Combines two lists by concatenation. */
  given listAccumulator[Err]: ErrorAccumulator[List[Err]] with {
    def combine(e1: List[Err], e2: List[Err]): List[Err] = e1 ::: e2
  }

  /** Instance for `Seq`. Combines two sequences by concatenation. */
  given seqAccumulator[Err]: ErrorAccumulator[Seq[Err]] with {
    def combine(e1: Seq[Err], e2: Seq[Err]): Seq[Err] = e1 ++ e2
  }

  /** Instance for `Set`. Combines two sets, preserving uniqueness of elements. */
  given setAccumulator[Err]: ErrorAccumulator[Set[Err]] with {
    def combine(e1: Set[Err], e2: Set[Err]): Set[Err] = e1 ++ e2
  }
}
