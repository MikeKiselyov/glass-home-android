package com.glasshome.tv

import android.app.Application
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Photo decoding and the like run below the UI thread, so they never hold up a frame. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BackgroundTest {

    @Test
    fun workRunsAtBackgroundPriority() {
        val executor = Background.executor("test")
        val priority = executor.submit<Int> { Process.getThreadPriority(Process.myTid()) }
        assertEquals(Process.THREAD_PRIORITY_BACKGROUND, priority.get(5, TimeUnit.SECONDS))
        executor.shutdown()
    }

    @Test
    fun threadsAreNamedForTop() {
        val executor = Background.executor("glasshome-photos")
        val name = executor.submit<String> { Thread.currentThread().name }
        assertEquals("glasshome-photos", name.get(5, TimeUnit.SECONDS))
        executor.shutdown()
    }
}
