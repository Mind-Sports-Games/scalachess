package strategygames.chess.format

import cats.data.Validated

import strategygames.chess.variant.Variant
import strategygames.chess.{ MoveOrDrop, Replay }
import strategygames.Actions

object UciDump {

  // a2a4, b8c6
  def apply(replay: Replay): Actions =
    replay.chronoActions.map(_.map(action(replay.setup.board.variant)))

  def apply(
      actions: Actions,
      initialFen: Option[FEN],
      variant: Variant
  ): Validated[String, Actions] =
    if (actions.isEmpty) Validated.valid(Nil)
    else Replay(actions, initialFen, variant) andThen (_.valid) map apply

  def action(variant: Variant)(mod: MoveOrDrop): String =
    mod match {
      case Left(m)  =>
        m.castle.fold(m.toUci.uci) {
          case ((kf, kt), (rf, _)) if kf == kt || variant.chess960 || variant.fromPosition => kf.key + rf.key
          case ((kf, kt), _)                                                               => kf.key + kt.key
        }
      case Right(d) => d.toUci.uci
    }
}
