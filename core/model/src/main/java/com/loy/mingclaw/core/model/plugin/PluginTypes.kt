package com.loy.mingclaw.core.model.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginMetadata(
    val pluginId: String,
    val version: String,
    val name: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val permissions: List<String>,
    val dependencies: List<PluginDependency>,
    val entryPoint: String,
    val minKernelVersion: String,
    val checksum: String
)

enum class PluginCategory {
    Tool, Service, UI, Integration, Experimental
}

@Serializable
data class PluginDependency(
    val pluginId: String,
    val minVersion: String,
    val maxVersion: String? = null,
    val required: Boolean = true
)

enum class PluginPermission(val description: String) {
    NetworkAccess("访问网络"),
    FileSystemRead("读取文件系统"),
    FileSystemWrite("写入文件系统"),
    CameraAccess("访问摄像头"),
    MicrophoneAccess("访问麦克风"),
    LocationAccess("访问位置信息"),
    ContactAccess("访问联系人"),
    NotificationAccess("显示通知"),
    BackgroundExecution("后台执行"),
    SystemSettings("修改系统设置"),
    SensitiveData("访问敏感数据"),
    PluginManagement("管理其他插件")
}

enum class ToolCategory {
    Information, Action, Computation, Media, System, Custom
}

@Serializable
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val default: JsonElement? = null,
    val enum: List<JsonElement>? = null,
    val format: String? = null
)

enum class ParameterType {
    String, Number, Integer, Boolean, Array, Object, Null
}

@Serializable
data class PluginInfo(
    val pluginId: String,
    val version: String,
    val name: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val status: PluginStatus,
    val permissions: List<PluginPermission>,
    val dependencies: List<PluginDependency>,
    val tools: List<String>
)

enum class PluginStatus {
    Unknown, Registered, Loading, Running, Stopped, Error, Unregistered
}
