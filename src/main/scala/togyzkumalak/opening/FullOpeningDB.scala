package strategygames.togyzkumalak.opening

import scala.annotation.nowarn

import strategygames.togyzkumalak.format.FEN
import strategygames.ActionStrs

object FullOpeningDB {

  private val SEARCH_MAX_TURNS = 40

  def findByFen(fen: FEN): Option[FullOpening] = None // TODO: ???

  // assumes standard initial FEN and variant
  def search(@nowarn actionStrs: ActionStrs): Option[FullOpening.AtPly] = None // TODO: ???

  def searchInFens(@nowarn fens: Vector[FEN]): Option[FullOpening] = None // TODO: ???
}
