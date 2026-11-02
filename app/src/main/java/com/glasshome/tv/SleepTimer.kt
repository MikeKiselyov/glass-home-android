package com.glasshome.tv

/** Where the sleep time is kept, so the timer outlasts a restart of Glass Home. */
interface SleepTimerStorage {
    /** The time to turn the TV off, in milliseconds; 0 for none. */
    fun loadSleepAt(): Long
    fun saveSleepAt(millis: Long)
}

/**
 * "Turn the TV off in 30 / 60 / 90 / 120 minutes", set on the website. When
 * it runs out, [HomeOverrideService.sleepTv] turns the screen off.
 */
class SleepTimer(private val storage: SleepTimerStorage) {

    @Volatile
    private var sleepAtMillis = storage.loadSleepAt()

    /** [minutes] is one of [LENGTHS] or 0 for off; false for anything else. */
    fun set(minutes: Int, now: Long): Boolean {
        if (minutes == 0) {
            sleepAtMillis = 0L
            storage.saveSleepAt(0L)
            return true
        }
        if (!LENGTHS.contains(minutes)) {
            return false
        }
        sleepAtMillis = now + minutes * MINUTE_MS
        storage.saveSleepAt(sleepAtMillis)
        return true
    }

    fun sleepAt(): Long? {
        if (sleepAtMillis == 0L) {
            return null
        }
        return sleepAtMillis
    }

    fun isDue(now: Long): Boolean {
        return sleepAtMillis != 0L && now >= sleepAtMillis
    }

    /**
     * True once, when the timer has run out: it is cleared then. A timer that
     * ran out more than [LATE_MS] ago (the TV was off) is cleared and false.
     */
    fun takeDue(now: Long): Boolean {
        if (!isDue(now)) {
            return false
        }
        val late = now - sleepAtMillis
        set(0, now)
        return late <= LATE_MS
    }

    /** Milliseconds until the app should look at the timer again; null without one. */
    fun nextCheckIn(now: Long): Long? {
        if (sleepAtMillis == 0L) {
            return null
        }
        return maxOf(0L, sleepAtMillis - now)
    }

    /** Rounded up: "1 minute" until the very end. */
    fun minutesLeft(now: Long): Long {
        if (sleepAtMillis == 0L || now >= sleepAtMillis) {
            return 0L
        }
        return (sleepAtMillis - now + MINUTE_MS - 1) / MINUTE_MS
    }

    companion object {
        val LENGTHS = listOf(30, 60, 90, 120)
        private const val MINUTE_MS = 60_000L
        private const val LATE_MS = 5 * MINUTE_MS
    }
}
