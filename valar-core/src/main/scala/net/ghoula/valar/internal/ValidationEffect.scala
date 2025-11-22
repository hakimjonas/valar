package net.ghoula.valar.internal

import scala.concurrent.{ExecutionContext, Future}

/** Internal abstraction for effect types used in validation.
  *
  * This is a minimal monad-like interface that allows collection validation logic to be written
  * once and reused for both synchronous (Id) and asynchronous (Future) validation.
  *
  * This trait is intentionally private to valar - it's an implementation detail, not a public API.
  */
private[valar] trait ValidationEffect[F[_]] {
  def pure[A](a: A): F[A]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def traverse[A, B](as: List[A])(f: A => F[B]): F[List[B]]
}

/** Synchronous effect (identity). Operations execute immediately. */
private[valar] object SyncEffect extends ValidationEffect[[X] =>> X] {
  def pure[A](a: A): A = a
  def map[A, B](fa: A)(f: A => B): B = f(fa)
  def flatMap[A, B](fa: A)(f: A => B): B = f(fa)
  def traverse[A, B](as: List[A])(f: A => B): List[B] = as.map(f)
}

/** Asynchronous effect using Future. Requires an ExecutionContext. */
private[valar] class FutureEffect(using ec: ExecutionContext) extends ValidationEffect[Future] {
  def pure[A](a: A): Future[A] = Future.successful(a)
  def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)
  def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
  def traverse[A, B](as: List[A])(f: A => Future[B]): Future[List[B]] = Future.traverse(as)(f)
}

private[valar] object FutureEffect {
  def apply()(using ec: ExecutionContext): FutureEffect = new FutureEffect()
}
