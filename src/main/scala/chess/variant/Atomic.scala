package strategygames.chess.variant

import strategygames.chess._
import strategygames.Player

case object Atomic
    extends Variant(
      id = 7,
      key = "atomic",
      name = "Atomic",
      standardInitialPosition = true
    ) {

  def perfId: Int    = 14
  def perfIcon: Char = '>'

  override def exoticChessVariant = true
  override def p1IsBetterVariant  = true
  override def blindModeVariant   = false

  def pieces = Standard.pieces

  override def hasMoveEffects = true

  /** Move threatens to explode the opponent's king */
  private def explodesOpponentKing(situation: Situation)(move: Move): Boolean =
    move.captures && {
      situation.board.kingPosOf(!situation.player) exists move.dest.touches
    }

  /** Move threatens to illegally explode our own king */
  private def explodesOwnKing(situation: Situation)(move: Move): Boolean = {
    move.captures && (situation.kingPos exists move.dest.touches)
  }

  private def protectedByOtherKing(board: Board, to: Pos, player: Player): Boolean =
    board.kingPosOf(player) exists to.touches

  /** In atomic chess, a king cannot be threatened while it is in the perimeter of the other king as were the
    * other player to capture it, their own king would explode. This effectively makes a king invincible while
    * connected with another king.
    */
  override def kingThreatened(
      board: Board,
      player: Player,
      to: Pos,
      filter: Piece => Boolean = _ => true,
      validatingCheck: Boolean = false
  ): Boolean = {
    board.pieces exists {
      case (pos, piece)
          if piece.player == player && filter(piece) && piece.eyes(pos, to) && !protectedByOtherKing(
            board,
            to,
            player
          ) =>
        (!piece.role.projection) || piece.role.dir(pos, to).exists {
          longRangeThreatens(board, pos, _, to)
        }
      case _ => false
    }
  }

  // moves exploding opponent king are always playable
  override def kingSafety(m: Move, filter: Piece => Boolean, kingPos: Option[Pos]): Boolean = {
    !kingPos.exists(kingThreatened(m.after, !m.player, _, filter)) ||
    explodesOpponentKing(m.situationBefore)(m)
  } && !explodesOwnKing(m.situationBefore)(m)

  /** If the move captures, we explode the surrounding pieces. Otherwise, nothing explodes. */
  private def explodeSurroundingPieces(move: Move): Move = {
    if (move.captures) {
      val affectedPos = surroundingPositions(move.dest)
      val afterBoard  = move.after
      val destination = move.dest

      val boardPieces = afterBoard.pieces

      // Pawns are immune (for some reason), but all pieces surrounding the captured piece and the capturing piece
      // itself explode
      val piecesToExplode = affectedPos.filter(boardPieces.get(_).fold(false)(_.isNot(Pawn))) + destination
      val afterExplosions = boardPieces -- piecesToExplode

      val newBoard = afterBoard withPieces afterExplosions
      move withAfter newBoard
    } else move
  }

  /** The positions surrounding a given position on the board. Any square at the edge of the board has less
    * surrounding positions than the usual eight.
    */
  private[chess] def surroundingPositions(pos: Pos): Set[Pos] =
    Set(pos.up, pos.down, pos.left, pos.right, pos.upLeft, pos.upRight, pos.downLeft, pos.downRight).flatten

  override def addVariantEffect(move: Move): Move = explodeSurroundingPieces(move)

  /** Since kings cannot confine each other, if either player has only a king then either a queen or multiple
    * pieces are required for checkmate.
    */
  private def insufficientAtomicWinningMaterial(board: Board) = {
    val kingsAndBishopsOnly           = board.pieces forall { p =>
      (p._2 is King) || (p._2 is Bishop)
    }
    lazy val bishopsOnOppositePlayers = InsufficientMatingMaterial.bishopsOnOppositePlayers(board)
    lazy val kingsAndKnightsOnly      = board.pieces forall { p =>
      (p._2 is King) || (p._2 is Knight)
    }
    lazy val kingsRooksAndMinorsOnly  = board.pieces forall { p =>
      (p._2 is King) || (p._2 is Rook) || (p._2 is Bishop) || (p._2 is Knight)
    }

    // Bishops of opposite player (no other pieces) endgames are dead drawn
    // except if either player has multiple bishops so a helpmate is possible
    if (board.count(P1) >= 2 && board.count(P2) >= 2)
      kingsAndBishopsOnly && board.pieces.size <= 4 && bishopsOnOppositePlayers

    // Queen, rook + any, bishop + any (same piece player), or 3 knights can mate
    else if (kingsAndKnightsOnly) board.pieces.size <= 4
    else kingsRooksAndMinorsOnly && !bishopsOnOppositePlayers && board.pieces.size <= 3
  }

  /*
   * Bishops on opposite coloured squares can never capture each other to cause a king to explode and a traditional
   * mate would be not be very likely. Additionally, a player can only mate another player with sufficient material.
   * We also look out for closed positions (pawns that cannot move and kings which cannot capture them.)
   */
  override def isInsufficientMaterial(board: Board) = {
    insufficientAtomicWinningMaterial(board) || atomicClosedPosition(board)
  }

  /** Since a king cannot capture, K + P vs K + P where none of the pawns can move is an automatic draw
    */
  private def atomicClosedPosition(board: Board) = {
    val closedStructure           = board.actors.values.forall(actor =>
      (actor.piece.is(Pawn) && actor.moves.isEmpty
        && InsufficientMatingMaterial.pawnBlockedByPawn(actor, board))
        || actor.piece.is(King) || actor.piece.is(Bishop)
    )
    val randomBishop              = board.pieces.find { case (_, piece) => piece.is(Bishop) }
    val bishopsAbsentOrPawnitized = randomBishop match {
      case Some((pos, piece)) => bishopPawnitized(board, piece.player, pos.isLight)
      case None               => true
    }
    closedStructure && bishopsAbsentOrPawnitized
  }

  private def bishopPawnitized(board: Board, sideWithBishop: Player, bishopLight: Boolean) = {
    board.actors.values.forall(actor =>
      (actor.piece.is(Pawn) && actor.piece.is(sideWithBishop)) ||
        (actor.piece.is(Pawn) && actor.piece.is(!sideWithBishop) && actor.pos.isLight == !bishopLight) ||
        (actor.piece.is(Bishop) && actor.piece.is(sideWithBishop) && actor.pos.isLight == bishopLight) ||
        actor.piece.is(King)
    )
  }

  /** In atomic chess, it is possible to win with a single knight, bishop, etc, by exploding a piece in the
    * opponent's king's proximity. On the other hand, a king alone or a king with immobile pawns is not
    * sufficient material to win with.
    */
  override def opponentHasInsufficientMaterial(situation: Situation) =
    situation.board.rolesOf(!situation.player) == List(King)

  /** Atomic chess has a special end where a king has been killed by exploding with an adjacent captured piece
    */
  override def specialEnd(situation: Situation) = situation.board.kingPos.size != 2
}
