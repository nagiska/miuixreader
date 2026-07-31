package io.github.nagiska.miuixreader.ui

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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * Backdrop backed by a static wallpaper bitmap, anchored to the window.
 *
 * Unlike a live layer capture, the wallpaper never moves, so glass surfaces
 * on the bookshelf stay visually aligned with the background while the book
 * list scrolls underneath.
 */
@Stable
class WallpaperBackdrop : Backdrop {
    private var image: ImageBitmap? by mutableStateOf(null)
    private var windowSize by mutableStateOf(IntSize.Zero)

    override val isCoordinatesDependent: Boolean = true

    fun setWallpaper(bitmap: Bitmap?, windowWidth: Int, windowHeight: Int) {
        image = bitmap?.asImageBitmap()
        windowSize = if (bitmap == null) {
            IntSize.Zero
        } else {
            IntSize(windowWidth.coerceAtLeast(1), windowHeight.coerceAtLeast(1))
        }
    }

    fun clear() {
        setWallpaper(null, 0, 0)
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val currentImage = image ?: return
        val currentWindowSize = windowSize
        if (currentWindowSize == IntSize.Zero) return
        val position = coordinates?.positionInWindow() ?: Offset.Zero
        val scale = maxOf(
            currentWindowSize.width.toFloat() / currentImage.width,
            currentWindowSize.height.toFloat() / currentImage.height,
        )
        val srcWidth = (currentWindowSize.width / scale).coerceAtMost(currentImage.width.toFloat())
        val srcHeight = (currentWindowSize.height / scale).coerceAtMost(currentImage.height.toFloat())
        drawImage(
            image = currentImage,
            srcOffset = IntOffset(
                ((currentImage.width - srcWidth) / 2f).roundToInt(),
                ((currentImage.height - srcHeight) / 2f).roundToInt(),
            ),
            srcSize = IntSize(srcWidth.roundToInt(), srcHeight.roundToInt()),
            dstOffset = IntOffset((-position.x).roundToInt(), (-position.y).roundToInt()),
            dstSize = currentWindowSize,
        )
    }
}
