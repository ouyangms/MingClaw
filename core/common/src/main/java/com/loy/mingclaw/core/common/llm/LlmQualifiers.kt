package com.loy.mingclaw.core.common.llm

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudLlm

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalLlm
