package com.example.ts3.data.model

/**
 * 连接状态（ts3j 会话生命周期）。
 * 仅使用 DISCONNECTED / CONNECTING / CONNECTED / ESTABLISHED 四个值：
 * CONNECTING = 正在发起 UDP 握手；ESTABLISHED = 已连上且频道列表可用。
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ESTABLISHED,
}

/** 频道 */
data class Channel(
    val id: Int,
    val parentId: Int,
    val name: String,
    val order: Int,
    val isDefault: Boolean,
    val hasPassword: Boolean,
    val clientCount: Int,
)

/** 频道内的用户 */
data class ClientUser(
    val id: Int,
    val channelId: Int,
    val nickname: String,
    val isTalking: Boolean,
    val isSelf: Boolean,
)

/** 服务器连接配置（敏感字段不包含密码，密码单独加密存储） */
data class ServerConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val nickname: String,
    val savePassword: Boolean,
    val lastUsedAt: Long = 0L,
) {
    /** 输入校验：返回错误提示，合法返回 null。 */
    fun validationError(): String? = when {
        host.isBlank() -> "请输入服务器地址"
        port !in 1..65535 -> "端口需在 1–65535 之间"
        nickname.isBlank() -> "请输入昵称"
        else -> null
    }
}
