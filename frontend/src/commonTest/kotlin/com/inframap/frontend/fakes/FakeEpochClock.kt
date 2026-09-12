package com.inframap.frontend.fakes

import com.inframap.frontend.data.time.EpochClock

class FakeEpochClock(
    var now: Long = 1_000_000L,
) : EpochClock {
    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }
}
