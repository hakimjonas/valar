package net.ghoula.valar

import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import net.ghoula.valar.ValidationErrors.ValidationError

/** Provides a comprehensive test suite for the [[AsyncValidator]] typeclass and its derivation.
  *
  * This spec verifies all core functionalities of the asynchronous validation mechanism:
  *   - Successful validation of valid objects.
  *   - Correct handling of failures from synchronous validators within an async context.
  *   - Correct handling of failures from native asynchronous validators.
  *   - Proper accumulation of errors from both sync and async sources.
  *   - Correct validation of nested case classes with proper error path annotation.
  *   - Robustness against null values, optional fields, collections, and exceptions within Futures.
  */
class AsyncValidatorSpec extends FunSuite {

  /** A simple case class for basic validation tests. */
  private case class User(name: String, age: Int)

  /** A nested case class for testing recursive derivation. */
  private case class Company(name: String, owner: User)

  /** A case class for testing collection validation. */
  private case class Post(title: String, comments: List[Comment])

  /** A simple model for items within a collection. */
  private case class Comment(author: String, text: String)

  /** A case class for testing optional field validation. */
  private case class UserProfile(username: String, email: Option[String])

  /** A standard synchronous validator for non-empty strings. */
  private given syncStringValidator: Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("Sync: String must not be empty"))
  }

  /** A standard synchronous validator for non-negative integers. */
  private given syncIntValidator: Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("Sync: Age must be non-negative"))
  }

  /** A native asynchronous validator that simulates a database check for usernames.
    *
    * This validator checks if a username is reserved (e.g., "admin", "root") by simulating an
    * asynchronous database lookup. If the username is not reserved, it delegates to the synchronous
    * string validator for basic validation.
    */
  private given asyncUsernameValidator: AsyncValidator[String] with {
    def validateAsync(name: String)(using ec: concurrent.ExecutionContext): Future[ValidationResult[String]] =
      Future {
        if (name.toLowerCase == "admin" || name.toLowerCase == "root") {
          ValidationResult.invalid(ValidationError(s"Async: Username '$name' is reserved."))
        } else {
          syncStringValidator.validate(name)
        }
      }
  }

  /** A native asynchronous validator that simulates a profanity filter.
    *
    * This validator checks if a text contains profanity by simulating an asynchronous profanity
    * checking service. If no profanity is detected, it delegates to the synchronous string
    * validator for basic validation.
    */
  private given asyncCommentTextValidator: AsyncValidator[String] with {
    def validateAsync(text: String)(using ec: concurrent.ExecutionContext): Future[ValidationResult[String]] =
      Future {
        if (text.toLowerCase.contains("heck")) {
          ValidationResult.invalid(ValidationError("Async: Comment contains profanity."))
        } else {
          syncStringValidator.validate(text)
        }
      }
  }

  /** A native asynchronous validator for email formats.
    *
    * This validator performs basic email format validation by checking for the presence of an '@'
    * symbol. In a real application, this would typically involve more sophisticated email
    * validation logic or external service calls.
    */
  private given asyncEmailValidator: AsyncValidator[String] with {
    def validateAsync(email: String)(using ec: concurrent.ExecutionContext): Future[ValidationResult[String]] =
      Future {
        if (email.contains("@")) ValidationResult.Valid(email)
        else ValidationResult.invalid(ValidationError("Async: Email format is invalid."))
      }
  }

  /** User validator using custom validators for both name and age fields.
    *
    * This validator demonstrates how to set up specific validators for different field types within
    * a case class. The username field uses the asynchronous username validator, while the age field
    * uses a synchronous validator lifted to async.
    */
  private given userAsyncValidator: AsyncValidator[User] = {
    given AsyncValidator[String] = asyncUsernameValidator
    given AsyncValidator[Int] = AsyncValidator.fromSync(syncIntValidator)
    AsyncValidator.derive
  }

  /** Company validator that reuses the user validation logic.
    *
    * This validator demonstrates automatic derivation where the existing user validator is used for
    * the nested User field, and the string validator is used for the company name.
    */
  private given companyAsyncValidator: AsyncValidator[Company] = AsyncValidator.derive

  /** A derived validator for Comment that uses the async profanity filter for the text field.
    *
    * This validator demonstrates how to use a specific validator for text content that requires
    * asynchronous profanity checking while using the standard validator for the author field.
    */
  private given commentAsyncValidator: AsyncValidator[Comment] = {
    given AsyncValidator[String] = asyncCommentTextValidator
    AsyncValidator.derive
  }

  /** A derived validator for Post that uses the async Comment validator for the comments-field.
    *
    * This validator demonstrates validation of collections where each item in the collection
    * requires asynchronous validation. The title field uses a synchronous validator, while the
    * comments-field uses the async comment validator.
    */
  private given postAsyncValidator: AsyncValidator[Post] = {
    given AsyncValidator[String] = AsyncValidator.fromSync(syncStringValidator)
    AsyncValidator.derive
  }

  /** A custom validator for UserProfile that handles different validation logic for username and
    * email fields.
    *
    * This validator demonstrates how to create custom validation logic when the automatic
    * derivation cannot distinguish between different String fields that require different
    * validation rules. The username field uses the username validator, while the optional email
    * field uses the email validator.
    */
  private given userProfileAsyncValidator: AsyncValidator[UserProfile] = new AsyncValidator[UserProfile] {
    def validateAsync(
      profile: UserProfile
    )(using ec: concurrent.ExecutionContext): Future[ValidationResult[UserProfile]] = {
      val usernameValidation = asyncUsernameValidator.validateAsync(profile.username)
      val emailValidation = profile.email match {
        case Some(email) => asyncEmailValidator.validateAsync(email).map(_.map(Some(_)))
        case None => Future.successful(ValidationResult.Valid(None))
      }

      for {
        nameResult <- usernameValidation
        emailResult <- emailValidation
      } yield {
        nameResult.zip(emailResult).map { case (name, email) =>
          UserProfile(name, email)
        }
      }
    }
  }

  test("validateAsync should succeed for a valid object") {
    val validUser = User("John", 30)
    val futureResult = userAsyncValidator.validateAsync(validUser)
    futureResult.map(result => assertEquals(result, ValidationResult.Valid(validUser)))
  }

  test("validateAsync should handle synchronous validation failures") {
    val invalidUser = User("John", -5)
    val futureResult = userAsyncValidator.validateAsync(invalidUser)
    futureResult.map {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("Sync: Age must be non-negative"))
      case _ => fail("Expected Invalid result")
    }
  }

  test("validateAsync should handle asynchronous validation failures") {
    val invalidUser = User("admin", 30)
    val futureResult = userAsyncValidator.validateAsync(invalidUser)
    futureResult.map {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("Async: Username 'admin' is reserved."))
      case _ => fail("Expected Invalid result")
    }
  }

  test("validateAsync should accumulate errors from both sync and async validators") {
    val invalidUser = User("root", -10)
    val futureResult = userAsyncValidator.validateAsync(invalidUser)
    futureResult.map {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assert(errors.exists(_.message.contains("Async: Username 'root' is reserved.")))
        assert(errors.exists(_.message.contains("Sync: Age must be non-negative")))
      case _ => fail("Expected Invalid result")
    }
  }

  test("validateAsync should handle nested case classes and annotate error paths correctly") {
    val invalidCompany = Company("BadCorp", User("", -1))
    val futureResult = companyAsyncValidator.validateAsync(invalidCompany)
    futureResult.map {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        val nameError = errors.find(_.fieldPath.contains("name")).get
        val ageError = errors.find(_.fieldPath.contains("age")).get
        assertEquals(nameError.fieldPath, List("owner", "name"))
        assertEquals(ageError.fieldPath, List("owner", "age"))
      case _ => fail("Expected Invalid result")
    }
  }

  test("validateAsync should recover from a failed Future in a validator") {
    val failingValidator: AsyncValidator[String] = new AsyncValidator[String] {
      def validateAsync(a: String)(using ec: concurrent.ExecutionContext): Future[ValidationResult[String]] =
        Future.failed(new RuntimeException("DB error"))
    }
    case class Service(endpoint: String)
    given serviceValidator: AsyncValidator[Service] = {
      given AsyncValidator[String] = failingValidator
      AsyncValidator.derive
    }
    val service = Service("https://example.com")
    val futureResult = serviceValidator.validateAsync(service)
    futureResult.map {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("Asynchronous validation failed unexpectedly"))
      case _ => fail("Expected Invalid result from a failed future")
    }
  }

  test("validateAsync should handle collections with async validators") {
    val post = Post(
      "My Thoughts",
      List(Comment("Alice", "Great post!"), Comment("Bob", "What the heck?"), Comment("Charlie", ""))
    )
    val futureResult = postAsyncValidator.validateAsync(post)
    futureResult.map {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assert(errors.exists(e => e.message.contains("profanity") && e.fieldPath == List("comments", "text")))
        assert(errors.exists(e => e.message.contains("empty") && e.fieldPath.contains("comments")))
      case _ => fail("Expected Invalid result for collection validation")
    }
  }

  test("validateAsync should handle optional fields with async validators") {
    val invalidProfile = UserProfile("testuser", Some("not-an-email"))
    val validProfileNoEmail = UserProfile("testuser", None)

    val invalidResultF = userProfileAsyncValidator.validateAsync(invalidProfile)
    val validResultF = userProfileAsyncValidator.validateAsync(validProfileNoEmail)

    for {
      invalidResult <- invalidResultF
      validResult <- validResultF
    } yield {
      invalidResult match {
        case ValidationResult.Invalid(errors) =>
          assertEquals(errors.size, 1)
          assert(errors.head.message.contains("Email format is invalid"))
        case _ => fail("Expected Invalid result for bad email")
      }

      validResult match {
        case ValidationResult.Valid(_) => ()
        case _ => fail("Expected Valid result for None email")
      }
    }
  }
}
