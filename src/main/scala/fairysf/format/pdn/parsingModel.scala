package strategygames.fairysf
package format.pdn

import strategygames.{ Move => StratMove }
import strategygames.format.pgn.{ Metas, San, Sans, Tags }

import cats.data.Validated
import cats.data.Validated.{ invalid, valid }
import cats.syntax.option._

case class Std(
    fields: List[Pos],
    capture: Boolean,
    metas: Metas = Metas.empty
) extends San {

  override def apply(
      situation: strategygames.Situation,
      iteratedCapts: Boolean,
      forbiddenUci: Option[List[String]]
  ): Validated[String, strategygames.MoveOrDrop] = ???
    //move(situation.toDraughts, iteratedCapts, forbiddenUci).map(m => Left(StratMove.wrap(m)))

  def move(
      situation: Situation,
      iteratedCapts: Boolean = false,
      forbiddenUci: Option[List[String]] = None,
      captures: Option[List[Pos]] = None
  ): Validated[String, strategygames.fairysf.Move] = {
    val src         = fields.head
    val dest        = fields.last
    val capturePath = if (capture) fields.tail.reverse else Nil
    situation.board.pieces.foldLeft(none[strategygames.fairysf.Move]) {
      case (None, (pos, piece)) if piece.color == situation.color && pos == src =>
        val a = Actor(piece, situation.board.posAt(pos), situation.board)
        // TODO: technically we should check situation.hasCaptures instead of actor
        val validMoves = if (a.captures.nonEmpty) a.captures else a.noncaptures
        validMoves.find { m =>
          m.dest == dest && (!iteratedCapts || m.situationAfter.ghosts == 0)
        } match {
          case None if capture && iteratedCapts =>
            a.capturesFinal.find { m =>
              m.dest == dest &&
              captures.fold(true)(m.capture.contains) &&
              !forbiddenUci.fold(false)(_.contains(m.toUci.uci)) &&
              (capturePath.length <= 1 || m.capture.contains(capturePath))
            }
          case m @ _ => m
        }
      case (m, _) => m
    } match {
      case None       => invalid(s"No move found: $this\n$situation")
      case Some(move) => valid(move)
    }
  }

  def withMetas(m: Metas) = copy(metas = m)
}