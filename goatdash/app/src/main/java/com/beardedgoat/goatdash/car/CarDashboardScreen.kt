package com.beardedgoat.goatdash.car

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.R
import com.beardedgoat.goatdash.dash.GaugeRenderer
import com.beardedgoat.goatdash.obd.ConnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The gauges on the car's screen. We draw straight onto the surface Android Auto gives us,
 * using the same [GaugeRenderer] as the phone.
 */
class CarDashboardScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private val app = carContext.applicationContext as GoatDashApp
    private val renderer = GaugeRenderer()
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8B95AB.toInt(); textAlign = Paint.Align.CENTER }

    private var surface: SurfaceContainer? = null
    private var visibleArea: Rect? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)

        lifecycleScope.launch {
            // Only redraw while the gauges are actually on screen (not behind the codes list).
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.obd.live, app.dashboard.config, app.dashboard.currentPage, app.obd.state) { _, _, _, _ -> Unit }
                    .conflate()
                    .collect {
                        render()
                        delay(50) // ~20 fps is plenty and spares the car's head unit.
                    }
            }
        }
        // The action strip changes with the connection (connect vs. disconnect button).
        lifecycleScope.launch {
            app.obd.state.map { it is ConnState.Connected || it is ConnState.Connecting }
                .distinctUntilChanged()
                .collect { invalidate() }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer
        render()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        render()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        render()
    }

    private fun render() {
        val container = surface ?: return
        val s = container.surface ?: return
        if (!s.isValid) return
        val canvas = try {
            s.lockCanvas(null)
        } catch (_: Exception) {
            return
        } ?: return
        try {
            canvas.drawColor(GaugeRenderer.BACKGROUND)
            // Stay inside the area not covered by Android Auto's own controls.
            val area = visibleArea?.takeIf { !it.isEmpty } ?: Rect(0, 0, container.width, container.height)
            val scale = container.dpi / 160f
            val cfg = app.dashboard.config.value
            val page = cfg.pages.getOrNull(app.dashboard.currentPage.value) ?: cfg.pages.first()
            renderer.drawPage(
                canvas, page, app.obd.live.value, cfg.imperial,
                area.left.toFloat(), area.top.toFloat(), area.width().toFloat(), area.height().toFloat(), scale,
            )
            statusLine()?.let { msg ->
                statusPaint.textSize = 16f * scale
                canvas.drawColor(0x99000000.toInt())
                statusPaint.color = Color.WHITE
                canvas.drawText(msg, area.exactCenterX(), area.exactCenterY(), statusPaint)
            }
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }

    private fun statusLine(): String? = when (val st = app.obd.state.value) {
        is ConnState.Connected -> null
        is ConnState.Connecting -> st.step
        is ConnState.Failed -> st.message
        ConnState.Disconnected -> "Not connected — tap the plug button to connect"
    }

    override fun onGetTemplate(): Template {
        val connected = app.obd.state.value.let { it is ConnState.Connected || it is ConnState.Connecting }
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_car_pages))
                    .setOnClickListener { app.dashboard.nextPage() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_car_codes))
                    .setOnClickListener { screenManager.push(CarCodesScreen(carContext)) }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(if (connected) R.drawable.ic_car_unplug else R.drawable.ic_car_plug))
                    .setOnClickListener {
                        val last = app.obd.lastTarget
                        when {
                            connected -> app.obd.disconnect()
                            last != null -> app.obd.connect(last)
                            else -> CarToast.makeText(carContext, "Pick your scanner in the GoatDash phone app first", CarToast.LENGTH_LONG).show()
                        }
                    }
                    .build(),
            )
            .build()
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()
}
