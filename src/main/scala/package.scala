import ornicar.scalalib

package object strategygames
    extends scalalib.Common
    with scalalib.OrnicarOption
    with scalalib.OrnicarBoolean {

  val P1 = Player.P1
  val P2 = Player.P2

  type PosInfo = (Piece, Int)

  type PieceMap = Map[Pos, PosInfo]

  type PositionHash = Array[Byte]

  type ActionStrs = Seq[Seq[String]]

  type VActionStrs = Vector[Vector[String]]

}
