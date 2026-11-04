package com.waha.tv.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Which card the remote was last on.
 *
 * The player covers the shelf without destroying it, so when it closes the viewer
 * expects the remote to be on the card they opened — not back at the top of the rail,
 * which, on a shelf of six rails, means walking to their place again. Every card
 * registers itself through [LocalTvCardFocusMemory] the moment the remote lands on it,
 * and the shell asks the memory to restore that card when the player leaves.
 *
 * Held per shell (one instance in `TvApp`), because a card's requester is only valid
 * while the card is composed: a stale one fails to focus, and the shell then falls
 * back to the rail.
 */
@Stable
internal class TvCardFocusMemory {

    private var lastFocused: FocusRequester? = null

    /** Notes that the remote has landed on the card owning [requester]. */
    fun register(requester: FocusRequester) {
        lastFocused = requester
    }

    /**
     * Returns the remote to the last card, or false when that card has since left the
     * screen (the library changed under the player, or the remote came from a rail).
     */
    suspend fun restore(): Boolean = lastFocused?.requestFocusWithRetry() ?: false
}

/**
 * Read by every card. Static: the memory instance never changes while the app runs.
 */
internal val LocalTvCardFocusMemory = staticCompositionLocalOf<TvCardFocusMemory?> { null }

/**
 * Focuses [this] requester, waiting a frame between attempts because a node is only
 * focusable once it has been placed. Returns false when every attempt failed, so the
 * caller can decide what to do instead of leaving the screen with no focus target.
 */
internal suspend fun FocusRequester.requestFocusWithRetry(attempts: Int = 4): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.isSuccess) return true
    }
    return false
}
