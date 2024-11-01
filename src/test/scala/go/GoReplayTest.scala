package strategygames.go

import org.specs2.matcher.ValidatedMatchers
import org.specs2.mutable.Specification

import format.{ Forsyth, Uci }

import strategygames.go.format.FEN
import strategygames.Player

class GoReplayTest extends Specification with ValidatedMatchers {

  "go replay " should {
    "replay from position " in {
      val fen   = FEN("""9/9/6S2/9/9/9/2S6/9/9[SSSSSSSSSSssssssssss] w - 810 65 65 0 1""")
      val moves =
        """s@g3 s@c7 s@f2 s@e5 s@e1 s@d4 s@h4 s@i1 s@i5 pass pass ss:i1""".split(' ').toList.map(List(_))
      Replay.gameWithUciWhileValid(moves, Player.P2, Player.P2, fen, variant.Go9x9) must beLike {
        case (_, games, None)         =>
          games.size must_== 12
        case (init, games, Some(err)) =>
          println(err)
          println(init)
          games.size must_== 12
      }
    }
  }

  "go replay 2" should {
    "replay from uci moves" in {
      Replay.situationsFromUci(
        ucis = List(
          Uci("s@g3"),
          Uci("s@c7"),
          Uci("s@f2"),
          Uci("s@e5"),
          Uci("s@e1"),
          Uci("s@d4"),
          Uci("s@h4"),
          Uci("s@i1"),
          Uci("s@i5"),
          Uci("pass"),
          Uci("pass"),
          Uci("ss:i1")
        ).flatten,
        initialFen = Some(FEN("""9/9/6S2/9/9/9/2S6/9/9[SSSSSSSSSSssssssssss] w - 810 65 0 0 65 0 1""")),
        variant = variant.Go9x9
      ) must beValid.like { situations =>
        situations.map(Forsyth.>>) must_== List(
          FEN("9/9/6S2/9/9/9/2S6/9/9[SSSSSSSSSSssssssssss] w - 810 65 0 0 65 0 1"),
          FEN("9/9/6S2/9/9/9/2S3s2/9/9[SSSSSSSSSSssssssssss] b - 20 75 0 0 65 0 2"),
          FEN("9/9/2S3S2/9/9/9/2S3s2/9/9[SSSSSSSSSSssssssssss] w - 30 75 0 0 65 0 2"),
          FEN("9/9/2S3S2/9/9/9/2S3s2/5s3/9[SSSSSSSSSSssssssssss] b - 30 85 0 0 65 0 3"),
          FEN("9/9/2S3S2/9/4S4/9/2S3s2/5s3/9[SSSSSSSSSSssssssssss] w - 40 85 0 0 65 0 3"),
          FEN("9/9/2S3S2/9/4S4/9/2S3s2/5s3/4s4[SSSSSSSSSSssssssssss] b - 40 95 0 0 65 0 4"),
          FEN("9/9/2S3S2/9/4S4/3S5/2S3s2/5s3/4s4[SSSSSSSSSSssssssssss] w - 50 95 0 0 65 0 4"),
          FEN("9/9/2S3S2/9/4S4/3S3s1/2S3s2/5s3/4s4[SSSSSSSSSSssssssssss] b - 50 105 0 0 65 0 5"),
          FEN("9/9/2S3S2/9/4S4/3S3s1/2S3s2/5s3/4s3S[SSSSSSSSSSssssssssss] w - 60 105 0 0 65 0 5"),
          FEN("9/9/2S3S2/9/4S3s/3S3s1/2S3s2/5s3/4s3S[SSSSSSSSSSssssssssss] b - 60 115 0 0 65 0 6"),
          FEN("9/9/2S3S2/9/4S3s/3S3s1/2S3s2/5s3/4s3S[SSSSSSSSSSssssssssss] w - 60 115 0 0 65 1 6"),
          FEN("9/9/2S3S2/9/4S3s/3S3s1/2S3s2/5s3/4s3S[SSSSSSSSSSssssssssss] b - 60 115 0 0 65 2 7"),
          FEN("9/9/2S3S2/9/4S3s/3S3s1/2S3s2/5s3/4s4[SSSSSSSSSSssssssssss] w - 50 215 0 0 65 3 7")
        )
      }
    }
  }
}
