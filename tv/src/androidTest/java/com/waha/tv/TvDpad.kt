package com.waha.tv

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress

/**
 * Presses [key] on the TV surface [count] times.
 *
 * A TV has no touchscreen, so nothing in these tests taps: focus is walked to a
 * node with the direction keys and activated with the centre key. Every press is
 * a real ACTION_DOWN/ACTION_UP pair with a wait afterwards, exactly like remote
 * hardware, so the focus system sees the same event stream it does in a living
 * room rather than a synthetic single-shot key.
 */
fun ComposeTestRule.pressDPadKey(key: Key, count: Int = 1) {
    repeat(count) {
        onRoot().performKeyPress(
            KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, key.nativeKeyCode))
        )
        onRoot().performKeyPress(
            KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_UP, key.nativeKeyCode))
        )
        waitForIdle()
    }
}
