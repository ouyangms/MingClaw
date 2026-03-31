package com.loy.mingclaw.core.common.dispatchers

import org.junit.Assert.assertEquals
import org.junit.Test

class DispatcherModuleTest {

    @Test
    fun `MingClawDispatchers has IO and Default dispatchers`() {
        assertEquals("IO", MingClawDispatchers.IO)
        assertEquals("Default", MingClawDispatchers.Default)
    }

    @Test
    fun `IODispatcher qualifier uses correct name`() {
        assertEquals("IODispatcher", IODispatcher::class.simpleName)
    }

    @Test
    fun `DefaultDispatcher qualifier uses correct name`() {
        assertEquals("DefaultDispatcher", DefaultDispatcher::class.simpleName)
    }
}
