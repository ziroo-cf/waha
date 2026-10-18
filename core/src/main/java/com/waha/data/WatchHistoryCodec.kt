package com.waha.data

/**
 * How the watch history is (de)serialised for DataStore.
 *
 * The entries are stored as newline-separated `key=value` text, which keeps the
 * format readable and — more importantly — means the round trip can be unit
 * tested on the JVM, where DataStore itself is unavailable.
 */
object WatchHistoryCodec {

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
    fun decodeMillis(raw: String?): Map<String, Long> =
        raw?.split('\n')
            ?.mapNotNull { entry ->
                val id = entry.substringBefore('=')
                val millis = entry.substringAfter('=', "").toLongOrNull()
                if (id.isNotBlank() && millis != null) id to millis else null
            }
            ?.toMap()
            ?: emptyMap()
}
