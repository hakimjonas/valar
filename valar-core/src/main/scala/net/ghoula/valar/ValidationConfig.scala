package net.ghoula.valar

/** Configuration for validation behavior, particularly for security and performance limits.
  *
  * This configuration allows you to set global limits on validation operations to prevent security
  * vulnerabilities and performance issues when processing untrusted input.
  *
  * ==Security Considerations==
  *
  * When validating data from untrusted sources (e.g., user input, external APIs), it's critical to
  * set appropriate limits to prevent:
  *   - '''Memory exhaustion:''' Validating extremely large collections can consume excessive memory
  *   - '''CPU exhaustion:''' Processing millions of elements can cause application hang
  *   - '''Stack overflow:''' Deeply nested structures can exhaust the call stack
  *
  * @example
  *   {{{
  * // For trusted, internal data - no limits
  * given ValidationConfig = ValidationConfig.default
  *
  * // For untrusted user input - strict limits
  * given ValidationConfig = ValidationConfig(
  *   maxCollectionSize = Some(1000),
  *   maxNestingDepth = Some(10)
  * )
  *
  * // Validate with limits in scope
  * val result = Validator[UserData].validate(untrustedInput)
  *   }}}
  *
  * @param maxCollectionSize
  *   Maximum number of elements allowed in a collection (List, Vector, Set, Map, etc.). If a
  *   collection exceeds this size, validation fails immediately without processing elements. `None`
  *   means unlimited (use with caution for untrusted input).
  * @param maxNestingDepth
  *   Maximum nesting depth for product types (case classes, tuples). This prevents stack overflow
  *   from deeply nested structures. `None` means unlimited. Currently not enforced but reserved for
  *   future use.
  */
final case class ValidationConfig(
  maxCollectionSize: Option[Int] = None,
  maxNestingDepth: Option[Int] = None
) {

  /** Validates that a collection size is within the configured limit.
    *
    * @param size
    *   The size of the collection to check
    * @param collectionType
    *   Description of the collection type for error messages
    * @return
    *   Valid(size) if within limits, or Invalid with a security-focused error
    */
  def checkCollectionSize(size: Int, collectionType: String): ValidationResult[Int] = {
    maxCollectionSize match {
      case Some(max) if size > max =>
        ValidationResult.invalid(
          ValidationErrors.ValidationError(
            message =
              s"$collectionType size ($size) exceeds maximum allowed size ($max). This limit protects against memory exhaustion attacks.",
            code = Some("validation.security.collection_too_large"),
            severity = Some("Error"),
            expected = Some(s"size <= $max"),
            actual = Some(size.toString)
          )
        )
      case _ => ValidationResult.Valid(size)
    }
  }
}

object ValidationConfig {

  /** Default configuration with no limits.
    *
    * '''Warning:''' This configuration is suitable for '''trusted input only'''. For untrusted user
    * input, use [[ValidationConfig.strict]] or define custom limits.
    */
  inline given default: ValidationConfig = ValidationConfig()

  /** Strict configuration suitable for untrusted user input.
    *
    * Limits:
    *   - Maximum 10,000 elements in any collection
    *   - Maximum nesting depth of 20 levels
    *
    * These limits balance security with typical use cases. Adjust as needed for your application.
    */
  private[valar] def strict: ValidationConfig = ValidationConfig(
    maxCollectionSize = Some(10000),
    maxNestingDepth = Some(20)
  )

  /** Permissive configuration for internal, trusted data with higher limits.
    *
    * Limits:
    *   - Maximum 1,000,000 elements in any collection
    *   - Maximum nesting depth of 100 levels
    */
  def permissive: ValidationConfig = ValidationConfig(
    maxCollectionSize = Some(1000000),
    maxNestingDepth = Some(100)
  )
}
