package strategygames.draughts

import scala.annotation.nowarn

import strategygames.{ ClockBase, MoveMetrics, Player }
import strategygames.draughts.format.FEN

import cats.data.Validated
import cats.syntax.option.none

import format.{ pdn, Uci }

case class DraughtsGame(
    situation: Situation,
    pdnMoves: Vector[String] = Vector.empty[String],
    clock: Option[ClockBase] = None,
    /** turns means plies here */
    turns: Int = 0,
    startedAtTurn: Int = 0
) {

  def apply(
      orig: Pos,
      dest: Pos,
      promotion: Option[PromotableRole] = None,
      @nowarn _metrics: MoveMetrics = MoveMetrics(),
      finalSquare: Boolean = false,
      captures: Option[List[Pos]] = None,
      partialCaptures: Boolean = false
  ): Validated[String, (DraughtsGame, Move)] =
    situation.move(orig, dest, promotion, finalSquare, none, captures, partialCaptures).map { fullMove =>
      val gameWithMove =
        if (partialCaptures && finalSquare && fullMove.dest != dest && captures.exists(_.size > 1)) {
          val steps = captures.get.reverse
          val first = situation
            .move(
              from = orig,
              to = steps.head
            )
            .map(m => apply(m) -> m)
            .toOption
            .map { case (g, m) =>
              g -> m.copy(
                capture = m.capture.flatMap(_.lastOption).map(List(_)),
                taken = m.taken.flatMap(_.lastOption).map(List(_))
              )
            }
          steps.tail.foldLeft(first) { (cur, step) =>
            cur.flatMap { case (curGame, curMove) =>
              curGame.situation
                .move(
                  from = curMove.dest,
                  to = step
                )
                .map(m => curGame.apply(m) -> m)
                .toOption
                .map { case (g, m) =>
                  g -> m.copy(
                    capture = m.capture.flatMap(c => curMove.capture.map(c.last :: _)),
                    taken = m.taken.flatMap(t => curMove.taken.map(t.last :: _))
                  )
                }
            }
          }
        } else none
      gameWithMove map { case (g, m) =>
        val fullSan = s"${orig.shortKey}x${dest.shortKey}"
        g.copy(pdnMoves = g.pdnMoves.dropRight(captures.get.size) :+ fullSan) -> m.copy(orig = orig)
      } getOrElse apply(fullMove) -> fullMove
    }

  def apply(move: Move): DraughtsGame = apply(move, false)

  def apply(move: Move, finalSquare: Boolean): DraughtsGame = {

    val newSituation = move.situationAfter(finalSquare)

    if (newSituation.ghosts != 0) {
      copy(
        situation = newSituation,
        turns = turns,
        pdnMoves = pdnMoves :+ pdn.Dumper(situation, move, newSituation),
        clock = clock
      )
    } else {
      copy(
        situation = newSituation,
        turns = turns + 1,
        pdnMoves = pdnMoves :+ pdn.Dumper(situation, move, newSituation),
        clock = applyClock(move.metrics, newSituation.status.isEmpty)
      )
    }

  }

  def apply(uci: Uci.Move): Validated[String, (DraughtsGame, Move)] = apply(uci, false)

  def apply(uci: Uci.Move, finalSquare: Boolean): Validated[String, (DraughtsGame, Move)] =
    apply(
      orig = uci.orig,
      dest = uci.dest,
      promotion = uci.promotion,
      finalSquare = finalSquare
    )

  private def applyClock(metrics: MoveMetrics, gameActive: Boolean) = clock.map { c =>
    {
      val newC = c.step(metrics, gameActive)
      if (turns - startedAtTurn == 1) newC.start else newC
    }
  }

  def displayTurns = if (situation.ghosts == 0) turns else turns + 1

  def player = situation.player

  def board = situation.board

  def isStandardInit = board.pieces == strategygames.draughts.variant.Standard.pieces
  def isInitial      = board.pieces == board.variant.pieces

  def halfMoveClock: Int = board.history.halfMoveClock

  /** Fullmove number: The number of the full move. It starts at 1, and is incremented after P2's move.
    */
  def fullMoveNumber: Int = 1 + turns / 2

  def moveString = s"${fullMoveNumber}${player.fold(".", "...")}"

  def pdnMovesConcat(fullCaptures: Boolean = false, dropGhosts: Boolean = false): Vector[String] = {
    val movesConcat = pdnMoves.foldLeft(Vector.empty[String]) { (moves, curMove) =>
      if (moves.isEmpty) moves :+ curMove
      else {
        val curX = curMove.indexOf('x')
        if (curX == -1) moves :+ curMove
        else {
          val lastMove = moves.last
          val lastX    = lastMove.lastIndexOf('x')
          if (lastX != -1 && lastMove.takeRight(lastMove.length - lastX - 1) == curMove.take(curX)) {
            val prefix = if (fullCaptures) lastMove else lastMove.take(lastX)
            moves.dropRight(1) :+ (prefix + curMove.takeRight(curMove.length - curX))
          } else moves :+ curMove
        }
      }
    }
    if (dropGhosts && situation.ghosts != 0) movesConcat.dropRight(1)
    else movesConcat
  }

  def withBoard(b: Board) = copy(situation = situation.copy(board = b))

  def updateBoard(f: Board => Board) = withBoard(f(board))

  def withPlayer(c: Player) = copy(situation = situation.copy(player = c))

  def withTurns(t: Int) = copy(turns = t)
}

object DraughtsGame {
  def apply(variant: strategygames.draughts.variant.Variant): DraughtsGame = new DraughtsGame(
    Situation(Board init variant, P1)
  )

  def apply(board: Board): DraughtsGame = apply(board, P1)

  def apply(board: Board, player: Player): DraughtsGame = new DraughtsGame(Situation(board, player))

  def apply(variantOption: Option[strategygames.draughts.variant.Variant], fen: Option[FEN]): DraughtsGame = {
    val variant = variantOption | strategygames.draughts.variant.Standard
    val g       = apply(variant)
    fen
      .flatMap {
        format.Forsyth.<<<@(variant, _)
      }
      .fold(g) { parsed =>
        g.copy(
          situation = Situation(
            board = parsed.situation.board withVariant g.board.variant,
            player = parsed.situation.player
          ),
          turns = parsed.turns
        )
      }
  }
}
