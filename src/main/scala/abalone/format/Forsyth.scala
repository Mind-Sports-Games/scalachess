package strategygames.abalone.format

import cats.implicits._

import strategygames.{ Player, Score }
import strategygames.abalone._
import strategygames.abalone.variant.Variant

/** Transform a game to standard Forsyth Edwards Notation
  * http://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation
  */
object Forsyth {

  // TODO Abalone Set
  val initial = FEN("")

  def <<@(variant: Variant, fen: FEN): Option[Situation] = {
    Some(
      Situation(
        Board(
          pieces = fen.pieces,
          history = History(),
          variant = variant
        ),
        fen.value.split(' ')(3) match {
          case "S" => P1
          case "N" => P2
          case _   => sys.error("Invalid player in fen")
        }
      ).withHistory(
        History(
          score = Score(fen.player1Score, fen.player2Score),
          // seems like we might be using History.halfMoveClock incorrectly
          halfMoveClock = fen.fullMove.getOrElse(0)
        )
      )
    )
  }

  def <<(fen: FEN): Option[Situation] = <<@(Variant.default, fen)

  case class SituationPlus(situation: Situation, fullTurnCount: Int) {

    def turnCount = fullTurnCount * 2 - situation.player.fold(2, 1)
    // when we get a multiaction variant we should set this
    def plies     = turnCount

  }

  def <<<@(variant: Variant, fen: FEN): Option[SituationPlus] =
    <<@(variant, fen) map { sit =>
      SituationPlus(
        // not doing half move clock history like we do in chess
        sit,
        fen.value.split(' ').last.toIntOption.map(_ max 1 min 500) | 1
      )
    }

  def <<<(fen: FEN): Option[SituationPlus] = <<<@(Variant.default, fen)

  def >>(situation: Situation): FEN = >>(SituationPlus(situation, 1))

  def >>(parsed: SituationPlus): FEN =
    parsed match {
      case SituationPlus(situation, _) =>
        >>(Game(situation, plies = parsed.plies, turnCount = parsed.turnCount))
    }

  def >>(game: Game): FEN = {
    val boardFen = boardPart(game.situation.board)
    val scoreStr = game.situation.board.history.score.fenStr
    val player   = game.situation.player.fold('S', 'N')
    val moves    = game.situation.board.history.halfMoveClock
    FEN(s"${boardFen} ${scoreStr} ${player} ${moves}")
  }

  def exportBoard(board: Board): String = {
    val boardFen = boardPart(board)
    val scoreStr = board.history.score.fenStr
    s"${boardFen} ${scoreStr}"
  }

  // TODO Abalone Set - it shouldnt be nothing like board.toString!
  def boardPart(board: Board): String = board.toString

  def boardAndPlayer(situation: Situation): String =
    boardAndPlayer(situation.board, situation.player)

  def boardAndPlayer(board: Board, turnPlayer: Player): String =
    s"${exportBoard(board)} ${turnPlayer.letter}"

}
