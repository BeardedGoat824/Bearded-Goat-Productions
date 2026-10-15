package com.beardedgoat.goatdash.dash

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
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
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val rect = RectF()
    private val path = Path()

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
    ) {
        cellRects(page, left, top, width, height, scale).forEachIndexed { i, cell ->
            val gauge = page.gauges.getOrNull(i) ?: return@forEachIndexed
            val pid = Pids[gauge.pidId] ?: return@forEachIndexed
            drawGauge(canvas, cell, gauge, pid, live, imperial, scale, selected = i == selectedSlot)
        }
    }

    private fun drawGauge(canvas: Canvas, cell: RectF, gauge: Gauge, pid: Pid, live: LiveData, imperial: Boolean, scale: Float, selected: Boolean) {
        val value = live.values[pid.id]
        val unsupported = pid.id in live.unsupported
        val warning = gauge.warnAbove != null && value != null && value > gauge.warnAbove
        val color = if (warning) GaugeColors.WARNING else gauge.color

        val radius = 14f * scale
        canvas.drawRoundRect(cell, radius, radius, panel)
        panelStroke.strokeWidth = (if (selected || warning) 2.5f else 1f) * scale
        panelStroke.color = when {
            selected -> Color.WHITE
            warning -> GaugeColors.WARNING
            else -> PANEL_EDGE
        }
        canvas.drawRoundRect(cell, radius, radius, panelStroke)

        val valueText = if (unsupported) "N/A" else pid.format(value, imperial)
        val unitText = pid.unit.label(imperial)
        val fraction = value?.let { ((it - pid.min) / (pid.max - pid.min)).toFloat().coerceIn(0f, 1f) }
        val warnFraction = gauge.warnAbove?.let { ((it - pid.min) / (pid.max - pid.min)).toFloat().coerceIn(0f, 1f) }

        when (gauge.style) {
            GaugeStyle.DIAL -> drawDial(canvas, cell, pid, valueText, unitText, fraction, warnFraction, color, scale)
            GaugeStyle.BAR -> drawBar(canvas, cell, pid, valueText, unitText, fraction, warnFraction, color, scale)
            GaugeStyle.DIGITAL -> drawDigital(canvas, cell, pid, valueText, unitText, color, scale)
            GaugeStyle.GRAPH -> drawGraph(canvas, cell, pid, valueText, unitText, live.history[pid.id].orEmpty(), color, scale)
        }
    }

    private fun drawDial(canvas: Canvas, cell: RectF, pid: Pid, valueText: String, unitText: String, fraction: Float?, warnFraction: Float?, color: Int, scale: Float) {
        val pad = 10f * scale
        val r = min(cell.width() - pad * 2, (cell.height() - pad * 2) * 1.18f) / 2f
        val cx = cell.centerX()
        val cy = cell.top + pad + r + (cell.height() - pad * 2 - r * 1.85f).coerceAtLeast(0f) / 2f
        val stroke = r * 0.13f
        val arcR = r - stroke / 2
        rect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)

        arc.strokeWidth = stroke
        arc.color = TRACK
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
        line.color = MUTED
        line.strokeWidth = 1.2f * scale
        for (i in 0..10) {
            val a = Math.toRadians((START + SWEEP * i / 10f).toDouble())
            val outer = arcR - stroke * 0.9f
            val inner = outer - if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f
            canvas.drawLine(cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(), line)
        }

        // Needle.
        if (fraction != null) {
            val a = Math.toRadians((START + SWEEP * fraction).toDouble())
            val len = arcR - stroke * 1.3f
            line.color = Color.WHITE
            line.strokeWidth = 2.2f * scale
            canvas.drawLine(cx, cy, cx + (cos(a) * len).toFloat(), cy + (sin(a) * len).toFloat(), line)
            fill.color = color
            canvas.drawCircle(cx, cy, stroke * 0.45f, fill)
        }

        text.color = TEXT
        text.textAlign = Paint.Align.CENTER
        fitText(text, valueText, r * 1.15f, r * 0.42f)
        canvas.drawText(valueText, cx, cy + r * 0.52f, text)
        small.textAlign = Paint.Align.CENTER
        fitText(small, "${pid.shortName.uppercase()}  $unitText", r * 1.5f, r * 0.15f)
        canvas.drawText("${pid.shortName.uppercase()}  $unitText", cx, cy + r * 0.82f, small)
    }

    private fun drawBar(canvas: Canvas, cell: RectF, pid: Pid, valueText: String, unitText: String, fraction: Float?, warnFraction: Float?, color: Int, scale: Float) {
        val pad = 12f * scale
        val innerW = cell.width() - pad * 2
        val labelSize = min(cell.height() * 0.13f, 16f * scale)
        small.textAlign = Paint.Align.LEFT
        fitText(small, pid.shortName.uppercase(), innerW, labelSize)
        canvas.drawText(pid.shortName.uppercase(), cell.left + pad, cell.top + pad + small.textSize, small)

        val barH = (cell.height() * 0.16f).coerceIn(6f * scale, 22f * scale)
        val barTop = cell.bottom - pad - barH
        text.color = TEXT
        text.textAlign = Paint.Align.LEFT
        val space = barTop - (cell.top + pad + small.textSize) - pad * 0.5f
        fitText(text, valueText, innerW * 0.7f, space * 0.75f)
        val baseline = barTop - pad * 0.6f - (space - text.textSize).coerceAtLeast(0f) / 2f
        canvas.drawText(valueText, cell.left + pad, baseline, text)
        val unitX = cell.left + pad + text.measureText(valueText) + 6f * scale
        small.textSize = text.textSize * 0.35f
        canvas.drawText(unitText, unitX, baseline, small)

        rect.set(cell.left + pad, barTop, cell.right - pad, barTop + barH)
        fill.color = TRACK
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

        text.color = TEXT
        text.textAlign = Paint.Align.RIGHT
        val shown = "$valueText $unitText"
        fitText(text, shown, cell.width() * 0.55f, labelSize * 1.5f)
        canvas.drawText(shown, cell.right - pad, cell.top + pad + text.textSize * 0.9f, text)

        val gTop = cell.top + pad * 1.6f + text.textSize
        val gBottom = cell.bottom - pad
        val gLeft = cell.left + pad
        val gRight = cell.right - pad
        line.color = TRACK
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
    }
}
