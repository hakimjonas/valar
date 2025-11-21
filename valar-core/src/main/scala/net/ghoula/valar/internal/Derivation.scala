package net.ghoula.valar.internal

import scala.concurrent.{ExecutionContext, Future}
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.{AsyncValidator, ValidationResult, Validator}

/** Internal derivation engine for automatically generating validator instances.
  *
  * This object provides the core macro infrastructure for deriving both synchronous and
  * asynchronous validators for product types (case classes). It handles the compile-time generation
  * of validation logic, field introspection, and error annotation.
  *
  * @note
  *   This object is strictly for internal use by Valar's macro system and is not part of the public
  *   API. All methods, signatures, and behavior are subject to change without notice in future
  *   versions.
  *
  * @since 0.5.0
  */
object Derivation {

  /** Processes validation results from multiple fields into a single consolidated result.
    *
    * This method aggregates validation outcomes from all fields of a product type. If any field
    * validation fails, all errors are collected and returned as an `Invalid` result. If all
    * validations succeed, the validated values are used to reconstruct the original product type
    * using the provided `Mirror`.
    *
    * @param results
    *   The validation results from each field of the product type.
    * @param mirror
    *   The mirror instance used to reconstruct the product type from validated field values.
    * @tparam T
    *   The product type being validated.
    * @return
    *   A `ValidationResult[T]` containing either the reconstructed valid product or accumulated
    *   errors.
    */
  private def processResults[T](
    results: List[ValidationResult[Any]],
    mirror: Mirror.ProductOf[T]
  ): ValidationResult[T] = {
    val allErrors = results.collect { case ValidationResult.Invalid(e) => e }.flatten.toVector
    if (allErrors.isEmpty) {
      val validValues = results.collect { case ValidationResult.Valid(v) => v }
      ValidationResult.Valid(mirror.fromProduct(Tuple.fromArray(validValues.toArray)))
    } else {
      ValidationResult.Invalid(allErrors)
    }
  }

  /** Enhances validation results with field-specific context information.
    *
    * This method annotates validation errors with the field name and type information, providing
    * better debugging and error reporting capabilities. Valid results are passed through unchanged.
    *
    * @param result
    *   The validation result to annotate.
    * @param label
    *   The field name for error context.
    * @param fieldValue
    *   The field value used to extract type information.
    * @return
    *   The validation result with enhanced error context if invalid, or unchanged if valid.
    */
  private def annotateErrors(
    result: ValidationResult[Any],
    label: String,
    fieldValue: Any
  ): ValidationResult[Any] = {
    result match {
      case ValidationResult.Valid(v) => ValidationResult.Valid(v)
      case ValidationResult.Invalid(errs) =>
        val fieldTypeName = Option(fieldValue).map(_.getClass.getSimpleName).getOrElse("null")
        ValidationResult.Invalid(errs.map(_.annotateField(label, fieldTypeName)))
    }
  }

  /** Applies validation logic to each field of a product type with null-safety handling.
    *
    * This method iterates through the fields of a product type, applying the appropriate validation
    * logic to each field. It handles null values appropriately based on whether the field is
    * optional, and provides consistent error handling for both synchronous and asynchronous
    * validation scenarios.
    *
    * @param product
    *   The product instance whose fields are being validated.
    * @param validators
    *   The sequence of validators corresponding to each field.
    * @param labels
    *   The field names for error reporting.
    * @param isOptionFlags
    *   Flags indicating which fields are optional (Option types).
    * @param validateAndAnnotate
    *   Function to apply validation and annotation to a field.
    * @param handleNull
    *   Function to handle null values in non-optional fields.
    * @tparam V
    *   The validator type (either `Validator` or `AsyncValidator`).
    * @tparam R
    *   The result type (either `ValidationResult` or `Future[ValidationResult]`).
    * @return
    *   A list of validation results for each field.
    */
  private def validateProduct[V, R](
    product: Product,
    validators: Seq[V],
    labels: List[String],
    isOptionFlags: List[Boolean],
    validateAndAnnotate: (V, Any, String) => R,
    handleNull: String => R
  ): List[R] = {
    product.productIterator.zipWithIndex.map { case (fieldValue, i) =>
      val label = labels(i)
      if (Option(fieldValue).isEmpty && !isOptionFlags(i)) {
        handleNull(label)
      } else {
        val validator = validators(i)
        validateAndAnnotate(validator, fieldValue, label)
      }
    }.toList
  }

  /** Extracts field names from a compile-time tuple of string literal types.
    *
    * This method recursively processes a tuple type containing string literals (typically from
    * `Mirror.MirroredElemLabels`) to extract the actual field names as a runtime `List[String]`. It
    * performs compile-time validation to ensure all labels are string literals.
    *
    * @param q
    *   The quotes context for macro operations.
    * @tparam Labels
    *   The tuple type containing string literal types for field names.
    * @return
    *   A list of field names extracted from the tuple type.
    * @throws Compilation
    *   error if any label is not a string literal.
    */
  private def getLabels[Labels <: Tuple: Type](using q: Quotes): List[String] = {
    import q.reflect.*
    def loop(tpe: TypeRepr): List[String] = tpe.dealias match {
      case AppliedType(_, List(head, tail)) =>
        head match {
          case ConstantType(StringConstant(label)) => label :: loop(tail)
          case _ =>
            report.errorAndAbort(
              s"Invalid field label type: expected string literal, found ${head.show}. " +
                "This typically indicates a structural issue with the case class definition.",
              Position.ofMacroExpansion
            )
        }
      case t if t =:= TypeRepr.of[EmptyTuple] => Nil
      case _ =>
        report.errorAndAbort(
          s"Invalid label tuple structure: ${tpe.show}. " +
            "This may indicate an incompatible case class or tuple definition.",
          Position.ofMacroExpansion
        )
    }
    loop(TypeRepr.of[Labels])
  }

  /** Analyzes field types to identify which fields are optional (`Option[T]`).
    *
    * This method examines each field type in a product type to determine if it's an `Option` type.
    * This information is used during validation to handle null values appropriately - null values
    * are acceptable for optional fields but trigger validation errors for required fields.
    *
    * @param q
    *   The quotes context for macro operations.
    * @tparam Elems
    *   The tuple type containing all field types.
    * @return
    *   A list of boolean flags indicating which fields are optional.
    */
  private def getIsOptionFlags[Elems <: Tuple: Type](using q: Quotes): List[Boolean] = {
    import q.reflect.*
    Type.of[Elems] match {
      case '[EmptyTuple] => Nil
      case '[h *: t] =>
        (TypeRepr.of[h] <:< TypeRepr.of[Option[Any]]) :: getIsOptionFlags[t]
    }
  }

  /** Generates a synchronous validator for product types using compile-time reflection.
    *
    * This method performs compile-time introspection of the product type, extracts field
    * information, summons appropriate validators for each field, and generates optimized
    * validation logic.
    *
    * The generated validator handles:
    *   - Field-by-field validation using appropriate validator instances
    *   - Error accumulation and proper error context annotation
    *   - Null-safety for optional vs required fields
    *
    * @param m
    *   The mirror instance for the product type being validated.
    * @param q
    *   The quotes context for macro operations.
    * @tparam T
    *   The product type for which to generate a validator.
    * @tparam Elems
    *   The tuple type containing all field types.
    * @tparam Labels
    *   The tuple type containing all field names as string literals.
    * @return
    *   An expression representing the generated `Validator[T]` instance.
    * @throws Compilation
    *   error if required validator instances cannot be found for any field type.
    */
  def deriveSyncValidatorImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[Validator[T]] = {
    import q.reflect.*

    val fieldLabels: List[String] = getLabels[Labels]
    val isOptionList: List[Boolean] = getIsOptionFlags[Elems]
    val fieldLabelsExpr: Expr[List[String]] = Expr(fieldLabels)
    val isOptionListExpr: Expr[List[Boolean]] = Expr(isOptionList)

    def summonValidators[E <: Tuple: Type]: List[Expr[Validator[Any]]] =
      Type.of[E] match {
        case '[EmptyTuple] => Nil
        case '[h *: t] =>
          val validatorExpr = Expr.summon[Validator[h]].getOrElse {
            report.errorAndAbort(
              s"Cannot derive Validator for ${Type.show[T]}: missing validator for field type ${Type.show[h]}. " +
                "Please provide a given instance of Validator[${Type.show[h]}].",
              Position.ofMacroExpansion
            )
          }
          '{ MacroHelper.upcastTo[Validator[Any]](${ validatorExpr }) } :: summonValidators[t]
      }

    val fieldValidators: List[Expr[Validator[Any]]] = summonValidators[Elems]
    val validatorsExpr: Expr[Seq[Validator[Any]]] = Expr.ofSeq(fieldValidators)

    '{
      new Validator[T] {
        def validate(a: T): ValidationResult[T] = {
          a match {
            case product: Product =>
              val validators = ${ validatorsExpr }
              val labels = ${ fieldLabelsExpr }
              val isOptionFlags = ${ isOptionListExpr }

              val results = validateProduct(
                product,
                validators,
                labels,
                isOptionFlags,
                validateAndAnnotate = (v, fv, l) => annotateErrors(v.validate(fv), l, fv),
                handleNull = l =>
                  ValidationResult.invalid(
                    ValidationError(
                      s"Field '$l' must not be null.",
                      List(l),
                      expected = Some("non-null value"),
                      actual = Some("null")
                    )
                  )
              )

              processResults(results, ${ m })
          }
        }
      }
    }
  }

  /** Generates an asynchronous validator for product types using compile-time reflection.
    *
    * This method performs compile-time introspection of the product type, extracts field
    * information, summons appropriate validators for each field, and generates optimized
    * async validation logic.
    *
    * The generated validator handles:
    *   - Field-by-field validation using appropriate validator instances
    *   - Error accumulation and proper error context annotation
    *   - Null-safety for optional vs required fields
    *   - Automatic lifting of synchronous validators in async contexts
    *   - Exception handling for asynchronous operations
    *
    * @param m
    *   The mirror instance for the product type being validated.
    * @param q
    *   The quotes context for macro operations.
    * @tparam T
    *   The product type for which to generate a validator.
    * @tparam Elems
    *   The tuple type containing all field types.
    * @tparam Labels
    *   The tuple type containing all field names as string literals.
    * @return
    *   An expression representing the generated `AsyncValidator[T]` instance.
    * @throws Compilation
    *   error if required validator instances cannot be found for any field type.
    */
  def deriveAsyncValidatorImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[AsyncValidator[T]] = {
    import q.reflect.*

    val fieldLabels: List[String] = getLabels[Labels]
    val isOptionList: List[Boolean] = getIsOptionFlags[Elems]
    val fieldLabelsExpr: Expr[List[String]] = Expr(fieldLabels)
    val isOptionListExpr: Expr[List[Boolean]] = Expr(isOptionList)

    def summonAsyncOrSync[E <: Tuple: Type]: List[Expr[AsyncValidator[Any]]] =
      Type.of[E] match {
        case '[EmptyTuple] => Nil
        case '[h *: t] =>
          val validatorExpr = Expr.summon[AsyncValidator[h]].orElse(Expr.summon[Validator[h]]).getOrElse {
            report.errorAndAbort(
              s"Cannot derive AsyncValidator for ${Type.show[T]}: missing validator for field type ${Type.show[h]}. " +
                "Please provide a given instance of either Validator[${Type.show[h]}] or AsyncValidator[${Type.show[h]}].",
              Position.ofMacroExpansion
            )
          }

          val finalExpr = validatorExpr.asTerm.tpe.asType match {
            case '[AsyncValidator[h]] => validatorExpr
            case '[Validator[h]] => '{ AsyncValidator.fromSync(${ validatorExpr.asExprOf[Validator[h]] }) }
          }

          '{ MacroHelper.upcastTo[AsyncValidator[Any]](${ finalExpr }) } :: summonAsyncOrSync[t]
      }

    val fieldValidators: List[Expr[AsyncValidator[Any]]] = summonAsyncOrSync[Elems]
    val validatorsExpr: Expr[Seq[AsyncValidator[Any]]] = Expr.ofSeq(fieldValidators)

    '{
      new AsyncValidator[T] {
        def validateAsync(a: T)(using ec: ExecutionContext): Future[ValidationResult[T]] = {
          a match {
            case product: Product =>
              val validators = ${ validatorsExpr }
              val labels = ${ fieldLabelsExpr }
              val isOptionFlags = ${ isOptionListExpr }

              val fieldResultsF = validateProduct(
                product,
                validators,
                labels,
                isOptionFlags,
                validateAndAnnotate = (v, fv, l) => v.validateAsync(fv).map(annotateErrors(_, l, fv)),
                handleNull = l =>
                  Future.successful(
                    ValidationResult.invalid(
                      ValidationError(
                        s"Field '$l' must not be null.",
                        List(l),
                        expected = Some("non-null value"),
                        actual = Some("null")
                      )
                    )
                  )
              )

              val allResultsF: Future[List[ValidationResult[Any]]] =
                Future.sequence(fieldResultsF.map { f =>
                  f.recover { case scala.util.control.NonFatal(ex) =>
                    ValidationResult.invalid(
                      ValidationError(s"Asynchronous validation failed unexpectedly: ${ex.getMessage}")
                    )
                  }
                })

              allResultsF.map(processResults(_, ${ m }))
          }
        }
      }
    }
  }
}
