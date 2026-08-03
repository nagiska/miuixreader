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
 * Flat press feedback for the liquid-glass shelf cards: the card shrinks
 * slightly around the exact press point (no 3D rotation), so pressing a
 * corner anchors that corner while the card sinks — without the perspective
 * artifacts that made the far corner look occluded on glass surfaces.
 */
@Stable
data class ReaderPressFeedback(
    val pressedScale: Float = 0.95f,
    val animationSpec: AnimationSpec<Float> = spring(0.8f, 600f),
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ReaderPressFeedbackNode(interactionSource, pressedScale, animationSpec)

    private class ReaderPressFeedbackNode(
        var interactionSource: InteractionSource,
        var pressedScale: Float,
        var animationSpec: AnimationSpec<Float>,
    ) : Modifier.Node(),
        LayoutModifierNode,
        PointerInputModifierNode {

        private var transformOrigin: TransformOrigin = TransformOrigin.Center
        private val animatedScale = Animatable(1f)
        private var isPressed = false
        private var isHoldDown = false

        private fun updateState() {
            val target = if (isPressed || isHoldDown) pressedScale else 1f
            coroutineScope.launch { animatedScale.animateTo(target, animationSpec) }
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
                    pivotFractionX = if (bounds.width > 0) {
                        (offset.x / bounds.width).coerceIn(0f, 1f)
                    } else {
                        0.5f
                    },
                    pivotFractionY = if (bounds.height > 0) {
                        (offset.y / bounds.height).coerceIn(0f, 1f)
                    } else {
                        0.5f
                    },
                )
                invalidateLayout()
            }
        }

        override fun onCancelPointerInput() {
            transformOrigin = TransformOrigin.Center
        }

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = animatedScale.value
                    scaleY = animatedScale.value
                    this.transformOrigin = this@ReaderPressFeedbackNode.transformOrigin
                }
            }
        }
    }
}
