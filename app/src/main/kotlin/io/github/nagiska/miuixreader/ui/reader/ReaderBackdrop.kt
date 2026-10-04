package io.github.nagiska.miuixreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
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
 * Backdrop backed by a window snapshot, anchored to the panel's rest position.
 *
 * [positionInWindow] reports the layout position without any graphicsLayer
 * transforms, which is exactly where the panel rests when the chrome is
 * settled. Drawing the snapshot at that offset makes the glass content a part
 * of the panel itself: it slides together with the text and the frame while
 * the chrome animates or is dragged, and it lines up perfectly with the page
 * behind the panel at rest.
 */
@Stable
class ReaderBackdrop : Backdrop {
    private var image: ImageBitmap? by mutableStateOf(null)
    private var destinationSize by mutableStateOf(IntSize.Zero)
    private var backgroundColor by mutableStateOf(Color.Black)
    private var wallpaper: ImageBitmap? by mutableStateOf(null)
    private var wallpaperScrim by mutableStateOf(0f)
    private var viewportSize by mutableStateOf(IntSize.Zero)

    override val isCoordinatesDependent: Boolean = true

    /** Published images are immutable and released by GC, never recycled while drawing. */
    fun setBitmap(next: Bitmap?, destinationWidth: Int = 0, destinationHeight: Int = 0) {
        image = next?.asImageBitmap()
        destinationSize = if (next == null) {
            IntSize.Zero
        } else {
            IntSize(destinationWidth.coerceAtLeast(1), destinationHeight.coerceAtLeast(1))
        }
    }

    /** An up-to-date wallpaper/color remains available while chrome prevents a clean capture. */
    fun setBackground(
        color: Color,
        bitmap: Bitmap? = null,
        scrim: Float = 0f,
        width: Int = 0,
        height: Int = 0,
    ) {
        backgroundColor = color
        wallpaper = bitmap?.asImageBitmap()
        wallpaperScrim = scrim.coerceIn(0f, 1f)
        viewportSize = IntSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
        clear()
    }

    fun clear() {
        setBitmap(null)
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val restPosition = coordinates?.positionInWindow() ?: Offset.Zero
        val snapshot = image
        if (snapshot == null) {
            drawRect(backgroundColor)
            val background = wallpaper ?: return
            val viewport = viewportSize
            val scale = maxOf(
                viewport.width.toFloat() / background.width,
                viewport.height.toFloat() / background.height,
            )
            val sourceWidth = (viewport.width / scale).roundToInt().coerceIn(1, background.width)
            val sourceHeight = (viewport.height / scale).roundToInt().coerceIn(1, background.height)
            drawImage(
                image = background,
                srcOffset = IntOffset((background.width - sourceWidth) / 2, (background.height - sourceHeight) / 2),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = IntOffset((-restPosition.x).roundToInt(), (-restPosition.y).roundToInt()),
                dstSize = viewport,
            )
            drawRect(Color.Black.copy(alpha = wallpaperScrim))
            return
        }
        val currentDestinationSize = destinationSize
        if (currentDestinationSize == IntSize.Zero) return
        drawImage(
            image = snapshot,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(snapshot.width, snapshot.height),
            dstOffset = IntOffset((-restPosition.x).roundToInt(), (-restPosition.y).roundToInt()),
            dstSize = currentDestinationSize,
        )
    }
}
