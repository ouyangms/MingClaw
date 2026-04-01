# Network Layer (core:network) 实现计划

## 概述
实现 LLM API 网络层 `core:network`，提供与 OpenAI 兼容 API（DashScope）的 HTTP 客户端，支持同步/流式聊天和向量嵌入。

## 设计决策
- 使用 Retrofit + OkHttp 而非 DashScope SDK：保持 API 兼容性，不绑定特定云服务商
- 通过 LlmProvider 抽象层（core:model）解耦，core:network 提供 CloudLlmProvider 实现
- 默认使用 DashScope API：`https://dashscope.aliyuncs.com/compatible-mode/v1`
- 嵌入模型: `text-embedding-v4`

## MVP 范围
- DTO: ChatCompletionRequest/Response, ChatCompletionChunk, EmbeddingRequest/Response
- LlmApi: Retrofit 接口（chat, chatStream, embedding）
- LlmService: 内部服务接口
- LlmServiceImpl: 委托 LlmApi 的实现
- CloudLlmProvider: 实现 core:model 的 LlmProvider 接口
- AuthInterceptor: OkHttp Bearer token 拦截器
- SseParser: SSE 流式响应解析器
- NetworkModule: Hilt DI 模块

## 延迟实现
- 请求重试策略
- 连接池调优
- API Key 安全存储（当前通过 AuthInterceptor 内存变量）
- 响应缓存
- 多 LLM 服务商路由

---

## Task T1: 更新版本目录 + 模块注册

在 `gradle/libs.versions.toml` 添加：
- `retrofit = "2.11.0"` → retrofit-core, retrofit-kotlin-serialization
- `okhttp = "4.12.0"` → okhttp-core, okhttp-logging, okhttp-sse
- `retrofitKotlinxSerializationJson = "1.0.0"`
- 在 `settings.gradle.kts` 添加 `include(":core:network")`

---

## Task T2: DTO 定义

在 `core/network/src/main/java/.../dto/` 下创建：

### ChatCompletionRequest.kt
- ChatCompletionRequest: model, messages, temperature, max_tokens, stream
- ChatMessageDto: role, content

### ChatCompletionResponse.kt
- ChatCompletionResponse: id, choices, usage
- ChatChoiceDto: index, message, finish_reason
- UsageDto: prompt_tokens, completion_tokens, total_tokens

### ChatCompletionChunk.kt
- ChatCompletionChunk: id, choices
- ChunkChoiceDto: index, delta, finish_reason
- ChunkDeltaDto: role, content

### EmbeddingRequest.kt
- EmbeddingRequest: model, input
- EmbeddingResponse: data, model, usage
- EmbeddingDataDto: index, embedding
- EmbeddingUsageDto: prompt_tokens, total_tokens

---

## Task T3: Retrofit API + 内部服务

### LlmApi (api/LlmApi.kt)
- @POST("v1/chat/completions") chatCompletion
- @POST("v1/chat/completions") chatCompletionStream
- @POST("v1/embeddings") createEmbedding

### LlmService (LlmService.kt)
- 内部接口: chat, chatStream, generateEmbedding, setApiKey, setBaseUrl

### LlmServiceImpl (internal/LlmServiceImpl.kt)
- 委托 LlmApi，管理 apiKey/baseUrl 状态
- chat: 构造 Bearer header + ChatCompletionRequest → ChatCompletionResponse
- chatStream: 构造 stream=true 请求 → Flow<ChatCompletionChunk>
- generateEmbedding: EmbeddingRequest → 排序后提取 embedding 列表

---

## Task T4: 辅助组件

### AuthInterceptor (internal/AuthInterceptor.kt)
- OkHttp Interceptor，注入 Authorization: Bearer {apiKey}
- apiKey 通过 @Volatile var 动态设置

### SseParser (internal/SseParser.kt)
- 解析 SSE 流: 逐行读取 "data: " 前缀行
- JSON 解码为 ChatCompletionChunk，遇到 [DONE] 停止
- 返回 Flow<ChatCompletionChunk>

---

## Task T5: CloudLlmProvider + DI

### CloudLlmProvider (internal/CloudLlmProvider.kt)
- 实现 core:model 的 LlmProvider 接口
- 委托 LlmService，将 DTO 转换为领域类型 (ChatResponse, ChatChunk)
- providerName() = "cloud"

### NetworkModule (di/NetworkModule.kt)
- Binds: LlmServiceImpl → LlmService
- Binds: CloudLlmProvider → LlmProvider (@CloudLlm 限定符)
- Provides: Json (ignoreUnknownKeys, isLenient)
- Provides: AuthInterceptor
- Provides: OkHttpClient (AuthInterceptor + LoggingInterceptor, 30s connect, 120s read)
- Provides: Retrofit (baseUrl + kotlinx.serialization converter)
- Provides: LlmApi
- Provides: 默认 LlmProvider 绑定到 @CloudLlm

---

## Task T6: 模型层抽象 (core:model 扩展)

### LlmProvider (core:model/src/main/java/.../llm/LlmProvider.kt)
- chat(model, messages, temperature, maxTokens): Result<ChatResponse>
- chatStream(model, messages, temperature, maxTokens): Result<Flow<ChatChunk>>
- embed(model, texts): Result<List<List<Float>>>
- providerName(): String

### ChatMessage, ChatResponse, ChatChunk 领域类型

### DI 限定符 (core:common)
- @CloudLlm / @LocalLlm 注解

### LocalLlmProvider (core:kernel/src/main/java/.../llm/LocalLlmProvider.kt)
- 空实现，所有方法返回 UnsupportedOperationException

---

## Task T7: 测试验证

### DtoSerializationTest
- chatCompletionRequest_serialization, chatCompletionResponse_deserialization
- chatCompletionChunk_deserialization, chatMessageDto_roles

### 构建验证
- `./gradlew :core:network:test` 通过
- `./gradlew assembleDebug` 通过
