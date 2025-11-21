package net.ghoula.valar.internal

import scala.concurrent.{ExecutionContext, Future}
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.{AsyncValidator, ValidationResult, Validator}

/** Represents a missing validator discovered during compile-time validation.
  *
  * @param fieldName
  *   The name of the field missing a validator.
  * @param fieldType
  *   A human-readable representation of the field's type.
  * @param suggestion
  *   A helpful suggestion for how to fix the issue.
  */
private[internal] case class MissingValidator(
  fieldName: String,
  fieldType: String,
  suggestion: String
)

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

  /** Extracts field names from a compile-time tuple of string literal types.
    *
    * Uses Scala 3.7.4's type-level pattern matching to extract string literals from the
    * `Mirror.MirroredElemLabels` tuple type. Each element is a singleton string type that gets
    * extracted to a runtime value.
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

    // Type-level extraction using pattern matching on quoted types
    // More idiomatic Scala 3 approach than manual TypeRepr traversal
    def extract[L <: Tuple: Type]: List[String] = Type.of[L] match {
      case '[EmptyTuple] => Nil
      case '[label *: rest] =>
        // Extract the singleton string type's value using constValue pattern
        Type.of[label] match {
          case '[l] =>
            TypeRepr.of[l] match {
              case ConstantType(StringConstant(s)) => s :: extract[rest]
              case other =>
                report.errorAndAbort(
                  s"Invalid field label type: expected string literal, found ${other.show}. " +
                    "This typically indicates a structural issue with the case class definition.",
                  Position.ofMacroExpansion
                )
            }
        }
    }

    extract[Labels]
  }

  /** Validates that all required validators are available at compile time.
    *
    * This method performs upfront validation of all field types before code generation, collecting
    * ALL missing validators rather than failing on the first one. This provides better developer
    * experience by showing all issues at once.
    *
    * @param q
    *   The quotes context for macro operations.
    * @tparam T
    *   The product type being validated.
    * @tparam Elems
    *   The tuple type containing all field types.
    * @param labels
    *   The list of field names.
    * @param isAsync
    *   Whether this is for async validation (affects error message).
    */
  private def validateAllFieldsHaveValidators[T: Type, Elems <: Tuple: Type](
    labels: List[String],
    isAsync: Boolean
  )(using q: Quotes): Unit = {
    import q.reflect.*

    def collectMissing[E <: Tuple: Type](
      remainingLabels: List[String],
      acc: List[MissingValidator]
    ): List[MissingValidator] =
      Type.of[E] match {
        case '[EmptyTuple] => acc.reverse
        case '[h *: t] =>
          val label = remainingLabels.head
          val fieldTypeStr = Type.show[h]

          // For async, we accept either AsyncValidator or Validator
          val hasValidator = if (isAsync) {
            Expr.summon[AsyncValidator[h]].isDefined || Expr.summon[Validator[h]].isDefined
          } else {
            Expr.summon[Validator[h]].isDefined
          }

          val newAcc = if (hasValidator) acc
          else {
            val suggestion = if (isAsync) {
              s"given Validator[$fieldTypeStr] = ... or given AsyncValidator[$fieldTypeStr] = ..."
            } else {
              s"given Validator[$fieldTypeStr] = ..."
            }
            MissingValidator(label, fieldTypeStr, suggestion) :: acc
          }

          collectMissing[t](remainingLabels.tail, newAcc)
      }

    val missing = collectMissing[Elems](labels, Nil)

    if (missing.nonEmpty) {
      val validatorType = if (isAsync) "AsyncValidator" else "Validator"
      val header = s"Cannot derive $validatorType for ${Type.show[T]}: missing validators for ${missing.length} field(s).\n"

      val details = missing.zipWithIndex.map { case (m, i) =>
        s"  ${i + 1}. Field '${m.fieldName}' of type ${m.fieldType}\n" +
          s"     Add: ${m.suggestion}"
      }.mkString("\n\n")

      val footer = "\n\nHint: Valar provides built-in validators for common types (Int, String, Option, etc.).\n" +
        "For custom types, either derive them with `Validator.derived` or provide explicit instances."

      report.errorAndAbort(header + "\n" + details + footer, Position.ofMacroExpansion)
    }
  }

  /** Generates a synchronous validator for product types using compile-time reflection.
    *
    * This method performs compile-time introspection of the product type, extracts field
    * information, summons appropriate validators for each field, and generates optimized validation
    * logic.
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

    // Compile-time validation: check ALL fields have validators before generating code
    // This provides better error messages by reporting all missing validators at once
    validateAllFieldsHaveValidators[T, Elems](fieldLabels, isAsync = false)

    // Type detection strategy:
    // 1. Regular tuples (Tuple2, etc.): T <:< Tuple, use _1, _2 accessors - zero cast
    // 2. Case classes: have actual field members, use field name accessor - zero cast
    // 3. Named tuples: NOT <:< Tuple, no field members, use productElement - requires cast
    //    (This matches Scala 3.7.4 stdlib pattern: NamedTuple.apply uses asInstanceOf)
    val isRegularTuple = TypeRepr.of[T] <:< TypeRepr.of[Tuple]

    // Generate validation expression for a single field
    // Field access strategy depends on type:
    // - Regular tuples: Select.unique(a, "_1") - zero cast
    // - Case classes: Select.unique(a, "fieldName") - zero cast
    // - Named tuples: productElement(index).asInstanceOf[H] - matches stdlib
    def generateFieldValidation[H: Type](
      aExpr: Expr[T],
      label: String,
      index: Int,
      isOption: Boolean,
      validatorExpr: Expr[Validator[H]]
    ): Expr[ValidationResult[Any]] = {
      val labelExpr = Expr(label)

      // Determine field access method based on type structure
      val fieldAccess: Expr[H] = if (isRegularTuple) {
        // Regular tuple: use _1, _2, etc. - typed accessors, zero cast
        Select.unique(aExpr.asTerm, s"_${index + 1}").asExprOf[H]
      } else {
        // Check if type has actual field member (case classes do, named tuples don't)
        val typeSymbol = TypeRepr.of[T].typeSymbol
        val hasFieldMember = typeSymbol.fieldMember(label) != Symbol.noSymbol

        if (hasFieldMember) {
          // Case class: direct field access - zero cast
          Select.unique(aExpr.asTerm, label).asExprOf[H]
        } else {
          // Named tuple: use productElement (matches Scala 3.7.4 stdlib pattern)
          // See: scala.NamedTuple.apply uses asInstanceOf for element access
          val indexExpr = Expr(index)
          '{ $aExpr.asInstanceOf[Product].productElement($indexExpr).asInstanceOf[H] } // scalafix:ok DisableSyntax.asInstanceOf
        }
      }

      if (isOption) {
        // Option fields: null is valid (will be None), just validate
        '{
          val fieldValue: H = $fieldAccess
          val result = $validatorExpr.validate(fieldValue)
          annotateErrors(result, $labelExpr, fieldValue)
        }
      } else {
        // Required fields: null triggers error
        '{
          val fieldValue: H = $fieldAccess
          if (fieldValue == null) { // scalafix:ok DisableSyntax.null
            ValidationResult.invalid(
              ValidationError(
                s"Field '${$labelExpr}' must not be null.",
                List($labelExpr),
                expected = Some("non-null value"),
                actual = Some("null")
              )
            )
          } else {
            val result = $validatorExpr.validate(fieldValue)
            annotateErrors(result, $labelExpr, fieldValue)
          }
        }
      }
    }

    // Generate all field validations at compile time
    // Uses type-level IsOption[H] match type for compile-time Option detection
    def generateAllValidations[E <: Tuple: Type](
      aExpr: Expr[T],
      index: Int,
      labels: List[String]
    ): List[Expr[ValidationResult[Any]]] =
      Type.of[E] match {
        case '[EmptyTuple] => Nil
        case '[h *: t] =>
          val label = labels.head

          // Type-level Option detection using quotes reflection
          // Checks if h <:< Option[?] at macro expansion time
          val isOption: Boolean = TypeRepr.of[h] <:< TypeRepr.of[Option[?]]

          // Safe to use .get - upfront validation guarantees validators exist
          val validatorExpr = Expr.summon[Validator[h]].get

          val fieldValidation = generateFieldValidation[h](aExpr, label, index, isOption, validatorExpr)
          fieldValidation :: generateAllValidations[t](aExpr, index + 1, labels.tail)
      }

    '{
      new Validator[T] {
        def validate(a: T): ValidationResult[T] = {
          val results: List[ValidationResult[Any]] = ${
            val aExpr = 'a
            Expr.ofList(generateAllValidations[Elems](aExpr, 0, fieldLabels))
          }
          processResults(results, $m)
        }
      }
    }
  }

  /** Generates an asynchronous validator for product types using compile-time reflection.
    *
    * This method performs compile-time introspection of the product type, extracts field
    * information, summons appropriate validators for each field, and generates optimized async
    * validation logic.
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

    // Compile-time validation: check ALL fields have validators before generating code
    validateAllFieldsHaveValidators[T, Elems](fieldLabels, isAsync = true)

    // Type detection strategy (same as sync version):
    // 1. Regular tuples: T <:< Tuple, use _1, _2 accessors - zero cast
    // 2. Case classes: have actual field members, use field name accessor - zero cast
    // 3. Named tuples: NOT <:< Tuple, no field members, use productElement - requires cast
    val isRegularTuple = TypeRepr.of[T] <:< TypeRepr.of[Tuple]

    // Generate async validation expression for a single field
    // Field access strategy depends on type structure
    def generateAsyncFieldValidation[H: Type](
      aExpr: Expr[T],
      label: String,
      index: Int,
      isOption: Boolean,
      asyncValidatorExpr: Expr[AsyncValidator[H]]
    ): Expr[ExecutionContext => Future[ValidationResult[Any]]] = {
      val labelExpr = Expr(label)

      // Determine field access method based on type structure
      val fieldAccess: Expr[H] = if (isRegularTuple) {
        // Regular tuple: use _1, _2, etc. - typed accessors, zero cast
        Select.unique(aExpr.asTerm, s"_${index + 1}").asExprOf[H]
      } else {
        // Check if type has actual field member (case classes do, named tuples don't)
        val typeSymbol = TypeRepr.of[T].typeSymbol
        val hasFieldMember = typeSymbol.fieldMember(label) != Symbol.noSymbol

        if (hasFieldMember) {
          // Case class: direct field access - zero cast
          Select.unique(aExpr.asTerm, label).asExprOf[H]
        } else {
          // Named tuple: use productElement (matches Scala 3.7.4 stdlib pattern)
          val indexExpr = Expr(index)
          '{ $aExpr.asInstanceOf[Product].productElement($indexExpr).asInstanceOf[H] } // scalafix:ok DisableSyntax.asInstanceOf
        }
      }

      if (isOption) {
        // Option fields: null is valid (will be None), just validate
        '{ (ec: ExecutionContext) =>
          given ExecutionContext = ec
          val fieldValue: H = $fieldAccess
          $asyncValidatorExpr.validateAsync(fieldValue)
            .map(result => annotateErrors(result, $labelExpr, fieldValue))
            .recover { case scala.util.control.NonFatal(ex) =>
              ValidationResult.invalid(
                ValidationError(s"Asynchronous validation failed unexpectedly: ${ex.getMessage}")
              )
            }
        }
      } else {
        // Required fields: null triggers error
        '{ (ec: ExecutionContext) =>
          given ExecutionContext = ec
          val fieldValue: H = $fieldAccess
          if (fieldValue == null) { // scalafix:ok DisableSyntax.null
            Future.successful(
              ValidationResult.invalid(
                ValidationError(
                  s"Field '${$labelExpr}' must not be null.",
                  List($labelExpr),
                  expected = Some("non-null value"),
                  actual = Some("null")
                )
              )
            )
          } else {
            $asyncValidatorExpr.validateAsync(fieldValue)
              .map(result => annotateErrors(result, $labelExpr, fieldValue))
              .recover { case scala.util.control.NonFatal(ex) =>
                ValidationResult.invalid(
                  ValidationError(s"Asynchronous validation failed unexpectedly: ${ex.getMessage}")
                )
              }
          }
        }
      }
    }

    // Generate all async field validations at compile time
    // Uses type-level IsOption[H] match type for compile-time Option detection
    def generateAllAsyncValidations[E <: Tuple: Type](
      aExpr: Expr[T],
      index: Int,
      labels: List[String]
    ): List[Expr[ExecutionContext => Future[ValidationResult[Any]]]] =
      Type.of[E] match {
        case '[EmptyTuple] => Nil
        case '[h *: t] =>
          val label = labels.head

          // Type-level Option detection using quotes reflection
          // Checks if h <:< Option[?] at macro expansion time
          val isOption: Boolean = TypeRepr.of[h] <:< TypeRepr.of[Option[?]]

          // Safe to use .get - upfront validation guarantees validators exist
          // Try AsyncValidator first, fall back to Validator
          val validatorExpr = Expr.summon[AsyncValidator[h]].orElse(Expr.summon[Validator[h]]).get

          // Convert sync validator to async if needed (compile-time type witness, no runtime cast)
          val asyncValidatorExpr: Expr[AsyncValidator[h]] = validatorExpr.asTerm.tpe.asType match {
            case '[AsyncValidator[`h`]] => validatorExpr.asExprOf[AsyncValidator[h]]
            case '[Validator[`h`]] => '{ AsyncValidator.fromSync(${ validatorExpr.asExprOf[Validator[h]] }) }
          }

          val fieldValidation = generateAsyncFieldValidation[h](aExpr, label, index, isOption, asyncValidatorExpr)
          fieldValidation :: generateAllAsyncValidations[t](aExpr, index + 1, labels.tail)
      }

    '{
      new AsyncValidator[T] {
        def validateAsync(a: T)(using ec: ExecutionContext): Future[ValidationResult[T]] = {
          val validations: List[ExecutionContext => Future[ValidationResult[Any]]] = ${
            val aExpr = 'a
            Expr.ofList(generateAllAsyncValidations[Elems](aExpr, 0, fieldLabels))
          }
          val futureResults = validations.map(_(ec))
          Future.sequence(futureResults).map(processResults(_, $m))
        }
      }
    }
  }
}
