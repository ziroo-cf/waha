package com.waha.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Instance ordering decides which public Piped server gets asked first. A dead
 * instance that keeps being tried first is exactly the "resolve takes 30
 * seconds" bug, so the ranking is tested as pure logic.
 */
class PipedInstancesTest {

    @Test
    fun `duplicates and trailing slashes are normalised away`() {
        val ordered = PipedInstances.distinct(
            listOf("https://a/", "https://a", " https://b ", "", "https://b/")
        )

        assertEquals(listOf("https://a", "https://b"), ordered)
    }

    @Test
    fun `instances with no history keep their declared order`() {
        val ordered = PipedInstances.order(
            known = listOf("https://first", "https://second", "https://third")
        )

        assertEquals(listOf("https://first", "https://second", "https://third"), ordered)
    }

    @Test
    fun `the fastest healthy instance is asked first and a failing one last`() {
        val healthy = InstanceHealth().apply { recordSuccess(200) }
        val faster = InstanceHealth().apply { recordSuccess(40) }
        val dead = InstanceHealth().apply { recordFailure() }

        val health = mapOf(
            "https://healthy" to healthy,
            "https://faster" to faster,
            "https://dead" to dead
        )

        val ordered = PipedInstances.order(
            known = listOf("https://dead", "https://unknown", "https://healthy", "https://faster"),
            health = { health[it] }
        )

        // Fast healthy, then slower healthy, then an instance we know nothing about,
        // then the one that just failed.
        assertEquals(
            listOf("https://faster", "https://healthy", "https://unknown", "https://dead"),
            ordered
        )
    }

    @Test
    fun `a success clears the failure streak`() {
        val health = InstanceHealth().apply {
            recordFailure()
            recordFailure()
            recordSuccess(120)
        }

        assertEquals(0, health.failureCount)
        assertEquals(120L, health.latencyMs)
    }

    @Test
    fun `the failure streak is capped`() {
        val health = InstanceHealth()
        repeat(20) { health.recordFailure() }

        assertEquals(5, health.failureCount)
    }

    @Test
    fun `discovered instances extend the known list without duplicating it`() {
        val ordered = PipedInstances.order(
            known = listOf("https://a", "https://b"),
            discovered = listOf("https://b", "https://c")
        )

        assertEquals(listOf("https://a", "https://b", "https://c"), ordered)
    }
}
