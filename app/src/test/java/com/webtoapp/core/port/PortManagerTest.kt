package com.webtoapp.core.port

import com.google.common.truth.Truth.assertThat
import com.webtoapp.util.isAliveCompat
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PortManagerTest {

    @Before
    fun setUp() {
        PortManager.releaseAll()
    }

    @After
    fun tearDown() {
        PortManager.releaseAll()
    }

    @Test
    fun `allocate prefers free preferred port`() {
        val preferred = PortManager.PortRange.LOCAL_HTTP.start + 3
        val allocated = PortManager.allocate(
            PortManager.PortRange.LOCAL_HTTP,
            "localhttp:test",
            preferredPort = preferred
        )
        assertThat(allocated).isEqualTo(preferred)
        assertThat(PortManager.isAllocated(preferred)).isTrue()
    }

    @Test
    fun `reassign when preferred busy without kill`() {
        val preferred = PortManager.PortRange.GENERAL.start + 10
        val blocker = ServerSocket(preferred)
        try {
            val allocated = PortManager.allocate(
                PortManager.PortRange.GENERAL,
                "owner:a",
                preferredPort = preferred,
                conflictPolicy = PortManager.ConflictPolicy.REASSIGN
            )
            assertThat(allocated).isGreaterThan(0)
            assertThat(allocated).isNotEqualTo(preferred)
        } finally {
            blocker.close()
        }
    }

    @Test
    fun `alert returns conflict when preferred busy`() {
        val preferred = PortManager.PortRange.LOCAL_HTTP.start + 7
        PortManager.allocate(PortManager.PortRange.LOCAL_HTTP, "localhttp:holder", preferred)
        val allocated = PortManager.allocate(
            PortManager.PortRange.LOCAL_HTTP,
            "localhttp:challenger",
            preferredPort = preferred,
            conflictPolicy = PortManager.ConflictPolicy.ALERT
        )
        assertThat(allocated).isEqualTo(PortManager.PORT_CONFLICT)
        assertThat(PortManager.getAllocation(preferred)?.owner).isEqualTo("localhttp:holder")
    }

    @Test
    fun `auto kill takes preferred after releasing holder`() {
        val preferred = PortManager.PortRange.GENERAL.start + 5
        PortManager.allocate(PortManager.PortRange.GENERAL, "localhttp:old", preferred)
        val stopped = AtomicBoolean(false)
        PortManager.registerStopHandler(preferred) {
            stopped.set(true)
        }

        val allocated = PortManager.allocate(
            PortManager.PortRange.GENERAL,
            "localhttp:new",
            preferredPort = preferred,
            conflictPolicy = PortManager.ConflictPolicy.AUTO_KILL
        )

        assertThat(allocated).isEqualTo(preferred)
        assertThat(stopped.get()).isTrue()
        assertThat(PortManager.getAllocation(preferred)?.owner).isEqualTo("localhttp:new")
    }

    @Test
    fun `release invokes stop handler and terminates process`() {
        val port = PortManager.allocate(PortManager.PortRange.LOCAL_HTTP, "localhttp:test")
        val process = ProcessBuilder("sh", "-c", "sleep 30").start()
        PortManager.registerProcess(port, process)
        val handlerCalled = AtomicBoolean(false)
        PortManager.registerStopHandler(port) { handlerCalled.set(true) }

        PortManager.release(port)

        assertThat(handlerCalled.get()).isTrue()
        assertThat(PortManager.isAllocated(port)).isFalse()
        assertThat(process.isAliveCompat()).isFalse()
    }

    @Test
    fun `track external keeps allocation while port is bound`() {
        val preferred = PortManager.PortRange.LOCAL_HTTP.start + 11
        val blocker = ServerSocket(preferred)
        try {
            val tracked = PortManager.trackExternal(
                preferred,
                "localhttp:ext",
                PortManager.PortRange.LOCAL_HTTP
            )
            assertThat(tracked).isTrue()
            assertThat(PortManager.isAllocated(preferred)).isTrue()
            assertThat(PortManager.getAllocation(preferred)?.external).isTrue()
        } finally {
            blocker.close()
            PortManager.release(preferred)
        }
    }

    @Test
    fun `release removes allocation`() {
        val port = PortManager.allocate(PortManager.PortRange.LOCAL_HTTP, "localhttp:test")
        assertThat(PortManager.isAllocated(port)).isTrue()

        PortManager.release(port)

        assertThat(PortManager.isAllocated(port)).isFalse()
    }

    @Test
    fun `convenience allocator registers expected owner prefix`() {
        val port = PortManager.allocateForLocalHttp("projectA")

        assertThat(PortManager.getAllocation(port)?.owner).isEqualTo("localhttp:projectA")
    }

    @Test
    fun `releaseAll clears all tracked allocations`() {
        PortManager.allocateForLocalHttp("a")
        PortManager.allocateForLocalHttp("b")
        PortManager.allocateForLocalHttp("c")

        assertThat(PortManager.getAllAllocations()).isNotEmpty()
        PortManager.releaseAll()
        assertThat(PortManager.getAllAllocations()).isEmpty()
    }
}
