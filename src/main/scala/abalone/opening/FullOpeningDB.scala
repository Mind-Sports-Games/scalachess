package strategygames.abalone.opening

import cats.syntax.option._

import strategygames.abalone.format.FEN
import strategygames.ActionStrs

object FullOpeningDB {

  private val SEARCH_MAX_TURNS = 40

  def findByFen(fen: FEN): Option[FullOpening] = None // TODO: ???

  // assumes standard initial FEN and variant
  def search(actionStrs: ActionStrs): Option[FullOpening.AtPly] = None // TODO: ???

  def searchInFens(fens: Vector[FEN]): Option[FullOpening] = None // TODO: ???
}
