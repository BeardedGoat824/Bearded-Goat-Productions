package com.beardedgoat.goatdash.dash

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The saved dashboard layout plus which page is showing. Phone and Android Auto both read
 * from here, so flipping pages on one flips the other.
 */
class DashboardStore(context: Context) {

    private val prefs = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<DashboardConfig> = _config.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private fun load(): DashboardConfig =
        prefs.getString(KEY, null)?.let { runCatching { DashboardConfig.fromJson(it) }.getOrNull() }
            ?: DashboardConfig.default()

    fun update(transform: (DashboardConfig) -> DashboardConfig) {
        _config.update(transform)
        prefs.edit().putString(KEY, _config.value.toJson()).apply()
        _currentPage.update { it.coerceIn(0, _config.value.pages.lastIndex) }
    }

    fun updatePage(index: Int, transform: (DashPage) -> DashPage) = update { cfg ->
        cfg.copy(pages = cfg.pages.mapIndexed { i, p -> if (i == index) transform(p) else p })
    }

    fun showPage(index: Int) {
        _currentPage.value = index.coerceIn(0, _config.value.pages.lastIndex)
    }

    fun nextPage() {
        val count = _config.value.pages.size
        _currentPage.update { (it + 1) % count }
    }

    fun resetToDefault() = update { DashboardConfig.default() }

    private companion object {
        const val KEY = "config_json"
    }
}
