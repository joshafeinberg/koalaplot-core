package io.github.koalaplot.core.xychart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.theme.KoalaPlotTheme
import io.github.koalaplot.core.util.Deg2Rad
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.HoverableElementArea
import io.github.koalaplot.core.util.HoverableElementAreaScope
import io.github.koalaplot.core.util.Vector
import io.github.koalaplot.core.util.VerticalRotation
import io.github.koalaplot.core.util.length
import io.github.koalaplot.core.util.lineDistance
import io.github.koalaplot.core.util.rotate
import io.github.koalaplot.core.util.rotateVertically
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Provides styling for lines.
 *
 * brush - the color or fill to be applied to the line
 * strokeWidth - stroke width to apply to the line
 * pathEffect - optional effect or pattern to apply to the line
 * alpha - opacity to be applied to the brush from 0.0f to 1.0f representing fully transparent to
 * fully opaque respectively
 * colorFilter - ColorFilter to apply to the brush when drawn into the destination
 * blendMode - the blending algorithm to apply to the brush
 */
public data class LineStyle(
    val brush: Brush,
    val strokeWidth: Dp = 0.dp,
    val pathEffect: PathEffect? = null,
    val alpha: Float = 1.0f,
    val colorFilter: ColorFilter? = null,
    val blendMode: BlendMode = DefaultBlendMode,
    val filledArea: Boolean = false,
)

/**
 * Provides a set of X-Y axes and grid for displaying an X-Y plot.
 *
 * @param X The data type for the x-axis
 * @param Y The data type for the y-axis
 * @param xAxisModel x-axis state controlling the display of the axis and coordinate transformation
 * @param yAxisModel y-axis state controlling the display of the axis and coordinate transformation
 * @param xAxisStyle Style for the x-axis
 * @param xAxisLabels Composable to display labels for specific x-axis values
 * @param xAxisTitle Title for the X-axis
 * @param yAxisStyle Style for the y-axis
 * @param yAxisLabels Composable to display labels for specific y-axis values
 * @param yAxisTitle Title for the y-axis
 * @param content The XY Chart content to be displayed, which should include one chart for each
 * series type to be displayed.
 */
@Composable
public fun <X, Y> XYChart(
    xAxisModel: AxisModel<X>,
    yAxisModel: AxisModel<Y>,
    modifier: Modifier = Modifier,
    xAxisStyle: AxisStyle = rememberAxisStyle(),
    xAxisLabels: @Composable (X) -> Unit,
    xAxisTitle: @Composable () -> Unit = {},
    yAxisStyle: AxisStyle = rememberAxisStyle(),
    yAxisLabels: @Composable (Y) -> Unit,
    yAxisTitle: @Composable () -> Unit = {},
    horizontalMajorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.majorGridlineStyle,
    horizontalMinorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.minorGridlineStyle,
    verticalMajorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.majorGridlineStyle,
    verticalMinorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.minorGridlineStyle,
    content: @Composable XYChartScope<X, Y>.() -> Unit
) {
    HoverableElementArea {
        SubcomposeLayout(modifier = modifier) { constraints ->
            val xAxisTitleMeasurable = subcompose("xaxistitle") {
                Box { xAxisTitle() }
            }[0]

            val yAxisTitleMeasurable = subcompose("yaxistitle") {
                Box { yAxisTitle() }
            }[0]

            // Computing the tick values isn't exact because composables can't be measured twice
            // and there's no way to compute the height/width of the axis tick labels before knowing
            // how many tick labels there should be. So we leave them out of the calculation for
            // the axis length constraints, meaning the actual min distance of the axis tick labels
            // may be closer than specified because the actual axis lengths computed later, which
            // take into account the axis label sizes, will be smaller than the lengths computed here.
            val xAxis = AxisDelegate.createHorizontalAxis(
                xAxisModel,
                xAxisStyle,
                (constraints.maxWidth - -yAxisTitleMeasurable.maxIntrinsicWidth(constraints.maxHeight)).toDp()
            )

            val yAxis = AxisDelegate.createVerticalAxis(
                yAxisModel,
                yAxisStyle,
                (constraints.maxHeight - -xAxisTitleMeasurable.maxIntrinsicHeight(constraints.maxWidth)).toDp()
            )

            val xAxisMeasurable = subcompose("xaxis") { Axis(xAxis) }[0]
            val yAxisMeasurable = subcompose("yaxis") { Axis(yAxis) }[0]

            val chartScope = XYChartScopeImpl(xAxisModel, yAxisModel, xAxis, yAxis, this@HoverableElementArea)

            val chartMeasurable = subcompose("chart") {
                Box(
                    modifier = Modifier.clip(RectangleShape)
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                transformAxis(yAxisModel, size.height, centroid.y, pan.y, zoom)
                                transformAxis(xAxisModel, size.width, centroid.x, -pan.x, zoom)
                            }
                        }
                ) { chartScope.content() }
            }[0]

            val measurables = subcompose(Unit) {
                Grid(
                    xAxis,
                    yAxis,
                    horizontalMajorGridLineStyle,
                    horizontalMinorGridLineStyle,
                    verticalMajorGridLineStyle,
                    verticalMinorGridLineStyle
                )

                xAxis.majorTickValues.forEach {
                    Box(modifier = Modifier.rotate(-xAxisStyle.labelRotation.toFloat())) { xAxisLabels(it) }
                }
                yAxis.majorTickValues.forEach {
                    Box(modifier = Modifier.rotate(-yAxisStyle.labelRotation.toFloat())) { yAxisLabels(it) }
                }
            }

            val measurablesMap = Measurables(
                measurables[0],
                chartMeasurable,
                xAxisMeasurable,
                measurables.subList(1, xAxis.majorTickValues.size + 1),
                xAxisTitleMeasurable,
                yAxisMeasurable,
                measurables.subList(
                    xAxis.majorTickValues.size + 1,
                    xAxis.majorTickValues.size + 1 + yAxis.majorTickValues.size
                ),
                yAxisTitleMeasurable
            )

            with(XYAxisMeasurePolicy(xAxis, yAxis)) {
                measure(measurablesMap, constraints)
            }
        }
    }
}

private fun <T> transformAxis(
    axis: AxisModel<T>,
    length: Int,
    centroid: Float,
    pan: Float,
    zoom: Float
) {
    val pivot = (length.toFloat() - centroid) / length.toFloat()
    axis.zoom(zoom, pivot.coerceIn(0f, 1f))
    axis.pan(pan / length.toFloat())
}

private data class Measurables(
    val grid: Measurable,
    val chart: Measurable,
    val xAxis: Measurable,
    val xAxisLabels: List<Measurable>,
    val xAxisTitle: Measurable,
    val yAxis: Measurable,
    val yAxisLabels: List<Measurable>,
    val yAxisTitle: Measurable,
)

private const val IterationLimit = 10
private const val ChangeThreshold = 0.05

/**
 * Calculates the heights of the x-axis labels constrained by the space available to them.
 * @receiver The measurables for the labels
 * @param labelRotation The angle, in degrees, at which the labels will be drawn
 * @param tickSpacing The spacing between adjacent ticks on the axis
 * @param verticalSpace The amount of vertical space available for the labels.
 * @param measure Measures the measurable given the width constraint and returns an object that can be used to get
 * the height for the measurable using the [getHeight] function.
 * @param getHeight Gets the height for the object returned by [measure]
 */
private fun <T> List<Measurable>.calcXAxisLabelWidthConstraints(
    labelRotation: Int,
    tickSpacing: Int,
    verticalSpace: Int,
    measure: (m: Measurable, width: Int) -> T,
    getHeight: (T) -> Int
): List<T> = buildList {
    var lastHeight = 0
    this@calcXAxisLabelWidthConstraints.forEachIndexed { index, label ->
        val w = if (labelRotation == 0) {
            tickSpacing
        } else {
            if (index == 0) {
                max((verticalSpace / sin(labelRotation * Deg2Rad)).roundToInt(), 0)
            } else { // potentially constrain the width so the label does not overlap the previous label
                val origin = Vector(0f, 0f)
                val p1 = Vector(0f, lastHeight.toFloat())
                val p2 = p1.rotate(-labelRotation.toFloat())
                val (distance, intersection) = lineDistance(
                    origin,
                    p2,
                    Vector(tickSpacing.toFloat(), 0f)
                )

                // Check if the intersection point interferes with the previous label
                // The origin, p2, and intersection are on the same line
                // So check if intersection is closer to p1 than p2
                if (intersection.norm() < p2.norm()) {
                    distance.toInt() // limit allowed width to position of previous label
                } else {
                    max((verticalSpace / sin(labelRotation * Deg2Rad)).roundToInt(), 0)
                }
            }
        }

        val t = measure(label, w)
        add(t)
        lastHeight = getHeight(t)
    }
}

/**
 * Class that holds the dimensions of different areas of the overall Chart to be used for computing the graph area,
 * with helper functions for recomputing the areas.
 */
private data class ChartAreas(
    private val constraints: Constraints,
    val yAxisOffset: Int = 0,
    val xAxisHeight: Int = 0,
    val xAxisOffset: Int = 0,
    val xAxisTitleHeight: Int = 0,
    val xAxisLabelAreaHeight: Int = 0,
    val xAxisFirstLabelExtensionWidth: Int = 0,
    val xAxisLastLabelExtensionWidth: Int = 0,
    val yAxisTitleWidth: Int = 0,
    val yAxisLabelAreaWidth: Int = 0,
    val yAxisFirstLabelExtensionHeight: Int = 0,
    val yAxisLastLabelExtensionHeight: Int = 0
) {
    fun graphSize(): IntSize {
        return IntSize(
            constraints.maxWidth -
                max(
                    yAxisTitleWidth + yAxisLabelAreaWidth + yAxisOffset,
                    xAxisFirstLabelExtensionWidth
                ) - xAxisLastLabelExtensionWidth,
            constraints.maxHeight - max(
                xAxisTitleHeight + xAxisLabelAreaHeight + xAxisHeight - xAxisOffset,
                yAxisFirstLabelExtensionHeight
            ) - yAxisLastLabelExtensionHeight
        )
    }

    /**
     * Calculates the x-axis tick spacing based on the width of the graph size and the provided [number] of ticks.
     */
    fun xTickSpacing(number: Int): Int {
        return graphSize().width / number
    }

    /**
     * Returns a copy of this ChartAreas after setting the [xAxisLabelAreaHeight],
     * [xAxisFirstLabelExtensionWidth], and [xAxisLastLabelExtensionWidth] based on the
     * x-axis labels and their rotation angle.
     */
    fun withComputedXAxisLabelAreas(xAxisLabels: List<Measurable>, rotation: Int): ChartAreas {
        val xAxisLabelHeights = xAxisLabels.calcXAxisLabelWidthConstraints(
            rotation,
            xTickSpacing(xAxisLabels.size),
            constraints.maxHeight - graphSize().height - xAxisHeight - xAxisTitleHeight + xAxisOffset,
            { meas, w -> meas.maxIntrinsicHeight(w) },
            { h -> h }
        )

        val firstXAxisTickLabelWidth = xAxisLabels.firstOrNull()?.maxIntrinsicWidth(
            max(
                0,
                constraints.maxHeight - graphSize().height - xAxisHeight + xAxisOffset - xAxisTitleHeight
            )
        ) ?: 0

        return copy(
            xAxisLabelAreaHeight = xAxisLabels.mapIndexed { index, label ->
                Vector(
                    label.maxIntrinsicWidth(xAxisLabelHeights[index]) / 2f,
                    xAxisLabelHeights[index] / 2f
                ).rotate(rotation.toFloat())[1] * 2f
            }.maxOfOrNull { it.roundToInt() } ?: 0,
            xAxisFirstLabelExtensionWidth = if (rotation == 0) {
                xTickSpacing(xAxisLabels.size) / 2
            } else {
                IntOffset(
                    firstXAxisTickLabelWidth,
                    -(xAxisLabelHeights.firstOrNull() ?: 0) / 2
                ).rotate(rotation).x
            },
            xAxisLastLabelExtensionWidth = if (rotation == 0) {
                xTickSpacing(xAxisLabels.size) / 2
            } else {
                val off =
                    IntOffset(
                        0,
                        (xAxisLabelHeights.lastOrNull() ?: 0) / 2
                    ).rotate(rotation).x
                -off
            }
        )
    }

    /**
     * Returns a copy of this ChartAreas after setting the [yAxisLabelAreaWidth],
     * [yAxisFirstLabelExtensionHeight], and [yAxisLastLabelExtensionHeight] based on the
     * y-axis labels and their rotation angle.
     */
    fun withComputedYAxisLabelAreas(yAxisLabels: List<Measurable>, rotation: Int): ChartAreas {
        val yAxisLabelsWidth = yAxisLabels.map {
            it.maxIntrinsicWidth(graphSize().height / yAxisLabels.size)
        }

        val yAxisLabelsHeight = yAxisLabels.mapIndexed { index, measurable ->
            measurable.maxIntrinsicHeight(yAxisLabelsWidth[index])
        }

        // compute width of the y-axis labels area based on widest space of rotated labels
        return copy(
            yAxisLabelAreaWidth = yAxisLabelsWidth.mapIndexed { index, w ->
                Vector(w / 2f, -yAxisLabelsHeight[index] / 2f).rotate(rotation.toFloat())[0] * 2f
            }.maxOfOrNull { it.roundToInt() } ?: 0,
            yAxisFirstLabelExtensionHeight = yAxisLabels.firstOrNull()?.let {
                val h =
                    it.maxIntrinsicHeight(
                        max(
                            0,
                            constraints.maxWidth - graphSize().width - yAxisOffset - yAxisTitleWidth
                        )
                    )
                val w = it.maxIntrinsicWidth(h)
                @Suppress("MagicNumber")
                if (rotation == 90) {
                    w / 2
                } else {
                    IntOffset(w, h / 2).rotate(rotation).y
                }
            } ?: 0,
            yAxisLastLabelExtensionHeight = yAxisLabels.lastOrNull()?.let {
                val h =
                    it.maxIntrinsicHeight(
                        max(
                            0,
                            constraints.maxWidth - graphSize().width - yAxisOffset - yAxisTitleWidth
                        )
                    )
                val w = it.maxIntrinsicWidth(h)
                @Suppress("MagicNumber")
                if (rotation == 90) {
                    w / 2
                } else {
                    IntOffset(0, h / 2).rotate(rotation).y
                }
            } ?: 0
        )
    }
}

private fun <X, Y> Density.optimizeGraphSize(
    constraints: Constraints,
    m: Measurables,
    xAxis: AxisDelegate<X>,
    yAxis: AxisDelegate<Y>
): IntSize {
    var iterations = 0
    var oldSize: IntSize

    val xAxisHeight = xAxis.thicknessDp.roundToPx()
    val xAxisOffset = xAxis.axisOffset.roundToPx()
    val yAxisOffset = yAxis.axisOffset.roundToPx()
    var chartAreas = ChartAreas(constraints, yAxisOffset, xAxisHeight, xAxisOffset)

    do {
        oldSize = chartAreas.graphSize()

        chartAreas = chartAreas.copy(
            xAxisTitleHeight = m.xAxisTitle.maxIntrinsicHeight(chartAreas.graphSize().width),
            yAxisTitleWidth = m.yAxisTitle.maxIntrinsicWidth(chartAreas.graphSize().height)
        )

        chartAreas = chartAreas.withComputedXAxisLabelAreas(m.xAxisLabels, xAxis.style.labelRotation)
        chartAreas = chartAreas.withComputedYAxisLabelAreas(m.yAxisLabels, yAxis.style.labelRotation)

        iterations++
    } while (iterations < IterationLimit &&
        abs(chartAreas.graphSize().length() - oldSize.length()) / oldSize.length() > ChangeThreshold
    )

    return chartAreas.graphSize()
}

private class XYAxisMeasurePolicy<X, Y>(
    val xAxis: AxisDelegate<X>,
    val yAxis: AxisDelegate<Y>
) {
    fun MeasureScope.measure(
        m: Measurables,
        constraints: Constraints
    ): MeasureResult {
        val graphSize = optimizeGraphSize(constraints, m, xAxis, yAxis)

        val yAxisLabelPlaceableDelegates: List<RotatedPlaceableDelegate> = m.yAxisLabels.map {
            RotatedPlaceableDelegate(
                it.measure(Constraints(maxHeight = graphSize.height / m.yAxisLabels.size)),
                -yAxis.style.labelRotation.toFloat()
            )
        }

        var plotArea = IntRect(0, 0, 0, 0)
        plotArea = plotArea.computePlotAreaTopBottom(graphSize, yAxisLabelPlaceableDelegates)

        val xAxisPlaceable = m.xAxis.measure(Constraints.fixedWidth(graphSize.width))
        val xAxisTitlePlaceable = m.xAxisTitle.measure(Constraints(maxWidth = graphSize.width))

        val xAxisLabelPlaceableDelegates = m.xAxisLabels.calcXAxisLabelWidthConstraints(
            xAxis.style.labelRotation,
            graphSize.width / m.xAxisLabels.size,
            constraints.maxHeight - plotArea.bottom - xAxisPlaceable.height - xAxisTitlePlaceable.height +
                xAxis.axisOffset.roundToPx(),
            { meas, w -> meas.measure(Constraints(maxWidth = w)) },
            { placeable -> placeable.height }
        ).map {
            RotatedPlaceableDelegate(it, -xAxis.style.labelRotation.toFloat())
        }

        val yAxisLabelMaxWidth: Int = yAxisLabelPlaceableDelegates.maxOfOrNull { it.width } ?: 0
        val yAxisTitlePlaceable = m.yAxisTitle.measure(Constraints(maxHeight = graphSize.height))

        plotArea = plotArea.computePlotAreaLeftRight(
            graphSize,
            yAxisTitlePlaceable,
            yAxisLabelMaxWidth,
            yAxis.axisOffset.roundToPx(),
            xAxisLabelPlaceableDelegates
        )

        plotArea = plotArea.copy(right = plotArea.left + graphSize.width)

        val xAxisLabelHeight = xAxisLabelPlaceableDelegates.maxOfOrNull { it.height } ?: 0
        val yAxisPlaceable = m.yAxis.measure(Constraints.fixedHeight(graphSize.height))

        return layout(constraints.maxWidth, constraints.maxHeight) {
            m.grid.measure(Constraints.fixed(plotArea.width, plotArea.height)).place(plotArea.left, plotArea.top)

            xAxisTitlePlaceable.place(
                plotArea.left + plotArea.width / 2 - xAxisTitlePlaceable.width / 2,
                plotArea.bottom + xAxisPlaceable.height - xAxis.axisOffset.roundToPx() + xAxisLabelHeight
            )

            xAxisLabelPlaceableDelegates.forEachIndexed { index, placeable ->
                val anchor = if (xAxis.style.labelRotation == 0) {
                    AnchorPoint.TopCenter
                } else {
                    AnchorPoint.RightMiddle
                }
                with(placeable) {
                    place(
                        (plotArea.left + xAxis.majorTickOffsets[index] * plotArea.width).toInt(),
                        plotArea.bottom + xAxisPlaceable.height - xAxis.axisOffset.roundToPx(),
                        anchor
                    )
                }
            }

            yAxisTitlePlaceable.place(
                plotArea.left - yAxis.axisOffset.roundToPx() - yAxisLabelMaxWidth - yAxisTitlePlaceable.width,
                plotArea.top + plotArea.height / 2 - yAxisTitlePlaceable.height / 2
            )

            yAxisLabelPlaceableDelegates.forEachIndexed { index, placeable ->
                @Suppress("MagicNumber")
                val anchor = if (yAxis.style.labelRotation == 90) {
                    AnchorPoint.BottomCenter
                } else {
                    AnchorPoint.RightMiddle
                }

                with(placeable) {
                    place(
                        plotArea.left - yAxis.axisOffset.roundToPx(),
                        (plotArea.bottom - yAxis.majorTickOffsets[index] * plotArea.height).toInt(),
                        anchor
                    )
                }
            }

            m.chart.measure(Constraints.fixed(plotArea.width, plotArea.height)).place(plotArea.left, plotArea.top)
            yAxisPlaceable.place(plotArea.left - yAxis.axisOffset.roundToPx(), plotArea.top)
            xAxisPlaceable.place(plotArea.left, plotArea.bottom - xAxis.axisOffset.roundToPx())
        }
    }

    private fun IntRect.computePlotAreaLeftRight(
        graphSize: IntSize,
        yAxisTitlePlaceable: Placeable,
        yAxisLabelMaxWidth: Int,
        yAxisPx: Int,
        xAxisLabelPlaceableDelegates: List<RotatedPlaceableDelegate>
    ): IntRect {
        val left = max(
            yAxisTitlePlaceable.width + yAxisLabelMaxWidth + yAxisPx,
            if (xAxis.style.labelRotation == 0) {
                xAxisLabelPlaceableDelegates.firstOrNull()?.widthLeft(AnchorPoint.TopCenter) ?: 0
            } else {
                xAxisLabelPlaceableDelegates.firstOrNull()?.widthLeft(AnchorPoint.RightMiddle) ?: 0
            }
        )
        val right = left + graphSize.width
        return copy(
            left = left,
            right = right
        )
    }

    private fun IntRect.computePlotAreaTopBottom(
        graphSize: IntSize,
        yAxisLabelPlaceableDelegates: List<RotatedPlaceableDelegate>
    ): IntRect {
        @Suppress("MagicNumber")
        val top = if (yAxis.style.labelRotation == 90) {
            yAxisLabelPlaceableDelegates.lastOrNull()?.heightAbove(AnchorPoint.BottomCenter) ?: 0
        } else {
            yAxisLabelPlaceableDelegates.lastOrNull()?.heightAbove(AnchorPoint.RightMiddle) ?: 0
        }
        val bottom = top + graphSize.height
        return copy(
            top = top,
            bottom = bottom
        )
    }
}

/**
 * A scope for XY plots providing axis and state context.
 */
public interface XYChartScope<X, Y> : HoverableElementAreaScope {
    public val xAxisModel: AxisModel<X>
    public val yAxisModel: AxisModel<Y>
    public val xAxisState: AxisState
    public val yAxisState: AxisState
}

private class XYChartScopeImpl<X, Y>(
    override val xAxisModel: AxisModel<X>,
    override val yAxisModel: AxisModel<Y>,
    override val xAxisState: AxisState,
    override val yAxisState: AxisState,
    val hoverableElementAreaScope: HoverableElementAreaScope
) : XYChartScope<X, Y>, HoverableElementAreaScope by hoverableElementAreaScope

@Composable
private fun Grid(
    xAxisState: AxisState,
    yAxisState: AxisState,
    horizontalMajorGridLineStyle: LineStyle? = null,
    horizontalMinorGridLineStyle: LineStyle? = null,
    verticalMajorGridLineStyle: LineStyle? = null,
    verticalMinorGridLineStyle: LineStyle? = null,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawVerticalGridLines(
            xAxisState.majorTickOffsets,
            size.width,
            verticalMajorGridLineStyle
        )

        drawVerticalGridLines(
            xAxisState.minorTickOffsets,
            size.width,
            verticalMinorGridLineStyle
        )

        drawHorizontalGridLines(
            yAxisState.majorTickOffsets,
            size.height,
            horizontalMajorGridLineStyle
        )

        drawHorizontalGridLines(
            yAxisState.minorTickOffsets,
            size.height,
            horizontalMinorGridLineStyle
        )
    }
}

private fun DrawScope.drawVerticalGridLines(
    values: List<Float>,
    scale: Float,
    style: LineStyle?
) {
    if (style != null) {
        values.forEach {
            drawGridLine(
                style,
                start = Offset(it * scale, 0f),
                end = Offset(it * scale, size.height)
            )
        }
    }
}

private fun DrawScope.drawHorizontalGridLines(
    values: List<Float>,
    scale: Float,
    style: LineStyle?
) {
    if (style != null) {
        values.forEach {
            drawGridLine(
                style,
                start = Offset(0f, scale - it * scale),
                end = Offset(size.width, scale - it * scale)
            )
        }
    }
}

private fun DrawScope.drawGridLine(gridLineStyle: LineStyle?, start: Offset, end: Offset) {
    if (gridLineStyle != null) {
        with(gridLineStyle) {
            drawLine(
                start = start,
                end = end,
                brush = brush,
                strokeWidth = strokeWidth.toPx(),
                pathEffect = pathEffect,
                alpha = alpha,
                colorFilter = colorFilter,
                blendMode = blendMode
            )
        }
    }
}

/**
 * An XYChart overload that takes Strings for axis labels and titles instead of Composables for use cases where
 * custom styling is not required.
 *
 * Provides a set of X-Y axes and grid for displaying an X-Y plot.
 *
 * @param X The data type for the x-axis
 * @param Y The data type for the y-axis
 * @param xAxisModel x-axis state controlling the display of the axis and coordinate transformation
 * @param yAxisModel y-axis state controlling the display of the axis and coordinate transformation
 * @param xAxisStyle Style for the x-axis
 * @param xAxisLabels String factory of x-axis label Strings
 * @param xAxisTitle Title for the X-axis
 * @param yAxisStyle Style for the y-axis
 * @param yAxisLabels String factory of y-axis label Strings
 * @param yAxisTitle Title for the y-axis
 * @param content The XY Chart content to be displayed, which should include one chart for each
 * series type to be displayed.
 */
@ExperimentalKoalaPlotApi
@Composable
public fun <X, Y> XYChart(
    xAxisModel: AxisModel<X>,
    yAxisModel: AxisModel<Y>,
    modifier: Modifier = Modifier,
    xAxisStyle: AxisStyle = rememberAxisStyle(),
    xAxisLabels: (X) -> String = { it.toString() },
    xAxisTitle: String = "",
    yAxisStyle: AxisStyle = rememberAxisStyle(),
    yAxisLabels: (Y) -> String = { it.toString() },
    yAxisTitle: String = "",
    horizontalMajorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.majorGridlineStyle,
    horizontalMinorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.minorGridlineStyle,
    verticalMajorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.majorGridlineStyle,
    verticalMinorGridLineStyle: LineStyle? = KoalaPlotTheme.axis.minorGridlineStyle,
    content: @Composable XYChartScope<X, Y>.() -> Unit
) {
    XYChart(
        xAxisModel,
        yAxisModel,
        modifier,
        xAxisStyle,
        xAxisLabels = {
            Text(
                xAxisLabels(it),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        },
        xAxisTitle = {
            Text(
                xAxisTitle,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            )
        },
        yAxisStyle,
        yAxisLabels = {
            Text(
                yAxisLabels(it),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        },
        yAxisTitle = {
            Text(
                yAxisTitle,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.rotateVertically(VerticalRotation.COUNTER_CLOCKWISE)
                    .padding(bottom = KoalaPlotTheme.sizes.gap),
            )
        },
        horizontalMajorGridLineStyle,
        horizontalMinorGridLineStyle,
        verticalMajorGridLineStyle,
        verticalMinorGridLineStyle,
        content
    )
}
