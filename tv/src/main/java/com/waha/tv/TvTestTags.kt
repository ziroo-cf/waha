package com.waha.tv

import com.waha.tv.ui.screens.TvDestination

/**
 * The semantics tags the TV focus tests address nodes by.
 *
 * A remote-driven layout is only as good as its focus order, and the only honest
 * way to test that order is to press real D-pad keys at tagged nodes — so the
 * tags live next to the composables they mark rather than in the test source set.
 */
object TvTestTags {

    /** The navigation rail down the screen's right edge. */
    const val SideBar = "tv_side_bar"

    /** One level-1 destination. */
    fun nav(destination: TvDestination): String = "tv_nav_${destination.name.lowercase()}"

    /**
     * One destination's label inside the rail.
     *
     * The rail shows these only while it is open, so the tag is how a test can ask
     * "are the names on screen right now?" without reading pixels.
     */
    fun navLabel(destination: TvDestination): String = "tv_nav_label_${destination.name.lowercase()}"

    /** The home screen's vertical list of rails. */
    const val HomeList = "tv_home_list"

    /** The library's vertical list (المحفوظات over السجل). */
    const val LibraryList = "tv_library_list"

    /** The search page's row of result cards. */
    const val SearchResults = "tv_search_results"

    /** The player's bottom progress bar. */
    const val PlayerProgress = "tv_player_progress"

    /** The control panel the down key opens over the video. */
    const val PlayerPanel = "tv_player_panel"

    /** The play/pause (or seek) glyph in the middle of the video. */
    const val PlayerCenter = "tv_player_center"

    /** One transport button in the player panel ("play_pause", "rewind", "forward"). */
    fun playerControl(name: String): String = "tv_player_control_$name"

    /** One quality pill in the player panel ("تلقائي", "1080p", …). */
    fun playerQuality(label: String): String = "tv_player_quality_$label"

    /** The reload button below every rail, and in the error state. */
    const val ReloadButton = "tv_reload_button"

    /** One horizontal rail. */
    fun rail(railKey: String): String = "tv_rail_$railKey"

    /** One video card. */
    fun card(videoId: String): String = "tv_card_$videoId"

    /** The artwork area of a card, measured by the 16:9 test. */
    fun artwork(videoId: String): String = "tv_artwork_$videoId"

    /** A card's duration badge, drawn over the artwork's bottom-left corner. */
    fun durationBadge(videoId: String): String = "tv_duration_$videoId"
}
