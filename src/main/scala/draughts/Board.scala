package strategygames.draughts

import strategygames.Player
import variant.Variant

case class Board(
    pieces: PieceMap,
    history: DraughtsHistory,
    variant: Variant
) {

  def apply(at: Pos): Option[Piece] = pieces get at

  def apply(at: List[Pos]): Option[List[Piece]] = {
    val pieceList = at.flatMap(pieces.get)
    if (pieceList.isEmpty) None
    else Some(pieceList)
  }

  def apply(x: Int, y: Int): Option[Piece] = posAt(x, y) flatMap pieces.get
  def apply(field: Int): Option[Piece]     = posAt(field) flatMap pieces.get

  def boardSize = variant.boardSize

  def posAt(x: Int, y: Int): Option[PosMotion] = variant.boardSize.pos.posAt(x, y)
  def posAt(field: Int): Option[PosMotion]     = variant.boardSize.pos.posAt(field)
  def posAt(pos: Pos): PosMotion               = variant.boardSize.pos.posAt(pos.fieldNumber).get

  lazy val actors: Map[Pos, Actor] = pieces map { case (pos, piece) =>
    (pos, Actor(piece, posAt(pos), this))
  }

  lazy val actorsOf: Player.Map[Seq[Actor]] = {
    val (w, b) = actors.values.toSeq.partition {
      _.player.p1
    }
    Player.Map(w, b)
  }

  def nonGhostActorsForPlayer(p: Player) = actorsOf(p).filterNot(_.piece.isGhost)

  lazy val ghosts = pieces.values.count(_.isGhost)

  def roleCount(r: Role): Int = pieces.values.count(_.role == r)

  def rolesOf(c: Player): List[Role] = pieces.values
    .collect {
      case piece if piece.player == c => piece.role
    }
    .to(List)

  def actorAt(at: Pos): Option[Actor] = actors get at

  def piecesOf(c: Player): Map[Pos, Piece] = pieces filter (_._2 is c)

  lazy val kingPos: Map[Player, Pos] = pieces.collect { case (pos, Piece(player, King)) =>
    player -> pos
  }

  def kingPosOf(c: Player): Option[Pos] = kingPos get c

  def seq(actions: (Board => Option[Board])*): Option[Board] =
    actions.foldLeft(Option(this): Option[Board])(_ flatMap _)

  def place(piece: Piece) = new {
    def at(at: Pos): Option[Board] =
      if (pieces contains at) None
      else Option(copy(pieces = pieces + ((at, piece))))
  }

  def place(piece: Piece, at: Pos): Option[Board] =
    if (pieces contains at) None
    else Some(copy(pieces = pieces + ((at, piece))))

  def take(at: Pos): Option[Board] = pieces get at map { _ =>
    copy(pieces = pieces - at)
  }

  def withoutGhosts = copy(
    pieces = pieces.filterValues(!_.isGhost)
  )

  def move(orig: Pos, dest: Pos): Option[Board] =
    if (pieces contains dest) None
    else
      pieces get orig map { piece =>
        copy(pieces = pieces - orig + (dest -> piece))
      }

  def moveUnsafe(orig: Pos, dest: Pos, piece: Piece): Board =
    copy(pieces = pieces - orig + (dest -> piece))

  def taking(orig: Pos, dest: Pos, taking: Pos): Option[Board] =
    if (pieces contains dest) None
    else
      for {
        piece <- pieces get orig
        taken <- pieces get taking
      } yield copy(pieces =
        pieces.updated(taking, Piece(taken.player, taken.ghostRole)) - orig + (dest -> piece)
      )

  def takingUnsafe(orig: Pos, dest: Pos, piece: Piece, taking: Pos, taken: Piece): Board =
    copy(pieces = pieces.updated(taking, Piece(taken.player, taken.ghostRole)) - orig + (dest -> piece))

  lazy val occupation: Player.Map[Set[Pos]] = Player.Map { player =>
    pieces.collect { case (pos, piece) if piece is player => pos }.to(Set)
  }

  def hasPiece(p: Piece) = pieces.values exists (p ==)

  def promote(pos: Pos): Option[Board] = for {
    piece <- apply(pos)
    if piece is Man
    b2    <- take(pos)
    b3    <- b2.place(Piece(piece.player, King), pos)
  } yield b3

  def withHistory(h: DraughtsHistory): Board = copy(history = h)

  def withPieces(newPieces: PieceMap) = copy(pieces = newPieces)

  def withVariant(v: Variant): Board = {
    copy(variant = v)
  }

  def updateHistory(f: DraughtsHistory => DraughtsHistory) = copy(history = f(history))

  def count(r: Role, c: Player): Int = pieces.values count (p => p.role == r && p.player == c)

  def count(p: Piece): Int = pieces.values count (_ == p)

  def count(c: Player): Int = pieces.values count (_.player == c)

  def pieceCount: Int = pieces.values.size

  def piecesOnLongDiagonal = actors.values.count(_.onLongDiagonal)

  def autoDraw: Boolean =
    ghosts == 0 && variant.maxDrawingMoves(this).fold(false)(m => history.halfMoveClock >= m)

  def situationOf(player: Player) = Situation(this, player)

  def valid(strict: Boolean) = variant.valid(this, strict)

  def materialImbalance: Int = pieces.values.foldLeft(0) { case (acc, Piece(player, role)) =>
    Role.valueOf(role).fold(acc) { value =>
      acc + value * player.fold(1, -1)
    }
  }

  override def toString = s"$variant Position after ${history.lastMove}: $pieces"
}

object Board {

  def apply(pieces: Iterable[(Pos, Piece)], variant: Variant): Board =
    Board(pieces.toMap, DraughtsHistory(), variant)

  def init(variant: Variant): Board = Board(variant.pieces, variant)

  def empty(variant: Variant): Board = Board(Nil, variant)

  sealed abstract class BoardSize(
      val pos: BoardPos,
      val width: Int,
      val height: Int
  ) {
    val key   = (width * height).toString
    val sizes = List(width, height)

    val fields        = (width * height) / 2
    val promotableYP1 = 1
    val promotableYP2 = height
  }
  object BoardSize {
    val all: List[BoardSize] = List(D100, D64)
    val max                  = D100.pos
  }

  case object D100
      extends BoardSize(
        pos = Pos100,
        width = 10,
        height = 10
      )
  case object D64
      extends BoardSize(
        pos = Pos64,
        width = 8,
        height = 8
      )
}
