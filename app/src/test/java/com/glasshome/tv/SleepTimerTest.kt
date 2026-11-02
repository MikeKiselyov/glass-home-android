package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Turn the TV off in 30 / 60 / 90 / 120 minutes". */
class SleepTimerTest {

    private class MemoryStorage : SleepTimerStorage {
        var saved = 0L

        override fun loadSleepAt(): Long {
            return saved
        }

        override fun saveSleepAt(millis: Long) {
            saved = millis
        }
    }

    private val minute = 60_000L

    @Test
    fun aTimerCountsDownAndIsKept() {
        val storage = MemoryStorage()
        val timer = SleepTimer(storage)
        assertTrue(timer.set(30, 1_000_000L))
        assertEquals(1_000_000L + 30 * minute, storage.saved)
        assertEquals(30L, timer.minutesLeft(1_000_000L))
        assertEquals(1L, timer.minutesLeft(1_000_000L + 29 * minute + 1))
        assertFalse(timer.isDue(1_000_000L + 29 * minute))
        assertTrue(timer.isDue(1_000_000L + 30 * minute))
        // After Glass Home starts again the timer is still there.
        assertEquals(30L, SleepTimer(storage).minutesLeft(1_000_000L))
    }

    @Test
    fun offClearsIt() {
        val timer = SleepTimer(MemoryStorage())
        timer.set(60, 0L)
        timer.set(0, 10L)
        assertNull(timer.sleepAt())
        assertFalse(timer.isDue(Long.MAX_VALUE))
        assertEquals(0L, timer.minutesLeft(10L))
    }

    @Test
    fun onlyTheOfferedLengths() {
        val timer = SleepTimer(MemoryStorage())
        assertFalse(timer.set(7, 0L))
        assertFalse(timer.set(-30, 0L))
        assertFalse(timer.set(100_000, 0L))
        assertNull(timer.sleepAt())
    }

    @Test
    fun takingADueTimerClearsIt() {
        val timer = SleepTimer(MemoryStorage())
        timer.set(30, 0L)
        assertFalse(timer.takeDue(29 * minute))
        assertTrue(timer.takeDue(30 * minute))
        assertNull(timer.sleepAt())
        assertFalse(timer.takeDue(31 * minute))
    }

    /**
     * A timer that ran out while the TV was off: turning the TV on must not
     * turn it off again. It is dropped instead.
     */
    @Test
    fun aTimerFromLongAgoIsDroppedNotActedOn() {
        val storage = MemoryStorage()
        storage.saved = 1_000L
        val timer = SleepTimer(storage)
        assertFalse(timer.takeDue(1_000L + 6 * minute))
        assertNull(timer.sleepAt())
        assertEquals(0L, storage.saved)
    }

    /** When the app looks at the timer next: at the end, at once if late, never without one. */
    @Test
    fun theNextLookIsAtTheEnd() {
        val timer = SleepTimer(MemoryStorage())
        assertNull(timer.nextCheckIn(0L))
        timer.set(30, 0L)
        assertEquals(30 * minute, timer.nextCheckIn(0L))
        assertEquals(0L, timer.nextCheckIn(31 * minute))
    }
}
