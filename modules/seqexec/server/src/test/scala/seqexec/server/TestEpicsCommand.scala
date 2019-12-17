package seqexec.server

import cats.{Applicative, Monad}
import cats.effect.concurrent.Ref
import cats.implicits._
import monocle.Lens
import monocle.macros.Lenses
import seqexec.model.enum.ApplyCommandResult
import squants.Time


object TestEpicsCommand {

  /*
   * Type parameters:
   * A: Type of the recorded events
   * S: State affected by the command.
   *
   * Parameters:
   * ist: initial internal state
   * markL: Lens to the mark flag in the internal state
   * st: Ref to system state
   * out: Ref to events accumulator
   */
  abstract class TestEpicsCommand0[F[_]: Monad, A, S](markL: Lens[S, TestEpicsCommand0.State], st: Ref[F, S],
                                                      out: Ref[F, List[A]]) extends EpicsCommand[F] {
    override def post: F[ApplyCommandResult] = st.get.flatMap{ s =>
      if(markL.get(s))
        out.modify(x => (x :+ event(s), ())) *>
        st.modify(s => (markL.set(false)(cmd(s)), ApplyCommandResult.Completed))
      else
        ApplyCommandResult.Completed.pure[F].widen[ApplyCommandResult]
    }

    override def mark: F[Unit] = st.modify(s => (markL.set(true)(s), ()))

    override def setTimeout(t: Time): F[Unit] = Applicative[F].unit

    protected def event(st: S): A

    protected def cmd(st: S): S
  }

  object TestEpicsCommand0 {
    type State = Boolean
  }

  abstract class TestEpicsCommand1[F[_]: Monad, S, A, U](l: Lens[S, TestEpicsCommand1.State[U]], st: Ref[F, S],
                                                         out: Ref[F, List[A]])
    extends TestEpicsCommand0[F, A, S](l ^|-> TestEpicsCommand1.State.mark, st, out) {
    def setParameter1(u: U): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand1.State.param1).set(u)(s), ()))
  }

  object TestEpicsCommand1 {
    @Lenses
    final case class State[U](mark: Boolean, param1: U)
  }

  abstract class TestEpicsCommand2[F[_]: Monad, S, A, U, V](l: Lens[S, TestEpicsCommand2.State[U, V]], st: Ref[F, S],
                                                           out: Ref[F, List[A]])
    extends TestEpicsCommand0[F, A, S](l ^|-> TestEpicsCommand2.State.mark, st, out) {
    def setParameter1(v: U): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand2.State.param1).set(v)(s), ()))
    def setParameter2(v: V): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand2.State.param2).set(v)(s), ()))
  }

  object TestEpicsCommand2 {
    @Lenses
    final case class State[U, V](mark: Boolean, param1: U, param2: V)
  }

  abstract class TestEpicsCommand3[F[_]: Monad, S, A, U, V, W](l: Lens[S, TestEpicsCommand3.State[U, V, W]],
                                                              st: Ref[F, S],
                                                              out: Ref[F, List[A]])
    extends TestEpicsCommand0[F, A, S](l ^|-> TestEpicsCommand3.State.mark[U, V, W], st, out) {
    def setParameter1(u: U): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand3.State.param1).set(u)(s), ()))
    def setParameter2(v: V): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand3.State.param2).set(v)(s), ()))
    def setParameter3(w: W): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand3.State.param3).set(w)(s), ()))
  }

  object TestEpicsCommand3 {
    @Lenses
    final case class State[U, V, W](mark: Boolean, param1: U, param2: V, param3: W)
  }

  abstract class TestEpicsCommand4[F[_]: Monad, S, A, U, V, W, X](l: Lens[S, TestEpicsCommand4.State[U, V, W, X]],
                                                                 st: Ref[F, S],
                                                                 out: Ref[F, List[A]])
    extends TestEpicsCommand0[F, A, S](l ^|-> TestEpicsCommand4.State.mark[U, V, W, X], st, out) {
    def setParameter1(u: U): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand4.State.param1).set(u)(s), ()))
    def setParameter2(v: V): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand4.State.param2).set(v)(s), ()))
    def setParameter3(w: W): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand4.State.param3).set(w)(s), ()))
    def setParameter4(x: X): F[Unit] = st.modify(s => ((l ^|-> TestEpicsCommand4.State.param4).set(x)(s), ()))
  }

  object TestEpicsCommand4 {
    @Lenses
    final case class State[U, V, W, X](mark: Boolean, param1: U, param2: V, param3: W, param4: X)
  }

  class DummyCmd[F[_]: Applicative] extends EpicsCommand[F] {
    override def post: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F].widen[ApplyCommandResult]
    override def mark: F[Unit] = Applicative[F].unit
    override def setTimeout(t: Time): F[Unit] = Applicative[F].unit
  }

}
