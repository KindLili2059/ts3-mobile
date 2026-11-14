package com.example.ts3.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.ts3.data.model.ServerConfig
import com.example.ts3.data.store.SecureStore
import com.example.ts3.data.store.ServerStore
import com.example.ts3.manager.SessionManager
import com.example.ts3.service.Ts3VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 连接业务：服务器书签、连接/断开、加入/离开频道，以及前台服务的启停。
 * 实际的 SDK 调用由 [SessionManager] 统一完成。
 */
class ConnectionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val serverStore = ServerStore(appContext)
    private val secureStore = SecureStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val servers: Flow<List<ServerConfig>> = serverStore.servers
    val lastConnectedId: Flow<String?> = serverStore.lastConnectedId

    /** 连接并启动前台服务保活。 */
    fun connect(config: ServerConfig, password: String) {
        scope.launch { serverStore.markConnected(config.id) }
        SessionManager.connect(config, password)
        startVoiceService()
    }

    fun disconnect() {
        SessionManager.disconnect()
        stopVoiceService()
    }

    fun joinChannel(channelId: Int, password: String) =
        SessionManager.joinChannel(channelId, password)

    fun leaveChannel() = SessionManager.leaveChannel()

    fun refresh() = SessionManager.refresh()

    // ---- 书签与密码 ----

    suspend fun saveServer(config: ServerConfig) = serverStore.upsert(config)

    suspend fun deleteServer(id: String) {
        serverStore.delete(id)
        secureStore.removePassword(id)
    }

    suspend fun getPassword(serverId: String): String? = secureStore.getPassword(serverId)

    suspend fun savePassword(serverId: String, password: String) =
        secureStore.savePassword(serverId, password)

    // ---- 前台服务 ----

    private fun startVoiceService() {
        val intent = Intent(appContext, Ts3VoiceService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopVoiceService() {
        appContext.stopService(Intent(appContext, Ts3VoiceService::class.java))
    }
}
