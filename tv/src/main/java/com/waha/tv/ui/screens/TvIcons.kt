package com.waha.tv.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The TV build's icons, in the source rather than in `material-icons-extended`.
 *
 * The extended set is a very large library — thousands of glyphs — brought in for the
 * twelve this build actually draws, and every one of them is dex the app has to load.
 * These are the same official Material paths the library ships, parsed once (lazily,
 * the first time a screen asks for one), so the screens keep the familiar
 * `ImageVector` API and call sites only change which object they name.
 *
 * Sizes are the Material grid: a 24dp icon on a 24×24 viewport, tinted by
 * `Icon(tint = …)` at the call site exactly as before.
 *
 * Each vector keeps the name the library gives it (`Filled.PlayArrow`, `Filled.Home`),
 * because that name is what the tests use to tell one glyph from another.
 */
@Suppress("MemberVisibilityCanBePrivate")
object TvIcons {

    /** The filled glyphs: the "remote is here" state, and the player's transport. */
    val PlayArrow: ImageVector by lazy { icon("Filled.PlayArrow", "M8 5v14l11-7z") }

    val Pause: ImageVector by lazy {
        icon("Filled.Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    }

    val Replay10: ImageVector by lazy {
        icon(
            "Filled.Replay10",
            "M11.99,5V1l-5,5l5,5V7c3.31,0,6,2.69,6,6s-2.69,6-6,6s-6-2.69-6-6h-2c0,4.42,3.58,8,8,8s8-3.58,8-8S16.41,5,11.99,5z",
            "M10.89,16h-0.85v-3.26l-1.01,0.31v-0.69l1.77-0.63h0.09V16z",
            "M15.17,14.24c0,0.32-0.03,0.6-0.1,0.82s-0.17,0.42-0.29,0.57s-0.28,0.26-0.45,0.33s-0.37,0.1-0.59,0.1 " +
                "s-0.41-0.03-0.59-0.1s-0.33-0.18-0.46-0.33s-0.23-0.34-0.3-0.57s-0.11-0.5-0.11-0.82V13.5c0-0.32,0.03-0.6,0.1-0.82 " +
                "s0.17-0.42,0.29-0.57s0.28-0.26,0.45-0.33s0.37-0.1,0.59-0.1s0.41,0.03,0.59,0.1c0.18,0.07,0.33,0.18,0.46,0.33 " +
                "s0.23,0.34,0.3,0.57s0.11,0.5,0.11,0.82V14.24z M14.32,13.38c0-0.19-0.01-0.35-0.04-0.48s-0.07-0.23-0.12-0.31 " +
                "s-0.11-0.14-0.19-0.17s-0.16-0.05-0.25-0.05s-0.18,0.02-0.25,0.05s-0.14,0.09-0.19,0.17s-0.09,0.18-0.12,0.31 " +
                "s-0.04,0.29-0.04,0.48v0.97c0,0.19,0.01,0.35,0.04,0.48s0.07,0.24,0.12,0.32s0.11,0.14,0.19,0.17s0.16,0.05,0.25,0.05 " +
                "s0.18-0.02,0.25-0.05s0.14-0.09,0.19-0.17s0.09-0.19,0.11-0.32s0.04-0.29,0.04-0.48V13.38z"
        )
    }

    val Forward10: ImageVector by lazy {
        icon(
            "Filled.Forward10",
            "M18,13c0,3.31-2.69,6-6,6s-6-2.69-6-6s2.69-6,6-6v4l5-5l-5-5v4c-4.42,0-8,3.58-8,8c0,4.42,3.58,8,8,8s8-3.58,8-8H18z",
            "M10.86,15.94 L10.86,11.67 L10.77,11.67 L9,12.3 L9,12.99 L10.01,12.68 L10.01,15.94 Z",
            "M12.25,13.44v0.74c0,1.9,1.31,1.82,1.44,1.82c0.14,0,1.44,0.09,1.44-1.82v-0.74c0-1.9-1.31-1.82-1.44-1.82 " +
                "C13.55,11.62,12.25,11.53,12.25,13.44z M14.29,13.32v0.97c0,0.77-0.21,1.03-0.59,1.03c-0.38,0-0.6-0.26-0.6-1.03v-0.97 " +
                "c0-0.75,0.22-1.01,0.59-1.01C14.07,12.3,14.29,12.57,14.29,13.32z"
        )
    }

    val Bookmark: ImageVector by lazy {
        icon("Filled.Bookmark", "M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z")
    }

    val BookmarkBorder: ImageVector by lazy {
        icon(
            "Filled.BookmarkBorder",
            "M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z"
        )
    }

    val History: ImageVector by lazy {
        icon(
            "Filled.History",
            "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7" +
                "c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9z" +
                "m-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"
        )
    }

    val Refresh: ImageVector by lazy {
        icon(
            "Filled.Refresh",
            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08" +
                "c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
        )
    }

    /** The rail's glyphs: solid at rest and while the remote is on them. */
    val Home: ImageVector by lazy { icon("Filled.Home", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z") }

    val Search: ImageVector by lazy {
        icon(
            "Filled.Search",
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16" +
                "c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5" +
                " 14 7.01 14 9.5 11.99 14 9.5 14z"
        )
    }

    val Settings: ImageVector by lazy {
        icon(
            "Filled.Settings",
            "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61" +
                " l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81" +
                "c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29" +
                "L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58" +
                "C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32" +
                "c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54" +
                " c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96" +
                " c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z" +
                " M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"
        )
    }

    val VideoLibrary: ImageVector by lazy {
        icon(
            "Filled.VideoLibrary",
            "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4" +
                "c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z"
        )
    }
}

/**
 * Builds a 24dp Material icon from one or more SVG path strings — the exact grammar
 * the Material icon set is authored in, so nothing is redrawn by hand.
 */
private fun icon(name: String, vararg pathData: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    pathData.forEach { data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }
    return builder.build()
}
