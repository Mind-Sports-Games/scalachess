package strategygames

import cats.data.Validated
import cats.implicits._

import variant.Variant
import format.{ FEN, Uci }

sealed abstract class Replay(val setup: Game, val moves: List[MoveOrDrop], val state: Game) {

  lazy val chronoMoves = moves.reverse

  // https://stackoverflow.com/questions/53016370/how-to-convert-a-list-to-list-of-lists-by-group-the-elements-when-an-element-rep
  lazy val chronoActions: List[List[MoveOrDrop]] =
    chronoMoves
      .drop(1)
      .foldLeft(List(chronoMoves.take(1))) { case (turn, mod) =>
        if (turn.head.head.fold(_.situationBefore.player, _.situationBefore.player) != mod.fold(_.situationBefore.player, _.situationBefore.player)) {
          List(mod) +: turn
        } else {
          (turn.head :+ mod) +: turn.tail
        }
      }
      .reverse

  def moveAtPly(ply: Int): Option[MoveOrDrop] =
    chronoMoves lift (ply - 1 - setup.startedAtTurn)

  // TODO: If we had a case class this would be automatic.
  def copy(state: Game): Replay

}

//lots of methods not wrapped, due to differences of Traversable/Iterable, and FEN/String
object Replay {

  final case class Chess(r: chess.Replay)
      extends Replay(
        Game.Chess(r.setup),
        r.moves.map(m =>
          m match {
            case Left(m)  => Left(Move.Chess(m))
            case Right(d) => Right(Drop.Chess(d))
          }
        ),
        Game.Chess(r.state)
      ) {
    def copy(state: Game): Replay = state match {
      case Game.Chess(state) => Replay.wrap(r.copy(state = state))
      case _                 => sys.error("Unable to copy a chess replay with a non-chess state")
    }
  }

  final case class Draughts(r: draughts.Replay)
      extends Replay(
        Game.Draughts(r.setup),
        r.moves.map((m: draughts.Move) => Left(Move.Draughts(m))),
        Game.Draughts(r.state)
      ) {
    def copy(state: Game): Replay = state match {
      case Game.Draughts(state) => Replay.wrap(r.copy(state = state))
      case _                    => sys.error("Unable to copy a draughts replay with a non-draughts state")
    }
  }

  final case class FairySF(r: fairysf.Replay)
      extends Replay(
        Game.FairySF(r.setup),
        r.moves.map(m =>
          m match {
            case Left(m)  => Left(Move.FairySF(m))
            case Right(d) => Right(Drop.FairySF(d))
          }
        ),
        Game.FairySF(r.state)
      ) {
    def copy(state: Game): Replay = state match {
      case Game.FairySF(state) => Replay.wrap(r.copy(state = state))
      case _                   => sys.error("Unable to copy a fairysf replay with a non-fairysf state")
    }
  }

  final case class Samurai(r: samurai.Replay)
      extends Replay(
        Game.Samurai(r.setup),
        r.moves.map((m: samurai.Move) => Left(Move.Samurai(m))),
        Game.Samurai(r.state)
      ) {
    def copy(state: Game): Replay = state match {
      case Game.Samurai(state) => Replay.wrap(r.copy(state = state))
      case _                   => sys.error("Unable to copy a samurai replay with a non-samurai state")
    }
  }

  final case class Togyzkumalak(r: togyzkumalak.Replay)
      extends Replay(
        Game.Togyzkumalak(r.setup),
        r.moves.map((m: togyzkumalak.Move) => Left(Move.Togyzkumalak(m))),
        Game.Togyzkumalak(r.state)
      ) {
    def copy(state: Game): Replay = state match {
      case Game.Togyzkumalak(state) => Replay.wrap(r.copy(state = state))
      case _                        => sys.error("Unable to copy a togyzkumalak replay with a non-togyzkumalak state")
    }
  }

  def apply(lib: GameLogic, setup: Game, moves: List[MoveOrDrop], state: Game): Replay =
    (lib, setup, state) match {
      case (GameLogic.Draughts(), Game.Draughts(setup), Game.Draughts(state))             =>
        Draughts(draughts.Replay(setup, moves.map(Move.toDraughts), state))
      case (GameLogic.Chess(), Game.Chess(setup), Game.Chess(state))                      =>
        Chess(chess.Replay(setup, moves.map(Move.toChess), state))
      case (GameLogic.FairySF(), Game.FairySF(setup), Game.FairySF(state))                =>
        FairySF(fairysf.Replay(setup, moves.map(Move.toFairySF), state))
      case (GameLogic.Samurai(), Game.Samurai(setup), Game.Samurai(state))                =>
        Samurai(samurai.Replay(setup, moves.map(Move.toSamurai), state))
      case (GameLogic.Togyzkumalak(), Game.Togyzkumalak(setup), Game.Togyzkumalak(state)) =>
        Togyzkumalak(togyzkumalak.Replay(setup, moves.map(Move.toTogyzkumalak), state))
      case _                                                                              => sys.error("Mismatched gamelogic types 5")
    }

  def gamePlyWhileValid(
      lib: GameLogic,
      actions: Actions,
      initialFen: FEN,
      variant: Variant,
      iteratedCapts: Boolean = false
  ): (Game, List[(Game, Uci.WithSan)], Option[String]) = (lib, initialFen, variant) match {
    case (GameLogic.Draughts(), FEN.Draughts(initialFen), Variant.Draughts(variant))             =>
      draughts.Replay.gamePlyWhileValid(actions, initialFen, variant, iteratedCapts) match {
        case (game, gameswithsan, message) =>
          (
            Game.Draughts(game),
            gameswithsan.map { case (g, u) => (Game.Draughts(g), Uci.DraughtsWithSan(u)) },
            message
          )
      }
    case (GameLogic.Chess(), FEN.Chess(initialFen), Variant.Chess(variant))                      =>
      chess.Replay.gamePlyWhileValid(actions, initialFen, variant) match {
        case (game, gameswithsan, message) =>
          (
            Game.Chess(game),
            gameswithsan.map { case (g, u) => (Game.Chess(g), Uci.ChessWithSan(u)) },
            message
          )
      }
    case (GameLogic.FairySF(), FEN.FairySF(initialFen), Variant.FairySF(variant))                =>
      fairysf.Replay.gamePlyWhileValid(actions, initialFen, variant) match {
        case (game, gameswithsan, message) =>
          (
            Game.FairySF(game),
            gameswithsan.map { case (g, u) => (Game.FairySF(g), Uci.FairySFWithSan(u)) },
            message
          )
      }
    case (GameLogic.Samurai(), FEN.Samurai(initialFen), Variant.Samurai(variant))                =>
      samurai.Replay.gamePlyWhileValid(actions, initialFen, variant) match {
        case (game, gameswithsan, message) =>
          (
            Game.Samurai(game),
            gameswithsan.map { case (g, u) => (Game.Samurai(g), Uci.SamuraiWithSan(u)) },
            message
          )
      }
    case (GameLogic.Togyzkumalak(), FEN.Togyzkumalak(initialFen), Variant.Togyzkumalak(variant)) =>
      togyzkumalak.Replay.gamePlyWhileValid(actions, initialFen, variant) match {
        case (game, gameswithsan, message) =>
          (
            Game.Togyzkumalak(game),
            gameswithsan.map { case (g, u) => (Game.Togyzkumalak(g), Uci.TogyzkumalakWithSan(u)) },
            message
          )
      }
    case _                                                                                       => sys.error("Mismatched gamelogic types 7")
  }

  def boards(
      lib: GameLogic,
      actions: Actions,
      initialFen: Option[FEN],
      variant: Variant,
      finalSquare: Boolean = false
  ): Validated[String, List[Board]] =
    situations(lib, actions, initialFen, variant, finalSquare) map (_ map (_.board))

  def situations(
      lib: GameLogic,
      actions: Actions,
      initialFen: Option[FEN],
      variant: Variant,
      finalSquare: Boolean = false
  ): Validated[String, List[Situation]] = (lib, variant) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant))         =>
      draughts.Replay
        .situations(actions, initialFen.map(_.toDraughts), variant, finalSquare)
        .toEither
        .map(s => s.map(Situation.Draughts))
        .toValidated
    case (GameLogic.Chess(), Variant.Chess(variant))               =>
      chess.Replay
        .situations(actions, initialFen.map(_.toChess), variant)
        .toEither
        .map(s => s.map(Situation.Chess))
        .toValidated
    case (GameLogic.FairySF(), Variant.FairySF(variant))           =>
      fairysf.Replay
        .situations(actions, initialFen.map(_.toFairySF), variant)
        .toEither
        .map(s => s.map(Situation.FairySF))
        .toValidated
    case (GameLogic.Samurai(), Variant.Samurai(variant))           =>
      samurai.Replay
        .situations(actions, initialFen.map(_.toSamurai), variant)
        .toEither
        .map(s => s.map(Situation.Samurai))
        .toValidated
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant)) =>
      togyzkumalak.Replay
        .situations(actions, initialFen.map(_.toTogyzkumalak), variant)
        .toEither
        .map(s => s.map(Situation.Togyzkumalak))
        .toValidated
    case _                                                         => sys.error("Mismatched gamelogic types 8")
  }

  private def draughtsUcis(ucis: List[Uci]): List[draughts.format.Uci] =
    ucis.flatMap(u =>
      u match {
        case u: Uci.Draughts => Some(u.unwrap)
        case _               => None
      }
    )

  private def chessUcis(ucis: List[Uci]): List[chess.format.Uci] =
    ucis.flatMap(u =>
      u match {
        case u: Uci.Chess => Some(u.unwrap)
        case _            => None
      }
    )

  private def fairysfUcis(ucis: List[Uci]): List[fairysf.format.Uci] =
    ucis.flatMap(u =>
      u match {
        case u: Uci.FairySF => Some(u.unwrap)
        case _              => None
      }
    )

  private def samuraiUcis(ucis: List[Uci]): List[samurai.format.Uci] =
    ucis.flatMap(u =>
      u match {
        case u: Uci.Samurai => Some(u.unwrap)
        case _              => None
      }
    )

  private def togyzkumalakUcis(ucis: List[Uci]): List[togyzkumalak.format.Uci] =
    ucis.flatMap(u =>
      u match {
        case u: Uci.Togyzkumalak => Some(u.unwrap)
        case _                   => None
      }
    )

  def boardsFromUci(
      lib: GameLogic,
      moves: List[Uci],
      initialFen: Option[FEN],
      variant: Variant,
      finalSquare: Boolean = false
  ): Validated[String, List[Board]] = (lib, variant) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant))         =>
      draughts.Replay
        .boardsFromUci(
          draughtsUcis(moves),
          initialFen.map(_.toDraughts),
          variant,
          finalSquare
        )
        .toEither
        .map(b => b.map(Board.Draughts))
        .toValidated
    case (GameLogic.Chess(), Variant.Chess(variant))               =>
      chess.Replay
        .boardsFromUci(chessUcis(moves), initialFen.map(_.toChess), variant)
        .toEither
        .map(b => b.map(Board.Chess))
        .toValidated
    case (GameLogic.FairySF(), Variant.FairySF(variant))           =>
      fairysf.Replay
        .boardsFromUci(fairysfUcis(moves), initialFen.map(_.toFairySF), variant)
        .toEither
        .map(b => b.map(Board.FairySF))
        .toValidated
    case (GameLogic.Samurai(), Variant.Samurai(variant))           =>
      samurai.Replay
        .boardsFromUci(samuraiUcis(moves), initialFen.map(_.toSamurai), variant)
        .toEither
        .map(b => b.map(Board.Samurai))
        .toValidated
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant)) =>
      togyzkumalak.Replay
        .boardsFromUci(togyzkumalakUcis(moves), initialFen.map(_.toTogyzkumalak), variant)
        .toEither
        .map(b => b.map(Board.Togyzkumalak))
        .toValidated
    case _                                                         => sys.error("Mismatched gamelogic types 8a")
  }

  def situationsFromUci(
      lib: GameLogic,
      moves: List[Uci],
      initialFen: Option[FEN],
      variant: Variant,
      finalSquare: Boolean = false
  ): Validated[String, List[Situation]] = (lib, variant) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant))         =>
      draughts.Replay
        .situationsFromUci(draughtsUcis(moves), initialFen.map(_.toDraughts), variant, finalSquare)
        .toEither
        .map(s => s.map(Situation.Draughts))
        .toValidated
    case (GameLogic.Chess(), Variant.Chess(variant))               =>
      chess.Replay
        .situationsFromUci(chessUcis(moves), initialFen.map(_.toChess), variant)
        .toEither
        .map(s => s.map(Situation.Chess))
        .toValidated
    case (GameLogic.FairySF(), Variant.FairySF(variant))           =>
      fairysf.Replay
        .situationsFromUci(fairysfUcis(moves), initialFen.map(_.toFairySF), variant)
        .toEither
        .map(s => s.map(Situation.FairySF))
        .toValidated
    case (GameLogic.Samurai(), Variant.Samurai(variant))           =>
      samurai.Replay
        .situationsFromUci(samuraiUcis(moves), initialFen.map(_.toSamurai), variant)
        .toEither
        .map(s => s.map(Situation.Samurai))
        .toValidated
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant)) =>
      togyzkumalak.Replay
        .situationsFromUci(togyzkumalakUcis(moves), initialFen.map(_.toTogyzkumalak), variant)
        .toEither
        .map(s => s.map(Situation.Togyzkumalak))
        .toValidated
    case _                                                         => sys.error("Mismatched gamelogic types 9")
  }

  def apply(
      lib: GameLogic,
      moves: List[Uci],
      initialFen: Option[FEN],
      variant: Variant,
      finalSquare: Boolean = false
  ): Validated[String, Replay] = (lib, variant) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant))         =>
      draughts
        .Replay(draughtsUcis(moves), initialFen.map(_.toDraughts), variant, finalSquare)
        .toEither
        .map(r => Replay.Draughts(r))
        .toValidated
    case (GameLogic.Chess(), Variant.Chess(variant))               =>
      chess
        .Replay(chessUcis(moves), initialFen.map(_.toChess), variant)
        .toEither
        .map(r => Replay.Chess(r))
        .toValidated
    case (GameLogic.FairySF(), Variant.FairySF(variant))           =>
      fairysf
        .Replay(fairysfUcis(moves), initialFen.map(_.toFairySF), variant)
        .toEither
        .map(r => Replay.FairySF(r))
        .toValidated
    case (GameLogic.Samurai(), Variant.Samurai(variant))           =>
      samurai.Replay
        .apply(samuraiUcis(moves), initialFen.map(_.toSamurai), variant)
        .toEither
        .map(r => Replay.Samurai(r))
        .toValidated
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant)) =>
      togyzkumalak.Replay
        .apply(togyzkumalakUcis(moves), initialFen.map(_.toTogyzkumalak), variant)
        .toEither
        .map(r => Replay.Togyzkumalak(r))
        .toValidated
    case _                                                         => sys.error("Mismatched gamelogic types 10")
  }

  def plyAtFen(
      lib: GameLogic,
      actions: Actions,
      initialFen: Option[FEN],
      variant: Variant,
      atFen: FEN
  ): Validated[String, Int] = (lib, variant, atFen) match {
    case (GameLogic.Draughts(), Variant.Draughts(variant), FEN.Draughts(atFen))             =>
      draughts.Replay.plyAtFen(actions, initialFen.map(_.toDraughts), variant, atFen)
    case (GameLogic.Chess(), Variant.Chess(variant), FEN.Chess(atFen))                      =>
      chess.Replay.plyAtFen(actions, initialFen.map(_.toChess), variant, atFen)
    case (GameLogic.FairySF(), Variant.FairySF(variant), FEN.FairySF(atFen))                =>
      fairysf.Replay.plyAtFen(actions, initialFen.map(_.toFairySF), variant, atFen)
    case (GameLogic.Samurai(), Variant.Samurai(variant), FEN.Samurai(atFen))                =>
      samurai.Replay.plyAtFen(actions, initialFen.map(_.toSamurai), variant, atFen)
    case (GameLogic.Togyzkumalak(), Variant.Togyzkumalak(variant), FEN.Togyzkumalak(atFen)) =>
      togyzkumalak.Replay.plyAtFen(actions, initialFen.map(_.toTogyzkumalak), variant, atFen)
    case _                                                                                  => sys.error("Mismatched gamelogic types 10")
  }

  def wrap(r: chess.Replay)        = Chess(r)
  def wrap(r: draughts.Replay)     = Draughts(r)
  def wrap(r: fairysf.Replay)      = FairySF(r)
  def wrap(r: samurai.Replay)      = Samurai(r)
  def wrap(r: togyzkumalak.Replay) = Togyzkumalak(r)

}
