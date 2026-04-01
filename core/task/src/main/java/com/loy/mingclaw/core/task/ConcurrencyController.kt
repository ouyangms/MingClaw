package com.loy.mingclaw.core.task

interface ConcurrencyController {
    suspend fun acquire()
    fun release()
    fun setMaxConcurrency(max: Int)
}
