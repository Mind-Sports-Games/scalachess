package strategygames.fairysf

import variant.Amazons
import strategygames.fairysf.format.FEN

class AmazonsVariantTest extends FairySFTest {

  // https://playstrategy.dev/4eCFkjwt
  val amazonsGame = List(
    "d1d6,d6g9",
    "d10f10,f10e10",
    "a4f9,f9e9",
    "a7a10,a10b10",
    "d6a9,a9b9",
    "j7j10,j10h10",
    "j4j9,j9h9",
    "j10i10,i10i9",
    "j9j10,j10j9" // then p2 has no moves
  )

  "Amazons" should {

    "not have winner from start position" in {
      val position =
        FEN(
          "3q2q3/10/10/q8q/10/10/Q8Q/10/10/3Q2Q3[PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPpppppppppppppppppppppppppppppppppppppppppppppp] w - - 0 1"
        )
      val game     = fenToGame(position, Amazons)
      game must beValid.like {
        case game => {
          game.situation.winner == None must beTrue
        }
      }
    }

    "2176 move from start pos" in {
      val position = Api.positionFromVariant(variant.Amazons)
      position.legalMoves.size must_== 2176
    }

    "have 32 pawn drops from starting d1d6 move" in {
      val position = Api.positionFromVariant(variant.Amazons)
      position.legalMoves.filter(x => x.contains("d1d6")).size must_== 32
    }

    "have openning move d1d6,d6g9" in {
      val position = Api.positionFromVariant(variant.Amazons)
      position.legalMoves.contains("d1d6,d6g9")
    }

    "doesn't allow knight movement of piece" in {
      val position = Api.positionFromVariant(variant.Amazons)
      !position.legalMoves.contains("d1c3")
    }

    "doesn't allow knight movement of arrows" in {
      val position = Api.positionFromVariant(variant.Amazons)
      !position.legalMoves.contains("d4e6")
    }

    "doesn't allow arrows through pieces" in {
      val position = Api.positionFromVariant(variant.Amazons)
      !position.legalMoves.contains("a4a5,a5a9")
    }

    "P1 win in example game" in {
      val position  = Api.positionFromVariant(variant.Amazons)
      val position2 = position.makeMoves(amazonsGame)
      position2.legalMoves.size must_== 0
      position2.gameEnd must_== true
    }

    "Should detect game end from fen" in {
      val position = FEN(
        "qp2pqqpqQ/QP2PQPPpP/10/10/10/10/10/10/10/6Q3[PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPpppppppppppppppppppppppppppppppppppppppppp] b - - 9 5"
      )
      val game     = fenToGame(position, Amazons)
      game must beValid.like {
        case game => {
          game.situation.playable(false) must beFalse
        }
      }
    }

    "Have 25 moves from position" in {
      val fen      = FEN(
        "q*2*qq*q1/Q*2*Q***1/10/10/10/10/10/10/10/6Q2Q[PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPpppppppppppppppppppppppppppppppppppppppppp] b - - 9 5"
      )
      val position = Api.positionFromVariantNameAndFEN(variant.Amazons.key, fen.toString())
      // position.legalMoves.map(println(_))
      position.legalMoves.size must_== 26
    }

    "play moves in two amazon games" in {
      val position  = Api.positionFromVariant(variant.Amazons)
      val position2 = position.makeMoves(List("d1a1,a1a2"))
      val position3 = position.makeMoves(List("g1g9,g9d9"))
      position2.legalMoves.size > position3.legalMoves.size
    }

    "play moves in two different games" in {
      val position  = Api.positionFromVariant(variant.Amazons)
      val position2 = position.makeMoves(List("g1g9,g9d9"))

      val positionShogi  = Api.positionFromVariant(variant.Shogi)
      val positionShogi2 =
        positionShogi.makeMoves(List("h3h4", "b7b6", "h4h5", "g7g6", "h2h4", "h8c3+", "b2c3"))
      // positionShogi2.legalMoves.map(println(_))
      val position3      = position2.makeMoves(List("j7e2,e2d2", "j4g7,g7b7"))

      position3.legalMoves.size > positionShogi2.legalMoves.size
    }

    "play part of a game" in {
      val position  = Api.positionFromVariant(variant.Amazons)
      val position2 = position.makeMoves(
        List(
          "d1d4,d4d5",
          "d10d7,d7d6",
          "g1g4,g4g5",
          "g10g7,g7g6",
          "d4e4,e4e5",
          "d7e7,e7e6",
          "g4f4,f4f5",
          "g7f7,f7f6",
          "a4c4,c4c5",
          "a7c7,c7c6",
          "c4b4,b4b5",
          "c7b7,b7b6",
          "b4a4,a4a5",
          "b7a7,a7a6",
          "j4h4,h4h5",
          "j7h7,h7h6",
          "h4i4,i4i5",
          "h7i7,i7i6",
          "i4j4,j4j5",
          "i7j7,j7j6"
        )
      )

      position2.gameEnd must_== false
      position2.fen.toString() must_== "10/10/10/q3qq3q/**********/**********/Q3QQ3Q/10/10/10 w - - 20 11"
    }

  }
}
