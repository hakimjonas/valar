package net.ghoula.valar
import net.ghoula.valar.ValidationResult

/** Defines the foundational extensibility pattern for Valar.
  *
  * This typeclass represents Valar's canonical pattern for extension development. It's designed to
  * be the standard way to build integrations and extensions for the validation library. By
  * implementing this trait and providing it as a `given` instance, developers can:
  *
  *   - Extend Valar with cross-cutting concerns (logging, metrics, auditing)
  *   - Build composable extensions that work together seamlessly
  *   - Integrate with external monitoring and diagnostic systems
  *   - Create specialized behaviors without modifying validation logic
  *
  * @see
  *   [[ValidationObserver.noOpObserver]] for the default, zero-overhead implementation used when no
  *   custom observer is provided.
  *
  * ==Architectural Pattern==
  *
  * The `ValidationObserver` pattern is the recommended approach for extending Valar's capabilities.
  * By using this pattern, you benefit from:
  *
  *   - A standardized, type-safe interface for integrating with Valar
  *   - Zero-cost abstractions through the inline implementation when not used
  *   - Clean composition with other features (like the translator module)
  *   - Future compatibility with upcoming Valar modules (planned: valar-cats-effect, valar-zio)
  *
  * When implementing extensions to Valar, prefer extending this trait over creating alternative
  * patterns.
  *
  * @example
  *   Building a simple extension for validation logging:
  *   {{{
  *   import org.slf4j.LoggerFactory
  *
  *   // 1. Define your extension by implementing ValidationObserver
  *   given loggingObserver: ValidationObserver with {
  *     private val logger = LoggerFactory.getLogger("ValidationAnalytics")
  *
  *     def onResult[A](result: ValidationResult[A]): Unit = result match {
  *       case ValidationResult.Valid(_) =>
  *         logger.info("Validation succeeded.")
  *       case ValidationResult.Invalid(errors) =>
  *         logger.warn(s"Validation failed with ${errors.size} errors: ${errors.map(_.message).mkString(", ")}")
  *     }
  *   }
  *
  *   // 2. Use your extension with the standard observe() pattern
  *   val result = someValidation().observe() // The observer is automatically used
  *
  *   // 3. Extensions compose cleanly with other Valar features
  *   val processedResult = someValidation()
  *     .observe() // Trigger logging/metrics through your observer
  *     .map(transform)
  *     // Can be chained with other extensions like translator
  *   }}}
  *
  * Creating a reusable extension module:
  * {{{
  *   // Define a specialized observer for metrics collection
  *   trait MetricsObserver extends ValidationObserver {
  *     def recordMetric(name: String, value: Double): Unit
  *
  *     def onResult[A](result: ValidationResult[A]): Unit = result match {
  *       case ValidationResult.Valid(_) =>
  *         recordMetric("validation.success", 1.0)
  *       case ValidationResult.Invalid(errors) =>
  *         recordMetric("validation.failure", 1.0)
  *         recordMetric("validation.error.count", errors.size.toDouble)
  *     }
  *   }
  *
  *   // Concrete implementation for a specific metrics library
  *   given PrometheusMetricsObserver: MetricsObserver with {
  *     def recordMetric(name: String, value: Double): Unit = {
  *       // Implementation using Prometheus client
  *     }
  *   }
  * }}}
  */
trait ValidationObserver {

  /** A callback executed for each `ValidationResult` passed to the `observe` method.
    *
    * Implementations of this method can inspect the result and trigger side effects, such as
    * writing to a log, incrementing a metrics counter, or sending an alert. This method should not
    * throw exceptions.
    *
    * @tparam A
    *   The type of the value within the ValidationResult.
    * @param result
    *   The `ValidationResult` to be observed.
    */
  def onResult[A](result: ValidationResult[A]): Unit
}

object ValidationObserver {

  /** The default, "no-op" `ValidationObserver` that performs no action.
    *
    * This instance is provided as an `inline given`. This is a critical optimization feature. When
    * this default observer is in scope, the Scala compiler, in conjunction with the `inline`
    * `observe()` extension method, will perform full dead-code elimination.
    *
    * This ensures that the observability feature is truly zero-cost and has no performance overhead
    * unless a custom `ValidationObserver` is explicitly provided.
    */
  inline given noOpObserver: ValidationObserver with {
    def onResult[A](result: ValidationResult[A]): Unit = () // No operation
  }
}

extension [A](vr: ValidationResult[A]) {

  /** Applies the in-scope `ValidationObserver` to this `ValidationResult`.
    *
    * This extension method is the primary interface for the ValidationObserver extension pattern.
    * It enables side-effecting operations and extensions to be applied to a validation result
    * without altering the validation logic or flow. It returns the original result unchanged,
    * allowing for seamless method chaining with other operations.
    *
    * ===Extension Pattern Entry Point===
    *
    * This method serves as the standardized entry point for all extensions built on the
    * ValidationObserver pattern. Current and future Valar modules that follow this pattern will be
    * usable through this consistent interface.
    *
    * This method is declared `inline` to facilitate powerful compile-time optimizations. If the
    * default [[ValidationObserver.noOpObserver]] is in scope, the compiler will eliminate this
    * entire method call from the generated bytecode, ensuring zero runtime overhead.
    *
    * @param observer
    *   The `ValidationObserver` instance provided by the implicit context.
    * @return
    *   The original, unmodified `ValidationResult`, to allow for method chaining.
    * @example
    *   {{{ import net.ghoula.valar.Validator import net.ghoula.valar.ValidationResult
    *
    * def validateUsername(name: String): ValidationResult[String] = ???
    *
    * // Assuming a `given ValidationObserver` is in scope val result =
    * validateUsername("test-user") .observe() // The observer's onResult is called here
    * .map(_.toUpperCase) }}}
    */
  inline def observe()(using observer: ValidationObserver): ValidationResult[A] = {
    observer.onResult(vr)
    vr
  }
}
