package strategygames

case class Division(middle: Option[Int], end: Option[Int], plies: Int) {

  def openingSize: Int        = middle | plies
  def middleSize: Option[Int] =
    middle.map { m =>
      (end | plies) - m
    }
  def endSize                 = end.map(plies -)

  def openingBounds = middle.map(0 -> _)
  def middleBounds  =
    for {
      m <- middle
      e <- end
    } yield m -> e
  def endBounds     = end.map(_ -> plies)
}

object Division {
  val empty = Division(None, None, 0)
}

object Divider {

  def draughtsBoards(boards: List[Board]): List[draughts.Board] =
    boards.flatMap(b =>
      b match {
        case Board.Draughts(b) => Some(b)
        case _                 => None
      }
    )

  def chessBoards(boards: List[Board]): List[chess.Board] =
    boards.flatMap(b =>
      b match {
        case Board.Chess(b) => Some(b)
        case _              => None
      }
    )

  def apply(lib: GameLogic, boards: List[Board]): Division = lib match {
    case GameLogic.Draughts() =>
      strategygames.draughts.Divider(draughtsBoards(boards))
    case GameLogic.Chess()    =>
      strategygames.chess.Divider(chessBoards(boards))
    case _                    =>
      sys.error("Divider is unimplemented for this GameLogic")
  }

}
