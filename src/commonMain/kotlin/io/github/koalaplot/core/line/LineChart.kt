package io.github.koalaplot.core.line

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import io.github.koalaplot.core.util.HoverableElementAreaScope
import io.github.koalaplot.core.xychart.LineStyle
import io.github.koalaplot.core.xychart.XYChartScope

/**
 * An XY Chart that draws series as points and lines.
 * @param X The type of the x-axis values
 * @param Y The type of the y-axis values
 * @param P The type of the line plot data points
 * @param data Data series to plot.
 * @param lineStyle Style to use for the line that connects the data points. If null, no line is
 * drawn.
 * @param symbol Composable for the symbol to be shown at each data point.
 * @param modifier Modifier for the chart.
 */
@Composable
public fun <X, Y, P : Point<X, Y>> XYChartScope<X, Y>.LineChart(
    data: List<P>,
    lineStyle: LineStyle? = null,
    symbol: (@Composable HoverableElementAreaScope.(P) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GeneralLineChart(data, lineStyle, symbol, modifier) { lastPoint, point, actualY, isFirstLine ->
        val strokeWidthPx = lineStyle!!.strokeWidth.toPx()
        drawLine(
            brush = lineStyle.brush,
            lastPoint,
            point,
            strokeWidth = strokeWidthPx,
            pathEffect = lineStyle.pathEffect,
            alpha = lineStyle.alpha,
            colorFilter = lineStyle.colorFilter,
            blendMode = lineStyle.blendMode
        )

        if (!lineStyle.filledArea) {
            return@GeneralLineChart
        }

        val slope = (point.y - lastPoint.y) / (point.x - lastPoint.x)
        val startY = -(slope * lastPoint.x) + lastPoint.y

/*        val previousSlope = if (lastPointPrev != null && pointPrev != null) {
            ((pointPrev as Float) - (lastPointPrev as Float)) / (point.x - lastPoint.x)
        } else {
            0.0f
        }
        val previousStartY = if (previousSlope != 0.0f) {
            -(previousSlope * lastPoint.x) + (lastPointPrev as Float)
        } else {
            0.0f
        }*/

        var currentX = lastPoint.x
        while (currentX < point.x) {
            //println("result: ${(previousSlope * currentX) + previousStartY}; lastPointPrev: $lastPointPrev; pointPrev: $pointPrev; previousSlope: $previousSlope; previousStartY: $previousStartY")
            val topPoint = (slope * currentX) + startY
            val bottomPoint = if (isFirstLine) {
                0.0f
            } else {
                topPoint - actualY
            }
            // println("topPoint: $topPoint; actualY: $actualY; result: $bottomPoint")
            drawLine(
                brush = lineStyle.brush,
                lastPoint.copy(currentX, y = topPoint),
                lastPoint.copy(currentX, y = bottomPoint),
                strokeWidth = strokeWidthPx,
                pathEffect = lineStyle.pathEffect,
                alpha = 0.2f,
                colorFilter = lineStyle.colorFilter,
                blendMode = lineStyle.blendMode
            )
            currentX += strokeWidthPx
        }
    }
}

/**
 * An XY Chart that draws series as points and stairsteps between points.
 * @param X The type of the x-axis values
 * @param Y The type of the y-axis values
 * @param P The type of the line plot data points
 * @param data Data series to plot.
 * @param lineStyle Style to use for the line that connects the data points.
 * @param symbol Composable for the symbol to be shown at each data point.
 * @param modifier Modifier for the chart.
 */
@Composable
public fun <X, Y, P : Point<X, Y>> XYChartScope<X, Y>.StairstepChart(
    data: List<P>,
    lineStyle: LineStyle,
    symbol: (@Composable HoverableElementAreaScope.(P) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GeneralLineChart(data, lineStyle, symbol, modifier) { lastPoint, point, _, _ ->
        val strokeWidthPx = lineStyle.strokeWidth.toPx()
        val midPoint = lastPoint.copy(x = point.x)
        drawLine(
            brush = lineStyle.brush,
            lastPoint,
            midPoint,
            strokeWidth = strokeWidthPx,
            pathEffect = lineStyle.pathEffect,
            alpha = lineStyle.alpha,
            colorFilter = lineStyle.colorFilter,
            blendMode = lineStyle.blendMode
        )
        drawLine(
            brush = lineStyle.brush,
            midPoint,
            point,
            strokeWidth = strokeWidthPx,
            pathEffect = lineStyle.pathEffect,
            alpha = lineStyle.alpha,
            colorFilter = lineStyle.colorFilter,
            blendMode = lineStyle.blendMode
        )
    }
}

/**
 * An XY Chart that draws series as points and lines.
 * @param X The type of the x-axis values
 * @param Y The type of the y-axis values
 * @param P The type of the line plot data points
 * @param data Data series to plot.
 * @param lineStyle Style to use for the line that connects the data points. If null, no line is
 * drawn.
 * @param symbol Composable for the symbol to be shown at each data point.
 * @param modifier Modifier for the chart.
 */
@Composable
private fun <X, Y, P : Point<X, Y>> XYChartScope<X, Y>.GeneralLineChart(
    data: List<P>,
    lineStyle: LineStyle? = null,
    symbol: (@Composable HoverableElementAreaScope.(P) -> Unit)? = null,
    modifier: Modifier = Modifier,
    drawConnectorLine: DrawScope.(lastPoint: Offset, point: Offset, actualHeight: Float, isFirstLine: Boolean) -> Unit,
) {
    Layout(modifier = modifier, content = {
        Canvas(modifier = Modifier.fillMaxSize()) {
            scale(scaleX = 1f, scaleY = -1f) {
                if (lineStyle != null && data.isNotEmpty()) {
                    var lastPoint =
                        scale(data[0], size) // to prevent scaling every point twice
                    for (pointIndex in 1..data.lastIndex) {
                        val point = scale(data[pointIndex], size)
                        drawConnectorLine(lastPoint.offset, point.offset, point.actualY, point.isFirstLine)
                        lastPoint = point
                    }
                }
            }
        }
        Symbols(data, symbol)
    }) { measurables: List<Measurable>, constraints: Constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {
            measurables.forEach {
                it.measure(constraints).place(0, 0)
            }
        }
    }
}

@Composable
private fun <X, Y, P : Point<X, Y>> XYChartScope<X, Y>.Symbols(
    data: List<P>,
    symbol: (@Composable HoverableElementAreaScope.(P) -> Unit)? = null
) {
    if (symbol != null) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                data.indices.forEach {
                    symbol.invoke(this, data[it])
                }
            }
        ) { measurables: List<Measurable>, constraints: Constraints ->
            val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

            layout(constraints.maxWidth, constraints.maxHeight) {
                data.indices.forEach {
                    val p = measurables[it].measure(constraints.copy(minWidth = 0, minHeight = 0))
                    var position = scale(data[it], size).offset
                    position = position.copy(y = constraints.maxHeight - position.y)
                    position -= Offset(p.width / 2f, p.height / 2f)
                    p.place(position.x.toInt(), position.y.toInt())
                }
            }
        }
    }
}

private fun <X, Y> XYChartScope<X, Y>.scale(
    offset: Point<X, Y>,
    size: Size
): OffsetWrapped<Y> {
    return OffsetWrapped(
        Offset(
            xAxisModel.computeOffset(offset.x) * size.width,
            yAxisModel.computeOffset(offset.y) * size.height
        ),
        yAxisModel.computeOffset(offset.actualHeight) * size.height,
        offset.isFirstLine,
    )
}

@Immutable
private data class OffsetWrapped<Y>(val offset: Offset, val actualY: Float, val isFirstLine: Boolean)

/**
 * Represents a point on a LineChart.
 * @param X The type of the x-axis values
 * @param Y The type of the y-axis values
 */
public interface Point<X, Y> {
    /**
     * The x-axis value of this Point.
     */
    public val x: X

    /**
     * The y-axis value of this Point.
     */
    public val y: Y

    /**
     * The actual height of this column if not stacked
     * Used for StackedAreaGraph
     */
    public val actualHeight: Y
        get() = y

    /**
     * If this is the first line in the stack
     * Used for StackedAreaGraph
     */
    public val isFirstLine: Boolean
}

// preferred naming per API Guidelines for Jetpack Compose
// See https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md
/**
 * Creates a new DefaultPoint at the specified coordinates.
 * @param X The type of the x-axis value
 * @param Y The type of the y-axis value
 */
@Suppress("FunctionNaming")
public fun <X, Y> Point(x: X, y: Y, actualHeight: Y = y): Point<X, Y> = DefaultPoint(x, y, actualHeight, y == actualHeight)

/**
 * Default implementation of the Point interface.
 * @param X The type of the x-axis values
 * @param Y The type of the y-axis values
 */
public data class DefaultPoint<X, Y>(override val x: X, override val y: Y, override val actualHeight: Y = y,
                                     override val isFirstLine: Boolean = true) :
    Point<X, Y>
