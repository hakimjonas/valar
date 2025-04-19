package net.ghoula.valar

import scala.util.control.NoStackTrace

/** Defines the public API for validation errors and related exceptions. */
object ValidationErrors {

  /** Public opaque type for validation errors. Internally represented as
    * [[InternalValidationError]]. This prevents users from directly constructing or pattern
    * matching on the internal representation, ensuring API stability. Use the methods on the
    * companion object or extensions to interact with errors.
    */
  opaque type ValidationError = InternalValidationError

  object ValidationError {

    /** Constructs a new `ValidationError`. Use this factory method to create base errors. */
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

    /** Constructs a nested error for a specific field, wrapping existing errors as children. */
    def nestField(field: String, errors: Vector[ValidationError]): ValidationError =
      InternalValidationError.nestField(field, errors.map(_.internal))

    /** Creates a simple error message for a failed union type validation. */
    def unionError(value: Any, types: String*): ValidationError = {
      val expected = types.mkString(" | ")
      ValidationError(
        message = s"Value is not one of the expected types: $expected",
        expected = Some(expected),
        actual = Some(value.toString)
      )
    }
  }

  /** Extension methods for the opaque `ValidationError` type, providing the public API for
    * accessing error details and formatting.
    */
  extension (ve: ValidationError) {

    /** Accesses the internal representation. Restricted to the library package `ghoula.net.valar`.
      */
    private[valar] def internal: InternalValidationError = ve

    /** Provides a compact, single-line string representation. */
    def show: String = internal.show

    /** Provides a multi-line, indented string representation. */
    def prettyPrint(indent: Int = 0): String = internal.prettyPrint(indent)

    /** Annotates an existing error with the context of the field it belongs to. */
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

    def message: String = internal.message
    def fieldPath: List[String] = internal.fieldPath
    def children: Vector[ValidationError] =
      internal.children.map(identity)
    def code: Option[String] = internal.code
    def severity: Option[String] = internal.severity
    def expected: Option[String] = internal.expected
    def actual: Option[String] = internal.actual
  }

  /** An Exception specifically for wrapping a [[ValidationError]]. Uses NoStackTrace. */
  final class ValidationException(val error: ValidationError) extends Exception(error.show) with NoStackTrace {

    /** Retrieves the underlying [[ValidationError]]. */
    def getValidationError: ValidationError = error
  }
}

/** Internal representation of a validation error, hidden by the opaque type
  * [[ValidationErrors.ValidationError]].
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

  /** Compact, single-line representation. */
  def show: String = { /* ... implementation unchanged ... */
    val path = if (fieldPath.isEmpty) "" else s"${fieldPath.reverse.mkString(".")}: "
    val extras = List(
      code.map(c => s"[$c]"),
      severity.map(s => s"<$s>"),
      expected.map(e => s"(expected: $e)"),
      actual.map(a => s"(got: $a)")
    ).flatten.mkString(" ")
    val childMessages = if (children.nonEmpty) children.map(_.show).mkString("\n  ", "\n  ", "") else ""
    s"$path$message $extras$childMessages".trim
  }

  /** Pretty-printed, multi-line representation with indentation. */
  def prettyPrint(indent: Int = 0): String = { /* ... implementation unchanged ... */
    val pad = " " * indent
    val fieldPrefix = if (fieldPath.isEmpty) "" else String.format("%s: ", fieldPath.reverse.mkString("."))
    val extras = List(
      code.map(c => s"[$c]"),
      severity.map(s => s"<$s>"),
      expected.map(e => s"(expected: $e)"),
      actual.map(a => s"(got: $a)")
    ).flatten.mkString(" ")
    val baseLine = s"$pad$fieldPrefix$message $extras".trim
    if (children.isEmpty) baseLine
    else {
      val childLines = children.map(_.prettyPrint(indent + 2)).mkString("\n")
      s"$baseLine\n$childLines"
    }
  }
}

/** Companion object for [[InternalValidationError]]. */
private[valar] object InternalValidationError {

  /** Creates a standard error message format for a field containing nested errors. */
  def nestField(field: String, errors: Vector[InternalValidationError]): InternalValidationError =
    InternalValidationError(s"Invalid field: $field", fieldPath = List(field), children = errors)
}
