package com.inframap.frontend.data.time

fun interface EpochClock {
    fun nowMillis(): Long
}

object SystemEpochClock : EpochClock {
    @OptIn(kotlin.time.ExperimentalTime::class)
    override fun nowMillis(): Long =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
}
