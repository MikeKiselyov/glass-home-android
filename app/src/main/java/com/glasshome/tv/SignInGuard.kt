package com.glasshome.tv

import java.util.Random

/** Where the sign-in code and session tokens are kept between launches. */
interface SignInStorage {
    fun loadCode(): String?
    fun saveCode(code: String)

    /** Each entry is "token:issuedAtMillis". */
    fun loadTokens(): Set<String>
    fun saveTokens(tokens: Set<String>)
}

sealed class SignInResult {
    data class Locked(val minutes: Long) : SignInResult()
    object WrongCode : SignInResult()
    data class SignedIn(val token: String) : SignInResult()
}

/**
 * The 6-digit code that signs a phone in, and the sessions it hands out.
 *
 * Two limits on guessing the code:
 * - per address: 5 wrong codes lock that address out for 5 minutes;
 * - for everyone: 20 wrong codes from any mix of addresses lock sign-in for
 *   everybody and replace the code, so guesses never add up. Each such lockout
 *   in a row lasts twice as long (5 minutes, 10, 20, ... up to a day).
 *
 * Wrong codes are forgotten after an hour without any, and the doubling starts
 * over after a day without a lockout, so old typos never add up to one.
 *
 * Sessions expire after 30 days. A new code from the TV signs every phone out
 * and lifts every lockout: whoever presses it is at the TV.
 */
class SignInGuard(
    private val storage: SignInStorage,
    private val random: Random,
    private val clock: () -> Long,
) {
    private val failuresByAddress = HashMap<String, Int>()
    private val lockedUntilByAddress = HashMap<String, Long>()
    private var failuresTotal = 0
    private var lastFailureAt = 0L
    private var lockedUntilForEveryone = 0L
    private var lockoutsInARow = 0
    @Volatile
    private var localSession: String? = null

    /** Called after the code changes, so the TV can show the new one. Set on the UI thread. */
    @Volatile
    var onCodeChanged: (() -> Unit)? = null

    /** Read by the TV's panel without the lock, written under it. */
    @Volatile
    var code: String = loadOrCreateCode()
        private set

    init {
        upgradeOldSessions()
    }

    fun signIn(address: String, entered: String): SignInResult {
        var codeChanged = false
        var result: SignInResult
        synchronized(this) {
            val now = clock()
            forgetOldFailures(now)
            val lockedUntil = maxOf(lockedUntilForEveryone, lockedUntilByAddress[address] ?: 0L)
            if (now < lockedUntil) {
                result = SignInResult.Locked(minutesLeft(lockedUntil, now))
            } else if (entered.trim() == code) {
                failuresByAddress.remove(address)
                failuresTotal = 0
                lockoutsInARow = 0
                result = SignInResult.SignedIn(newSession(now))
            } else {
                codeChanged = countFailure(address, now)
                result = SignInResult.WrongCode
            }
        }
        // Outside the lock: the TV redraws its panel from here.
        if (codeChanged) {
            onCodeChanged?.invoke()
        }
        return result
    }

    fun isSignedIn(token: String?): Boolean {
        if (token.isNullOrEmpty()) {
            return false
        }
        synchronized(this) {
            val now = clock()
            for (entry in storage.loadTokens()) {
                val session = Session.parse(entry) ?: continue
                if (session.token == token && !session.isExpired(now)) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * A session for the TV's own settings screen, which needs no code: it is
     * the TV. The same one is used again while it is valid.
     */
    fun newLocalSession(): String {
        val reusable = localSession
        if (reusable != null && isSignedIn(reusable)) {
            return reusable
        }
        synchronized(this) {
            val created = newSession(clock())
            localSession = created
            return created
        }
    }

    /** A fresh code that also signs every phone out. */
    fun resetCode() {
        synchronized(this) {
            code = newCode(code)
            storage.saveCode(code)
            storage.saveTokens(emptySet())
            failuresByAddress.clear()
            lockedUntilByAddress.clear()
            failuresTotal = 0
            lockedUntilForEveryone = 0L
            lockoutsInARow = 0
        }
        onCodeChanged?.invoke()
    }

    /** An hour without wrong codes clears the count; a day without a lockout clears the doubling. */
    private fun forgetOldFailures(now: Long) {
        if (failuresTotal > 0 && now - lastFailureAt >= FORGET_FAILURES_MS) {
            failuresTotal = 0
            failuresByAddress.clear()
        }
        if (lockoutsInARow > 0 && now - lockedUntilForEveryone >= MAX_LOCKOUT_MS) {
            lockoutsInARow = 0
        }
    }

    /**
     * Older versions saved bare tokens without the time they were issued.
     * They count as issued now, so phones stay signed in after an update.
     */
    private fun upgradeOldSessions() {
        val saved = storage.loadTokens()
        var changed = false
        val upgraded = HashSet<String>()
        for (entry in saved) {
            if (entry.contains(':')) {
                upgraded.add(entry)
            } else if (entry.isNotEmpty()) {
                upgraded.add(entry + ":" + clock())
                changed = true
            }
        }
        if (changed) {
            storage.saveTokens(upgraded)
        }
    }

    /** Returns true when the code was replaced. */
    private fun countFailure(address: String, now: Long): Boolean {
        lastFailureAt = now
        val failures = (failuresByAddress[address] ?: 0) + 1
        if (failures >= MAX_FAILURES_PER_ADDRESS) {
            failuresByAddress.remove(address)
            lockedUntilByAddress[address] = now + ADDRESS_LOCKOUT_MS
        } else {
            failuresByAddress[address] = failures
        }

        failuresTotal += 1
        if (failuresTotal < MAX_FAILURES_TOTAL) {
            return false
        }
        failuresTotal = 0
        failuresByAddress.clear()
        var lockout = EVERYONE_LOCKOUT_MS
        for (step in 0 until lockoutsInARow) {
            lockout *= 2
            if (lockout >= MAX_LOCKOUT_MS) {
                lockout = MAX_LOCKOUT_MS
                break
            }
        }
        lockoutsInARow += 1
        lockedUntilForEveryone = now + lockout
        code = newCode(code)
        storage.saveCode(code)
        return true
    }

    private fun newSession(now: Long): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        val token = StringBuilder()
        for (byte in bytes) {
            token.append(String.format("%02x", byte))
        }

        val kept = HashSet<String>()
        for (entry in storage.loadTokens()) {
            val session = Session.parse(entry) ?: continue
            if (!session.isExpired(now)) {
                kept.add(entry)
            }
        }
        kept.add(token.toString() + ":" + now)
        storage.saveTokens(kept)
        return token.toString()
    }

    private fun loadOrCreateCode(): String {
        val saved = storage.loadCode()
        if (saved != null) {
            return saved
        }
        val created = newCode(null)
        storage.saveCode(created)
        return created
    }

    /** A random 6-digit code that differs from [previous]. */
    private fun newCode(previous: String?): String {
        while (true) {
            val candidate = String.format("%06d", random.nextInt(1_000_000))
            if (candidate != previous) {
                return candidate
            }
        }
    }

    private fun minutesLeft(lockedUntil: Long, now: Long): Long {
        return (lockedUntil - now + 59_999) / 60_000
    }

    private class Session(val token: String, val issuedAt: Long) {
        fun isExpired(now: Long): Boolean {
            return now - issuedAt >= SESSION_MS
        }

        companion object {
            /** "token:issuedAt"; null for anything else. */
            fun parse(entry: String): Session? {
                val separator = entry.indexOf(':')
                if (separator <= 0) {
                    return null
                }
                val issuedAt = entry.substring(separator + 1).toLongOrNull() ?: return null
                return Session(entry.substring(0, separator), issuedAt)
            }
        }
    }

    companion object {
        const val SESSION_SECONDS = 30 * 24 * 3600
        private const val SESSION_MS = SESSION_SECONDS * 1000L
        private const val MAX_FAILURES_PER_ADDRESS = 5
        private const val ADDRESS_LOCKOUT_MS = 5 * 60_000L
        private const val MAX_FAILURES_TOTAL = 20
        private const val EVERYONE_LOCKOUT_MS = 5 * 60_000L
        private const val MAX_LOCKOUT_MS = 24 * 3600_000L
        private const val FORGET_FAILURES_MS = 3600_000L
    }
}
