package strategygames.fairysf
package format.pgn

import strategygames.{ ActionStrs, GameFamily }
import strategygames.fairysf.format.Uci

import scala.util.Try

object Binary {

  // writeMove only used in tests for chess/draughts
  // would need to reconsider how we do this when we write gameFamily at the start of the game
  // def writeMove(gf: GameFamily, m: String)             = Try(Writer.ply(gf, m))
  def writeMoves(gf: GameFamily, ms: Iterable[String]) = Try(Writer.plies(gf, ms))

  def writeActionStrs(gf: GameFamily, as: ActionStrs) = Try(Writer.actionStrs(gf, as))

  def readActionStrs(bs: List[Byte])          = Try(Reader.actionStrs(bs))
  def readActionStrs(bs: List[Byte], nb: Int) = Try(Reader.actionStrs(bs, nb))

  private object MoveType {
    val Move = 0
    val Drop = 1
  }

  private object Delimiter {
    val str = ""
    val int = 255
  }

  // If changing this, consider changing other gamelogics and also lila game maxPlies
  val maxPlies = 1000

  private object Reader {

    def actionStrs(bs: List[Byte]): ActionStrs          = actionStrs(bs, maxPlies)
    def actionStrs(bs: List[Byte], nb: Int): ActionStrs = toActionStrs(intPlies(bs map toInt, nb, None))

    def toActionStrs(plies: List[String]): ActionStrs =
      if (plies.contains(Delimiter.str)) unflatten(plies.drop(1))
      else
        plies.headOption match {
          // handle old Amazons
          case Some(gameFamilyId) if gameFamilyId == "8" => plies.drop(1).sliding(2, 2).toList
          case Some(_)                                   => plies.drop(1).map(List(_))
          case None                                      => List(plies)
        }

    def unflatten(plies: List[String]): List[List[String]] =
      if (plies.size == 0) List()
      else plies.takeWhile(_ != Delimiter.str) :: unflatten(plies.dropWhile(_ != Delimiter.str).drop(1))

    def intPlies(bs: List[Int], pliesToGo: Int, gf: Option[GameFamily]): List[String] =
      (bs, gf) match {
        case (_, _) if pliesToGo <= 0                                                                => Nil
        case (Nil, _)                                                                                => Nil
        case (b1 :: rest, None)                                                                      => b1.toString() :: intPlies(rest, pliesToGo, Some(GameFamily(b1)))
        case (b1 :: rest, gf) if b1 == Delimiter.int                                                 => Delimiter.str :: intPlies(rest, pliesToGo, gf)
        case (b1 :: b2 :: rest, Some(gf)) if headerBit(b1) == MoveType.Move                          =>
          moveUci(b1, b2) :: intPlies(rest, pliesToGo - 1, Some(gf))
        case (b1 :: rest, Some(gf)) if headerBit(b1) == MoveType.Drop && gf == GameFamily.Flipello() =>
          dropUciFlipello(b1) :: intPlies(rest, pliesToGo - 1, Some(gf))
        case (b1 :: b2 :: rest, Some(gf)) if headerBit(b1) == MoveType.Drop                          =>
          dropUciDefault(gf, b1, b2) :: intPlies(rest, pliesToGo - 1, Some(gf))
        case (x, _)                                                                                  => !!(x map showByte mkString ",")
      }

    // 255 => 11111111 => marker for end of turn

    // 1 movetype (move or drop)
    // 7 pos (from)
    // ----
    // 1 promotion (bool)
    // 7 pos (dest)
    def moveUci(b1: Int, b2: Int): String =
      s"${posFromInt(b1)}${posFromInt(b2)}${promotionFromInt(b2)}"

    // 1 movetype
    // 7 pos (dest)
    def dropUciFlipello(b1: Int): String =
      s"P@${posFromInt(b1)}"

    // 1 movetype
    // 7 pos (dest)
    // ----
    // 8 piece (only needs 4 bits?)
    def dropUciDefault(gf: GameFamily, b1: Int, b2: Int): String =
      s"${pieceFromInt(gf, b2)}@${posFromInt(b1)}"

    def posFromInt(b: Int): String = Pos(right(b, 7)).get.toString()

    def promotionFromInt(b: Int): String = headerBit(b) match {
      case 1 => "+"
      case _ => ""
    }

    def pieceFromInt(gf: GameFamily, b: Int): String =
      Role.allByBinaryInt(gf).get(right(b, 7)).get.forsyth.toString

    private def headerBit(i: Int) = i >> 7

    private def right(i: Int, x: Int): Int = i & lengthMasks(x)
    private val lengthMasks                =
      Map(1 -> 0x01, 2 -> 0x03, 3 -> 0x07, 4 -> 0x0f, 5 -> 0x1f, 6 -> 0x3f, 7 -> 0x7f, 8 -> 0xff)
    private def !!(msg: String)            = throw new Exception("Binary reader failed: " + msg)
  }

  private object Writer {

    def ply(gf: GameFamily, str: String): List[Byte] =
      (str match {
        case Uci.Move.moveR(src, dst, promotion) => moveUci(src, dst, promotion)
        case Uci.Drop.dropR(piece, dst)          => dropUci(gf, piece, dst)
        case Delimiter.str                       => List(Delimiter.int)
        case _                                   => sys.error(s"Invalid move to write: ${str}")
      }) map (_.toByte)

    def plies(gf: GameFamily, strs: Iterable[String]): Array[Byte] =
      (gf.id.toByte :: strs.toList.flatMap(ply(gf, _))).to(Array)

    def actionStrs(gf: GameFamily, strs: ActionStrs): Array[Byte] =
      if (strs.size == 0 || strs.map(_.size).max == 1) plies(gf, strs.flatten)
      else plies(gf, strs.toList.map(_.toList :+ "").flatten)

    def moveUci(src: String, dst: String, promotion: String) = List(
      (headerBit(MoveType.Move)) + Pos.fromKey(src).get.index,
      (headerBit(promotion.headOption match {
        case Some(_) => 1
        case None    => 0
      })) + Pos.fromKey(dst).get.index
    )

    def dropUci(gf: GameFamily, piece: String, dst: String) =
      if (gf == GameFamily.Flipello()) dropUciFlipello(dst)
      else dropUciDefault(gf, piece, dst)

    def dropUciFlipello(dst: String) = List(
      (headerBit(MoveType.Drop)) + Pos.fromKey(dst).get.index
    )

    def dropUciDefault(gf: GameFamily, piece: String, dst: String) = List(
      (headerBit(MoveType.Drop)) + Pos.fromKey(dst).get.index,
      Role.allByForsyth(gf).get(piece(0)).get.binaryInt
    )

    private def headerBit(i: Int) = i << 7

  }

  @inline private def toInt(b: Byte): Int = b & 0xff
  private def showByte(b: Int): String    = "%08d" format (b.toBinaryString.toInt)
}
