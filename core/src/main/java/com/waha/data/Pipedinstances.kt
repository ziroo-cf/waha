package com.waha.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * What we learned about one Piped instance from actually using it.
 *
 * Public instances come and go, and a dead one used to cost a full timeout on
 * every single resolve. Keeping the last latency and a failure streak lets the
 * known-good instance be tried first and the broken ones last.
 */
internal class InstanceHealth {
    private val failures = AtomicInteger(0)

    @Volatile
    var latencyMs: Long = Long.MAX_VALUE
        private set

    val failureCount: Int get() = failures.get()

    fun recordSuccess(elapsedMs: Long) {
        failures.set(0)
        latencyMs = elapsedMs
    }

    fun recordFailure() {
        // Capped so a long-dead instance cannot accumulate an unbounded penalty.
        failures.updateAndGet { if (it >= 5) 5 else it + 1 }
    }
}

/**
 * The instance pool: the bundled list plus whatever the public directory
 * reports, de-duplicated and ordered by observed health.
 *
 * Pure apart from the health lookup passed in, so the ordering rules are unit
 * tested without a network or a clock.
 */
internal object PipedInstances {

    /** Normalises `https://host/` and drops blanks, keeping the first occurrence. */
    fun distinct(urls: List<String>): List<String> =
        urls.asSequence()
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
            .toCollection(LinkedHashSet())
            .toList()

    /**
     * Orders [known] then [discovered] for the next attempt.
     *
     * A stable sort: instances we have no data on keep the order they were
     * declared in, so the bundled list still drives the first run of the app.
     */
    fun order(
        known: List<String>,
        discovered: List<String> = emptyList(),
        health: (String) -> InstanceHealth? = { null }
    ): List<String> {
        val candidates = distinct(known + discovered)
        return candidates.sortedWith(
            compareBy<String> { health(it)?.failureCount ?: 0 }
                .thenBy { health(it)?.latencyMs ?: Long.MAX_VALUE }
        )
    }
}

/** Health of every instance this process has talked to. */
internal object PipedInstanceHealth {
    private val entries = ConcurrentHashMap<String, InstanceHealth>()

    fun of(url: String): InstanceHealth = entries.getOrPut(url.trim().trimEnd('/')) { InstanceHealth() }

    fun peek(url: String): InstanceHealth? = entries[url.trim().trimEnd('/')]

    fun clear() = entries.clear()
}
