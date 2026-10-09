package com.waha.tv

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing for a 10-foot UI.
 *
 * The phone build's 16dp gutters and 15sp body text disappear across a living
 * room, so the TV build runs a deliberately larger scale: every measurement
 * here is roughly the phone value doubled.
 */
object WahaTvDimens {

    /** Outer gutter of every screen (phone: 16dp). */
    val ScreenPadding = 48.dp

    /** Vertical rhythm between rails and sections (phone: 20dp). */
    val SectionSpacing = 36.dp

    /** Gap between two cards inside a rail. */
    val CardSpacing = 16.dp

    /** Fixed card width inside a rail; keeps rows predictable for D-pad travel. */
    val CardWidth = 320.dp

    /** 16:9 poster height that pairs with [CardWidth]. */
    val CardHeight = 180.dp

    /** Card title area under the thumbnail. */
    val CardTitleMaxLines = 2

    /** The sidebar's fixed width (phone's tablet rail is ~80dp). */
    val SidebarWidth = 232.dp

    /** Height of one sidebar entry. */
    val SidebarItemHeight = 64.dp

    /** The hero banner at the top of Home. */
    val HeroHeight = 360.dp

    /** The hero grows only a little when focused: it is already the biggest thing on screen. */
    const val HeroFocusScale = 1.03f

    /** Focus enlargement, per Material's TV guidance (phone uses 1.0 with a press dip). */
    const val FocusScale = 1.1f

    /** Focus border thickness in the brand accent. */
    val FocusBorderWidth = 3.dp

    /** Focus glow radius. */
    val FocusGlowRadius = 16.dp

    /** Corner radius for cards, badges and panels. */
    val CornerRadius = 12.dp

    /** The player's bottom control panel. */
    val PlayerPanelPadding = 32.dp

    /** Side padding for the on-screen keyboard. */
    val KeyboardKeyWidth = 64.dp
    val KeyboardKeyHeight = 56.dp
}
