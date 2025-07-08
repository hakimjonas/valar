package net.ghoula.valar.translator

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationResult

/** A typeclass that defines how to translate a ValidationError into a human-readable string.
  * Implement this to integrate with i18n libraries.
  */
trait Translator {

  /** Translates a single validation error.
    * @param error
    *   The structured ValidationError containing the key, args, and default message.
    * @return
    *   A localized string message.
    */
  def translate(error: ValidationError): String
}

extension [A](vr: ValidationResult[A]) {

  /** Translates all errors within an Invalid result using the in-scope Translator. If the result is
    * Valid, it is returned unchanged.
    *
    * @param translator
    *   The given Translator instance.
    * @return
    *   A new ValidationResult with translated error messages.
    */
  def translateErrors()(using translator: Translator): ValidationResult[A] = {
    vr match {
      case ValidationResult.Valid(a) => ValidationResult.Valid(a)
      case ValidationResult.Invalid(errors) =>
        val translatedErrors = errors.map { err =>
          ValidationError(
            message = translator.translate(err),
            fieldPath = err.fieldPath,
            children = err.children,
            code = err.code,
            severity = err.severity,
            expected = err.expected,
            actual = err.actual
          )
        }
        ValidationResult.Invalid(translatedErrors)
    }
  }
}
