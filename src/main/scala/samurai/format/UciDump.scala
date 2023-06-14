package strategygames.samurai.format

import cats.data.Validated

import strategygames.samurai.variant.Variant
import strategygames.samurai.{ Move, Replay }
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

  def action(variant: Variant)(mod: Move): String = mod.toUci.uci

}
