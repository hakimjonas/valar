# valar-munit

The `valar-munit` module provides testing utilities for Valar validation logic using the [MUnit](https://scalameta.org/munit/) testing framework. It introduces the `ValarSuite` trait that extends MUnit's `FunSuite` with specialized assertion helpers for validation results.

## Usage

Add the `valar-munit` dependency to your build and extend the `ValarSuite` trait in your test classes:

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

The `ValarSuite` trait provides three main assertion helpers:

### 1. `assertValid`

Asserts that a `ValidationResult` is `Valid` and returns the validated value for further assertions:

```scala
test("valid data passes validation") {
  val result = MyValidator.validate(validData)
  val value = assertValid(result)
  
  // Additional assertions on the validated value
  assertEquals(value.id, 123)
}
```

You can also provide a custom clue message:

```scala
assertValid(result, "User validation should succeed with valid data")
```

### 2. `assertHasOneError`

Asserts that a `ValidationResult` is `Invalid` and contains exactly one error. This is ideal for testing individual validation rules:

```scala
test("empty name is rejected") {
  val result = User.validate(User("", 25))
  
  assertHasOneError(result) {
    case ValidationError("user.name.empty", _, _) => // Success
    case other => fail(s"Expected name.empty error but got: $other")
  }
}
```

### 3. `assertInvalid`

Asserts that a `ValidationResult` is `Invalid`. Use this for complex cases where multiple, accumulated errors are expected:

```scala
test("multiple validation errors are accumulated") {
  val result = User.validate(User("", -5))
  
  assertInvalid(result) { errors =>
    assertEquals(errors.size, 2)
    assert(errors.exists(_.key.contains("name.empty")))
    assert(errors.exists(_.key.contains("age.negative")))
  }
}
```

## Benefits

The `ValarSuite` trait provides several benefits for testing validation logic:

1. **Cleaner Tests**: Specialized assertions make validation tests more concise and readable.
2. **Better Error Messages**: When assertions fail, you get detailed error reports with pretty-printed validation errors.
3. **Type Safety**: The assertion helpers maintain type information, allowing for chained assertions on the validated value.