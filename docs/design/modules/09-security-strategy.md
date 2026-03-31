# MingClaw 安全策略设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [安全策略概述](#安全策略概述)
2. [权限管理](#权限管理)
3. [沙箱隔离](#沙箱隔离)
4. [数据加密](#数据加密)
5. [审计日志](#审计日志)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 安全策略概述

### 设计目标

MingClaw 安全策略实现：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **权限控制** | 细粒度的权限管理 | RBAC + 权限声明 |
| **沙箱隔离** | 插件间相互隔离 | ClassLoader隔离 |
| **数据保护** | 敏感数据加密存储 | Android Keystore |
| **安全审计** | 完整的操作日志 | 审计日志系统 |
| **漏洞防护** | 防止常见攻击 | 输入验证 + 安全编码 |

### 安全架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Security Layer                                │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Security Manager                             │  │
│  │  - 统一安全接口                                                    │  │
│  │  - 安全策略执行                                                    │  │
│  │  - 安全事件响应                                                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │Permission │ │  Sandbox  │ │  Crypto   │ │   Audit   │ │  Threat   │ │
│  │  Manager  │ │  Manager  │ │  Manager  │ │   Logger   │ │  Detector │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Security Services                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐  │
│  │  Keystore    │ │  Encrypted  │ │  Permission  │ │   Audit      │  │
│  │  Provider    │ │  FileStore  │ │  Validator   │ │   Storage    │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 权限管理

### 权限管理器接口

```kotlin
/**
 * 权限管理器接口
 */
interface PermissionManager {

    /**
     * 检查权限
     */
    fun checkPermission(
        subject: SecuritySubject,
        permission: Permission
    ): Boolean

    /**
     * 请求权限
     */
    suspend fun requestPermission(
        subject: SecuritySubject,
        permission: Permission
    ): PermissionResult

    /**
     * 授予权限
     */
    suspend fun grantPermission(
        subject: SecuritySubject,
        permission: Permission,
        grantor: SecuritySubject
    ): Result<Unit>

    /**
     * 撤销权限
     */
    suspend fun revokePermission(
        subject: SecuritySubject,
        permission: Permission,
        revoker: SecuritySubject
    ): Result<Unit>

    /**
     * 获取主体权限
     */
    fun getPermissions(subject: SecuritySubject): Set<Permission>

    /**
     * 检查插件权限
     */
    fun checkPluginPermission(
        pluginId: String,
        permission: Permission
    ): Boolean

    /**
     * 验证操作权限
     */
    fun validateOperation(
        subject: SecuritySubject,
        operation: SecureOperation
    ): ValidationResult
}

/**
 * 安全主体
 */
@Serializable
sealed class SecuritySubject {
    abstract val id: String

    @Serializable
    data class User(override val id: String) : SecuritySubject()

    @Serializable
    data class Plugin(override val id: String) : SecuritySubject()

    @Serializable
    data class System(override val id: String = "system") : SecuritySubject()
}

/**
 * 权限定义
 */
@Serializable
data class Permission(
    val domain: String,
    val action: String,
    val resource: String? = null
) {
    override fun toString(): String {
        return buildString {
            append(domain)
            append(".")
            append(action)
            resource?.let {
                append(":")
                append(it)
            }
        }
    }

    companion object {
        // 文件系统权限
        val FILE_READ = Permission("file", "read")
        val FILE_WRITE = Permission("file", "write")
        val FILE_DELETE = Permission("file", "delete")

        // 网络权限
        val NETWORK_ACCESS = Permission("network", "access")
        val NETWORK_HTTPS = Permission("network", "https")

        // 系统权限
        val SYSTEM_CONFIG = Permission("system", "config")
        val SYSTEM_PLUGIN = Permission("system", "plugin")

        // 数据权限
        val DATA_READ = Permission("data", "read")
        val DATA_WRITE = Permission("data", "write")
        val DATA_DELETE = Permission("data", "delete")

        // LLM权限
        val LLM_ACCESS = Permission("llm", "access")
        val LLM_STREAM = Permission("llm", "stream")

        // 内存权限
        val MEMORY_READ = Permission("memory", "read")
        val MEMORY_WRITE = Permission("memory", "write")
    }
}

/**
 * 权限结果
 */
sealed class PermissionResult {
    object Granted : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
    data class Pending(val requestId: String) : PermissionResult()
}

/**
 * 安全操作
 */
@Serializable
data class SecureOperation(
    val type: OperationType,
    val target: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
enum class OperationType {
    FILE_READ,
    FILE_WRITE,
    FILE_DELETE,
    NETWORK_REQUEST,
    PLUGIN_LOAD,
    PLUGIN_UNLOAD,
    DATA_ACCESS,
    LLM_CALL,
    MEMORY_ACCESS
}
```

### 权限管理器实现

```kotlin
/**
 * 权限管理器实现
 */
internal class PermissionManagerImpl @Inject constructor(
    private val permissionStore: PermissionStore,
    private val roleManager: RoleManager,
    private val auditLogger: AuditLogger
) : PermissionManager {

    override fun checkPermission(
        subject: SecuritySubject,
        permission: Permission
    ): Boolean {
        // 系统主体拥有所有权限
        if (subject is SecuritySubject.System) {
            return true
        }

        // 获取主体角色
        val roles = when (subject) {
            is SecuritySubject.User -> roleManager.getUserRoles(subject.id)
            is SecuritySubject.Plugin -> roleManager.getPluginRoles(subject.id)
            is SecuritySubject.System -> return true
        }

        // 检查角色权限
        return roles.any { role ->
            role.permissions.contains(permission.toString())
        }
    }

    override suspend fun requestPermission(
        subject: SecuritySubject,
        permission: Permission
    ): PermissionResult = withContext(Dispatchers.IO) {
        // 检查是否已有权限
        if (checkPermission(subject, permission)) {
            return@withContext PermissionResult.Granted
        }

        // 创建权限请求
        val requestId = generateRequestId()
        val request = PermissionRequest(
            id = requestId,
            subject = subject,
            permission = permission,
            timestamp = Clock.System.now()
        )

        // 保存请求
        permissionStore.savePermissionRequest(request)

        // 记录审计日志
        auditLogger.logPermissionRequest(request)

        // 返回待处理状态
        PermissionResult.Pending(requestId)
    }

    override suspend fun grantPermission(
        subject: SecuritySubject,
        permission: Permission,
        grantor: SecuritySubject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 验证授予者权限
            if (!checkPermission(grantor, Permission.SYSTEM_CONFIG)) {
                return@withContext Result.failure(
                    SecurityException("Grantor does not have permission to grant permissions")
                )
            }

            // 获取主体角色
            val roles = when (subject) {
                is SecuritySubject.User -> roleManager.getUserRoles(subject.id)
                is SecuritySubject.Plugin -> roleManager.getPluginRoles(subject.id)
                is SecuritySubject.System -> return@withContext Result.success(Unit)
            }

            // 授予权限到角色
            roles.firstOrNull()?.let { role ->
                roleManager.addPermissionToRole(role.id, permission)
            }

            // 记录审计日志
            auditLogger.logPermissionGrant(subject, permission, grantor)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokePermission(
        subject: SecuritySubject,
        permission: Permission,
        revoker: SecuritySubject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 验证撤销者权限
            if (!checkPermission(revoker, Permission.SYSTEM_CONFIG)) {
                return@withContext Result.failure(
                    SecurityException("Revoker does not have permission to revoke permissions")
                )
            }

            // 获取主体角色
            val roles = when (subject) {
                is SecuritySubject.User -> roleManager.getUserRoles(subject.id)
                is SecuritySubject.Plugin -> roleManager.getPluginRoles(subject.id)
                is SecuritySubject.System -> return@withContext Result.success(Unit)
            }

            // 从角色撤销权限
            roles.forEach { role ->
                roleManager.removePermissionFromRole(role.id, permission)
            }

            // 记录审计日志
            auditLogger.logPermissionRevocation(subject, permission, revoker)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPermissions(subject: SecuritySubject): Set<Permission> {
        val roles = when (subject) {
            is SecuritySubject.User -> roleManager.getUserRoles(subject.id)
            is SecuritySubject.Plugin -> roleManager.getPluginRoles(subject.id)
            is SecuritySubject.System -> return Permission::class.sealedSubclasses
                .flatMap { it.objectInstance?.let { listOf(it) } ?: emptyList() }
                .toSet()
        }

        return roles.flatMap { role ->
            role.permissions.map { Permission.fromString(it) }
        }.toSet()
    }

    override fun checkPluginPermission(
        pluginId: String,
        permission: Permission
    ): Boolean {
        return checkPermission(SecuritySubject.Plugin(pluginId), permission)
    }

    override fun validateOperation(
        subject: SecuritySubject,
        operation: SecureOperation
    ): ValidationResult {
        val requiredPermission = when (operation.type) {
            OperationType.FILE_READ -> Permission.FILE_READ
            OperationType.FILE_WRITE -> Permission.FILE_WRITE
            OperationType.FILE_DELETE -> Permission.FILE_DELETE
            OperationType.NETWORK_REQUEST -> Permission.NETWORK_ACCESS
            OperationType.PLUGIN_LOAD -> Permission.SYSTEM_PLUGIN
            OperationType.PLUGIN_UNLOAD -> Permission.SYSTEM_PLUGIN
            OperationType.DATA_ACCESS -> Permission.DATA_READ
            OperationType.LLM_CALL -> Permission.LLM_ACCESS
            OperationType.MEMORY_ACCESS -> Permission.MEMORY_READ
        }

        return if (checkPermission(subject, requiredPermission)) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Missing required permission: $requiredPermission")
        }
    }

    private fun generateRequestId(): String {
        return "req_${Clock.System.now().toEpochMilliseconds()}_${UUID.randomUUID()}"
    }

    private fun Permission.Companion.fromString(str: String): Permission? {
        val parts = str.split(".", ":")
        return if (parts.size >= 2) {
            Permission(
                domain = parts[0],
                action = parts[1],
                resource = parts.getOrNull(2)
            )
        } else {
            null
        }
    }
}
```

### 角色管理

```kotlin
/**
 * 角色管理器接口
 */
interface RoleManager {
    fun createRole(role: Role): Result<Unit>
    fun deleteRole(roleId: String): Result<Unit>
    fun getRole(roleId: String): Role?
    fun getUserRoles(userId: String): List<Role>
    fun getPluginRoles(pluginId: String): List<Role>
    fun assignRole(subject: SecuritySubject, roleId: String): Result<Unit>
    fun revokeRole(subject: SecuritySubject, roleId: String): Result<Unit>
    fun addPermissionToRole(roleId: String, permission: Permission): Result<Unit>
    fun removePermissionFromRole(roleId: String, permission: Permission): Result<Unit>
}

/**
 * 角色定义
 */
@Serializable
data class Role(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<String> = emptySet(),
    val isSystem: Boolean = false
) {
    companion object {
        // 系统角色
        val ADMIN = Role(
            id = "admin",
            name = "Administrator",
            description = "Full system access",
            isSystem = true
        )

        val USER = Role(
            id = "user",
            name = "User",
            description = "Standard user permissions",
            isSystem = true
        )

        val PLUGIN_BASIC = Role(
            id = "plugin_basic",
            name = "Plugin Basic",
            description = "Basic plugin permissions",
            isSystem = true
        )

        val PLUGIN_ADVANCED = Role(
            id = "plugin_advanced",
            name = "Plugin Advanced",
            description = "Advanced plugin permissions",
            isSystem = true
        )
    }
}

/**
 * 角色管理器实现
 */
internal class RoleManagerImpl @Inject constructor(
    private val roleStore: RoleStore,
    private val auditLogger: AuditLogger
) : RoleManager {

    override fun createRole(role: Role): Result<Unit> {
        return try {
            // 验证角色ID
            if (!isValidRoleId(role.id)) {
                return Result.failure(IllegalArgumentException("Invalid role ID"))
            }

            // 保存角色
            roleStore.saveRole(role)
            auditLogger.logRoleCreation(role)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteRole(roleId: String): Result<Unit> {
        return try {
            val role = getRole(roleId)
                ?: return Result.failure(IllegalArgumentException("Role not found"))

            // 不允许删除系统角色
            if (role.isSystem) {
                return Result.failure(IllegalArgumentException("Cannot delete system role"))
            }

            // 删除角色
            roleStore.deleteRole(roleId)
            auditLogger.logRoleDeletion(role)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getRole(roleId: String): Role? {
        return roleStore.getRole(roleId)
    }

    override fun getUserRoles(userId: String): List<Role> {
        return roleStore.getSubjectRoles(SecuritySubject.User(userId))
    }

    override fun getPluginRoles(pluginId: String): List<Role> {
        return roleStore.getSubjectRoles(SecuritySubject.Plugin(pluginId))
    }

    override fun assignRole(subject: SecuritySubject, roleId: String): Result<Unit> {
        return try {
            val role = getRole(roleId)
                ?: return Result.failure(IllegalArgumentException("Role not found"))

            roleStore.assignRole(subject, role)
            auditLogger.logRoleAssignment(subject, role)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun revokeRole(subject: SecuritySubject, roleId: String): Result<Unit> {
        return try {
            val role = getRole(roleId)
                ?: return Result.failure(IllegalArgumentException("Role not found"))

            roleStore.revokeRole(subject, role)
            auditLogger.logRoleRevocation(subject, role)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun addPermissionToRole(
        roleId: String,
        permission: Permission
    ): Result<Unit> {
        return try {
            val role = getRole(roleId)
                ?: return Result.failure(IllegalArgumentException("Role not found"))

            val updatedRole = role.copy(
                permissions = role.permissions + permission.toString()
            )

            roleStore.saveRole(updatedRole)
            auditLogger.logPermissionAddedToRole(role, permission)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun removePermissionFromRole(
        roleId: String,
        permission: Permission
    ): Result<Unit> {
        return try {
            val role = getRole(roleId)
                ?: return Result.failure(IllegalArgumentException("Role not found"))

            val updatedRole = role.copy(
                permissions = role.permissions - permission.toString()
            )

            roleStore.saveRole(updatedRole)
            auditLogger.logPermissionRemovedFromRole(role, permission)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidRoleId(roleId: String): Boolean {
        return roleId.matches(Regex("[a-z0-9_]+"))
    }
}
```

---

## 沙箱隔离

### 沙箱管理器接口

```kotlin
/**
 * 沙箱管理器接口
 */
interface SandboxManager {

    /**
     * 创建插件沙箱
     */
    suspend fun createSandbox(pluginId: String): Result<Sandbox>

    /**
     * 销毁沙箱
     */
    suspend fun destroySandbox(sandboxId: String): Result<Unit>

    /**
     * 获取沙箱
     */
    fun getSandbox(sandboxId: String): Sandbox?

    /**
     * 在沙箱中执行操作
     */
    suspend fun <T> executeInSandbox(
        sandboxId: String,
        operation: suspend () -> T
    ): Result<T>

    /**
     * 验证沙箱操作
     */
    fun validateSandboxOperation(
        sandboxId: String,
        operation: SandboxOperation
    ): ValidationResult
}

/**
 * 沙箱定义
 */
@Serializable
data class Sandbox(
    val id: String,
    val pluginId: String,
    val classLoader: SandboxClassLoader,
    val permissions: Set<Permission>,
    val resourceLimits: ResourceLimits,
    val createdAt: Instant,
    val state: SandboxState = SandboxState.ACTIVE
)

@Serializable
enum class SandboxState {
    ACTIVE,
    PAUSED,
    DESTROYED
}

/**
 * 资源限制
 */
@Serializable
data class ResourceLimits(
    val maxMemory: Long = 64 * 1024 * 1024, // 64MB
    val maxCpuTime: Long = 5000, // 5秒
    val maxFileDescriptors: Int = 16,
    val maxNetworkConnections: Int = 4,
    val allowedPaths: List<String> = emptyList()
)

/**
 * 沙箱操作
 */
@Serializable
sealed class SandboxOperation {
    data class FileRead(val path: String) : SandboxOperation()
    data class FileWrite(val path: String, val content: String) : SandboxOperation()
    data class NetworkRequest(val url: String) : SandboxOperation()
    data class SystemCommand(val command: String) : SandboxOperation()
    data class ClassLoad(val className: String) : SandboxOperation()
}
```

### 沙箱管理器实现

```kotlin
/**
 * 沙箱管理器实现
 */
internal class SandboxManagerImpl @Inject constructor(
    private val permissionManager: PermissionManager,
    private val auditLogger: AuditLogger,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : SandboxManager {

    private val sandboxes = mutableMapOf<String, Sandbox>()
    private val mutex = Mutex()

    override suspend fun createSandbox(pluginId: String): Result<Sandbox> = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                // 检查插件权限
                val subject = SecuritySubject.Plugin(pluginId)
                if (!permissionManager.checkPermission(subject, Permission.SYSTEM_PLUGIN)) {
                    return@withContext Result.failure(
                        SecurityException("Plugin does not have permission to create sandbox")
                    )
                }

                // 生成沙箱ID
                val sandboxId = generateSandboxId(pluginId)

                // 创建隔离的ClassLoader
                val classLoader = createIsolatedClassLoader(pluginId)

                // 获取插件权限
                val permissions = permissionManager.getPermissions(subject)

                // 设置资源限制
                val resourceLimits = ResourceLimits()

                // 创建沙箱
                val sandbox = Sandbox(
                    id = sandboxId,
                    pluginId = pluginId,
                    classLoader = classLoader,
                    permissions = permissions,
                    resourceLimits = resourceLimits,
                    createdAt = Clock.System.now()
                )

                // 保存沙箱
                sandboxes[sandboxId] = sandbox

                // 记录审计日志
                auditLogger.logSandboxCreation(sandbox)

                Result.success(sandbox)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun destroySandbox(sandboxId: String): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val sandbox = sandboxes.remove(sandboxId)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Sandbox not found")
                    )

                // 关闭ClassLoader
                sandbox.classLoader.close()

                // 记录审计日志
                auditLogger.logSandboxDestruction(sandbox)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun getSandbox(sandboxId: String): Sandbox? {
        return sandboxes[sandboxId]
    }

    override suspend fun <T> executeInSandbox(
        sandboxId: String,
        operation: suspend () -> T
    ): Result<T> = withContext(ioDispatcher) {
        try {
            val sandbox = getSandbox(sandboxId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Sandbox not found")
                )

            // 在沙箱上下文中执行
            withContext(sandbox.classLoader.asCoroutineDispatcher()) {
                val result = operation()
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun validateSandboxOperation(
        sandboxId: String,
        operation: SandboxOperation
    ): ValidationResult {
        val sandbox = getSandbox(sandboxId)
            ?: return ValidationResult(false, "Sandbox not found")

        return when (operation) {
            is SandboxOperation.FileRead -> {
                if (sandbox.permissions.contains(Permission.FILE_READ)) {
                    ValidationResult(true)
                } else {
                    ValidationResult(false, "Missing FILE_READ permission")
                }
            }
            is SandboxOperation.FileWrite -> {
                if (sandbox.permissions.contains(Permission.FILE_WRITE)) {
                    ValidationResult(true)
                } else {
                    ValidationResult(false, "Missing FILE_WRITE permission")
                }
            }
            is SandboxOperation.NetworkRequest -> {
                if (sandbox.permissions.contains(Permission.NETWORK_ACCESS)) {
                    ValidationResult(true)
                } else {
                    ValidationResult(false, "Missing NETWORK_ACCESS permission")
                }
            }
            is SandboxOperation.SystemCommand -> {
                ValidationResult(false, "System commands are not allowed in sandbox")
            }
            is SandboxOperation.ClassLoad -> {
                // 验证类加载
                if (isClassAllowed(operation.className, sandbox)) {
                    ValidationResult(true)
                } else {
                    ValidationResult(false, "Class loading not allowed: ${operation.className}")
                }
            }
        }
    }

    private fun createIsolatedClassLoader(pluginId: String): SandboxClassLoader {
        val parentClassLoader = SandboxManagerImpl::class.java.classLoader
        return SandboxClassLoader(pluginId, parentClassLoader)
    }

    private fun generateSandboxId(pluginId: String): String {
        return "sandbox_${pluginId}_${Clock.System.now().toEpochMilliseconds()}"
    }

    private fun isClassAllowed(className: String, sandbox: Sandbox): Boolean {
        // 白名单机制
        val allowedPackages = listOf(
            "kotlin.",
            "kotlinx.",
            "java.lang.",
            "java.util."
        )

        return allowedPackages.any { className.startsWith(it) }
    }

    private fun ClassLoader.asCoroutineDispatcher(): CoroutineDispatcher {
        return object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                Thread.currentThread().contextClassLoader = this@asCoroutineDispatcher
                block.run()
            }
        }
    }
}

/**
 * 沙箱ClassLoader
 */
internal class SandboxClassLoader(
    private val pluginId: String,
    parent: ClassLoader
) : ClassLoader(parent) {

    private val loadedClasses = mutableMapOf<String, Class<*>>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // 检查是否已加载
        loadedClasses[name]?.let { return it }

        // 检查是否为系统类
        if (isSystemClass(name)) {
            return parent.loadClass(name)
        }

        // 加载插件类
        return try {
            val clazz = findClass(name)
            loadedClasses[name] = clazz
            if (resolve) {
                resolveClass(clazz)
            }
            clazz
        } catch (e: ClassNotFoundException) {
            parent.loadClass(name)
        }
    }

    private fun isSystemClass(name: String): Boolean {
        val systemPackages = listOf(
            "kotlin.",
            "kotlinx.",
            "java.",
            "javax.",
            "android."
        )
        return systemPackages.any { name.startsWith(it) }
    }

    fun close() {
        loadedClasses.clear()
    }
}
```

---

## 数据加密

### 加密管理器接口

```kotlin
/**
 * 加密管理器接口
 */
interface CryptoManager {

    /**
     * 加密数据
     */
    suspend fun encrypt(data: ByteArray, keyAlias: String): Result<EncryptedData>

    /**
     * 解密数据
     */
    suspend fun decrypt(encryptedData: EncryptedData, keyAlias: String): Result<ByteArray>

    /**
     * 生成密钥
     */
    suspend fun generateKey(
        keyAlias: String,
        keySpec: KeySpec
    ): Result<Unit>

    /**
     * 删除密钥
     */
    suspend fun deleteKey(keyAlias: String): Result<Unit>

    /**
     * 检查密钥是否存在
     */
    fun keyExists(keyAlias: String): Boolean

    /**
     * 获取密钥信息
     */
    fun getKeyInfo(keyAlias: String): KeyInfo?

    /**
     * 加密文件
     */
    suspend fun encryptFile(
        inputFile: Path,
        outputFile: Path,
        keyAlias: String
    ): Result<Unit>

    /**
     * 解密文件
     */
    suspend fun decryptFile(
        inputFile: Path,
        outputFile: Path,
        keyAlias: String
    ): Result<Unit>
}

/**
 * 加密数据
 */
@Serializable
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val tag: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (tag != null) {
            if (other.tag == null) return false
            if (!tag.contentEquals(other.tag)) return false
        } else if (other.tag != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + (tag?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * 密钥规范
 */
@Serializable
sealed class KeySpec {
    abstract val algorithm: String
    abstract val keySize: Int

    @Serializable
    data class Aes(override val keySize: Int = 256) : KeySpec() {
        override val algorithm: String = KeyProperties.KEY_ALGORITHM_AES
    }

    @Serializable
    data class Rsa(override val keySize: Int = 2048) : KeySpec() {
        override val algorithm: String = KeyProperties.KEY_ALGORITHM_RSA
    }
}

/**
 * 密钥信息
 */
@Serializable
data class KeyInfo(
    val alias: String,
    val algorithm: String,
    val keySize: Int,
    val creationDate: Instant,
    val isValid: Boolean
)
```

### 加密管理器实现

```kotlin
/**
 * 加密管理器实现
 */
@RequiresApi(Build.VERSION_CODES.M)
internal class CryptoManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CryptoManager {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    override suspend fun encrypt(
        data: ByteArray,
        keyAlias: String
    ): Result<EncryptedData> = withContext(Dispatchers.IO) {
        try {
            val key = getSecretKey(keyAlias)
            val cipher = Cipher.getInstance(TRANSFORMATION_AEAD)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(data)

            Result.success(
                EncryptedData(
                    ciphertext = ciphertext,
                    iv = iv
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun decrypt(
        encryptedData: EncryptedData,
        keyAlias: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val key = getSecretKey(keyAlias)
            val cipher = Cipher.getInstance(TRANSFORMATION_AEAD)
            val spec = GCMParameterSpec(128, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val plaintext = cipher.doFinal(encryptedData.ciphertext)
            Result.success(plaintext)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateKey(keyAlias: String, keySpec: KeySpec): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when (keySpec) {
                is KeySpec.Aes -> generateAesKey(keyAlias, keySpec)
                is KeySpec.Rsa -> generateRsaKey(keyAlias, keySpec)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteKey(keyAlias: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            keyStore.deleteEntry(keyAlias)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun keyExists(keyAlias: String): Boolean {
        return keyStore.containsAlias(keyAlias)
    }

    override fun getKeyInfo(keyAlias: String): KeyInfo? {
        val entry = keyStore.getEntry(keyAlias, null) ?: return null

        val secretKey = entry as? KeyStore.SecretKeyEntry ?: return null
        val algorithm = secretKey.secretKey.algorithm
        val keySize = secretKey.secretKey.encoded.size * 8

        return KeyInfo(
            alias = keyAlias,
            algorithm = algorithm,
            keySize = keySize,
            creationDate = Clock.System.now(), // TODO: 获取实际创建时间
            isValid = true
        )
    }

    override suspend fun encryptFile(
        inputFile: Path,
        outputFile: Path,
        keyAlias: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val inputData = context.contentResolver.openInputStream(inputFile.toUri())?.use {
                it.readBytes()
            } ?: return@withContext Result.failure(IOException("Cannot read input file"))

            val encryptedData = encrypt(inputData, keyAlias).getOrThrow()

            context.contentResolver.openOutputStream(outputFile.toUri())?.use { output ->
                // 写入IV
                output.write(encryptedData.iv.size)
                output.write(encryptedData.iv)
                // 写入密文
                output.write(encryptedData.ciphertext)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun decryptFile(
        inputFile: Path,
        outputFile: Path,
        keyAlias: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(inputFile.toUri())?.use { input ->
                // 读取IV
                val ivSize = input.read()
                val iv = ByteArray(ivSize)
                input.read(iv)

                // 读取密文
                val ciphertext = input.readBytes()

                // 解密
                val encryptedData = EncryptedData(ciphertext, iv)
                val plaintext = decrypt(encryptedData, keyAlias).getOrThrow()

                // 写入输出文件
                context.contentResolver.openOutputStream(outputFile.toUri())?.use { output ->
                    output.write(plaintext)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateAesKey(keyAlias: String, keySpec: KeySpec.Aes) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(keySpec.keySize)
            .build()

        keyGenerator.init(keyGenSpec)
        keyGenerator.generateKey()
    }

    private fun generateRsaKey(keyAlias: String, keySpec: KeySpec.Rsa) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(keySpec.keySize)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .build()

        keyPairGenerator.initialize(keyGenSpec)
        keyPairGenerator.generateKeyPair()
    }

    private fun getSecretKey(keyAlias: String): SecretKey {
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw KeyNotFoundException("Key not found: $keyAlias")
        return entry.secretKey
    }

    private fun Path.toUri(): Uri = Uri.parse(this.toString())

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION_AEAD = "AES/GCM/NoPadding"
    }
}
```

---

## 审计日志

### 审计日志接口

```kotlin
/**
 * 审计日志接口
 */
interface AuditLogger {

    /**
     * 记录权限请求
     */
    suspend fun logPermissionRequest(request: PermissionRequest)

    /**
     * 记录权限授予
     */
    suspend fun logPermissionGrant(
        subject: SecuritySubject,
        permission: Permission,
        grantor: SecuritySubject
    )

    /**
     * 记录权限撤销
     */
    suspend fun logPermissionRevocation(
        subject: SecuritySubject,
        permission: Permission,
        revoker: SecuritySubject
    )

    /**
     * 记录沙箱创建
     */
    suspend fun logSandboxCreation(sandbox: Sandbox)

    /**
     * 记录沙箱销毁
     */
    suspend fun logSandboxDestruction(sandbox: Sandbox)

    /**
     * 记录角色创建
     */
    suspend fun logRoleCreation(role: Role)

    /**
     * 记录角色删除
     */
    suspend fun logRoleDeletion(role: Role)

    /**
     * 记录角色分配
     */
    suspend fun logRoleAssignment(subject: SecuritySubject, role: Role)

    /**
     * 记录角色撤销
     */
    suspend fun logRoleRevocation(subject: SecuritySubject, role: Role)

    /**
     * 记录安全事件
     */
    suspend fun logSecurityEvent(event: SecurityEvent)

    /**
     * 查询审计日志
     */
    suspend fun queryLogs(
        filter: AuditLogFilter
    ): Flow<AuditLogEntry>

    /**
     * 获取审计统计
     */
    suspend fun getAuditStats(
        timeRange: TimeRange
    ): AuditStats
}

/**
 * 审计日志条目
 */
@Serializable
data class AuditLogEntry(
    val id: String,
    val timestamp: Instant,
    val eventType: AuditEventType,
    val subject: SecuritySubject,
    val action: String,
    val resource: String? = null,
    val result: AuditResult,
    val details: Map<String, String> = emptyMap()
)

@Serializable
enum class AuditEventType {
    PERMISSION_REQUEST,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    SANDBOX_CREATED,
    SANDBOX_DESTROYED,
    ROLE_CREATED,
    ROLE_DELETED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    SECURITY_EVENT
}

@Serializable
enum class AuditResult {
    SUCCESS,
    FAILURE,
    PENDING
}

/**
 * 安全事件
 */
@Serializable
data class SecurityEvent(
    val type: SecurityEventType,
    val severity: SecuritySeverity,
    val description: String,
    val source: String? = null,
    val details: Map<String, String> = emptyMap()
)

@Serializable
enum class SecurityEventType {
    AUTHENTICATION_FAILURE,
    AUTHORIZATION_FAILURE,
    MALICIOUS_ACTIVITY,
    VULNERABILITY_DETECTED,
    POLICY_VIOLATION,
    ANOMALY_DETECTED
}

@Serializable
enum class SecuritySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

---

## 依赖关系

### 模块依赖

```
SecurityManager
    ├─→ PermissionManager
    ├─→ SandboxManager
    ├─→ CryptoManager
    └─→ AuditLogger

PermissionManager
    ├─→ PermissionStore
    ├─→ RoleManager
    └─→ AuditLogger

SandboxManager
    ├─→ PermissionManager
    └─→ AuditLogger

CryptoManager
    └─→ AndroidKeyStore
```

### 安全数据流

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Security Request                                 │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Permission Check                                 │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  checkPermission() → validateOperation() → grant/revoke           │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Sandboxed Execution                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  createSandbox() → executeInSandbox() → destroySandbox           │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Data Encryption                                  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  encrypt() → decrypt() → encryptFile() → decryptFile()            │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Audit Logging                                    │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  log*() → queryLogs() → getAuditStats()                          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 附录

### A. 权限声明示例

**AndroidManifest.xml**
```xml
<manifest>
    <!-- 文件访问权限 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <!-- 网络权限 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- 前台服务权限 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application>
        ...
    </application>
</manifest>
```

### B. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统设计

---

**文档维护**: 本文档应随着安全策略演进持续更新
**审查周期**: 每月一次或重大安全变更时
