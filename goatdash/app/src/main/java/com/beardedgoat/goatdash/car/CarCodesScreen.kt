package com.beardedgoat.goatdash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.obd.ConnState
import com.beardedgoat.goatdash.obd.DtcDescriptions
import kotlinx.coroutines.launch

/**
 * Trouble codes on the car screen. Read-only on purpose: clearing codes is left to the phone
 * so nobody does it by accident while driving.
 */
class CarCodesScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val app = carContext.applicationContext as GoatDashApp

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        if (app.obd.state.value is ConnState.Connected && app.obd.codes.value.report == null) app.obd.readCodes()
        lifecycleScope.launch { app.obd.codes.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val codes = app.obd.codes.value
        val builder = ListTemplate.Builder()
            .setTitle("Trouble codes")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Re-scan").setOnClickListener { app.obd.readCodes() }.build())
                    .build(),
            )

        if (codes.busy) return builder.setLoading(true).build()

        val limit = if (carContext.carAppApiLevel >= 2) {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } else 6
        val list = ItemList.Builder()
        val report = codes.report
        val rows = mutableListOf<Row>()

        when {
            app.obd.state.value !is ConnState.Connected && report == null ->
                rows += Row.Builder().setTitle("Not connected").addText("Connect to your scanner to read codes.").build()
            report == null ->
                rows += Row.Builder().setTitle(codes.message ?: "No scan yet").addText("Tap Re-scan to read codes.").build()
            else -> {
                rows += Row.Builder()
                    .setTitle(if (report.milOn) "Check engine light: ON" else "Check engine light: off")
                    .addText(if (report.isEmpty) "No trouble codes found" else "${report.stored.size} stored · ${report.pending.size} pending · ${report.permanent.size} permanent")
                    .build()
                val all = report.stored.map { it to "Stored" } + report.pending.map { it to "Pending" } + report.permanent.map { it to "Permanent" }
                all.forEach { (code, kind) ->
                    rows += Row.Builder().setTitle("$code · $kind").addText(DtcDescriptions.describe(code)).build()
                }
            }
        }
        rows.take(limit).forEach { list.addItem(it) }
        return builder.setSingleList(list.build()).build()
    }
}
