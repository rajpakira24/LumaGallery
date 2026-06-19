package com.webstudio.lumagallery.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Latest rate-limit usage reported by the proxy via X-RateLimit-* headers. */
data class AiUsage(
    val remainingMin: Int? = null,
    val remainingHour: Int? = null,
    val remainingDay: Int? = null,
    val imageRemainingDay: Int? = null,
    val resetDaySec: Int? = null,
)

/** App-wide latest usage snapshot; UI observes this to show a low-quota hint. */
object AiUsageState {
    private val _usage = MutableStateFlow<AiUsage?>(null)
    val usage: StateFlow<AiUsage?> = _usage.asStateFlow()
    fun update(u: AiUsage) { _usage.value = u }
}
