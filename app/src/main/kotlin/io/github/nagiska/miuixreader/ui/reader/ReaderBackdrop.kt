package io.github.nagiska.miuixreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * Backdrop backed by a window snapshot, anchored to the window.
 *
 * The snapshot is refreshed continuously while the chrome is visible, so the
 * glass always shows the live content behind the panel, aligned with the page.
 */
@Stable
class ReaderBackdrop : Backdrop {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? by mutableStateOf(null)
    private var destinationSize by mutableStateOf(IntSize.Zero)

    override val isCoordinatesDependent: Boolean = true

    fun setBitmap(next: Bitmap?, destinationWidth: Int = 0, destinationHeight: Int = 0) {
        val previous = bitmap
        bitmap = next
        image = next?.asImageBitmap()
        destinationSize = if (next == null) {
            IntSize.Zero
        } else {
            IntSize(destinationWidth.coerceAtLeast(1), destinationHeight.coerceAtLeast(1))
        }
        if (previous != null && previous !== next && !previous.isRecycled) {
            previous.recycle()
        }
    }

    fun clear() {
        setBitmap(null)
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val currentImage = image ?: return
        val currentDestinationSize = destinationSize
        if (currentDestinationSize == IntSize.Zero) return
        val position = coordinates?.positionInWindow() ?: Offset.Zero
        drawImage(
            image = currentImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(currentImage.width, currentImage.height),
            dstOffset = IntOffset((-position.x).roundToInt(), (-position.y).roundToInt()),
            dstSize = currentDestinationSize,
        )
    }
}
