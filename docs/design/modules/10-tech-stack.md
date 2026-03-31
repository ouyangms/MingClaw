# MingClaw 技术栈选型

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [技术栈概述](#技术栈概述)
2. [核心技术选型](#核心技术选型)
3. [依赖管理](#依赖管理)
4. [版本策略](#版本策略)
5. [替代方案分析](#替代方案分析)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 技术栈概述

### 选型原则

MingClaw 技术栈选择遵循以下原则：

| 原则 | 说明 | 权重 |
|------|------|------|
| **Android优先** | 使用Android官方推荐技术栈 | 高 |
| **稳定性** | 选择稳定、成熟的技术 | 高 |
| **可维护性** | 代码可读、可维护 | 高 |
| **性能** | 满足性能要求 | 中 |
| **社区支持** | 有活跃的社区支持 | 中 |
| **学习曲线** | 团队易于掌握 | 低 |

### 技术栈架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Application Layer                               │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Jetpack Compose 1.7+ | Material Design 3 | Navigation Compose    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Architecture Layer                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  MVVM | Clean Architecture | Repository Pattern | UseCase         │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Business Logic Layer                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Kotlin Coroutines 1.7+ | Kotlin Flow | StateFlow | SharedFlow    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Data Layer                                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Room 2.6+ | DataStore | Retrofit 2.11+ | OkHttp 4.12+             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Dependency Injection                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Hilt 2.51+ | Dagger 2.51+                                        │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Testing Layer                                   │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  JUnit 5 | MockK | Turbine | Robolectric | Espresso               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 核心技术选型

### 1. 用户界面

#### Jetpack Compose

| 属性 | 说明 |
|------|------|
| **版本** | 1.7.0+ |
| **用途** | 声明式UI框架 |
| **选择理由** | - Google官方推荐<br>- 简化UI开发<br>- 类型安全<br>- 减少样板代码<br>- 与Kotlin完美集成 |
| **替代方案** | XML + ViewBinding (传统方式，已被Compose取代) |

**核心依赖**
```kotlin
// build.gradle.kts
dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.navigation)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.testManifest)
}
```

**示例代码**
```kotlin
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit
) {
    Scaffold(
        topBar = { ChatTopBar() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MessageList(messages = uiState.messages)
            MessageInput(onSend = onSendMessage)
        }
    }
}
```

#### Material Design 3

| 属性 | 说明 |
|------|------|
| **版本** | 1.2.0+ |
| **用途** | 设计系统 |
| **选择理由** | - 最新Material设计<br>- 动态配色<br>- 更好的可访问性<br>- 与Compose集成 |

**主题配置**
```kotlin
@Composable
fun MingClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC6)
        )
        else -> lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### 2. 架构模式

#### MVVM + Clean Architecture

| 属性 | 说明 |
|------|------|
| **用途** | 应用架构模式 |
| **选择理由** | - 关注点分离<br>- 可测试性强<br>- 易于维护<br>- Google推荐 |

**分层结构**
```kotlin
// Domain Layer (核心业务逻辑)
data class Message(val id: String, val content: String)

// Data Layer (数据访问)
interface MessageRepository {
    fun getMessages(): Flow<List<Message>>
    suspend fun sendMessage(message: Message)
}

// Presentation Layer (UI)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: MessageRepository
) : ViewModel() {
    val uiState: StateFlow<ChatUiState> = repository.getMessages()
        .map { ChatUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState.Loading
        )
}
```

### 3. 异步处理

#### Kotlin Coroutines & Flow

| 属性 | 说明 |
|------|------|
| **版本** | 1.7.0+ |
| **用途** | 异步编程和响应式流 |
| **选择理由** | - Kotlin原生支持<br>- 结构化并发<br>- 类型安全<br>- 易于测试 |

**依赖配置**
```kotlin
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

**使用示例**
```kotlin
class MessageRepositoryImpl @Inject constructor(
    private val dao: MessageDao,
    private val api: MessageApi
) : MessageRepository {

    override fun getMessages(): Flow<List<Message>> = flow {
        // 从本地数据库加载
        emit(dao.getAllMessages())

        // 从网络获取最新数据
        val remoteMessages = api.fetchMessages()
        dao.insertAll(remoteMessages)

        // 发送更新后的数据
        emit(dao.getAllMessages())
    }
}
```

### 4. 依赖注入

#### Hilt

| 属性 | 说明 |
|------|------|
| **版本** | 2.51+ |
| **用途** | 依赖注入框架 |
| **选择理由** | - Android官方推荐<br>- 编译时验证<br>- 减少样板代码<br>- 与ViewModel集成 |

**依赖配置**
```kotlin
dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // For Hilt Navigation Compose integration
    implementation(libs.androidx.hilt.navigation.compose)
}
```

**使用示例**
```kotlin
@HiltAndroidApp
class MingClawApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mingclaw_db"
        ).build()
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: MessageRepository
) : ViewModel()
```

### 5. 数据存储

#### Room Database

| 属性 | 说明 |
|------|------|
| **版本** | 2.6.0+ |
| **用途** | 本地数据库 |
| **选择理由** | - SQLite抽象<br>- 编译时验证<br>- 迁移支持<br>- Flow集成 |
| **向量扩展** | sqlite-vec 0.1+ |

**依赖配置**
```kotlin
dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)
}
```

**使用示例**
```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val content: String,
    val timestamp: Long,
    val embedding: ByteArray? = null
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("""
        SELECT *, distance(embedding, :queryEmbedding) as dist
        FROM messages
        ORDER BY dist
        LIMIT :limit
    """)
    fun searchSimilar(queryEmbedding: ByteArray, limit: Int): Flow<List<MessageEntity>>
}

@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
```

#### DataStore

| 属性 | 说明 |
|------|------|
| **版本** | 1.0.0+ |
| **用途** | 键值对存储 |
| **选择理由** | - 替代SharedPreferences<br>- 类型安全<br>- 支持协程<br>- 数据迁移支持 |

**依赖配置**
```kotlin
dependencies {
    implementation(libs.androidx.datastore.preferences)
}
```

**使用示例**
```kotlin
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore("settings")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
```

### 6. 网络通信

#### Retrofit + OkHttp

| 属性 | 说明 |
|------|------|
| **版本** | Retrofit 2.11+, OkHttp 4.12+ |
| **用途** | HTTP客户端 |
| **选择理由** | - 类型安全<br>- 支持协程<br>- 拦截器支持<br>- 缓存支持 |

**依赖配置**
```kotlin
dependencies {
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
}
```

**使用示例**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(AuthInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.mingclaw.com/")
            .client(okHttpClient)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideLlmApi(retrofit: Retrofit): LlmApi {
        return retrofit.create(LlmApi::class.java)
    }
}

interface LlmApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse

    @Streaming
    @POST("v1/chat/completions")
    fun chatCompletionStream(@Body request: ChatCompletionRequest): Flow<ChatCompletionChunk>
}
```

### 7. 向量数据库

#### sqlite-vec

| 属性 | 说明 |
|------|------|
| **版本** | 0.1.0+ |
| **用途** | 向量相似度搜索 |
| **选择理由** | - 原生SQLite扩展<br>- 无需额外服务<br>- 高性能<br>- 易于集成 |

**集成方式**
```kotlin
// 自定义Room Database
abstract class AppDatabase : RoomDatabase() {

    companion object {
        fun createDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // 加载sqlite-vec扩展
                        loadVecExtension(db)
                    }
                })
                .build()
        }

        private fun loadVecExtension(db: SupportSQLiteDatabase) {
            // 加载预编译的vec0扩展
            db.execSQL("SELECT load_extension('vec0')")
        }
    }
}

// 向量搜索查询
@Dao
interface MessageDao {
    @Query("""
        SELECT id, content, timestamp,
               distance(embedding, ?) as similarity
        FROM messages
        ORDER BY similarity
        LIMIT ?
    """)
    fun searchByEmbedding(embedding: ByteArray, limit: Int): Flow<List<MessageWithSimilarity>>
}
```

---

## 依赖管理

### Gradle Version Catalog

MingClaw 使用 Gradle Version Catalog (libs.versions.toml) 管理依赖：

**gradle/libs.versions.toml**
```toml
[versions]
# Kotlin
kotlin = "1.9.22"
kotlin-coroutines = "1.7.3"
kotlin-serialization = "1.6.2"

# AndroidX
androidx-core = "1.12.0"
androidx-appcompat = "1.6.1"
androidx-lifecycle = "2.7.0"
androidx-activity = "1.8.2"
androidx-compose = "1.7.0"
androidx-compose-material3 = "1.2.0"
androidx-compose-navigation = "2.7.6"
androidx-hilt = "1.1.0"
androidx-room = "2.6.1"
androidx-datastore = "1.0.0"

# DI
hilt = "2.51"

# Network
retrofit = "2.11.0"
okhttp = "4.12.0"

# Testing
junit = "5.10.2"
mockk = "1.13.9"
turbine = "1.1.0"
robolectric = "4.11.1"
espresso = "3.5.1"

[libraries]
# Kotlin
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlin-coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlin-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlin-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlin-serialization" }

# AndroidX Core
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidx-appcompat" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "androidx-lifecycle" }
androidx-lifecycle-viewmodel-ktx = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "androidx-lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }

# Compose
androidx-compose-bom = { module = "androidx.compose:compose-bom", version = "2024.02.01" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "androidx-compose-material3" }
androidx-compose-navigation = { module = "androidx.navigation:navigation-compose", version.ref = "androidx-compose-navigation" }

# DI
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidx-hilt" }

# Room
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "androidx-room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }
androidx-room-paging = { module = "androidx.room:room-paging", version.ref = "androidx-room" }

# DataStore
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "androidx-datastore" }

# Network
retrofit-core = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlin-serialization = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version = "1.0.0" }
okhttp-core = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }

# Testing
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version = "1.1.5" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
androidx-compose-ui-test = { module = "androidx.compose.ui:ui-test-junit4" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "1.9.22-1.0.17" }
```

### 模块依赖规则

**核心模块依赖**
```kotlin
// core/build.gradle.kts
dependencies {
    // Kotlin标准库
    api(libs.kotlinx.coroutines.core)

    // AndroidX核心
    api(libs.androidx.core.ktx)

    // 不依赖任何其他MingClaw模块
}
```

**功能模块依赖**
```kotlin
// feature/chat/build.gradle.kts
dependencies {
    // 依赖核心模块
    implementation(project(":core:context"))
    implementation(project(":core:memory"))
    implementation(project(":core:llm"))

    // 依赖其他模块的API
    implementation(project(":feature:task:api"))

    // AndroidX依赖
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.navigation)
}
```

**应用模块依赖**
```kotlin
// app/build.gradle.kts
dependencies {
    // 依赖所有功能模块的实现
    implementation(project(":feature:chat:impl"))
    implementation(project(":feature:task:impl"))
    implementation(project(":feature:plugin:impl"))

    // 不直接依赖功能模块的API
}
```

---

## 版本策略

### 语义化版本控制

MingClaw 遵循语义化版本控制 (SemVer)：

```
MAJOR.MINOR.PATCH

示例: 1.2.3

MAJOR: 不兼容的API变更
MINOR: 向后兼容的功能新增
PATCH: 向后兼容的问题修复
```

### 版本命名规范

| 类型 | 格式 | 示例 | 说明 |
|------|------|------|------|
| **正式版** | X.Y.Z | 1.0.0 | 稳定版本 |
| **RC版** | X.Y.Z-rc.N | 1.0.0-rc.1 | 候选版本 |
| **Beta版** | X.Y.Z-beta.N | 1.0.0-beta.1 | 测试版本 |
| **Alpha版** | X.Y.Z-alpha.N | 1.0.0-alpha.1 | 内部版本 |

### 依赖版本策略

**固定版本**
```kotlin
// 生产环境使用固定版本
implementation(libs.androidx.compose.ui) // 1.7.0
```

**动态版本**
```kotlin
// 开发环境可以使用动态版本
implementation("androidx.compose.ui:ui:1.7.+") // 不推荐生产环境
```

**BOM管理**
```kotlin
// 使用BOM统一管理版本
dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom) // 自动选择兼容版本
}
```

### 升级策略

**定期审查周期**
- 每月审查一次依赖更新
- 每季度进行一次大版本升级
- 每年进行一次架构评估

**升级优先级**
1. **安全补丁** - 立即升级
2. **关键Bug修复** - 尽快升级
3. **新功能** - 计划升级
4. **依赖更新** - 定期升级

---

## 替代方案分析

### UI框架选择

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Jetpack Compose** | - 声明式<br>- 类型安全<br>- 简洁 | - 相对新<br>- 学习曲线 | ✓ |
| **XML + ViewBinding** | - 成熟稳定<br>- 广泛使用 | - 样板代码多<br>- 非类型安全 | ✗ |
| **Flutter** | - 跨平台<br>- 高性能 | - 非原生<br>- 包体积大 | ✗ |

### 异步框架选择

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Coroutines + Flow** | - Kotlin原生<br>- 结构化并发<br>- 简洁 | - 需要学习 | ✓ |
| **RxJava** | - 成熟<br>- 功能强大 | - 复杂<br>- 学习曲线陡 | ✗ |
| **LiveData** | - 生命周期感知 | - 功能有限 | ✗ |

### DI框架选择

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Hilt** | - 官方推荐<br>- 编译时验证 | - 依赖Dagger | ✓ |
| **Koin** | - 简单<br>- 轻量 | - 运行时检查 | ✗ |
| **Dagger** | - 功能强大 | - 复杂<br>- 样板代码 | ✗ |

### 数据库选择

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Room + sqlite-vec** | - 原生支持<br>- 向量搜索 | - 需要扩展 | ✓ |
| **Realm** | - 简单<br>- 实时同步 | - 非原生<br>- 许可证 | ✗ |
| **ObjectBox** | - 高性能<br>- 简单API | - 不支持向量 | ✗ |

---

## 依赖关系

### 技术栈依赖图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Application Layer                               │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Jetpack Compose → Material Design 3 → Navigation Compose         │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Architecture Layer                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  MVVM → Clean Architecture → Repository Pattern                   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Business Logic Layer                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Kotlin Coroutines → Kotlin Flow → StateFlow → SharedFlow         │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Data Layer                                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Room → DataStore → Retrofit → OkHttp → sqlite-vec                │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Dependency Injection                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Hilt → Dagger → KAPT/KSP                                          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 模块依赖规则

**依赖方向**
```
app → feature:impl → feature:api → core → model
```

**禁止依赖**
```
- core 不依赖 feature
- feature:api 不依赖 feature:impl
- model 不依赖任何其他模块
```

---

## 附录

### A. 完整依赖列表

**核心依赖**
```kotlin
// Kotlin
implementation(libs.kotlin.stdlib)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.coroutines.android)
implementation(libs.kotlinx.serialization.json)

// AndroidX
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.appcompat)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.activity.compose)

// Compose
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.compose.navigation)

// DI
implementation(libs.hilt.android)
kapt(libs.hilt.compiler)
implementation(libs.androidx.hilt.navigation.compose)

// Data
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
implementation(libs.androidx.datastore.preferences)

// Network
implementation(libs.retrofit.core)
implementation(libs.retrofit.kotlin.serialization)
implementation(libs.okhttp.core)
implementation(libs.okhttp.logging)
```

**测试依赖**
```kotlin
// Unit Tests
testImplementation(libs.junit.jupiter)
testImplementation(libs.mockk)
testImplementation(libs.turbine)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.robolectric)

// Instrumented Tests
androidTestImplementation(libs.androidx.test.ext.junit)
androidTestImplementation(libs.androidx.test.espresso.core)
androidTestImplementation(libs.androidx.compose.ui.test)
```

### B. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [13-implementation-guide.md](./13-implementation-guide.md) - 实施指南

### C. 参考资源

- [Android Developers](https://developer.android.com/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html)
- [Hilt User Guide](https://dagger.dev/hilt/)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)

---

**文档维护**: 本文档应随着技术栈演进持续更新
**审查周期**: 每季度一次或重大技术变更时
