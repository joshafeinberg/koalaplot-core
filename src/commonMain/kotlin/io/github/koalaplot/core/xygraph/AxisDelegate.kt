package io.github.koalaplot.core.xygraph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.github.koalaplot.core.style.KoalaPlotTheme

/**
 * Specifies the position of axis ticks.
 */
public enum class TickPosition {
    Outside, Inside, None
}

internal enum class AxisOrientation {
    Horizontal, Vertical
}

/**
 * Styling configuration for an Axis.
 *
 * @param labelRotation Rotation angle, in degrees, to apply to the axis labels. Increasing values result in
 * counter-clockwise rotation. Valid values are between 0 and 90.
 */
public data class AxisStyle(
    val color: Color = Color.Black,
    val majorTickSize: Dp = 0.dp,
    val minorTickSize: Dp = 0.dp,
    val tickPosition: TickPosition = TickPosition.None,
    val lineWidth: Dp = 1.dp,
    val labelRotation: Int = 0
) {
    init {
        @Suppress("MagicNumber")
        require(labelRotation in 0..90) { "labelRotation must be between 0 and 90" }
    }
}

/**
 * Creates and remembers an AxisStyle.
 */
@Composable
public fun rememberAxisStyle(
    color: Color = KoalaPlotTheme.axis.color,
    majorTickSize: Dp = KoalaPlotTheme.axis.majorTickSize,
    minorTickSize: Dp = KoalaPlotTheme.axis.minorTickSize,
    tickPosition: TickPosition = KoalaPlotTheme.axis.xyGraphTickPosition,
    lineWidth: Dp = KoalaPlotTheme.axis.lineThickness,
    labelRotation: Int = 0
): AxisStyle = remember(color, majorTickSize, minorTickSize, tickPosition, lineWidth, labelRotation) {
    AxisStyle(
        color,
        majorTickSize,
        minorTickSize,
        tickPosition,
        lineWidth,
        labelRotation
    )
}

internal class AxisDelegate<T> private constructor(
    private val axisState: AxisState,
    private val tickValues: TickValues<T>,
    val orientation: AxisOrientation,
    val style: AxisStyle,
) : AxisState by axisState, TickValues<T> by tickValues {
    companion object {
        fun <T> createVerticalAxis(axisModel: AxisModel<T>, style: AxisStyle, length: Dp): AxisDelegate<T> {
            return createAxis(AxisOrientation.Vertical, axisModel, style, length)
        }

        fun <T> createHorizontalAxis(axisModel: AxisModel<T>, style: AxisStyle, length: Dp): AxisDelegate<T> {
            return createAxis(AxisOrientation.Horizontal, axisModel, style, length)
        }

        private fun <T> createAxis(
            orientation: AxisOrientation,
            axisModel: AxisModel<T>,
            style: AxisStyle,
            length: Dp
        ): AxisDelegate<T> {
            val tickValues = axisModel.computeTickValues(length)

            val axisState = object : AxisState {
                override val majorTickOffsets: List<Float> =
                    tickValues.majorTickValues.map { axisModel.computeOffset(it) }
                override val minorTickOffsets: List<Float> =
                    tickValues.minorTickValues.map { axisModel.computeOffset(it) }
            }
            return AxisDelegate(axisState, tickValues, orientation, style)
        }
    }

    // overall thickness of the axis, either width for Vertical axis or height for Horizontal axis
    val thicknessDp = max(max(style.majorTickSize, style.minorTickSize), style.lineWidth)

    // offset, in Dp, of the axis line within the drawing space. This accounts for the tick sizes.
    // For a vertical axis it is the x-axis offset of the vertical axis line relative to the
    // coordinate system origin. For a horizontal axis it is the y-axis offset of the horizontal axis
    // line relative to the coordinate system origin.
    val axisOffset = if (orientation == AxisOrientation.Vertical) {
        when (style.tickPosition) {
            TickPosition.Outside -> thicknessDp
            TickPosition.Inside -> 0.dp
            TickPosition.None -> 0.dp
        }
    } else {
        when (style.tickPosition) {
            TickPosition.Outside -> 0.dp
            TickPosition.Inside -> thicknessDp
            TickPosition.None -> thicknessDp
        }
    }

    /**
     * Draws the Axis to fill the full width (for horizontal axis) or height (for vertical axis)
     * of the DrawScope.
     */
    fun DrawScope.drawAxis() {
        val sizeInline = if (orientation == AxisOrientation.Vertical) {
            size.height
        } else {
            size.width
        }

        val thicknessPx = thicknessDp.roundToPx()

        // The axis is drawn as a vertical axis at x=0 with ticks to the right,
        // then transforms are applied for tick position and horizontal orientation
        withTransform({
            if (orientation == AxisOrientation.Horizontal) {
                @Suppress("MagicNumber")
                rotate(-90f, pivot = Offset(0f, 0f)) // rotate to horizontal
                translate(left = -thicknessPx.toFloat())
            } else {
                scale(1f, -1f)
            }
            if (style.tickPosition == TickPosition.Outside) {
                translate(left = thicknessPx.toFloat())
                scale(-1f, 1f, pivot = Offset.Zero)
            }
        }) {
            // Axis line
            drawLine(
                style.color,
                Offset(0f, 0f),
                Offset(0f, sizeInline),
                strokeWidth = style.lineWidth.toPx(),
                cap = StrokeCap.Butt
            )

            if (style.tickPosition != TickPosition.None) {
                if (style.majorTickSize != 0.dp) {
                    axisState.majorTickOffsets.forEach { offset ->
                        val y = offset * sizeInline
                        drawLine(
                            style.color,
                            Offset(0f, y),
                            Offset(style.majorTickSize.toPx(), y),
                            strokeWidth = style.lineWidth.toPx(),
                            cap = StrokeCap.Butt
                        )
                    }
                }

                if (style.minorTickSize != 0.dp) {
                    axisState.minorTickOffsets.forEach { offset ->
                        val y = offset * sizeInline
                        drawLine(
                            style.color,
                            Offset(0f, y),
                            Offset(style.minorTickSize.toPx(), y),
                            strokeWidth = style.lineWidth.toPx(),
                            cap = StrokeCap.Butt
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> Axis(delegate: AxisDelegate<T>) {
    Canvas(
        modifier = if (delegate.orientation == AxisOrientation.Vertical) {
            Modifier.fillMaxHeight().width(delegate.thicknessDp)
        } else {
            Modifier.fillMaxWidth().height(delegate.thicknessDp)
        }
    ) {
        with(delegate) {
            drawAxis()
        }
    }
}
