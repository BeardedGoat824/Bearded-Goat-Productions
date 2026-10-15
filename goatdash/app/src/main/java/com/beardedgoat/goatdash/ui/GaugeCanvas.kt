package com.beardedgoat.goatdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.beardedgoat.goatdash.dash.DashPage
import com.beardedgoat.goatdash.dash.GaugeRenderer
import com.beardedgoat.goatdash.dash.isShifting
import com.beardedgoat.goatdash.obd.LiveData
import kotlinx.coroutines.delay

/** A dashboard page drawn with the same renderer Android Auto uses. */
@Composable
fun GaugePage(
    page: DashPage,
    live: LiveData,
    imperial: Boolean,
    modifier: Modifier = Modifier,
    selectedSlot: Int = -1,
    onSlotTap: ((Int) -> Unit)? = null,
) {
    val renderer = remember { GaugeRenderer() }
    val scale = LocalDensity.current.density
    val tap = if (onSlotTap == null) Modifier else Modifier.pointerInput(page) {
        detectTapGestures { offset ->
            val slot = renderer.slotAt(page, offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), scale)
            if (slot >= 0) onSlotTap(slot)
        }
    }
    // While a shift light is lit, tick a clock so the tile keeps blinking between readings.
    val shifting = page.isShifting(live)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(shifting) {
        while (shifting) {
            now = System.currentTimeMillis()
            delay(GaugeRenderer.FLASH_MS / 2)
        }
    }
    Canvas(modifier.then(tap)) {
        drawIntoCanvas { canvas ->
            renderer.drawPage(canvas.nativeCanvas, page, live, imperial, 0f, 0f, size.width, size.height, scale, selectedSlot, now)
        }
    }
}
