package io.github.nagiska.miuixreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop

/**
 * Backdrop backed by a window snapshot, drawn relative to the panel itself.
 *
 * The snapshot moves together with the bar, sheet, or button it backs, so the
 * glass content descends in sync with the text while the chrome animates in.
 */
@Stable
class ReaderBackdrop : Backdrop {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? by mutableStateOf(null)
    private var destinationSize by mutableStateOf(IntSize.Zero)

    override val isCoordinatesDependent: Boolean = false

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
        drawImage(
            image = currentImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(currentImage.width, currentImage.height),
            dstOffset = IntOffset.Zero,
            dstSize = currentDestinationSize,
        )
    }
}
