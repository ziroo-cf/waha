package com.waha.data

/**
 * How the watch history is (de)serialised for DataStore.
 *
 * The whole snapshot travels as one text payload: four separate keys meant four
 * strings built on the caller's thread and four writes per save, with no way to
 * keep them consistent if the process died between them. Sections are marked
 * with `#`, entries with `key=value`, which keeps the format readable and — more
 * importantly — round-trippable on the JVM, where DataStore itself is
 * unavailable.
 */
object WatchHistoryCodec {

    private const val SECTION_IDS = "#ids"
    private const val SECTION_TIMES = "#times"
    private const val SECTION_POSITIONS = "#positions"
    private const val SECTION_DURATIONS = "#durations"

    /** Ordered ids, one per line. */
    fun encodeIds(ids: List<String>): String = ids.joinToString("\n")

    /** Ids back from [encodeIds]; blanks are dropped. */
    fun decodeIds(raw: String?): List<String> =
        raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    /** A `videoId=millis` map as newline-separated entries. */
    fun encodeMillis(values: Map<String, Long>): String =
        values.entries.joinToString("\n") { (id, millis) -> "$id=$millis" }

    /**
     * A [encodeMillis] payload back into a map. Malformed lines (no id, or a
     * value that is not a number) are skipped instead of failing the load.
     */
    fun decodeMillis(raw: String?): Map<String, Long> = decodeEntries(raw)

    /** The whole history as one payload, sections in a fixed order. */
    fun encodeState(state: HistorySnapshot): String = buildString {
        appendLine(SECTION_IDS)
        for (id in state.recentIds) appendLine(id)
        appendLine(SECTION_TIMES)
        for ((id, millis) in state.watchedAtMs) appendLine("$id=$millis")
        appendLine(SECTION_POSITIONS)
        for ((id, millis) in state.lastPositionMs) appendLine("$id=$millis")
        appendLine(SECTION_DURATIONS)
        for ((id, millis) in state.durationMs) appendLine("$id=$millis")
    }

    /**
     * Parses [encodeState]. A truncated or legacy payload degrades to the sections
     * that are present; unknown lines are ignored, and only ids listed in the
     * `#ids` section end up in the snapshot, so a stray map entry cannot resurrect
     * a deleted video.
     */
    fun decodeState(raw: String?): HistorySnapshot {
        if (raw.isNullOrBlank()) return HistorySnapshot()

        val ids = mutableListOf<String>()
        val times = LinkedHashMap<String, Long>()
        val positions = LinkedHashMap<String, Long>()
        val durations = LinkedHashMap<String, Long>()

        var section = ""
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            when (trimmed) {
                SECTION_IDS -> section = SECTION_IDS
                SECTION_TIMES -> section = SECTION_TIMES
                SECTION_POSITIONS -> section = SECTION_POSITIONS
                SECTION_DURATIONS -> section = SECTION_DURATIONS
                "" -> Unit
                else -> when (section) {
                    SECTION_IDS -> if (!trimmed.startsWith("#")) ids += trimmed
                    SECTION_TIMES -> entryOf(trimmed)?.let { (id, value) -> times[id] = value }
                    SECTION_POSITIONS -> entryOf(trimmed)?.let { (id, value) -> positions[id] = value }
                    SECTION_DURATIONS -> entryOf(trimmed)?.let { (id, value) -> durations[id] = value }
                }
            }
        }

        // The ids list is the source of order and membership; keep only its entries.
        val ordered = ids.distinct()
        val members = ordered.toSet()
        return HistorySnapshot(
            recentIds = ordered,
            watchedAtMs = times.filterKeys { it in members },
            lastPositionMs = positions.filterKeys { it in members },
            durationMs = durations.filterKeys { it in members }
        )
    }

    private fun decodeEntries(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, Long>()
        for (line in raw.lineSequence()) {
            entryOf(line.trim())?.let { (id, value) -> result[id] = value }
        }
        return result
    }

    /** `videoId=1234` -> (videoId, 1234); null for blanks and malformed values. */
    private fun entryOf(line: String): Pair<String, Long>? {
        if (line.isEmpty() || line.startsWith("#")) return null
        val id = line.substringBefore('=')
        val value = line.substringAfter('=', "").toLongOrNull()
        return if (id.isNotBlank() && value != null) id to value else null
    }
}
