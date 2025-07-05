package net.ghoula.valar

/** A typeclass for combining errors of type E during validation.
  *
  * This typeclass defines how multiple error instances should be combined when accumulating
  * validation failures. It enables flexible error handling strategies - from simple concatenation
  * of collections to more sophisticated merging logic for custom error types.
  *
  * The primary use case is in validation scenarios where multiple independent checks may fail, and
  * all failures should be preserved rather than stopping at the first error.
  *
  * @tparam E
  *   The type of error to accumulate (typically a collection like Vector[ValidationError]).
  */
trait ErrorAccumulator[E] {

  /** Combines two error instances into a single accumulated error.
    *
    * This operation should be associative: `combine(combine(a, b), c) == combine(a, combine(b, c))`
    * to ensure consistent behavior when accumulating multiple errors.
    *
    * @param e1
    *   The first error instance.
    * @param e2
    *   The second error instance.
    * @return
    *   A single error instance containing the combined information from both inputs.
    */
  def combine(e1: E, e2: E): E
}

/** Companion object for [[ErrorAccumulator]] providing default instances for common collection
  * types.
  *
  * These instances follow the standard pattern of concatenating collections, which is appropriate
  * for most error accumulation scenarios where you want to preserve all validation failures.
  */
object ErrorAccumulator {

  /** Default instance for `Vector[T]`. Combines vectors by concatenation.
    *
    * This is the primary accumulator used throughout Valar's validation system, chosen for its
    * efficient append operations and ordered preservation of errors.
    */
  given vectorAccumulator[T]: ErrorAccumulator[Vector[T]] with {
    def combine(e1: Vector[T], e2: Vector[T]): Vector[T] = e1 ++ e2
  }

  /** Instance for `List[T]`. Combines lists by concatenation using the efficient `:::` operator. */
  given listAccumulator[T]: ErrorAccumulator[List[T]] with {
    def combine(e1: List[T], e2: List[T]): List[T] = e1 ::: e2
  }

  /** Instance for `Seq[T]`. Combines sequences by concatenation. */
  given seqAccumulator[T]: ErrorAccumulator[Seq[T]] with {
    def combine(e1: Seq[T], e2: Seq[T]): Seq[T] = e1 ++ e2
  }

  /** Instance for `Set[T]`. Combines sets using union operation, preserving uniqueness of elements.
    */
  given setAccumulator[T]: ErrorAccumulator[Set[T]] with {
    def combine(e1: Set[T], e2: Set[T]): Set[T] = e1 ++ e2
  }
}
