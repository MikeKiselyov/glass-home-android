package com.glasshome.tv

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Threads for work the screen waits on but must not compete with: decoding
 * photos, reading the app list, the weather. They run below the UI and render
 * threads, so the first frame and moving along the dock come first. A thread
 * made the plain way gets the priority of the thread that made it, which is
 * usually the UI thread's.
 */
object Background {

    fun executor(name: String): ExecutorService {
        return Executors.newSingleThreadExecutor(factory(name))
    }

    fun factory(name: String): ThreadFactory {
        return ThreadFactory { work ->
            val thread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                work.run()
            }, name)
            thread.isDaemon = true
            thread
        }
    }

    /** The priority the threads above run at; Android's scale, larger is lower. */
    const val PRIORITY = Process.THREAD_PRIORITY_BACKGROUND
}
