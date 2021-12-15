package strategygames.fairysf
package variant

import cats.implicits._
import cats.syntax.option._
import cats.data.Validated

import strategygames.fairysf._
import strategygames.GameFamily

//import org.playstrategy.FairyStockfish

case object Shogi
    extends Variant(
      id = 1,
      key = "shogi",
      name = "Shogi",
      shortName = "Shogi",
      title = "Shogi (Japanese Chess)",
      standardInitialPosition = true,
      fairysfName=FairySFName("shogi"),
      boardSize = Board.Dim9x9
    ) {
  
  def gameFamily: GameFamily = GameFamily.Shogi()

  override def dropsVariant = true

  def perfIcon: Char = 's'
  def perfId: Int = 200

  override def baseVariant: Boolean = true

  val kingPiece: Role = ShogiKing

  //cache this rather than checking with the API everytime
  override def initialFen =
    format.FEN("lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL[-] w 0 1")

  override def validDrops(situation: Situation): List[Drop] =
    super.validDrops(situation).filterNot(
      d => d.piece.role == ShogiPawn && {
        val kingPos = situation.board.posMap.get(
          Piece(!situation.color, kingPiece)
        ).flatMap(_.headOption)
        Some(d.pos) == (situation.color match {
          case White => kingPos.flatMap(_.down)
          case Black => kingPos.flatMap(_.up)
        })
      } && (situation.board.fen match {
        case Some(fen) => Api.gameResult(
          fairysfName.name,
          fen.value,
          Some(List(d.toUci.uci))
        )
        case None      => Api.gameResult(
          fairysfName.name,
          initialFen.value,
          Some(situation.board.uciMoves :+ d.toUci.uci)
        )
      }) == GameResult.Checkmate()
    )

  val position: Api.FairyPosition = new Api.FairyPosition(this.key)
}
