package io.github.nagiska.miuixreader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.asin
import kotlin.math.sin
import kotlin.math.toDegrees
import kotlin.math.toRadians
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction

/**
 * Same corner-sinking tilt as Miuix [top.yukonga.miuix.kmp.utils.TiltFeedback],
 * but with a much larger camera distance so the perspective distortion does
 * not squash the card's rounded corners into sharp points while pressed.
 */
@Stable
data class ReaderTiltFeedback(
    val tiltAmount: Float = 8f,
    val animationSpec: AnimationSpec<Float> = spring(0.6f, 400f),
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ReaderTiltFeedbackNode(interactionSource, tiltAmount, animationSpec)

    private class ReaderTiltFeedbackNode(
        var interactionSource: InteractionSource,
        var tiltAmount: Float,
        var animationSpec: AnimationSpec<Float>,
    ) : Modifier.Node(),
        LayoutModifierNode,
        PointerInputModifierNode {

        private var transformOrigin: TransformOrigin = TransformOrigin.Center
        private var targetX = 0f
        private var targetY = 0f
        private val animatedTiltX = Animatable(0f)
        private val animatedTiltY = Animatable(0f)
        private var isPressed = false
        private var isHoldDown = false

        private fun updateState() {
            if (isPressed || isHoldDown) {
                coroutineScope.launch { animatedTiltX.animateTo(targetX, animationSpec) }
                coroutineScope.launch { animatedTiltY.animateTo(targetY, animationSpec) }
            } else {
                coroutineScope.launch { animatedTiltX.animateTo(0f, animationSpec) }
                coroutineScope.launch { animatedTiltY.animateTo(0f, animationSpec) }
            }
        }

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction: Interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> isPressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
                        is HoldDownInteraction.HoldDown -> isHoldDown = true
                        is HoldDownInteraction.Release -> isHoldDown = false
                        else -> return@collect
                    }
                    updateState()
                }
            }
        }

        override fun onPointerEvent(
            pointerEvent: PointerEvent,
            pass: PointerEventPass,
            bounds: IntSize,
        ) {
            if (pass != PointerEventPass.Main) return
            if (pointerEvent.type == PointerEventType.Press) {
                val offset = pointerEvent.changes.first().position

                transformOrigin = TransformOrigin(
                    pivotFractionX = if (offset.x < bounds.width / 2f) 1f else 0f,
                    pivotFractionY = if (offset.y < bounds.height / 2f) 1f else 0f,
                )

                // Balance the two rotations so the far corner sinks
                // symmetrically: the rotationY edge displacement (W/2 * sin)
                // must match the rotationX edge displacement (H/2 * sin),
                // otherwise on wide cards the adjacent corner drifts away
                // and looks like it disappears.
                val tiltX = tiltAmount
                val tiltY = if (bounds.width > 0) {
                    toDegrees(
                        asin(
                            (sin(toRadians(tiltAmount.toDouble())) *
                                bounds.height / bounds.width).coerceIn(-1.0, 1.0),
                        ),
                    ).toFloat()
                } else {
                    tiltAmount
                }

                targetX = if (offset.y < bounds.height / 2f) tiltX else -tiltX
                targetY = if (offset.x < bounds.width / 2f) -tiltY else tiltY
            }
        }

        override fun onCancelPointerInput() {
            transformOrigin = TransformOrigin.Center
            targetX = 0f
            targetY = 0f
        }

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    rotationX = animatedTiltX.value
                    rotationY = animatedTiltY.value
                    // 30dp keeps the tilt readable while the rounded corners
                    // stay round (Miuix uses 12dp, which squashes them).
                    cameraDistance = 30 * density
                    this.transformOrigin = this@ReaderTiltFeedbackNode.transformOrigin
                }
            }
        }
    }
}
