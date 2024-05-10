package strategygames.chess
import strategygames.MoveMetrics

import cats.syntax.option.none

import strategygames.chess.format.Uci

case class Drop(
    piece: Piece,
    pos: Pos,
    situationBefore: Situation,
    after: Board,
    autoEndTurn: Boolean,
    metrics: MoveMetrics = MoveMetrics()
) extends Action(situationBefore) {

  def situationAfter =
    Situation(finalizeAfter, if (autoEndTurn) !piece.player else piece.player)

  def withHistory(h: History) = copy(after = after withHistory h)

  def finalizeAfter: Board = {
    val board = after.variant.finalizeBoard(
      after updateHistory { h =>
        h.copy(
          lastTurn = if (autoEndTurn) h.currentTurn :+ toUci else h.lastTurn,
          currentTurn = if (autoEndTurn) List() else h.currentTurn :+ toUci,
          unmovedRooks = before.unmovedRooks,
          halfMoveClock = if (piece is Pawn) 0 else h.halfMoveClock + 1
        )
      },
      toUci,
      none
    )

    board updateHistory { h =>
      val basePositionHashes =
        if (h.positionHashes.isEmpty) Hash(situationBefore) else board.history.positionHashes
      h.copy(positionHashes = Hash(Situation(board, !piece.player)) ++ basePositionHashes)
    }
  }

  def player = piece.player

  def withAfter(newBoard: Board) = copy(after = newBoard)

  def withMetrics(m: MoveMetrics) = copy(metrics = m)

  def toUci = Uci.Drop(piece.role, pos)

  override def toString = toUci.uci
}
