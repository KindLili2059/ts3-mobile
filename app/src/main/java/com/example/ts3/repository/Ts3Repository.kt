package com.example.ts3.repository

import android.content.Context
import com.example.ts3.data.model.Channel
import com.example.ts3.data.model.ClientUser
import com.example.ts3.data.model.ConnectionState
import com.example.ts3.data.model.ServerConfig
import com.example.ts3.manager.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 统一业务门面：ViewModel 只依赖本类，不直接接触 SessionManager / native 层。
 * 组合 [ConnectionRepository] 与 [VoiceRepository]，并透传会话状态。
 */
class Ts3Repository(context: Context) {

    private val connection = ConnectionRepository(context)
    private val voice = VoiceRepository()

    // ---- 会话状态 ----

    val connectionState: StateFlow<ConnectionState> = SessionManager.connectionState
    val channels: StateFlow<List<Channel>> = SessionManager.channels
    val clients: StateFlow<List<ClientUser>> = SessionManager.clients
    val currentChannelId: StateFlow<Int> = SessionManager.currentChannelId
    val ownClientId: StateFlow<Int> = SessionManager.ownClientId
    val micMuted: StateFlow<Boolean> = SessionManager.micMuted
    val speakerMuted: StateFlow<Boolean> = SessionManager.speakerMuted
    val lastError: StateFlow<String?> = SessionManager.lastError
    val reconnecting: StateFlow<Boolean> = SessionManager.reconnecting
    val activeServer: StateFlow<ServerConfig?> = SessionManager.activeServer

    // ---- 书签 ----

    val servers: Flow<List<ServerConfig>> = connection.servers
    val lastConnectedId: Flow<String?> = connection.lastConnectedId

    suspend fun saveServer(config: ServerConfig) = connection.saveServer(config)

    suspend fun deleteServer(id: String) = connection.deleteServer(id)

    suspend fun getPassword(serverId: String): String? = connection.getPassword(serverId)

    suspend fun savePassword(serverId: String, password: String) =
        connection.savePassword(serverId, password)

    // ---- 连接 / 频道 ----

    fun connect(config: ServerConfig, password: String) = connection.connect(config, password)

    fun disconnect() = connection.disconnect()

    fun joinChannel(channelId: Int, password: String) = connection.joinChannel(channelId, password)

    fun leaveChannel() = connection.leaveChannel()

    fun refresh() = connection.refresh()

    fun clearError() = SessionManager.clearError()

    // ---- 语音 ----

    fun setMicMuted(muted: Boolean) = voice.setMicMuted(muted)

    fun setSpeakerMuted(muted: Boolean) = voice.setSpeakerMuted(muted)

    fun setPtt(pressed: Boolean) = voice.setPtt(pressed)
}
