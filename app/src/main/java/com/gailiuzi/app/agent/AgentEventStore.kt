package com.gailiuzi.app.agent

import com.gailiuzi.app.model.AgentEvent
import com.gailiuzi.app.model.AgentEventType
import com.gailiuzi.app.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object AgentEventStore {
    private val sequence = AtomicLong(0)
    private val mutableEvents = MutableStateFlow<List<AgentEvent>>(emptyList())
    val events: StateFlow<List<AgentEvent>> = mutableEvents.asStateFlow()

    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        mutableRunning.value = running
    }

    fun add(
        type: AgentEventType,
        title: String,
        detail: String,
        platform: Platform? = null,
    ) {
        val event = AgentEvent(
            id = sequence.incrementAndGet(),
            type = type,
            title = title,
            detail = detail,
            timestamp = System.currentTimeMillis(),
            platform = platform,
        )
        mutableEvents.value = (listOf(event) + mutableEvents.value).take(MAX_EVENTS)
    }

    fun clear() {
        mutableEvents.value = emptyList()
    }

    private const val MAX_EVENTS = 30
}

