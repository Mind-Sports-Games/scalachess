package strategygames.draughts
import strategygames.MoveMetrics

import cats.implicits._

import format.Uci

case class Move(
    piece: Piece,
    orig: Pos,
    dest: Pos,
    situationBefore: Situation,
    after: Board,
    autoEndTurn: Boolean,
    capture: Option[List[Pos]],
    taken: Option[List[Pos]],
    promotion: Option[PromotableRole] = None,
    metrics: MoveMetrics = MoveMetrics()
) extends Action(situationBefore) {

  def situationAfter: Situation = situationAfter(false)

  // TODO: Use autoEndTurn when rewriting draughts
  def situationAfter(finalSquare: Boolean): Situation =
    Situation.withPlayerAfter(finalizeAfter(finalSquare), piece.player)

  def withHistory(h: DraughtsHistory) = copy(after = after withHistory h)

  def finalizeAfter(finalSquare: Boolean = false): Board = {
    val board = after updateHistory { h1 =>
      h1.copy(lastMove = Some(toUci))
    }

    val finalized =
      board.variant.finalizeBoard(board, toUci, taken flatMap before.apply, situationBefore, finalSquare)
    if (finalized.ghosts != 0) finalized
    else
      finalized updateHistory { h =>
        // Update position hashes last, only after updating the board, and when capture is complete
        h.copy(positionHashes = board.variant.updatePositionHashes(board, this, h.positionHashes))
      }
  }

  def applyVariantEffect: Move = before.variant addVariantEffect this

  def afterWithLastMove(finalSquare: Boolean = false) = after.variant.finalizeBoard(
    after.copy(history = after.history.withLastMove(toUci)),
    toUci,
    taken flatMap before.apply,
    situationBefore,
    finalSquare
  )

  // does this move capture an opponent piece?
  def captures = capture.fold(false)(_.nonEmpty)

  // Returns the first move without any of the captgures.
  def first: Move = copy(dest = capture.flatMap(_.lastOption).getOrElse(dest), capture = None)

  def promotes = promotion.isDefined

  def player = piece.player

  def withPromotion(op: Option[PromotableRole]): Option[Move] =
    op.fold(this.some) { p =>
      if ((after count Piece(player, King)) > (before count Piece(player, King))) for {
        b2 <- after take dest
        b3 <- b2.place(Piece(player, p), dest)
      } yield copy(after = b3, promotion = Option(p))
      else this.some
    }

  def withAfter(newBoard: Board) = copy(after = newBoard)

  def withMetrics(m: MoveMetrics) = copy(metrics = m)

  def toUci      = Uci.Move(orig, dest, promotion, capture)
  def toShortUci =
    Uci.Move(orig, dest, promotion, if (capture.isDefined) capture.get.takeRight(1).some else None)

  def toSan     = s"${orig.shortKey}${if (capture.nonEmpty) "x" else "-"}${dest.shortKey}"
  def toFullSan = {
    val sep = if (capture.nonEmpty) "x" else "-"
    orig.shortKey + sep + capture.fold(dest.shortKey)(_.reverse.map(_.shortKey) mkString sep)
  }

  def toScanMove =
    if (taken.isDefined)
      (List(orig.shortKey, dest.shortKey) ::: taken.get.reverse.map(_.shortKey)) mkString "x"
    else s"${orig.shortKey}-${dest.shortKey}"

  override def toString = s"$piece ${toUci.uci}"

}
