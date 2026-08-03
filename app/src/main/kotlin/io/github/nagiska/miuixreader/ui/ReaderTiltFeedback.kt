// Adapted from miuix-kmp (compose-miuix-ui/miuix), Apache-2.0:
// https://github.com/compose-miuix-ui/miuix — top.yukonga.miuix.kmp.utils.TiltFeedback
// Only the camera distance differs (40dp instead of 12dp).
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction

/**
 * The stock Miuix [top.yukonga.miuix.kmp.utils.TiltFeedback], line for line
 * (8deg tilt, spring(0.6f, 400f), quadrant-based transform origin on the
 * opposite corner), except the camera distance is raised from 12dp to 40dp.
 *
 * At the stock 12dp the perspective of a half-transparent glass layer
 * squashes the rounded corners and pushes the far edge down while tilting;
 * a farther camera keeps the official press feel but the corners stay round
 * and the edges stay put.
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

                targetX = if (offset.y < bounds.height / 2f) tiltAmount else -tiltAmount
                targetY = if (offset.x < bounds.width / 2f) -tiltAmount else tiltAmount
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
                    // Stock Miuix uses 12 * density, which squashes the
                    // rounded corners on the glass surface.
                    cameraDistance = 40 * density
                    this.transformOrigin = this@ReaderTiltFeedbackNode.transformOrigin
                }
            }
        }
    }
}
