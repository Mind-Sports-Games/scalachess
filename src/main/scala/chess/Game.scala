package strategygames.chess
import strategygames.{ ClockBase, MoveMetrics, Player }

import cats.data.Validated

import strategygames.chess.format.FEN
import strategygames.chess.format.{ pgn, Uci }

case class Game(
    situation: Situation,
    pgnMoves: Vector[String] = Vector(),
    clock: Option[ClockBase] = None,
    turns: Int = 0, // plies
    startedAtTurn: Int = 0
) {
  def apply(
      orig: Pos,
      dest: Pos,
      promotion: Option[PromotableRole] = None,
      metrics: MoveMetrics = MoveMetrics()
  ): Validated[String, (Game, Move)] =
    situation.move(orig, dest, promotion).map(_.normalizeCastle withMetrics metrics) map { move =>
      apply(move) -> move
    }

  def apply(move: Move): Game = {
    val newSituation = move.situationAfter

    copy(
      situation = newSituation,
      turns = turns + 1,
      pgnMoves = pgnMoves :+ pgn.Dumper(situation, move, newSituation),
      clock = applyClock(move.metrics, newSituation.status.isEmpty)
    )
  }

  def drop(
      role: Role,
      pos: Pos,
      metrics: MoveMetrics = MoveMetrics()
  ): Validated[String, (Game, Drop)] =
    situation.drop(role, pos).map(_ withMetrics metrics) map { drop =>
      applyDrop(drop) -> drop
    }

  def applyDrop(drop: Drop): Game = {
    val newSituation = drop situationAfter

    copy(
      situation = newSituation,
      turns = turns + 1,
      pgnMoves = pgnMoves :+ pgn.Dumper(drop, newSituation),
      clock = applyClock(drop.metrics, newSituation.status.isEmpty)
    )
  }

  private def applyClock(metrics: MoveMetrics, gameActive: Boolean) =
    clock.map { c =>
      {
        val newC = c.step(metrics, gameActive)
        if (turns - startedAtTurn == 1) newC.start else newC
      }
    }

  def apply(uci: Uci.Move): Validated[String, (Game, Move)] = apply(uci.orig, uci.dest, uci.promotion)
  def apply(uci: Uci.Drop): Validated[String, (Game, Drop)] = drop(uci.role, uci.pos)
  def apply(uci: Uci): Validated[String, (Game, Action)]    = (uci match {
    case u: Uci.Move => apply(u)
    case u: Uci.Drop => apply(u)
  }) map { case (g, a) => g -> a }

  def player = situation.player

  def board = situation.board

  def isStandardInit = board.pieces == strategygames.chess.variant.Standard.pieces

  def halfMoveClock: Int = board.history.halfMoveClock

  /** Fullmove number: The number of the full move. It starts at 1, and is incremented after P2's move.
    */
  def fullMoveNumber: Int = 1 + turns / 2

  def moveString = s"$fullMoveNumber${player.fold(".", "...")}"

  def withBoard(b: Board) = copy(situation = situation.copy(board = b))

  def updateBoard(f: Board => Board) = withBoard(f(board))

  def withPlayer(c: Player) = copy(situation = situation.copy(player = c))

  def withTurns(t: Int) = copy(turns = t)
}

object Game {
  def apply(variant: strategygames.chess.variant.Variant): Game =
    new Game(Situation(Board init variant, P1))

  def apply(board: Board): Game = apply(board, P1)

  def apply(board: Board, player: Player): Game = new Game(Situation(board, player))

  def apply(variantOption: Option[strategygames.chess.variant.Variant], fen: Option[FEN]): Game = {
    val variant = variantOption | strategygames.chess.variant.Standard
    val g       = apply(variant)
    fen
      .flatMap {
        format.Forsyth.<<<@(variant, _)
      }
      .fold(g) { parsed =>
        g.copy(
          situation = Situation(
            board = parsed.situation.board withVariant g.board.variant withCrazyData {
              parsed.situation.board.pocketData orElse g.board.pocketData
            },
            player = parsed.situation.player
          ),
          turns = parsed.turns
        )
      }
  }
}
