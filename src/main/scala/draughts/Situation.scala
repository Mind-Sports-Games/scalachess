package strategygames.draughts

import strategygames.{ Player, Status }

import cats.data.Validated
import cats.implicits._

import format.Uci

case class Situation(board: Board, player: Player) {

  lazy val actors = board actorsOf player

  lazy val ghosts = board.ghosts

  def validMovesVerified(finalSquare: Boolean = false): Map[Pos, List[Move]] =
    if (ghosts <= 0) board.variant.validMoves(this, finalSquare)
    else
      // Assumptions: ghosts > 0 means it's our move and we made a capture.
      //              Maybe we should check it later?
      board.history.lastMove.fold(
        board.variant.validMoves(this, finalSquare)
      ) {
        case (lastUci: Uci.Move) =>
          Map(lastUci.dest -> board.variant.validMovesFrom(this, lastUci.dest, finalSquare))
        case _                   => board.variant.validMoves(this, finalSquare)
      }

  lazy val validMoves: Map[Pos, List[Move]]      = validMovesVerified()
  lazy val validMovesFinal: Map[Pos, List[Move]] = validMovesVerified(true)

  lazy val allCaptures: Map[Pos, List[Move]] =
    actors
      .collect {
        case actor if actor.captures.nonEmpty =>
          actor.pos -> actor.captures
      }
      .to(Map)

  lazy val allMovesCaptureLength: Int =
    actors.foldLeft(0) { case (max, actor) =>
      Math.max(actor.captureLength, max)
    }

  def hasCaptures = actors.foldLeft(false) { case (capture, actor) =>
    if (capture) true
    else actor.captures.nonEmpty
  }

  def ambiguitiesMove(move: Move): Int           = ambiguitiesMove(move.orig, move.dest)
  def ambiguitiesMove(orig: Pos, dest: Pos): Int = countAmbiguities(
    movesFrom(orig, true).filter(_.dest == dest)
  )

  private def countAmbiguities(moves: List[Move]) =
    moves.foldLeft(0) { (total, m1) =>
      if (
        moves.exists { m2 =>
          m1 != m2 &&
          m1.dest == m2.dest &&
          m1.situationAfter.board.pieces != m2.situationAfter.board.pieces
        }
      ) total + 1
      else total
    }

  def movesFrom(pos: Pos, finalSquare: Boolean = false): List[Move] =
    board.variant.validMovesFrom(this, pos, finalSquare)

  def captureLengthFrom(pos: Pos): Option[Int] =
    actorAt(pos).map(_.captureLength)

  lazy val allDestinations: Map[Pos, List[Pos]]        = validMoves map (kv => (kv._1, kv._2.map(_.dest)))
  lazy val allCaptureDestinations: Map[Pos, List[Pos]] = allCaptures map (kv => (kv._1, kv._2.map(_.dest)))

  def destinationsFrom(pos: Pos, finalSquare: Boolean = false): List[Pos] =
    movesFrom(pos, finalSquare) map (_.dest)

  def validMoveCount = validMoves.foldLeft(0)((t, p) => t + p._2.length)

  def actorAt(pos: Pos): Option[Actor] = board.actorAt(pos)

  def drops: Option[List[Pos]] = None

  def history = board.history

  def checkMate: Boolean = board.variant checkmate this

  def autoDraw: Boolean = board.autoDraw || board.variant.specialDraw(this)

  lazy val threefoldRepetition: Boolean = board.history.threefoldRepetition

  def variantEnd = board.variant specialEnd this

  def end: Boolean = checkMate || autoDraw || variantEnd

  def winner: Option[Player] = board.variant.winner(this)

  def playable(strict: Boolean): Boolean =
    (board valid strict) && !end

  lazy val status: Option[Status] =
    if (checkMate) Status.Mate.some
    else if (variantEnd) Status.VariantEnd.some
    else if (autoDraw) Status.Draw.some
    else none

  def move(
      from: Pos,
      to: Pos,
      promotion: Option[PromotableRole] = None,
      finalSquare: Boolean = false,
      forbiddenUci: Option[List[String]] = None,
      captures: Option[List[Pos]] = None,
      partialCaptures: Boolean = false
  ): Validated[String, Move] =
    board.variant.move(this, from, to, promotion, finalSquare, forbiddenUci, captures, partialCaptures)

  def move(uci: Uci.Move): Validated[String, Move] =
    board.variant.move(this, uci.orig, uci.dest, uci.promotion)

  def withHistory(history: DraughtsHistory) = copy(
    board = board withHistory history
  )

  def withVariant(variant: strategygames.draughts.variant.Variant) = copy(
    board = board withVariant variant
  )

  def withoutGhosts = copy(
    board = board.withoutGhosts
  )

  def unary_! = copy(player = !player)
}

object Situation {

  def apply(variant: strategygames.draughts.variant.Variant): Situation = Situation(Board init variant, P1)

  def withPlayerAfter(board: Board, playerBefore: Player): Situation =
    if (board.ghosts == 0) Situation(board, !playerBefore)
    else Situation(board, playerBefore)
}
