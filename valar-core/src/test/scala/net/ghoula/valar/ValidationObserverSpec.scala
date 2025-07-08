package net.ghoula.valar

import munit.FunSuite

import scala.collection.mutable.ListBuffer

import net.ghoula.valar.ValidationErrors.ValidationError

/** Verifies the behavior of the `ValidationObserver` typeclass and its `observe` extension method.
  *
  * This spec ensures that:
  *   - The default `noOpObserver` is a transparent, zero-cost operation when no custom observer is
  *     in scope.
  *   - A custom `given` `ValidationObserver` is correctly invoked for both `Valid` and `Invalid`
  *     results.
  *   - The `observe` method faithfully returns the original `ValidationResult` to preserve method
  *     chaining.
  */
class ValidationObserverSpec extends FunSuite {

  /** A mock observer that records any results passed to its `onResult` method. */
  private class TestObserver extends ValidationObserver {
    val observedResults: ListBuffer[ValidationResult[?]] = ListBuffer()
    override def onResult[A](result: ValidationResult[A]): Unit = {
      observedResults += result
    }
  }

  test("observe should be transparent when using the default no-op observer") {
    val validResult = ValidationResult.Valid(42)
    assertEquals(validResult.observe(), validResult)

    val invalidResult = ValidationResult.invalid(ValidationError("An error"))
    assertEquals(invalidResult.observe(), invalidResult)
  }

  test("observe should invoke a custom observer for a Valid result") {
    val testObserver = new TestObserver
    given customObserver: ValidationObserver = testObserver

    val validResult = ValidationResult.Valid("success")
    val returnedResult = validResult.observe()

    assertEquals(testObserver.observedResults.size, 1)
    assertEquals(testObserver.observedResults.head, validResult)
    assertEquals(returnedResult, validResult)
  }

  test("observe should invoke a custom observer for an Invalid result") {
    val testObserver = new TestObserver
    given customObserver: ValidationObserver = testObserver

    val error = ValidationError("A critical failure")
    val invalidResult = ValidationResult.invalid(error)
    val returnedResult = invalidResult.observe()

    assertEquals(testObserver.observedResults.size, 1)
    assertEquals(testObserver.observedResults.head, invalidResult)
    assertEquals(returnedResult, invalidResult)
  }
}
