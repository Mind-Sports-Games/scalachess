package strategygames.go
import strategygames.MoveMetrics

import strategygames.go.format.Uci

case class Pass(
    situationBefore: Situation,
    after: Board,
    autoEndTurn: Boolean,
    metrics: MoveMetrics = MoveMetrics()
) extends Action(situationBefore, after) {

  def situationAfter =
    Situation(finalizeAfter, if (autoEndTurn) !situationBefore.player else situationBefore.player)

  def applyVariantEffect = this

  def player = situationBefore.player

  def withMetrics(m: MoveMetrics) = copy(metrics = m)

  def toUci = Uci.Pass()

  override def toString = toUci.uci

}
