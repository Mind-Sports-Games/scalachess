package strategygames.draughts
package format

import cats.implicits._

import strategygames.Player

import variant.{ Standard, Variant }

/** Transform a game to draughts Forsyth Edwards Notation
  * https://en.wikipedia.org/wiki/Portable_Draughts_Notation Additions: Piece role G/P = Ghost man or king of
  * that player, has been captured but not removed because the forced capture sequence is not finished yet
  * ":Hx" = Halfmove clock: This is the number of halfmoves since a forced draw material combination appears.
  * This is used to determine if a draw can be claimed. ":Fx" = Fullmove number: The number of the full move.
  * It starts at 1, and is incremented after P2's move.
  */
object Forsyth {

  val initial              =
    FEN(
      "W:W31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50:B1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20:H0:F1"
    )
  val initialPieces        =
    FEN(
      "W31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50:B1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20"
    )
  val initialMoveAndPieces =
    FEN(
      "W:W31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50:B1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20"
    )

  def <<@(variant: Variant, fen: FEN): Option[Situation] =
    makeBoard(variant, fen) map { board =>
      val situation = Player.apply(fen.value.charAt(0)) match {
        case Some(player) => Situation(board, player)
        case _            => Situation(board, P1)
      }

      situation withHistory {
        val history = DraughtsHistory(
          positionHashes = Array.empty,
          variant = variant
        )
        if (variant.frisianVariant) {
          val kingMoves = fen.value.split(':').lastOption.flatMap(makeKingMoves(_, variant.boardSize.pos))
          kingMoves.fold(history)(history.withKingMoves)
        } else history
      }

    }

  def <<(fen: FEN): Option[Situation] = <<@(Standard, fen)

  case class SituationPlus(situation: Situation, fullTurnCount: Int) {

    def turnCount = fullTurnCount * 2 - (if (situation.player.p1) 2 else 1)
    // when we convert draughts to multiaction we should consider setting this
    // we may be able to deprecate this at that point as actions.flatten.size should count plies
    def plies     = turnCount

  }

  def <<<@(variant: Variant, fen: FEN): Option[SituationPlus] =
    <<@(variant, fen) map { sit =>
      val splitted       = fen.value.split(':')
      val fullMoveNumber = splitted find { s => s.length > 1 && s.charAt(0) == 'F' } flatMap { s =>
        parseIntOption(s drop 1)
      } map (_ max 1 min 500)
      val halfMoveClock  = splitted find { s => s.length > 1 && s.charAt(0) == 'H' } flatMap { s =>
        parseIntOption(s drop 1)
      } map (_ max 0 min 100)
      SituationPlus(
        halfMoveClock.map(sit.history.setHalfMoveClock).fold(sit)(sit.withHistory),
        fullMoveNumber | 1
      )
    }

  def <<<(fen: FEN): Option[SituationPlus] = <<<@(Standard, fen)

  def makeKingMoves(str: String, pos: BoardPos): Option[KingMoves] = {
    str.split('+').filter(_.nonEmpty).map(_.toList) match {
      case Array(w, b) if (w.length == 1 || w.length == 3) && (b.length == 1 || b.length == 3) =>
        for {
          p1 <- parseIntOption(w.head.toString) if p1 <= 3
          p2 <- parseIntOption(b.head.toString) if p2 <= 3
        } yield KingMoves(p2, p1, pos.posAt(b.tail.mkString), pos.posAt(w.tail.mkString))
      case _                                                                                   => None
    }
  }

  /** Only cares about pieces positions on the board (second and third part of FEN string)
    */
  def makeBoard(variant: Variant, fen: FEN): Option[Board] = {
    val fenPieces        = fen.value.split(':').drop(1)
    def posAt(f: String) = variant.boardSize.pos.posAt(f)
    if (fenPieces.isEmpty) none
    else {
      val allFields = new scala.collection.mutable.ArrayBuffer[(Pos, Piece)]
      for (line <- fenPieces) {
        if (line.nonEmpty)
          Player.apply(line.charAt(0)).foreach { player =>
            val fields = if (line.endsWith(".")) line.substring(1, line.length - 1) else line.drop(1)
            for (field <- fields.split(',')) {
              if (field.nonEmpty)
                field.charAt(0) match {
                  case 'K' => posAt(field.drop(1)).foreach { pos => allFields.+=((pos, Piece(player, King))) }
                  case 'G' =>
                    posAt(field.drop(1)).foreach { pos => allFields.+=((pos, Piece(player, GhostMan))) }
                  case 'P' =>
                    posAt(field.drop(1)).foreach { pos => allFields.+=((pos, Piece(player, GhostKing))) }
                  case _   => posAt(field).foreach { pos => allFields.+=((pos, Piece(player, Man))) }
                }
            }
          }
      }
      Board(allFields, variant).some
    }
  }

  def toAlgebraic(variant: Variant, fen: FEN): Option[FEN] =
    <<<@(variant, fen) map { case parsed @ SituationPlus(situation, _) =>
      doExport(
        DraughtsGame(situation, plies = parsed.plies, turnCount = parsed.turnCount),
        algebraic = true
      )
    }

  def countGhosts(fen: FEN): Int =
    fen.value.split(':').filter(_.nonEmpty).foldLeft(0) { (ghosts, line) =>
      Player.apply(line.charAt(0)).fold(ghosts) { _ =>
        line.drop(1).split(',').foldLeft(ghosts) { (lineGhosts, field) =>
          if (field.nonEmpty && "GP".indexOf(field.charAt(0)) != -1) lineGhosts + 1 else lineGhosts
        }
      }
    }

  def countKings(fen: FEN): Int =
    fen.value.split(':').filter(_.nonEmpty).foldLeft(0) { (kings, line) =>
      Player.apply(line.charAt(0)).fold(kings) { _ =>
        line.drop(1).split(',').foldLeft(kings) { (lineKings, field) =>
          if (field.nonEmpty && field.charAt(0) == 'K') lineKings + 1 else lineKings
        }
      }
    }

  def >>(situation: Situation): FEN = >>(SituationPlus(situation, 1))

  def >>(parsed: SituationPlus): FEN = parsed match {
    case SituationPlus(situation, _) =>
      >>(DraughtsGame(situation, plies = parsed.plies, turnCount = parsed.turnCount))
  }

  def >>(game: DraughtsGame): FEN = doExport(game, algebraic = false)

  private def doExport(game: DraughtsGame, algebraic: Boolean): FEN = FEN {
    {
      List(
        game.player.letter.toUpper,
        exportBoard(game.board, algebraic),
        "H" + game.halfMoveClock.toString,
        "F" + game.fullTurnCount.toString
      ) ::: {
        if (game.board.variant.frisianVariant) List(exportKingMoves(game.board))
        else List()
      }
    } mkString ":"
  }

  def exportStandardPositionTurn(board: Board, ply: Int): String = List(
    Player(ply % 2 == 0).letter,
    exportBoard(board)
  ) mkString ":"

  def exportKingMoves(board: Board) = board.history.kingMoves match {
    case KingMoves(p1, p2, p1King, p2King) =>
      s"+$p2${p2King.fold("")(_.toString)}+$p1${p1King.fold("")(_.toString)}"
  }

  def exportBoard(board: Board, algebraic: Boolean = false): String = {
    val fenW = new scala.collection.mutable.StringBuilder(60)
    val fenB = new scala.collection.mutable.StringBuilder(60)
    fenW.append(P1.letter)
    fenB.append(P2.letter)
    for (f <- 1 to board.boardSize.fields) {
      board(f).foreach { piece =>
        if (piece is P1) {
          if (fenW.length > 1) fenW append ','
          if (piece isNot Man) fenW append piece.forsyth
          if (algebraic) board.boardSize.pos.algebraic(f) foreach fenW.append
          else fenW append f
        } else {
          if (fenB.length > 1) fenB append ','
          if (piece isNot Man) fenB append piece.forsyth
          if (algebraic) board.boardSize.pos.algebraic(f) foreach fenB.append
          else fenB append f
        }
      }
    }
    fenW append ':'
    fenW append fenB
    fenW.toString
  }

  def boardAndPlayer(situation: Situation): String =
    boardAndPlayer(situation.board, situation.player)

  def boardAndPlayer(board: Board, turnPlayer: Player): String =
    s"${turnPlayer.letter.toUpper}:${exportBoard(board)}"

  def compressedBoard(board: Board): String = {
    def posAt(f: Int)        = board.boardSize.pos.posAt(f)
    // roles as numbers to prevent conflict with position piotrs
    def roleId(piece: Piece) = piece.role match {
      case Man       => '1'
      case King      => '2'
      case GhostMan  => '3'
      case GhostKing => '4'
    }
    val fenW                 = new scala.collection.mutable.StringBuilder(30)
    val fenB                 = new scala.collection.mutable.StringBuilder(30)
    fenB.append('0')
    for (f <- 1 to board.boardSize.fields) {
      board(f).foreach { piece =>
        if (piece is P1) {
          if (piece isNot Man) fenW append roleId(piece)
          fenW append posAt(f).get.piotr
        } else {
          if (piece isNot Man) fenB append roleId(piece)
          fenB append posAt(f).get.piotr
        }
      }
    }
    fenW append fenB
    fenW.toString
  }

  def exportScanPosition(sit: Option[Situation]): String = sit.fold("") { situation =>
    val fields = situation.board.boardSize.fields
    val pos    = new scala.collection.mutable.StringBuilder(fields + 1)
    pos.append(situation.player.letter.toUpper)

    for (f <- 1 to fields) {
      situation.board(f) match {
        case Some(Piece(P1, Man))  => pos append 'w'
        case Some(Piece(P2, Man))  => pos append 'b'
        case Some(Piece(P1, King)) => pos append 'W'
        case Some(Piece(P2, King)) => pos append 'B'
        case _                     => pos append 'e'
      }
    }
    pos.toString
  }

  def shorten(fen: FEN): FEN = {
    val fen2 = if (fen.value.endsWith(":+0+0")) fen.value.dropRight(5) else fen.value
    if (fen2.endsWith(":H0:F1")) FEN(fen2.dropRight(6)) else FEN(fen2)
  }

  def getFullMove(fen: FEN): Option[Int] =
    fen.value.split(':') filter (s => s.length > 1 && s.charAt(0) == 'F') lift 0 flatMap parseIntOption

  def getPlayer(fen: FEN): Option[Player] = fen.value lift 0 flatMap Player.apply

  def getPly(fen: FEN): Option[Int] =
    getFullMove(fen) map { fullMove =>
      fullMove * 2 - (if (getPlayer(fen).exists(_.p1)) 2 else 1)
    }

}
