# valar-munit

The valar-munit module provides testing utilities for Valar validation logic using the MUnit testing framework. It
introduces the ValarSuite trait that extends MUnit's FunSuite with specialized assertion helpers for ValidationResult.

## Installation

Add the valar-munit dependency to your build.sbt:

```scala
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.5.0" % Test
```

## Usage

Extend the ValarSuite trait in your test classes to get access to the assertion helpers.

```scala
import net.ghoula.valar.munit.ValarSuite

class MyValidatorSpec extends ValarSuite {
  test("valid data passes validation") {
    val result = MyValidator.validate(validData)
    val value = assertValid(result)

    // You can make additional assertions on the validated value
    assertEquals(value.name, "Expected Name")
  }
}
```

## Assertion Helpers

The ValarSuite trait provides several assertion helpers for different validation testing scenarios.

### 1. assertValid

Asserts that a ValidationResult is Valid and returns the validated value for further assertions.

```scala
test("valid data passes validation") {
  val result = MyValidator.validate(validData)
  val value = assertValid(result)

  // Additional assertions on the validated value
  assertEquals(value.id, 123)
}
```

### 2. assertHasOneError

Asserts that a ValidationResult is Invalid and contains exactly one error. This is ideal for testing individual
validation rules.

```scala
test("empty name is rejected") {
  val result = User.validate(User("", 25))

  assertHasOneError(result) { error =>
    assertEquals(error.fieldPath, List("name"))
    assert(error.message.contains("empty"))
  }
}
```

### 3. assertHasNErrors

Asserts that a ValidationResult is Invalid and contains exactly N errors.

```scala
test("multiple specific errors are reported") {
  val result = User.validate(User("", -5))

  assertHasNErrors(result, 2) { errors =>
    // Assert on the collection of exactly 2 errors
    assert(errors.exists(_.fieldPath.contains("name")))
    assert(errors.exists(_.fieldPath.contains("age")))
  }
}
```

### 4. assertInvalid

Asserts that a ValidationResult is Invalid using a partial function. Use this for complex cases where multiple,
accumulated errors are expected.

```scala
test("multiple validation errors are accumulated") {
  val result = User.validate(User("", -5))

  assertInvalid(result) {
    case errors if errors.size == 2 =>
      assert(errors.exists(_.fieldPath.contains("name")))
      assert(errors.exists(_.fieldPath.contains("age")))
  }
}
```

### 5. assertInvalidWith

Asserts that a ValidationResult is Invalid and allows flexible assertions on the error collection using a regular
function. This is a simpler alternative to assertInvalid.

```scala
test("validation fails with expected errors") {
  val result = User.validate(User("", -5))

  assertInvalidWith(result) { errors =>
    assertEquals(errors.size, 2)
    assert(errors.exists(_.fieldPath.contains("name")))
    assert(errors.exists(_.fieldPath.contains("age")))
  }
}
```

## Benefits

- **Comprehensive Coverage**: Assertion helpers cover all common validation testing scenarios.

- **Cleaner Tests**: Specialized assertions make validation tests more concise and readable.

- **Better Error Messages**: Failed assertions provide detailed error reports with pretty-printed validation errors.

- **Type Safety**: The assertion helpers maintain type information, allowing for chained assertions on the validated value.

- **Flexible API**: Multiple assertion styles (partial functions, regular functions, specific error counts) to match your testing preferences.
