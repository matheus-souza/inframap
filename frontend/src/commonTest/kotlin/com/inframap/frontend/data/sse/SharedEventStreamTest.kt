package com.inframap.frontend.data.sse

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Records every connection the stream opens, standing in for the browser's EventSource. */
private class RecordingOpener : EventStreamOpener {
    val urls = mutableListOf<String>()
    var closed = 0
    lateinit var onOpen: () -> Unit
    lateinit var onError: () -> Unit
    lateinit var onEvent: (String, String, String) -> Unit

    val open: Int get() = urls.size - closed

    override fun open(
        url: String,
        onOpen: () -> Unit,
        onError: () -> Unit,
        onEvent: (id: String, type: String, data: String) -> Unit,
    ): EventStreamConnection {
        urls += url
        this.onOpen = onOpen
        this.onError = onError
        this.onEvent = onEvent
        return EventStreamConnection { closed++ }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharedEventStreamTest {
    private val url = "/api/v1/events/stream"

    @Test
    fun everyClientOfATabSharesOneConnection() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            val badge = SharedSSEClient(stream)
            val dashboard = SharedSSEClient(stream)

            badge.connect(url)
            dashboard.connect(url)

            assertEquals(1, opener.urls.size, "the browser allows six connections per host; a tab must hold one")
        }

    @Test
    fun theConnectionClosesWhenItsLastClientLeaves() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            val badge = SharedSSEClient(stream)
            val dashboard = SharedSSEClient(stream)
            badge.connect(url)
            dashboard.connect(url)

            dashboard.disconnect()
            assertEquals(1, opener.open)

            badge.disconnect()
            assertEquals(0, opener.open)
        }

    @Test
    fun disconnectingTwiceReleasesTheConnectionOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            val badge = SharedSSEClient(stream)
            val dashboard = SharedSSEClient(stream)
            badge.connect(url)
            dashboard.connect(url)

            dashboard.disconnect()
            dashboard.disconnect()

            assertEquals(1, opener.open, "the badge still listens")
        }

    @Test
    fun eventsReachEveryClient() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            val first = SharedSSEClient(stream).connect(url)
            val second = SharedSSEClient(stream).connect(url)
            val received = mutableListOf<SSEEvent>()
            backgroundScope.launch { first.collect { received += it } }
            backgroundScope.launch { second.collect { received += it } }

            opener.onEvent("7", "device.updated", "{}")

            assertEquals(2, received.filterIsInstance<SSEEvent.DeviceUpdated>().size)
        }

    @Test
    fun aClientJoiningAnOpenConnectionIsToldItIsConnected() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            SharedSSEClient(stream).connect(url)
            opener.onOpen()

            val joined = SharedSSEClient(stream).connect(url).first()

            assertIs<SSEEvent.Connected>(joined)
        }

    @Test
    fun aDroppedConnectionIsReportedToEveryClientAndReopenedOnTheNextConnect() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            val badge = SharedSSEClient(stream)
            val dashboard = SharedSSEClient(stream)
            val badgeEvents = badge.connect(url)
            val dashboardEvents = dashboard.connect(url)
            val seen = mutableListOf<SSEEvent>()
            backgroundScope.launch { badgeEvents.take(1).toList(seen) }
            backgroundScope.launch { dashboardEvents.take(1).toList(seen) }

            opener.onError()

            assertEquals(2, seen.count { it is SSEEvent.Disconnected })
            assertEquals(0, opener.open)

            badge.disconnect()
            dashboard.disconnect()
            badge.connect(url)
            dashboard.connect(url)
            assertEquals(1, opener.open)
        }

    @Test
    fun aReopenedConnectionResumesAfterTheLastEventSeen() =
        runTest(UnconfinedTestDispatcher()) {
            val opener = RecordingOpener()
            val stream = SharedEventStream(backgroundScope, opener)
            val client = SharedSSEClient(stream)
            client.connect(url)
            opener.onEvent("42", "device.created", "{}")
            opener.onError()

            client.disconnect()
            client.connect(url)

            assertTrue(opener.urls.last().endsWith("?last_event_id=42"), "reopened with ${opener.urls.last()}")
        }
}
