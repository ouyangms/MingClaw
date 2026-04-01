package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.task.ConcurrencyController
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConcurrencyControllerImpl @Inject constructor() : ConcurrencyController {
    @Volatile
    private var semaphore = Semaphore(4) // default 4 concurrent tasks

    override suspend fun acquire() {
        semaphore.acquire()
    }

    override fun release() {
        semaphore.release()
    }

    override fun setMaxConcurrency(max: Int) {
        semaphore = Semaphore(max.coerceAtLeast(1))
    }
}
