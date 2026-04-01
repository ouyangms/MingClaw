package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.task.internal.ConcurrencyControllerImpl
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ConcurrencyControllerImplTest {

    private lateinit var controller: ConcurrencyControllerImpl

    @Before
    fun setup() {
        controller = ConcurrencyControllerImpl()
    }

    @Test
    fun acquire_and_release() = runTest {
        controller.acquire()
        controller.release()
        // Should not block
        controller.acquire()
        controller.release()
    }

    @Test
    fun setMaxConcurrency_changesLimit() = runTest {
        controller.setMaxConcurrency(2)
        controller.acquire()
        controller.acquire()
        controller.release()
        controller.release()
    }
}
