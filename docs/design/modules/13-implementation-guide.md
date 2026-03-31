# MingClaw 实施指南

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [实施指南概述](#实施指南概述)
2. [开发环境搭建](#开发环境搭建)
3. [编码规范](#编码规范)
4. [测试策略](#测试策略)
5. [部署流程](#部署流程)
6. [附录](#附录)

---

## 实施指南概述

### 实施阶段

MingClaw 的实施分为以下阶段：

| 阶段 | 持续时间 | 目标 | 交付物 |
|------|----------|------|--------|
| **准备阶段** | 1周 | 环境搭建、团队培训 | 开发环境、培训文档 |
| **基础架构** | 2周 | 核心框架搭建 | 微内核、事件总线 |
| **核心功能** | 4周 | 基础功能实现 | 上下文管理、记忆管理 |
| **高级功能** | 3周 | 进化功能实现 | 三种进化路径 |
| **集成测试** | 2周 | 测试和修复 | 测试报告、Bug修复 |
| **部署发布** | 1周 | 应用发布 | APK、发布说明 |

### 开发流程

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Development Process                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  1. Planning                                                       │  │
│  │     - 需求分析                                                      │  │
│  │     - 任务分解                                                      │  │
│  │     - 时间估算                                                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  2. Development                                                    │  │
│  │     - 编写代码                                                      │  │
│  │     - 单元测试                                                      │  │
│  │     - 代码审查                                                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  3. Testing                                                        │  │
│  │     - 集成测试                                                      │  │
│  │     - UI测试                                                        │  │
│  │     - 性能测试                                                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  4. Deployment                                                     │  │
│  │     - 构建APK                                                       │  │
│  │     - 签名                                                          │  │
│  │     - 发布                                                          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 开发环境搭建

### 系统要求

| 组件 | 最低要求 | 推荐配置 |
|------|----------|----------|
| **操作系统** | Windows 10/11, macOS 12+, Ubuntu 20.04+ | 最新版本 |
| **内存** | 8GB | 16GB+ |
| **存储** | 20GB可用空间 | SSD 50GB+ |
| **处理器** | 双核 | 四核+ |

### 必需软件

1. **Android Studio**
   - 版本: Hedgehog (2023.1.1) 或更高
   - 下载: https://developer.android.com/studio

2. **JDK**
   - 版本: JDK 17
   - 推荐: Amazon Corretto 17

3. **Git**
   - 版本: 2.30+
   - 配置SSH密钥

### 环境配置

```bash
# 1. 克隆项目
git clone https://github.com/your-org/MingClaw.git
cd MingClaw

# 2. 安装依赖
./gradlew build

# 3. 同步项目
# 在Android Studio中: File -> Sync Project with Gradle Files

# 4. 运行应用
./gradlew installDebug
```

### Android Studio配置

**推荐插件**
- Kotlin
- .gitignore (用于.gitignore文件)
- Save Actions (自动格式化)
- SonarLint (代码质量检查)

**代码风格配置**
```xml
<!-- .idea/codeStyles/Project.xml -->
<component name="ProjectCodeStyleConfiguration">
  <code_scheme name="Project" version="173">
    <option name="RIGHT_MARGIN" value="120" />
    <option name="WRAP_WHEN_TYPING_LONG_LINE" value="true" />
    <KotlinCodeStyleSettings>
      <option name="CONTINUATION_INDENT_IN_CHAINS" value="false" />
    </KotlinCodeStyleSettings>
  </code_scheme>
</component>
```

---

## 编码规范

### Kotlin代码规范

遵循官方Kotlin编码规范和Android编码最佳实践。

#### 命名规范

```kotlin
// 类名使用大驼峰
class MessageRepository

// 函数名使用小驼峰
fun sendMessage() {}

// 常量使用全大写下划线分隔
const val MAX_RETRY_COUNT = 3

// 变量名使用小驼峰
val messageCount = 0

// 私有属性使用下划线前缀（可选）
private var _internalState: State? = null
```

#### 文件组织

```kotlin
// 1. 文件头注释（可选）
/*
 * Copyright 2025 MingClaw Team
 */

// 2. 包声明
package com.loy.mingclaw.core.data

// 3. 导入（按字母顺序，分组）
import android.content.Context
import androidx.room.Room
import com.loy.mingclaw.core.model.Message
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// 4. 类/接口声明
class MessageRepositoryImpl @Inject constructor(
    private val context: Context
) : MessageRepository {

    // 5. 伴随对象
    companion object {
        private const val TAG = "MessageRepository"
        private const val DATABASE_NAME = "mingclaw_db"
    }

    // 6. 属性
    private val database = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        DATABASE_NAME
    ).build()

    // 7. 初始化块
    init {
        // 初始化逻辑
    }

    // 8. 公共方法
    override fun getMessages(): Flow<List<Message>> {
        // 实现
    }

    // 9. 私有方法
    private fun handleError(error: Throwable) {
        // 实现
    }

    // 10. 内部类
    inner class Cache {
        // 实现
    }
}
```

#### 注释规范

```kotlin
/**
 * 消息仓库接口
 *
 * 负责管理消息的存储和检索操作
 *
 * @see Message
 * @see MessageDao
 */
interface MessageRepository {

    /**
     * 获取所有消息
     *
     * @return Flow发射消息列表
     * @throws DatabaseException 如果数据库访问失败
     */
    fun getMessages(): Flow<List<Message>>

    /**
     * 发送消息
     *
     * @param message 要发送的消息
     * @return Result<Unit> 成功或失败
     */
    suspend fun sendMessage(message: Message): Result<Unit>
}

// 单行注释用于简要说明
private const val MAX_MESSAGES = 100 // 最大消息数量

// TODO注释用于标记待办事项
// TODO: 实现消息去重功能
```

#### 函数编写规范

```kotlin
// 好的函数示例
/**
 * 计算消息的Token数量
 *
 * @param message 要计算的消息
 * @param encoder Token编码器
 * @return Token数量
 */
fun calculateTokens(
    message: Message,
    encoder: TokenEncoder
): Int {
    // 1. 参数验证
    require(message.content.isNotEmpty()) { "Message content cannot be empty" }

    // 2. 核心逻辑
    val contentTokens = encoder.encode(message.content)
    val metadataTokens = encoder.encode(buildMetadata(message))

    // 3. 返回结果
    return contentTokens.size + metadataTokens.size
}

// 使用默认参数提高可读性
fun sendMessage(
    message: Message,
    priority: MessagePriority = MessagePriority.NORMAL,
    retryCount: Int = 0
) {
    // 实现
}

// 使用扩展函数
fun String.toMessage(): Message {
    return Message(content = this)
}
```

#### 错误处理规范

```kotlin
// 使用Result类型包装可能失败的操作
suspend fun sendMessage(message: Message): Result<Unit> {
    return try {
        val response = api.sendMessage(message)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(ServerException(response.message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// 使用自定义异常
sealed class MingClawException(message: String) : Exception(message) {
    class NetworkException(message: String) : MingClawException(message)
    class ValidationException(message: String) : MingClawException(message)
    class ServerException(message: String) : MingClawException(message)
}

// 使用require进行参数验证
fun saveMessage(message: Message) {
    require(message.id.isNotEmpty()) { "Message ID cannot be empty" }
    require(message.content.length <= MAX_LENGTH) { "Message too long" }
    // 保存逻辑
}
```

### 协程使用规范

```kotlin
// 在ViewModel中使用viewModelScope
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun sendMessage(content: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ChatUiState.Sending

                val message = Message(content = content)
                repository.sendMessage(message)

                _uiState.value = ChatUiState.Success
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理资源
    }
}

// 在Repository中使用withContext
class MessageRepositoryImpl(
    private val api: MessageApi,
    private val dao: MessageDao
) : MessageRepository {

    override suspend fun sendMessage(message: Message): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 网络请求
            val response = api.sendMessage(message)

            // 数据库操作
            dao.insert(message.toEntity())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 使用Flow进行数据流处理
fun observeMessages(): Flow<List<Message>> = flow {
    // 发射初始数据
    emit(dao.getAllMessages())

    // 监听更新
    messageUpdateChannel.consumeAsFlow().collect { update ->
        emit(update.messages)
    }
}.flowOn(Dispatchers.IO)
```

### Compose UI规范

```kotlin
// Compose函数命名使用PascalCase
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit
) {
    // 使用Scaffold作为顶层结构
    Scaffold(
        topBar = { ChatTopBar() }
    ) { paddingValues ->
        // 使用Modifier链式调用
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 根据状态渲染不同UI
            when (uiState) {
                is ChatUiState.Loading -> LoadingIndicator()
                is ChatUiState.Success -> MessageList(uiState.messages)
                is ChatUiState.Error -> ErrorMessage(uiState.error)
            }
        }
    }
}

// 提取可复用的组件
@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(message)
        }
    }
}

// 使用remember避免重复计算
@Composable
fun ExpensiveComponent(data: List<Data>) {
    val sortedData = remember(data) {
        data.sortedBy { it.timestamp }
    }

    // 使用sortedData
}

// 使用LaunchedSideEffect处理一次性事件
@Composable
fun DataLoader(dataId: String) {
    var data by remember { mutableStateOf<Data?>(null) }

    LaunchedEffect(dataId) {
        data = loadData(dataId)
    }

    data?.let { DisplayData(it) }
}
```

### 依赖注入规范

```kotlin
// 定义Hilt模块
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mingclaw_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }
}

// 使用限定符区分不同实现
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalApi

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    @RemoteApi
    fun provideRemoteApi(): MessageApi {
        return Retrofit.Builder()
            .baseUrl("https://api.mingclaw.com/")
            .build()
            .create(MessageApi::class.java)
    }

    @Provides
    @LocalApi
    fun provideLocalApi(dao: MessageDao): MessageApi {
        return LocalMessageApi(dao)
    }
}

// 在ViewModel中注入
@HiltViewModel
class ChatViewModel @Inject constructor(
    @LocalApi private val localApi: MessageApi,
    @RemoteApi private val remoteApi: MessageApi
) : ViewModel() {
    // 实现
}
```

---

## 测试策略

### 测试金字塔

```
                    E2E Tests (10%)
                   ┌────────────┐
                   │            │
     Integration   │   UI Tests │
       Tests (20%) │            │
      ┌────────────┴────────────┴─────┐
      │                              │
      │      Unit Tests (70%)         │
      │                              │
      └──────────────────────────────┘
```

### 单元测试

```kotlin
// 使用JUnit 5 + MockK
class MessageRepositoryTest {

    private lateinit var repository: MessageRepository
    private val mockDao: MessageDao = mockk()
    private val mockApi: MessageApi = mockk()

    @Before
    fun setup() {
        repository = MessageRepositoryImpl(mockDao, mockApi)
    }

    @Test
    fun `getMessages returns data from DAO`() = runTest {
        // Given
        val expectedMessages = listOf(
            MessageEntity(id = "1", content = "Hello"),
            MessageEntity(id = "2", content = "World")
        )
        every { mockDao.getAllMessages() } returns flowOf(expectedMessages)

        // When
        val result = repository.getMessages().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("Hello", result[0].content)
    }

    @Test
    fun `sendMessage calls API and saves to DAO`() = runTest {
        // Given
        val message = Message(id = "1", content = "Test")
        coEvery { mockApi.sendMessage(message) } returns Result.success(Unit)
        coEvery { mockDao.insert(any()) } just Runs

        // When
        repository.sendMessage(message)

        // Then
        coVerify { mockApi.sendMessage(message) }
        coVerify { mockDao.insert(any()) }
    }
}
```

### 集成测试

```kotlin
// 使用Hilt进行依赖注入
@HiltAndroidTest
class ChatFlowIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var chatViewModel: ChatViewModel

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun sendingMessage_updatesUI() = runTest {
        // When
        chatViewModel.sendMessage("Hello")

        // Then
        val state = chatViewModel.uiState.filterIsInstance<ChatUiState.Success>().first()
        assertTrue(state.messages.any { it.content == "Hello" })
    }
}

// 测试模块
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideTestDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
```

### UI测试

```kotlin
// 使用Compose Testing
class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatScreen_displaysMessages() {
        // Given
        val messages = listOf(
            Message(id = "1", content = "Hello", role = MessageRole.USER),
            Message(id = "2", content = "Hi!", role = MessageRole.ASSISTANT)
        )

        // When
        composeTestRule.setContent {
            MingClawTheme {
                ChatScreen(
                    uiState = ChatUiState.Success(messages),
                    onSendMessage = {}
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hi!").assertIsDisplayed()
    }

    @Test
    fun chatScreen_sendsMessage() {
        var sentMessage = ""

        composeTestRule.setContent {
            MingClawTheme {
                ChatScreen(
                    uiState = ChatUiState.Success(emptyList()),
                    onSendMessage = { sentMessage = it }
                )
            }
        }

        // Type and send
        composeTestRule.onNodeWithText("Send message")
            .performTextInput("Test")
        composeTestRule.onNodeWithContentDescription("Send")
            .performClick()

        assertEquals("Test", sentMessage)
    }
}
```

### 测试最佳实践

1. **测试命名**
```kotlin
// 使用描述性测试名称
@Test
fun `sendMessage with empty content throws exception`() { }

@Test
fun `getMessages when database is empty returns empty list`() { }
```

2. **使用测试辅助函数**
```kotlin
private fun createTestMessage(
    id: String = "test_id",
    content: String = "test_content"
) = Message(id = id, content = content)

private fun <T> Flow<T>.test(): TestCollector<T> = TestCollector(this)
```

3. **测试协程代码**
```kotlin
@Test
fun `async operation completes successfully`() = runTest {
    // 使用advanceUntilIdle等待所有协程完成
    val result = async { repository.getData() }
    advanceUntilIdle()
    assertNotNull(result.await())
}
```

---

## 部署流程

### 构建配置

**build.gradle.kts (app模块)**
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("production") {
            dimension = "environment"
            applicationId = "com.loy.mingclaw"
            versionCode = 1
            versionName = "1.0.0"
        }
        create("staging") {
            dimension = "environment"
            applicationId = "com.loy.mingclaw.staging"
            versionName = "1.0.0-staging"
        }
    }
}
```

### 版本发布流程

1. **版本准备**
```bash
# 更新版本号
./gradlew updateVersion -PnewVersion=1.0.0

# 生成变更日志
./gradlew generateChangelog
```

2. **构建发布包**
```bash
# 清理
./gradlew clean

# 构建Release APK
./gradlew assembleProductionRelease

# 构建AAB (用于Google Play)
./gradlew bundleProductionRelease
```

3. **签名验证**
```bash
# 验证APK签名
jarsigner -verify -verbose -certs app/build/outputs/apk/production/release/*.apk

# 检查APK对齐
zipalign -c -v 4 app/build/outputs/apk/production/release/*.apk
```

4. **测试发布包**
```bash
# 安装到设备进行测试
adb install -r app/build/outputs/apk/production/release/*.apk
```

### CI/CD配置

**.github/workflows/release.yml**
```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew assembleProductionRelease --stacktrace

      - name: Sign APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA256 \
            -keystore ${{ secrets.KEYSTORE_FILE }} \
            -storepass $KEYSTORE_PASSWORD \
            -keypass $KEY_PASSWORD \
            app/build/outputs/apk/production/release/*.apk \
            $KEY_ALIAS

      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: release-apk
          path: app/build/outputs/apk/production/release/*.apk
```

---

## 附录

### A. 开发工具推荐

| 工具 | 用途 |
|------|------|
| Android Studio | IDE |
| Git | 版本控制 |
| ADB | 调试桥接 |
| Profiler | 性能分析 |
| Layout Inspector | 布局检查 |
| Database Inspector | 数据库检查 |

### B. 常用命令

```bash
# 构建相关
./gradlew build                      # 构建项目
./gradlew assembleDebug             # 构建Debug APK
./gradlew installDebug              # 安装Debug APK
./gradlew clean                     # 清理构建

# 测试相关
./gradlew test                      # 运行单元测试
./gradlew connectedAndroidTest      # 运行设备测试
./gradlew lint                      # 运行代码检查

# 调试相关
adb logcat                          # 查看日志
adb shell pm list packages          # 列出已安装应用
adb install -r app.apk              # 安装APK
adb uninstall com.loy.mingclaw      # 卸载应用
```

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [10-tech-stack.md](./10-tech-stack.md) - 技术栈选型
- [12-quality-assurance.md](./12-quality-assurance.md) - 质量保证

---

**文档维护**: 本文档应随着实施进展持续更新
**审查周期**: 每周一次或重大流程变更时
