package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

class SignInGuardTest {

    private class MemoryStorage : SignInStorage {
        var code: String? = null
        var tokens: Set<String> = emptySet()

        override fun loadCode(): String? {
            return code
        }

        override fun saveCode(code: String) {
            this.code = code
        }

        override fun loadTokens(): Set<String> {
            return tokens
        }

        override fun saveTokens(tokens: Set<String>) {
            this.tokens = HashSet(tokens)
        }
    }

    private lateinit var storage: MemoryStorage
    private var now = 1_000_000_000_000L
    private lateinit var guard: SignInGuard

    @Before
    fun setUp() {
        storage = MemoryStorage()
        guard = SignInGuard(storage, Random(42)) { now }
    }

    private fun wrongCode(): String {
        if (guard.code == "000000") {
            return "000001"
        }
        return "000000"
    }

    private fun minutes(count: Long): Long {
        return count * 60_000L
    }

    // ---------- the code ----------

    @Test
    fun createsASixDigitCodeAndSavesIt() {
        assertTrue(guard.code.matches(Regex("\\d{6}")))
        assertEquals(guard.code, storage.code)
    }

    @Test
    fun keepsTheSavedCodeAcrossLaunches() {
        val again = SignInGuard(storage, Random(7)) { now }
        assertEquals(guard.code, again.code)
    }

    @Test
    fun rightCodeSignsIn() {
        val result = guard.signIn("192.168.1.30", guard.code)
        assertTrue(result is SignInResult.SignedIn)
    }

    @Test
    fun spacesAroundTheCodeAreIgnored() {
        val result = guard.signIn("192.168.1.30", " " + guard.code + " ")
        assertTrue(result is SignInResult.SignedIn)
    }

    @Test
    fun wrongCodeIsRefused() {
        val result = guard.signIn("192.168.1.30", wrongCode())
        assertEquals(SignInResult.WrongCode, result)
    }

    // ---------- per-address lockout ----------

    @Test
    fun fiveWrongCodesLockThatAddressForFiveMinutes() {
        for (attempt in 1..5) {
            guard.signIn("192.168.1.30", wrongCode())
        }
        val locked = guard.signIn("192.168.1.30", guard.code)
        assertEquals(SignInResult.Locked(5), locked)

        now += minutes(5)
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.SignedIn)
    }

    @Test
    fun lockedAddressDoesNotLockOthers() {
        for (attempt in 1..5) {
            guard.signIn("192.168.1.30", wrongCode())
        }
        assertTrue(guard.signIn("192.168.1.31", guard.code) is SignInResult.SignedIn)
    }

    @Test
    fun rightCodeResetsTheAddressCount() {
        for (attempt in 1..4) {
            guard.signIn("192.168.1.30", wrongCode())
        }
        guard.signIn("192.168.1.30", guard.code)
        for (attempt in 1..4) {
            guard.signIn("192.168.1.30", wrongCode())
        }
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.SignedIn)
    }

    // ---------- lockout for everyone ----------

    /** 4 wrong codes from each of [addressCount] addresses: under the per-address limit. */
    private fun guessFromManyAddresses(addressCount: Int) {
        for (index in 0 until addressCount) {
            for (attempt in 1..4) {
                guard.signIn("fe80::$index", wrongCode())
            }
        }
    }

    @Test
    fun twentyWrongCodesFromManyAddressesLockEveryone() {
        guessFromManyAddresses(5)
        val owner = guard.signIn("192.168.1.30", guard.code)
        assertEquals(SignInResult.Locked(5), owner)
    }

    @Test
    fun lockoutForEveryoneReplacesTheCode() {
        val before = guard.code
        var changes = 0
        guard.onCodeChanged = { changes += 1 }

        guessFromManyAddresses(5)

        assertNotEquals(before, guard.code)
        assertEquals(guard.code, storage.code)
        assertEquals(1, changes)
    }

    @Test
    fun oldCodeStopsWorkingAfterTheLockout() {
        val before = guard.code
        guessFromManyAddresses(5)
        now += minutes(5)
        assertEquals(SignInResult.WrongCode, guard.signIn("192.168.1.30", before))
    }

    @Test
    fun newCodeWorksAfterTheLockout() {
        guessFromManyAddresses(5)
        now += minutes(5)
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.SignedIn)
    }

    @Test
    fun lockoutsInARowDoubleInLength() {
        guessFromManyAddresses(5)
        now += minutes(5)
        guessFromManyAddresses(5)
        assertEquals(SignInResult.Locked(10), guard.signIn("192.168.1.30", guard.code))

        now += minutes(10)
        guessFromManyAddresses(5)
        assertEquals(SignInResult.Locked(20), guard.signIn("192.168.1.30", guard.code))
    }

    @Test
    fun lockoutNeverExceedsADay() {
        for (round in 1..12) {
            guessFromManyAddresses(5)
            now += 24 * 3600_000L
        }
        guessFromManyAddresses(5)
        assertEquals(SignInResult.Locked(24 * 60), guard.signIn("192.168.1.30", guard.code))
    }

    @Test
    fun rightCodeResetsTheDoubling() {
        guessFromManyAddresses(5)
        now += minutes(5)
        guard.signIn("192.168.1.30", guard.code)
        guessFromManyAddresses(5)
        assertEquals(SignInResult.Locked(5), guard.signIn("192.168.1.30", guard.code))
    }

    // ---------- forgetting ----------

    @Test
    fun wrongCodesAreForgottenAfterAQuietHour() {
        guessFromManyAddresses(4)
        now += minutes(60)
        guessFromManyAddresses(4)
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.SignedIn)
    }

    @Test
    fun wrongCodesWithinTheHourStillCount() {
        guessFromManyAddresses(4)
        now += minutes(59)
        for (attempt in 1..4) {
            guard.signIn("10.0.0.9", wrongCode())
        }
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.Locked)
    }

    @Test
    fun doublingStartsOverAfterADayWithoutALockout() {
        guessFromManyAddresses(5)
        now += minutes(5)
        guessFromManyAddresses(5)
        now += 24 * 3600_000L + minutes(10)
        guessFromManyAddresses(5)
        assertEquals(SignInResult.Locked(5), guard.signIn("192.168.1.30", guard.code))
    }

    @Test
    fun lockoutForEveryoneKeepsPhonesSignedIn() {
        val token = (guard.signIn("192.168.1.30", guard.code) as SignInResult.SignedIn).token
        guessFromManyAddresses(5)
        assertTrue(guard.isSignedIn(token))
    }

    // ---------- sessions ----------

    @Test
    fun tokenFromSignInIsAccepted() {
        val token = (guard.signIn("192.168.1.30", guard.code) as SignInResult.SignedIn).token
        assertEquals(48, token.length)
        assertTrue(guard.isSignedIn(token))
    }

    @Test
    fun unknownOrMissingTokenIsRefused() {
        guard.signIn("192.168.1.30", guard.code)
        assertFalse(guard.isSignedIn("not-a-token"))
        assertFalse(guard.isSignedIn(""))
        assertFalse(guard.isSignedIn(null))
    }

    @Test
    fun sessionsExpireAfterThirtyDays() {
        val token = (guard.signIn("192.168.1.30", guard.code) as SignInResult.SignedIn).token
        now += 30L * 24 * 3600_000L - 1
        assertTrue(guard.isSignedIn(token))
        now += 1
        assertFalse(guard.isSignedIn(token))
    }

    @Test
    fun expiredSessionsAreDroppedFromStorage() {
        guard.signIn("192.168.1.30", guard.code)
        now += 31L * 24 * 3600_000L
        guard.signIn("192.168.1.31", guard.code)
        assertEquals(1, storage.tokens.size)
    }

    @Test
    fun phonesSignedInBeforeTheUpdateStaySignedIn() {
        storage.tokens = setOf("abc123")
        val updated = SignInGuard(storage, Random(1)) { now }

        assertTrue(updated.isSignedIn("abc123"))
        assertEquals(setOf("abc123:$now"), storage.tokens)
    }

    @Test
    fun upgradedOldSessionsExpireThirtyDaysAfterTheUpdate() {
        storage.tokens = setOf("abc123")
        val updated = SignInGuard(storage, Random(1)) { now }
        now += 30L * 24 * 3600_000L
        assertFalse(updated.isSignedIn("abc123"))
    }

    @Test
    fun brokenSavedEntriesAreIgnored() {
        storage.tokens = setOf("abc:notatime", ":123")
        val updated = SignInGuard(storage, Random(1)) { now }
        assertFalse(updated.isSignedIn("abc"))
        assertFalse(updated.isSignedIn(""))
    }

    @Test
    fun tokenPrefixDoesNotMatch() {
        val token = (guard.signIn("192.168.1.30", guard.code) as SignInResult.SignedIn).token
        assertFalse(guard.isSignedIn(token.substring(0, 10)))
    }

    // ---------- new code from the TV ----------

    @Test
    fun resetCodeSignsEveryPhoneOut() {
        val token = (guard.signIn("192.168.1.30", guard.code) as SignInResult.SignedIn).token
        guard.resetCode()
        assertFalse(guard.isSignedIn(token))
        assertTrue(storage.tokens.isEmpty())
    }

    @Test
    fun resetCodeReplacesTheCodeAndTellsTheTv() {
        val before = guard.code
        var changes = 0
        guard.onCodeChanged = { changes += 1 }
        guard.resetCode()
        assertNotEquals(before, guard.code)
        assertEquals(guard.code, storage.code)
        assertEquals(1, changes)
    }

    /** Whoever presses "New code" is at the TV: a lockout from the network must not keep them out. */
    @Test
    fun resetCodeLiftsTheLockoutForEveryone() {
        guessFromManyAddresses(5)
        guessFromManyAddresses(0)
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.Locked)

        guard.resetCode()

        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.SignedIn)
    }

    @Test
    fun resetCodeStartsTheDoublingOver() {
        guessFromManyAddresses(5)
        now += minutes(5)
        guessFromManyAddresses(5)
        guard.resetCode()
        guessFromManyAddresses(5)
        assertEquals(SignInResult.Locked(5), guard.signIn("192.168.1.30", guard.code))
    }

    @Test
    fun resetCodeLiftsAddressLockouts() {
        for (attempt in 1..5) {
            guard.signIn("192.168.1.30", wrongCode())
        }
        guard.resetCode()
        assertTrue(guard.signIn("192.168.1.30", guard.code) is SignInResult.SignedIn)
    }

    // ---------- the TV's own settings screen ----------

    @Test
    fun localSessionWorksWithoutTheCode() {
        assertTrue(guard.isSignedIn(guard.newLocalSession()))
    }

    @Test
    fun localSessionIsReusedWhileValid() {
        val first = guard.newLocalSession()
        val second = guard.newLocalSession()
        assertEquals(first, second)
        assertEquals(1, storage.tokens.size)
    }

    @Test
    fun localSessionIsReplacedAfterNewCode() {
        val first = guard.newLocalSession()
        guard.resetCode()
        val second = guard.newLocalSession()
        assertNotEquals(first, second)
        assertTrue(guard.isSignedIn(second))
    }
}
