package com.example.ts3.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ts3.data.model.Channel
import com.example.ts3.data.model.ClientUser
import com.example.ts3.data.model.ConnectionState
import com.example.ts3.data.model.ServerConfig
import com.example.ts3.repository.Ts3Repository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 会话 UI 状态与用户操作入口。UI 只与本类交互。
 */
class Ts3ViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Ts3Repository(application)

    // ---- 会话状态 ----

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val channels: StateFlow<List<Channel>> = repository.channels
    val clients: StateFlow<List<ClientUser>> = repository.clients
    val currentChannelId: StateFlow<Int> = repository.currentChannelId
    val ownClientId: StateFlow<Int> = repository.ownClientId
    val micMuted: StateFlow<Boolean> = repository.micMuted
    val speakerMuted: StateFlow<Boolean> = repository.speakerMuted
    val lastError: StateFlow<String?> = repository.lastError
    val reconnecting: StateFlow<Boolean> = repository.reconnecting
    val activeServer: StateFlow<ServerConfig?> = repository.activeServer

    val servers: Flow<List<ServerConfig>> = repository.servers
    val lastConnectedId: Flow<String?> = repository.lastConnectedId

    // ---- 连接 / 频道 ----

    fun connect(config: ServerConfig, password: String) = repository.connect(config, password)

    fun disconnect() = repository.disconnect()

    fun joinChannel(channelId: Int, password: String) =
        repository.joinChannel(channelId, password)

    fun leaveChannel() = repository.leaveChannel()

    fun refresh() = repository.refresh()

    fun clearError() = repository.clearError()

    // ---- 语音 ----

    fun toggleMic() = repository.setMicMuted(!repository.micMuted.value)

    fun toggleSpeaker() = repository.setSpeakerMuted(!repository.speakerMuted.value)

    fun setPtt(pressed: Boolean) = repository.setPtt(pressed)

    // ---- 书签 ----

    fun saveBookmark(config: ServerConfig, password: String) {
        viewModelScope.launch {
            repository.saveServer(config)
            if (config.savePassword) repository.savePassword(config.id, password)
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { repository.deleteServer(id) }
    }

    /** 读取已保存的服务器密码（用于一键连接已保存的服务器）。 */
    fun loadPassword(serverId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(repository.getPassword(serverId)) }
    }
}
