package com.waha.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [primary] and, if it has not produced a result after [backupDelayMs],
 * starts [backup] too; whichever produces a value first wins and the other is
 * cancelled.
 *
 * The delay is not wasted work avoidance alone: when [primary] fails fast the
 * backup starts immediately instead of waiting out the head start, so the slow
 * path never pays a penalty for an early failure.
 *
 * Both lanes always report exactly once (a null on failure), so this never waits
 * on a lane that silently died.
 */
internal suspend fun <T : Any> firstNonNull(
    primary: suspend () -> T?,
    backup: suspend () -> T?,
    backupDelayMs: Long
): T? = coroutineScope {
    val results = Channel<T?>(Channel.UNLIMITED)
    val primaryFailed = CompletableDeferred<Unit>()

    suspend fun run(lane: suspend () -> T?): T? = try {
        lane()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    val primaryJob = launch {
        val value = run(primary)
        if (value == null) primaryFailed.complete(Unit)
        results.send(value)
    }

    val backupJob = launch {
        // Either the head start elapses or the primary reports failure, whichever
        // comes first; then the backup joins the race.
        withTimeoutOrNull(backupDelayMs) { primaryFailed.await() }
        results.send(run(backup))
    }

    var winner: T? = null
    var received = 0
    while (winner == null && received < 2) {
        val value = results.receive()
        received++
        if (value != null) winner = value
    }

    primaryJob.cancel()
    backupJob.cancel()
    winner
}
