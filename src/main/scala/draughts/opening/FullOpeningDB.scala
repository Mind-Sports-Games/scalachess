package strategygames.draughts
package opening

import format.FEN
import strategygames.ActionStrs

import cats.implicits._

object FullOpeningDB {

  private val SEARCH_MAX_TURNS = 40

  private def all: Vector[FullOpening] = Vector()
  /*private def all: Vector[FullOpening] = OpeningTable.tableFMJD.positions.map { p =>
    new FullOpening(
      code = p.code,
      name = p.name.getOrElse("?"),
      fen = p.fen.split(':').take(3).mkString(":"),
      source = OpeningTable.tableFMJD.name.some
    )
  } toVector*/

  lazy val byFen: collection.Map[String, FullOpening] =
    all.map { o =>
      o.fen -> o
    }.toMap

  def findByFen(fen: String) = byFen get fen.split(':').take(3).mkString(":")

  // assumes standard initial FEN and variant
  def search(actionStrs: ActionStrs): Option[FullOpening.AtPly] =
    strategygames.draughts.Replay
      .boards(actionStrs.take(SEARCH_MAX_TURNS), None, variant.Standard)
      .toOption
      .flatMap {
        _.zipWithIndex.drop(1).foldRight(none[FullOpening.AtPly]) {
          case ((board, ply), None) =>
            val fen = format.Forsyth.exportStandardPositionTurn(board, ply)
            byFen get fen map (_ atPly ply)
          case (_, found)           => found
        }
      }

  def searchInFens(fens: List[FEN]): Option[FullOpening] =
    fens.foldRight(none[FullOpening]) {
      case (fen, None) =>
        byFen get {
          fen.value.split(':').take(3) mkString ":"
        }
      case (_, found)  => found
    }
}
