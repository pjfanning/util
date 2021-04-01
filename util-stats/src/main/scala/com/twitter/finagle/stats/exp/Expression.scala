package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.exp.Expression.HistogramComponent
import com.twitter.finagle.stats.{HistogramSchema, MetricSchema, StatsReceiver}
import scala.annotation.varargs

private[twitter] object Expression {

  sealed trait HistogramComponent
  case object Min extends HistogramComponent
  case object Max extends HistogramComponent
  case object Avg extends HistogramComponent
  case object Sum extends HistogramComponent
  case object Count extends HistogramComponent

  private[exp] def getStatsReceivers(expr: Expression): Set[StatsReceiver] = expr match {
    case FunctionExpression(_, exprs) =>
      exprs.foldLeft(Set.empty[StatsReceiver]) {
        case (acc, expr) =>
          getStatsReceivers(expr) ++ acc
      }
    case MetricExpression(schema) => Set(schema.metricBuilder.statsReceiver)
    case HistogramExpression(schema, _) => Set(schema.metricBuilder.statsReceiver)
    case ConstantExpression(_) => Set.empty
  }

  /**
   * Create a expression with a constant number in double
   */
  def apply(num: Double): Expression = ConstantExpression(num.toString)

  /**
   * Create a histogram expression
   * @param component the histogram component either a [[HistogramComponent]] for Left
   *                  or a percentile in Double for Right.
   */
  def apply(schema: HistogramSchema, component: Either[HistogramComponent, Double]) =
    HistogramExpression(schema, component)

  /**
   * Create an single expression wrapping a counter or gauge.
   */
  def apply(schema: MetricSchema): Expression = {
    require(!schema.isInstanceOf[HistogramSchema], "provide a component for histogram")
    MetricExpression(schema)
  }
}

/**
 * Metrics with their arithmetical(or others) calculations
 */
private[twitter] sealed trait Expression {
  def plus(other: Expression): Expression = FunctionExpression("plus", Seq(this, other))

  def minus(other: Expression): Expression = FunctionExpression("minus", Seq(this, other))

  def divide(other: Expression): Expression = FunctionExpression("divide", Seq(this, other))

  def multiply(other: Expression): Expression =
    FunctionExpression("multiply", Seq(this, other))

  @varargs
  def func(name: String, rest: Expression*): Expression =
    FunctionExpression(name, this +: rest)
}

/**
 * Represents a constant double number
 */
case class ConstantExpression private (repr: String) extends Expression

/**
 * Represents compound metrics and their arithmetical(or others) calculations
 */
case class FunctionExpression private (fnName: String, exprs: Seq[Expression]) extends Expression {
  require(exprs.size != 0, "Functions must have at least 1 argument")
}

/**
 * Represents the leaf metrics
 */
case class MetricExpression private (schema: MetricSchema) extends Expression

/**
 * Represent a histogram expression with specified component, for example the average, or a percentile
 * @param component either a [[HistogramComponent]] or a percentile in Double
 */
case class HistogramExpression private (
  schema: HistogramSchema,
  component: Either[HistogramComponent, Double])
    extends Expression
