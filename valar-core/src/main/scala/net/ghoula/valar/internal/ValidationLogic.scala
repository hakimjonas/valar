package net.ghoula.valar.internal

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.{ValidationConfig, ValidationResult}

/** Shared validation logic for collections.
  *
  * This eliminates duplication between Validator and AsyncValidator by abstracting over the effect
  * type F[_]. The collection validation algorithm is written once here.
  */
private[valar] object ValidationLogic {

  /** Validates a collection of items, checking size limits and accumulating errors.
    *
    * @tparam F
    *   The effect type (Id for sync, Future for async)
    * @tparam A
    *   The element type
    * @tparam C
    *   The collection type
    * @param items
    *   The collection to validate
    * @param buildResult
    *   Function to reconstruct the collection from validated elements
    * @param collectionType
    *   Name of collection type for error messages (e.g., "List", "Vector")
    * @param validateItem
    *   Function to validate a single item
    * @param F
    *   The effect instance
    * @param config
    *   Validation configuration with size limits
    * @return
    *   Validation result wrapped in effect F
    */
  def validateCollection[F[_], A, C](
    items: Iterable[A],
    buildResult: List[A] => C,
    collectionType: String
  )(
    validateItem: A => F[ValidationResult[A]]
  )(using
    F: ValidationEffect[F],
    config: ValidationConfig
  ): F[ValidationResult[C]] = {

    // 1. Check size limit (pure/sync operation)
    val sizeCheck: ValidationResult[Unit] = config.checkCollectionSize(items.size, collectionType) match {
      case ValidationResult.Valid(_) => ValidationResult.Valid(())
      case ValidationResult.Invalid(errors) => ValidationResult.Invalid(errors)
    }

    // 2. If size check fails, return early
    sizeCheck match {
      case ValidationResult.Invalid(errors) =>
        F.pure(ValidationResult.Invalid(errors))

      case ValidationResult.Valid(_) =>
        // 3. Validate all items
        val itemResults: F[List[ValidationResult[A]]] = F.traverse(items.toList)(validateItem)

        // 4. Aggregate results
        F.map(itemResults) { results =>
          val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], List.empty[A])) {
            case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
            case ((errs, vals), ValidationResult.Invalid(e)) => (errs ++ e, vals)
          }

          if (errors.isEmpty) ValidationResult.Valid(buildResult(validValues))
          else ValidationResult.Invalid(errors)
        }
    }
  }

  /** Validates a Map by validating each key and value.
    *
    * @tparam F
    *   The effect type
    * @tparam K
    *   The key type
    * @tparam V
    *   The value type
    */
  def validateMap[F[_], K, V](
    m: Map[K, V]
  )(
    validateKey: K => F[ValidationResult[K]],
    validateValue: V => F[ValidationResult[V]]
  )(using
    F: ValidationEffect[F],
    config: ValidationConfig
  ): F[ValidationResult[Map[K, V]]] = {

    // 1. Check size limit
    val sizeCheck = config.checkCollectionSize(m.size, "Map") match {
      case ValidationResult.Valid(_) => ValidationResult.Valid(())
      case ValidationResult.Invalid(errors) => ValidationResult.Invalid(errors)
    }

    sizeCheck match {
      case ValidationResult.Invalid(errors) =>
        F.pure(ValidationResult.Invalid(errors))

      case ValidationResult.Valid(_) =>
        // 2. Validate all entries
        val entryResults: F[List[ValidationResult[(K, V)]]] = F.traverse(m.toList) { case (k, v) =>
          val keyResult: F[ValidationResult[K]] = F.map(validateKey(k)) {
            case ValidationResult.Valid(kk) => ValidationResult.Valid(kk)
            case ValidationResult.Invalid(es) =>
              ValidationResult.Invalid(es.map(e => e.annotateField("key", k.getClass.getSimpleName)))
          }

          val valueResult: F[ValidationResult[V]] = F.map(validateValue(v)) {
            case ValidationResult.Valid(vv) => ValidationResult.Valid(vv)
            case ValidationResult.Invalid(es) =>
              ValidationResult.Invalid(es.map(e => e.annotateField("value", v.getClass.getSimpleName)))
          }

          // Combine key and value results
          F.flatMap(keyResult) { kr =>
            F.map(valueResult) { vr =>
              kr.zip(vr)
            }
          }
        }

        // 3. Aggregate results
        F.map(entryResults) { results =>
          val (errors, validPairs) = results.foldLeft((Vector.empty[ValidationError], Map.empty[K, V])) {
            case ((errs, acc), ValidationResult.Valid(pair)) => (errs, acc + pair)
            case ((errs, acc), ValidationResult.Invalid(e)) => (errs ++ e, acc)
          }

          if (errors.isEmpty) ValidationResult.Valid(validPairs)
          else ValidationResult.Invalid(errors)
        }
    }
  }
}
