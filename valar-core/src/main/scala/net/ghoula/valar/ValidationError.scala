package net.ghoula.valar

import scala.util.control.NoStackTrace

/** Defines the public API for validation errors and related exceptions. */
object ValidationErrors {

  /** Represents a structured validation error with detailed context information.
    *
    * `ValidationError` provides comprehensive error reporting including field paths, expected vs.
    * actual values, optional error codes, severity levels, and support for nested error
    * hierarchies. This enables both human-readable error messages and machine-processable error
    * handling.
    *
    * '''Implementation Note''': This is an opaque type wrapping [[InternalValidationError]] to
    * ensure API stability. Users cannot directly construct or pattern match on the internal
    * representation - instead, use the factory methods in the companion object and the extension
    * methods for access.
    *
    * '''Error Hierarchies''': Errors can contain child errors, making it possible to represent
    * complex validation failures in nested data structures while preserving the full context of
    * where each failure occurred.
    *
    * @example
    *   {{{ // Create a simple validation error val error = ValidationError( message = "Value must
    *   be positive", expected = Some("> 0"), actual = Some("-5") )
    *
    * // Create a nested error for a field val fieldError = ValidationError.nestField("age",
    * Vector(error))
    *
    * // Access error details println(error.message) // "Value must be positive"
    * println(error.prettyPrint()) // Multi-line formatted output }}}
    */
  opaque type ValidationError = InternalValidationError

  object ValidationError {

    /** Creates a new `ValidationError` with the specified details.
      *
      * @param message
      *   Human-readable description of the validation failure
      * @param fieldPath
      *   Path to the field in nested structures (e.g., List("user", "address", "street"))
      * @param children
      *   Nested validation errors from child fields or elements
      * @param code
      *   Optional application-specific error code for programmatic handling
      * @param severity
      *   Optional severity level (e.g., "Error", "Warning")
      * @param expected
      *   Description of what was expected (e.g., "positive number", "valid email format")
      * @param actual
      *   Description of the actual problematic value
      * @return
      *   A new `ValidationError` instance
      */
    def apply(
      message: String,
      fieldPath: List[String] = Nil,
      children: Vector[ValidationError] = Vector.empty,
      code: Option[String] = None,
      severity: Option[String] = None,
      expected: Option[String] = None,
      actual: Option[String] = None
    ): ValidationError =
      InternalValidationError(message, fieldPath, children, code, severity, expected, actual)

    /** Creates a field-level error that wraps child validation errors.
      *
      * This is typically used when validating nested structures to indicate which field contained
      * the validation failures. The field name becomes part of the error path.
      *
      * @param field
      *   Name of the field that failed validation
      * @param errors
      *   Child errors that occurred within this field
      * @return
      *   A `ValidationError` representing the field-level failure
      */
    def nestField(field: String, errors: Vector[ValidationError]): ValidationError =
      InternalValidationError.nestField(field, errors.map(_.internal))

    /** Creates a validation error for union type failures.
      *
      * Used when a value doesn't match any of the expected types in a union type validation.
      * Provides a clear summary of what types were expected.
      *
      * @param value
      *   The value that failed validation
      * @param types
      *   The expected types (as strings)
      * @return
      *   A `ValidationError` describing the union type failure
      */
    def unionError(value: Any, types: String*): ValidationError = {
      val expected = types.mkString(" | ")
      ValidationError(
        message = s"Value is not one of the expected types: $expected",
        expected = Some(expected),
        actual = Some(value.toString)
      )
    }
  }

  /** Extension methods providing the public API for `ValidationError`.
    *
    * These methods allow access to error details and provide various formatting options while
    * maintaining the opacity of the underlying implementation.
    */
  extension (ve: ValidationError) {

    /** Provides access to the internal representation (restricted to library internals). */
    private[valar] def internal: InternalValidationError = ve

    /** Returns a compact, single-line string representation of the error. */
    def show: String = internal.show

    /** Returns a formatted, multi-line string representation with indentation.
      *
      * @param indent
      *   Number of spaces to indent (useful for nested display)
      * @return
      *   Pretty-printed error with child errors indented
      */
    def prettyPrint(indent: Int = 0): String = internal.prettyPrint(indent)

    /** Adds field context to an existing error.
      *
      * This method is used to annotate errors with information about which field they occurred in
      * and what type that field was expected to be. The field name is prepended to the existing
      * field path.
      *
      * @param field
      *   The name of the field
      * @param fieldType
      *   The expected type of the field
      * @return
      *   A new `ValidationError` with updated context
      */
    def annotateField(field: String, fieldType: String): ValidationError = {
      val internalError = ve.internal
      InternalValidationError(
        message = s"Invalid field: $field, field type: $fieldType: ${internalError.message}",
        fieldPath = field :: internalError.fieldPath,
        children = internalError.children,
        code = internalError.code,
        severity = internalError.severity,
        expected = internalError.expected,
        actual = internalError.actual
      )
    }

    /** Accessor methods for retrieving error properties.
      *
      * These methods provide read-only access to the various components of a validation error,
      * allowing inspection of error details for logging, debugging, or programmatic error handling.
      */
    def message: String = internal.message
    def fieldPath: List[String] = internal.fieldPath
    def children: Vector[ValidationError] = internal.children.map(identity)
    def code: Option[String] = internal.code
    def severity: Option[String] = internal.severity
    def expected: Option[String] = internal.expected
    def actual: Option[String] = internal.actual
  }

  /** Exception that wraps a `ValidationError` for integration with exception-based error handling.
    *
    * This exception uses `NoStackTrace` for performance, as validation errors typically don't need
    * stack traces - the `ValidationError` itself contains the relevant context.
    *
    * @param error
    *   The underlying validation error
    */
  final class ValidationException(val error: ValidationError) extends Exception(error.show) with NoStackTrace {

    /** Retrieves the underlying `ValidationError`.
      *
      * @return
      *   The wrapped validation error
      */
    def getValidationError: ValidationError = error
  }
}

/** Internal representation of a validation error.
  *
  * This case class is hidden behind the opaque `ValidationError` type to ensure API stability.
  * Users should not interact with this class directly.
  */
private[valar] final case class InternalValidationError(
  message: String,
  fieldPath: List[String] = Nil,
  children: Vector[InternalValidationError] = Vector.empty,
  code: Option[String] = None,
  severity: Option[String] = None,
  expected: Option[String] = None,
  actual: Option[String] = None
) {

  /** Returns a copy with the field name prepended to the path. */
  def withField(field: String): InternalValidationError = copy(fieldPath = field :: fieldPath)

  /** Returns a copy with additional nested child errors. */
  def nest(childErrors: Vector[InternalValidationError]): InternalValidationError =
    copy(children = children ++ childErrors)

  /** Formats optional metadata (code, severity, expected, actual) for display. */
  private def formatExtras: String = {
    List(
      code.map(c => s"[$c]"),
      severity.map(s => s"<$s>"),
      expected.map(e => s"(expected: $e)"),
      actual.map(a => s"(got: $a)")
    ).flatten.mkString(" ")
  }

  /** Formats the field path for display.
    *
    * @param separator
    *   String to append after the path (e.g., ": " or " -> ")
    * @return
    *   Formatted field path or empty string if no path
    */
  private def formatFieldPath(separator: String): String = {
    if (fieldPath.isEmpty) "" else s"${fieldPath.reverse.mkString(".")}$separator"
  }

  /** Returns a compact, single-line representation. */
  def show: String = {
    val path = formatFieldPath(": ")
    val extras = formatExtras
    val childMessages = if (children.nonEmpty) children.map(_.show).mkString("\n  ", "\n  ", "") else ""
    s"$path$message $extras$childMessages".trim
  }

  /** Returns a formatted, multi-line representation with indentation. */
  def prettyPrint(indent: Int = 0): String = {
    val pad = " " * indent
    val fieldPrefix = formatFieldPath(": ")
    val extras = formatExtras
    val baseLine = s"$pad$fieldPrefix$message $extras".trim
    if (children.isEmpty) baseLine
    else {
      val childLines = children.map(_.prettyPrint(indent + 2)).mkString("\n")
      s"$baseLine\n$childLines"
    }
  }
}

/** Companion object for `InternalValidationError`. */
private[valar] object InternalValidationError {

  /** Creates a field-level error with nested children.
    *
    * @param field
    *   The field name
    * @param errors
    *   Child errors within this field
    * @return
    *   A field-level error containing the children
    */
  def nestField(field: String, errors: Vector[InternalValidationError]): InternalValidationError =
    InternalValidationError(s"Invalid field: $field", fieldPath = List(field), children = errors)
}
