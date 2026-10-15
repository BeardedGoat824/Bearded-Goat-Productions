package com.beardedgoat.goatdash.dash

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.beardedgoat.goatdash.music.MusicState
import com.beardedgoat.goatdash.obd.LiveData
import com.beardedgoat.goatdash.obd.ObdManager
import com.beardedgoat.goatdash.obd.Pid
import com.beardedgoat.goatdash.obd.Pids
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a dashboard page onto a plain Android [Canvas]. The phone (via Compose) and the
 * Android Auto screen (via its drawing surface) both use this, so they always match.
 *
 * [scale] is roughly the screen density; it keeps strokes and padding sensible on both.
 */
class GaugeRenderer {

    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL }
    private val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val rect = RectF()
    private val path = Path()

    // Where music buttons were drawn on the last frame, so taps can be matched to them.
    private val musicHits = mutableListOf<Pair<RectF, MusicButton>>()

    // Ink colours for the gauge being drawn; they flip to black/white while a shift light flashes.
    private var ink = TEXT
    private var muted = MUTED
    private var track = TRACK

    /** Cell rectangles for [page] laid out inside the given area (row by row). */
    fun cellRects(page: DashPage, left: Float, top: Float, width: Float, height: Float, scale: Float): List<RectF> {
        // On a tall phone screen a wide grid would squash the gauges, so turn it sideways.
        val portrait = height > width * 1.15f
        val cols = if (portrait && page.columns > page.rows) page.rows else page.columns
        val rows = if (portrait && page.columns > page.rows) page.columns else page.rows
        val gap = 6f * scale
        val cw = (width - gap * (cols + 1)) / cols
        val ch = (height - gap * (rows + 1)) / rows
        return (0 until page.slots).map { i ->
            val c = i % cols
            val r = i / cols
            val l = left + gap + c * (cw + gap)
            val t = top + gap + r * (ch + gap)
            RectF(l, t, l + cw, t + ch)
        }
    }

    /** The music button drawn under the point on the last frame, if any. */
    fun musicButtonAt(x: Float, y: Float): MusicButton? = musicHits.firstOrNull { it.first.contains(x, y) }?.second

    /** Index of the gauge under the point, or -1. */
    fun slotAt(page: DashPage, x: Float, y: Float, width: Float, height: Float, scale: Float): Int =
        cellRects(page, 0f, 0f, width, height, scale).indexOfFirst { it.contains(x, y) }

    fun drawPage(
        canvas: Canvas,
        page: DashPage,
        live: LiveData,
        imperial: Boolean,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        selectedSlot: Int = -1,
        nowMillis: Long = System.currentTimeMillis(),
        music: MusicState? = null,
        musicButtons: List<MusicButton> = emptyList(),
    ) {
        musicHits.clear()
        // Shift lights blink on and off together, a few times a second.
        val flashPhase = (nowMillis / FLASH_MS) % 2 == 0L
        cellRects(page, left, top, width, height, scale).forEachIndexed { i, cell ->
            val gauge = page.gauges.getOrNull(i) ?: return@forEachIndexed
            if (gauge.isMusic) {
                drawMusic(canvas, cell, gauge, music, musicButtons, scale, selected = i == selectedSlot)
                return@forEachIndexed
            }
            val pid = Pids[gauge.pidId] ?: return@forEachIndexed
            drawGauge(canvas, cell, gauge, pid, live, imperial, scale, selected = i == selectedSlot, flashPhase = flashPhase)
        }
    }

    private fun drawGauge(canvas: Canvas, cell: RectF, gauge: Gauge, pid: Pid, live: LiveData, imperial: Boolean, scale: Float, selected: Boolean, flashPhase: Boolean) {
        val value = live.values[pid.id]
        val unsupported = pid.id in live.unsupported
        val warning = gauge.warnAbove != null && value != null && value > gauge.warnAbove
        val shifting = gauge.isShifting(live)
        val flashOn = shifting && flashPhase

        if (flashOn) {
            // Fill the tile with the shift colour and draw everything else in black or white,
            // whichever reads better on it, so the number stays legible mid-flash.
            val flash = gauge.flashColor
            ink = if (isLight(flash)) Color.BLACK else Color.WHITE
            muted = (ink and 0x00FFFFFF) or 0xB3000000.toInt()
            track = (ink and 0x00FFFFFF) or 0x40000000
            panel.color = flash
        } else {
            ink = TEXT
            muted = MUTED
            track = TRACK
            panel.color = PANEL
        }
        small.color = muted
        val color = when {
            flashOn -> ink
            warning -> GaugeColors.WARNING
            else -> gauge.color
        }

        val radius = 14f * scale
        canvas.drawRoundRect(cell, radius, radius, panel)
        panelStroke.strokeWidth = (if (shifting) 4f else if (selected || warning) 2.5f else 1f) * scale
        panelStroke.color = when {
            shifting -> if (flashOn) ink else gauge.flashColor
            selected -> Color.WHITE
            warning -> GaugeColors.WARNING
            else -> PANEL_EDGE
        }
        canvas.drawRoundRect(cell, radius, radius, panelStroke)

        val valueText = if (unsupported) "N/A" else pid.format(value, imperial)
        val unitText = pid.unit.label(imperial)
        val fraction = value?.let { ((it - pid.min) / (pid.max - pid.min)).toFloat().coerceIn(0f, 1f) }
        val warnFraction = gauge.warnAbove?.let { ((it - pid.min) / (pid.max - pid.min)).toFloat().coerceIn(0f, 1f) }
        val shiftMark = gauge.shiftAt?.let { ((it - pid.min) / (pid.max - pid.min)).toFloat().coerceIn(0f, 1f) }
        val markColor = if (flashOn) ink else gauge.flashColor

        when (gauge.style) {
            GaugeStyle.DIAL -> drawDial(canvas, cell, pid, valueText, unitText, fraction, warnFraction, shiftMark, markColor, color, scale)
            GaugeStyle.BAR -> drawBar(canvas, cell, pid, valueText, unitText, fraction, warnFraction, shiftMark, markColor, color, scale)
            GaugeStyle.DIGITAL -> drawDigital(canvas, cell, pid, valueText, unitText, color, scale)
            GaugeStyle.GRAPH -> drawGraph(canvas, cell, pid, valueText, unitText, live.history[pid.id].orEmpty(), color, scale)
        }
    }

    private fun drawDial(canvas: Canvas, cell: RectF, pid: Pid, valueText: String, unitText: String, fraction: Float?, warnFraction: Float?, shiftMark: Float?, markColor: Int, color: Int, scale: Float) {
        val pad = 10f * scale
        val r = min(cell.width() - pad * 2, (cell.height() - pad * 2) * 1.18f) / 2f
        val cx = cell.centerX()
        val cy = cell.top + pad + r + (cell.height() - pad * 2 - r * 1.85f).coerceAtLeast(0f) / 2f
        val stroke = r * 0.13f
        val arcR = r - stroke / 2
        rect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)

        arc.strokeWidth = stroke
        arc.color = track
        canvas.drawArc(rect, START, SWEEP, false, arc)
        if (warnFraction != null) {
            arc.color = GaugeColors.WARNING and 0x66FFFFFF
            canvas.drawArc(rect, START + SWEEP * warnFraction, SWEEP * (1 - warnFraction), false, arc)
        }
        if (fraction != null && fraction > 0.003f) {
            arc.color = color
            canvas.drawArc(rect, START, SWEEP * fraction, false, arc)
        }

        // Tick marks just inside the arc.
        line.color = muted
        line.strokeWidth = 1.2f * scale
        for (i in 0..10) {
            val a = Math.toRadians((START + SWEEP * i / 10f).toDouble())
            val outer = arcR - stroke * 0.9f
            val inner = outer - if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f
            canvas.drawLine(cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(), line)
        }

        // Shift point: a bold mark across the arc.
        if (shiftMark != null) {
            val a = Math.toRadians((START + SWEEP * shiftMark).toDouble())
            val outer = arcR + stroke * 0.7f
            val inner = arcR - stroke * 0.9f
            line.color = markColor
            line.strokeWidth = 3f * scale
            canvas.drawLine(cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(), line)
        }

        // Needle.
        if (fraction != null) {
            val a = Math.toRadians((START + SWEEP * fraction).toDouble())
            val len = arcR - stroke * 1.3f
            line.color = ink
            line.strokeWidth = 2.2f * scale
            canvas.drawLine(cx, cy, cx + (cos(a) * len).toFloat(), cy + (sin(a) * len).toFloat(), line)
            fill.color = color
            canvas.drawCircle(cx, cy, stroke * 0.45f, fill)
        }

        text.color = ink
        text.textAlign = Paint.Align.CENTER
        fitText(text, valueText, r * 1.15f, r * 0.42f)
        canvas.drawText(valueText, cx, cy + r * 0.52f, text)
        small.textAlign = Paint.Align.CENTER
        fitText(small, "${pid.shortName.uppercase()}  $unitText", r * 1.5f, r * 0.15f)
        canvas.drawText("${pid.shortName.uppercase()}  $unitText", cx, cy + r * 0.82f, small)
    }

    private fun drawBar(canvas: Canvas, cell: RectF, pid: Pid, valueText: String, unitText: String, fraction: Float?, warnFraction: Float?, shiftMark: Float?, markColor: Int, color: Int, scale: Float) {
        val pad = 12f * scale
        val innerW = cell.width() - pad * 2
        val labelSize = min(cell.height() * 0.13f, 16f * scale)
        small.textAlign = Paint.Align.LEFT
        fitText(small, pid.shortName.uppercase(), innerW, labelSize)
        canvas.drawText(pid.shortName.uppercase(), cell.left + pad, cell.top + pad + small.textSize, small)

        val barH = (cell.height() * 0.16f).coerceIn(6f * scale, 22f * scale)
        val barTop = cell.bottom - pad - barH
        text.color = ink
        text.textAlign = Paint.Align.LEFT
        val space = barTop - (cell.top + pad + small.textSize) - pad * 0.5f
        fitText(text, valueText, innerW * 0.7f, space * 0.75f)
        val baseline = barTop - pad * 0.6f - (space - text.textSize).coerceAtLeast(0f) / 2f
        canvas.drawText(valueText, cell.left + pad, baseline, text)
        val unitX = cell.left + pad + text.measureText(valueText) + 6f * scale
        small.textSize = text.textSize * 0.35f
        canvas.drawText(unitText, unitX, baseline, small)

        rect.set(cell.left + pad, barTop, cell.right - pad, barTop + barH)
        fill.color = track
        canvas.drawRoundRect(rect, barH / 2, barH / 2, fill)
        if (fraction != null && fraction > 0f) {
            fill.color = color
            rect.right = rect.left + (rect.width() * fraction).coerceAtLeast(barH)
            canvas.drawRoundRect(rect, barH / 2, barH / 2, fill)
        }
        if (warnFraction != null) {
            val x = cell.left + pad + innerW * warnFraction
            line.color = GaugeColors.WARNING
            line.strokeWidth = 2.5f * scale
            canvas.drawLine(x, barTop - 3f * scale, x, barTop + barH + 3f * scale, line)
        }
        if (shiftMark != null) {
            val x = cell.left + pad + innerW * shiftMark
            line.color = markColor
            line.strokeWidth = 3f * scale
            canvas.drawLine(x, barTop - 4f * scale, x, barTop + barH + 4f * scale, line)
        }
    }

    private fun drawDigital(canvas: Canvas, cell: RectF, pid: Pid, valueText: String, unitText: String, color: Int, scale: Float) {
        val pad = 12f * scale
        val innerW = cell.width() - pad * 2
        small.textAlign = Paint.Align.CENTER
        fitText(small, pid.shortName.uppercase(), innerW, min(cell.height() * 0.13f, 16f * scale))
        canvas.drawText(pid.shortName.uppercase(), cell.centerX(), cell.top + pad + small.textSize, small)
        val labelBottom = cell.top + pad + small.textSize

        text.color = color
        text.textAlign = Paint.Align.CENTER
        fitText(text, valueText, innerW, (cell.bottom - labelBottom) * 0.55f)
        val baseline = labelBottom + (cell.bottom - labelBottom) * 0.62f
        canvas.drawText(valueText, cell.centerX(), baseline, text)

        val unitSize = text.textSize * 0.28f
        small.textSize = unitSize.coerceAtMost(18f * scale)
        canvas.drawText(unitText, cell.centerX(), baseline + small.textSize * 1.6f, small)
    }

    private fun drawGraph(canvas: Canvas, cell: RectF, pid: Pid, valueText: String, unitText: String, history: List<Float>, color: Int, scale: Float) {
        val pad = 12f * scale
        val labelSize = min(cell.height() * 0.13f, 16f * scale)
        small.textAlign = Paint.Align.LEFT
        fitText(small, pid.shortName.uppercase(), cell.width() * 0.5f, labelSize)
        canvas.drawText(pid.shortName.uppercase(), cell.left + pad, cell.top + pad + small.textSize, small)

        text.color = ink
        text.textAlign = Paint.Align.RIGHT
        val shown = "$valueText $unitText"
        fitText(text, shown, cell.width() * 0.55f, labelSize * 1.5f)
        canvas.drawText(shown, cell.right - pad, cell.top + pad + text.textSize * 0.9f, text)

        val gTop = cell.top + pad * 1.6f + text.textSize
        val gBottom = cell.bottom - pad
        val gLeft = cell.left + pad
        val gRight = cell.right - pad
        line.color = track
        line.strokeWidth = 1f * scale
        canvas.drawLine(gLeft, gBottom, gRight, gBottom, line)
        if (history.size < 2) return

        // Scale to the data actually seen, but never tighter than 5% of the sensor's range.
        var lo = history.min()
        var hi = history.max()
        val minSpan = ((pid.max - pid.min) * 0.05).toFloat()
        if (hi - lo < minSpan) {
            val mid = (hi + lo) / 2
            lo = mid - minSpan / 2
            hi = mid + minSpan / 2
        }
        val step = (gRight - gLeft) / (ObdManager.HISTORY_SIZE - 1)
        val startX = gRight - step * (history.size - 1)
        path.reset()
        history.forEachIndexed { i, v ->
            val x = startX + step * i
            val y = gBottom - (v - lo) / (hi - lo) * (gBottom - gTop)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        line.color = color
        line.strokeWidth = 2.5f * scale
        canvas.drawPath(path, line)
        path.lineTo(gRight, gBottom)
        path.lineTo(startX, gBottom)
        path.close()
        fill.color = (color and 0x00FFFFFF) or 0x33000000
        canvas.drawPath(path, fill)
    }

    private fun drawMusic(canvas: Canvas, cell: RectF, gauge: Gauge, music: MusicState?, buttons: List<MusicButton>, scale: Float, selected: Boolean) {
        ink = TEXT
        muted = MUTED
        track = TRACK
        small.color = muted
        panel.color = PANEL
        val radius = 14f * scale
        canvas.drawRoundRect(cell, radius, radius, panel)
        panelStroke.strokeWidth = (if (selected) 2.5f else 1f) * scale
        panelStroke.color = if (selected) Color.WHITE else PANEL_EDGE
        canvas.drawRoundRect(cell, radius, radius, panelStroke)

        val pad = 12f * scale
        val now = music?.nowPlaying

        // Buttons along the bottom, spread evenly.
        val showButtons = now != null && buttons.isNotEmpty()
        val slotW = (cell.width() - pad * 2) / buttons.size.coerceAtLeast(1)
        val btn = min(min(cell.height() * 0.34f, slotW * 0.8f), 64f * scale)
        val rowY = cell.bottom - pad - btn / 2
        if (showButtons) {
            buttons.forEachIndexed { i, b ->
                val cx = cell.left + pad + slotW * (i + 0.5f)
                drawMusicButton(canvas, b, cx, rowY, btn, now!!.isPlaying, gauge.color, scale)
                // Generous touch target: the whole column of the row, not just the icon.
                musicHits += RectF(cx - slotW / 2, rowY - btn / 2 - pad / 2, cx + slotW / 2, rowY + btn / 2 + pad / 2) to b
            }
        }

        // Track info above the buttons: album art on the left, text beside it.
        val top = cell.top + pad
        val bottom = if (showButtons) rowY - btn / 2 - pad * 0.6f else cell.bottom - pad
        val blockH = bottom - top
        if (blockH <= 4f * scale) return
        var left = cell.left + pad
        val art = now?.art
        if (art != null && blockH > 24f * scale) {
            val size = min(blockH, cell.width() * 0.35f)
            val artTop = top + (blockH - size) / 2
            rect.set(left, artTop, left + size, artTop + size)
            canvas.drawBitmap(art, null, rect, fill)
            left += size + pad * 0.8f
        }
        val width = cell.right - pad - left
        if (width <= 0f) return

        val (first, second, third) = when {
            music == null || !music.hasAccess -> Triple("Music", "Allow access in GoatDash", "Customize → Music")
            now == null -> Triple("Nothing playing", "Start music in any app", null)
            else -> Triple(now.title ?: "Unknown track", now.artist, now.appName)
        }
        val titleSize = min(blockH * 0.32f, 20f * scale)
        val subSize = titleSize * 0.78f
        val total = titleSize + (if (second != null) subSize * 1.35f else 0f) + (if (third != null) subSize * 1.25f else 0f)
        var y = top + (blockH - total).coerceAtLeast(0f) / 2 + titleSize

        text.color = ink
        text.textAlign = Paint.Align.LEFT
        text.textSize = titleSize
        canvas.drawText(ellipsize(text, first, width), left, y, text)
        small.textAlign = Paint.Align.LEFT
        if (second != null) {
            y += subSize * 1.35f
            small.textSize = subSize
            small.color = ink and 0xCCFFFFFF.toInt()
            canvas.drawText(ellipsize(small, second, width), left, y, small)
            small.color = muted
        }
        if (third != null) {
            y += subSize * 1.25f
            small.textSize = subSize * 0.85f
            small.color = gauge.color
            canvas.drawText(ellipsize(small, third, width), left, y, small)
            small.color = muted
        }
    }

    private fun drawMusicButton(canvas: Canvas, button: MusicButton, cx: Float, cy: Float, size: Float, playing: Boolean, color: Int, scale: Float) {
        val s = size * 0.42f
        path.reset()
        when (button) {
            MusicButton.PLAY_PAUSE -> {
                fill.color = color
                canvas.drawCircle(cx, cy, size / 2, fill)
                fill.color = BACKGROUND
                if (playing) {
                    canvas.drawRect(cx - s * 0.4f, cy - s * 0.5f, cx - s * 0.1f, cy + s * 0.5f, fill)
                    canvas.drawRect(cx + s * 0.1f, cy - s * 0.5f, cx + s * 0.4f, cy + s * 0.5f, fill)
                } else {
                    path.moveTo(cx - s * 0.32f, cy - s * 0.52f)
                    path.lineTo(cx + s * 0.55f, cy)
                    path.lineTo(cx - s * 0.32f, cy + s * 0.52f)
                    path.close()
                    canvas.drawPath(path, fill)
                }
            }
            MusicButton.NEXT, MusicButton.PREVIOUS -> {
                // Triangle pointing the way we skip, plus a bar at the end.
                val dir = if (button == MusicButton.NEXT) 1f else -1f
                fill.color = ink
                path.moveTo(cx - dir * s * 0.5f, cy - s * 0.5f)
                path.lineTo(cx + dir * s * 0.3f, cy)
                path.lineTo(cx - dir * s * 0.5f, cy + s * 0.5f)
                path.close()
                canvas.drawPath(path, fill)
                val barX = cx + dir * s * 0.3f
                canvas.drawRect(min(barX, barX + dir * s * 0.2f), cy - s * 0.5f, maxOf(barX, barX + dir * s * 0.2f), cy + s * 0.5f, fill)
            }
            MusicButton.BACK_15, MusicButton.FORWARD_30 -> {
                arc.color = ink
                arc.strokeWidth = 2f * scale
                canvas.drawCircle(cx, cy, size * 0.36f, arc)
                small.color = ink
                small.textAlign = Paint.Align.CENTER
                small.textSize = size * 0.3f
                val label = if (button == MusicButton.BACK_15) "-15" else "+30"
                canvas.drawText(label, cx, cy + small.textSize * 0.36f, small)
                small.color = muted
            }
        }
    }

    /** Cuts [s] down with "…" so it fits in [maxWidth] at [paint]'s current size. */
    private fun ellipsize(paint: Paint, s: String, maxWidth: Float): String {
        if (paint.measureText(s) <= maxWidth) return s
        var end = s.length
        while (end > 0 && paint.measureText(s, 0, end) + paint.measureText("…") > maxWidth) end--
        return s.substring(0, end).trimEnd() + "…"
    }

    private fun isLight(color: Int): Boolean {
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        return r * 0.299 + g * 0.587 + b * 0.114 > 150
    }

    /** Shrinks [paint]'s text size from [preferred] until [s] fits in [maxWidth]. */
    private fun fitText(paint: Paint, s: String, maxWidth: Float, preferred: Float) {
        paint.textSize = preferred.coerceAtLeast(1f)
        val w = paint.measureText(s)
        if (w > maxWidth && w > 0) paint.textSize = (preferred * maxWidth / w).coerceAtLeast(1f)
    }

    companion object {
        const val BACKGROUND = 0xFF07090D.toInt()
        private const val PANEL = 0xFF12151C.toInt()
        private const val PANEL_EDGE = 0xFF222834.toInt()
        private const val TRACK = 0xFF262C38.toInt()
        private const val TEXT = 0xFFF1F5F9.toInt()
        private const val MUTED = 0xFF8B95AB.toInt()
        private const val START = 150f
        private const val SWEEP = 240f
        const val FLASH_MS = 125L
    }
}
