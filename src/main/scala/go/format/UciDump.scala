package strategygames.go.format

import scala.annotation.nowarn
import cats.data.Validated

import strategygames.go.variant.Variant
import strategygames.go.{ Action, Drop, Pass, Replay, SelectSquares }
import strategygames.{ ActionStrs, Player }

object UciDump {

  // a2a4, b8c6
  def apply(replay: Replay): ActionStrs =
    replay.chronoActions.map(_.map(action(replay.setup.board.variant)))

  def apply(
      actionStrs: ActionStrs,
      initialFen: Option[FEN],
      variant: Variant
  ): Validated[String, ActionStrs] =
    if (actionStrs.isEmpty) Validated.valid(Nil)
    else
      Replay(
        actionStrs = actionStrs,
        // we can default to this because in UciDump we are only looking to validate
        // the current actionStrs, not work out future actionStrs
        startPlayer = Player.P1,
        activePlayer = Player.fromTurnCount(actionStrs.size),
        initialFen = initialFen,
        variant = variant
      ) andThen (_.valid) map apply

  def action(@nowarn variant: Variant)(a: Action): String = a match {
    case ss: SelectSquares => ss.toUci.uci
    case p: Pass           => p.toUci.uci
    case d: Drop           => d.toUci.uci
  }

}
