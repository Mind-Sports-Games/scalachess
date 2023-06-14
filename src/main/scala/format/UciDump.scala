package strategygames.format

import cats.data.Validated

import strategygames.variant.Variant
import strategygames.{ Actions, Drop, GameFamily, GameLogic, Move, MoveOrDrop }

object UciDump {

  def apply(
      lib: GameLogic,
      actions: Actions,
      initialFen: Option[FEN],
      variant: Variant,
      finalSquare: Boolean = false
  ): Validated[String, Actions] = (lib, variant) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant))         =>
      strategygames.draughts.format.UciDump(actions, initialFen.map(_.toDraughts), variant, finalSquare)
    case (GameLogic.Chess(), Variant.Chess(variant))               =>
      strategygames.chess.format.UciDump(actions, initialFen.map(_.toChess), variant)
    case (GameLogic.FairySF(), Variant.FairySF(variant))           =>
      strategygames.fairysf.format.UciDump(actions, initialFen.map(_.toFairySF), variant)
    case (GameLogic.Samurai(), Variant.Samurai(variant))           =>
      strategygames.samurai.format.UciDump(actions, initialFen.map(_.toSamurai), variant)
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant)) =>
      strategygames.togyzkumalak.format.UciDump(actions, initialFen.map(_.toTogyzkumalak), variant)
    case _                                                         => sys.error("Mismatched gamelogic types 12")
  }

  def action(lib: GameLogic, variant: Variant)(mod: MoveOrDrop): String = (lib, variant, mod) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant), Left(Move.Draughts(mod)))             =>
      strategygames.draughts.format.UciDump.action(variant)(mod)
    case (GameLogic.Chess(), Variant.Chess(variant), Left(Move.Chess(mod)))                      =>
      strategygames.chess.format.UciDump.action(variant)(Left(mod))
    case (GameLogic.Chess(), Variant.Chess(variant), Right(Drop.Chess(mod)))                     =>
      strategygames.chess.format.UciDump.action(variant)(Right(mod))
    case (GameLogic.FairySF(), Variant.FairySF(variant), Left(Move.FairySF(mod)))                =>
      strategygames.fairysf.format.UciDump.action(variant)(Left(mod))
    case (GameLogic.FairySF(), Variant.FairySF(variant), Right(Drop.FairySF(mod)))               =>
      strategygames.fairysf.format.UciDump.action(variant)(Right(mod))
    case (GameLogic.Samurai(), Variant.Samurai(variant), Left(Move.Samurai(mod)))                =>
      strategygames.samurai.format.UciDump.action(variant)(mod)
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant), Left(Move.Togyzkumalak(mod))) =>
      strategygames.togyzkumalak.format.UciDump.action(variant)(mod)
    case _                                                                                       => sys.error("Mismatched gamelogic types 13")
  }

  def fishnetUci(variant: Variant)(moves: List[Uci]): String = variant match {
    case Variant.FairySF(variant) =>
      strategygames.fairysf.format.UciDump.fishnetUci(variant)(moves.map(_.toFairySF))
    case _                        =>
      moves.map(_.fishnetUci).mkString(" ")
  }
}
