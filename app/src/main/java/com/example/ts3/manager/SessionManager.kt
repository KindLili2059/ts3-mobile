package com.example.ts3.manager

import android.content.Context
import android.util.Log
import com.example.ts3.audio.Ts3AudioEngine
import com.example.ts3.data.model.Channel
import com.example.ts3.data.model.ClientUser
import com.example.ts3.data.model.ConnectionState
import com.example.ts3.data.model.ServerConfig
import com.example.ts3.data.store.SecureStore
import com.github.manevolent.ts3j.command.CommandException
import com.github.manevolent.ts3j.command.SingleCommand
import com.github.manevolent.ts3j.command.parameter.CommandSingleParameter
import com.github.manevolent.ts3j.event.ChannelCreateEvent
import com.github.manevolent.ts3j.event.ChannelDeletedEvent
import com.github.manevolent.ts3j.event.ChannelEditedEvent
import com.github.manevolent.ts3j.event.ChannelListEvent
import com.github.manevolent.ts3j.event.ChannelMovedEvent
import com.github.manevolent.ts3j.event.ClientJoinEvent
import com.github.manevolent.ts3j.event.ClientLeaveEvent
import com.github.manevolent.ts3j.event.ClientMovedEvent
import com.github.manevolent.ts3j.event.ClientPokeEvent
import com.github.manevolent.ts3j.event.ClientUpdatedEvent
import com.github.manevolent.ts3j.event.DisconnectedEvent
import com.github.manevolent.ts3j.event.TS3Listener
import com.github.manevolent.ts3j.event.TextMessageEvent
import com.github.manevolent.ts3j.identity.LocalIdentity
import com.github.manevolent.ts3j.protocol.ProtocolRole
import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket
import com.github.manevolent.ts3j.util.Ts3Crypt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeoutException
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 连接会话的唯一所有者。全应用只存在一个 ts3j 会话，
 * 所有对 [LocalTeamspeakClientSocket] 的调用与状态都集中在这里。
 */
object SessionManager : TS3Listener {

    private const val TAG = "Ts3Session"
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val JOIN_TIMEOUT_MS = 5_000L

    /** 终态断线原因（封禁/踢出），不自动重连。 */
    private val TERMINAL_DISCONNECT_REASONS = setOf(4, 5)

    /** 普通客户端类型（忽略 query/其他类型）。 */
    private const val REGULAR_CLIENT_TYPE = 0

    private lateinit var appContext: Context
    private lateinit var secureStore: SecureStore
    private lateinit var audioEngine: Ts3AudioEngine

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var socket: LocalTeamspeakClientSocket? = null

    // ---- 会话状态（ts3j 事件维护） ----

    private val channelMap = ConcurrentHashMap<Int, Channel>()
    private val clientMap = ConcurrentHashMap<Int, ClientUser>()

    // ---- 自动重连 ----

    /** 有界退避序列（毫秒），取自参考项目的重连策略。 */
    private val reconnectDelaysMs = intArrayOf(1000, 2000, 4000, 8000, 15000, 30000)

    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    /** 用户主动断开的标记：置位后不再自动重连。 */
    @Volatile
    private var userRequestedDisconnect = false

    /** 是否曾成功建立过连接（断线后是否自动重连的判据之一）。 */
    @Volatile
    private var connectedOnce = false

    private var lastConfig: ServerConfig? = null
    private var lastPassword: String = ""

    /** 上次加入的频道（用于重连后恢复），仅内存态，随会话清空。 */
    private var lastJoinedChannelId = 0
    private var lastJoinedChannelPassword: String = ""

    /** 最近一次后台网络线程异常（如「too many clones」），用于给出更友好的错误提示。 */
    @Volatile
    private var backgroundError: String? = null

    // ---- 对外状态 ----

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _clients = MutableStateFlow<List<ClientUser>>(emptyList())
    val clients: StateFlow<List<ClientUser>> = _clients.asStateFlow()

    private val _currentChannelId = MutableStateFlow(0)
    val currentChannelId: StateFlow<Int> = _currentChannelId.asStateFlow()

    private val _ownClientId = MutableStateFlow(0)
    val ownClientId: StateFlow<Int> = _ownClientId.asStateFlow()

    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    private val _speakerMuted = MutableStateFlow(false)
    val speakerMuted: StateFlow<Boolean> = _speakerMuted.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private val _activeServer = MutableStateFlow<ServerConfig?>(null)
    val activeServer: StateFlow<ServerConfig?> = _activeServer.asStateFlow()

    val isEstablished: Boolean
        get() = _connectionState.value == ConnectionState.ESTABLISHED

    /** 由 Application 在进程启动时调用一次。 */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        secureStore = SecureStore(appContext)
        audioEngine = Ts3AudioEngine(appContext)
    }

    /** 从已保存配置建立连接（password 已由上层解密取出）。用户发起的新连接。 */
    fun connect(config: ServerConfig, password: String) {
        userRequestedDisconnect = false
        connectedOnce = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnecting.value = false
        lastConfig = config
        lastPassword = password
        lastJoinedChannelId = 0
        lastJoinedChannelPassword = ""
        Log.d(TAG, "connect() 请求: host=${config.host} port=${config.port} nick=${config.nickname}")
        ioScope.launch { performConnect(config, password) }
    }

    /** 实际发起一次连接（新连接与自动重连共用）。 */
    private suspend fun performConnect(config: ServerConfig, password: String) {
        closeSocket()
        backgroundError = null
        val identity = ensureIdentity()

        _activeServer.value = config
        _connectionState.value = ConnectionState.CONNECTING
        channelMap.clear()
        clientMap.clear()
        publishSnapshot()
        _micMuted.value = false
        _speakerMuted.value = false

        val sock = LocalTeamspeakClientSocket()
        sock.setIdentity(identity)
        sock.setNickname(config.nickname)
        sock.setHWID(identity.uid.toBase64())
        sock.setMicrophone(audioEngine)
        sock.setExceptionHandler { error ->
            backgroundError = error.message ?: error.javaClass.simpleName
            Log.w(TAG, "ts3j 后台异常", error)
        }
        sock.setVoiceHandler { packet -> audioEngine.onVoicePacket(packet.codecType, packet.codecData) }
        sock.setWhisperHandler { packet -> audioEngine.onVoicePacket(packet.codecType, packet.codecData) }
        sock.addListener(this)
        socket = sock

        val wasReconnect = connectedOnce
        try {
            val address = InetSocketAddress(InetAddress.getByName(config.host), config.port)
            Log.d(TAG, "开始 TeamSpeak UDP 连接")
            sock.connect(address, password.toTs3jServerPassword(), CONNECT_TIMEOUT_MS)
            try {
                sock.subscribeAll()
            } catch (error: CommandException) {
                Log.w(TAG, "事件订阅不可用", error)
            }
            _ownClientId.value = sock.clientId
            connectedOnce = true
            reconnectAttempt = 0
            _reconnecting.value = false
            _connectionState.value = ConnectionState.ESTABLISHED
            publishSnapshot()
            audioEngine.start()
            Log.d(TAG, "ts3j 连接成功，clientId=${sock.clientId}")
            if (wasReconnect && lastJoinedChannelId != 0) {
                joinChannel(lastJoinedChannelId, lastJoinedChannelPassword)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "连接失败", error)
            _connectionState.value = ConnectionState.DISCONNECTED
            val bg = backgroundError
            _lastError.value = when {
                bg?.contains("too many clones") == true ->
                    "连接被拒绝：同一身份同时连接数过多（请稍候重试，或检查是否已在其他设备登录）"
                error is TimeoutException && bg != null ->
                    "连接失败: $bg"
                else -> "连接失败: ${error.message ?: error.javaClass.simpleName}"
            }
            closeSocket()
            if (!userRequestedDisconnect && connectedOnce) {
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnecting.value = false
        audioEngine.stop()
        closeSocket()
        channelMap.clear()
        clientMap.clear()
        publishSnapshot()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentChannelId.value = 0
        _activeServer.value = null
        _micMuted.value = false
        _speakerMuted.value = false
        lastJoinedChannelId = 0
        lastJoinedChannelPassword = ""
    }

    fun clearError() {
        _lastError.value = null
    }

    fun joinChannel(channelId: Int, password: String) {
        lastJoinedChannelId = channelId
        lastJoinedChannelPassword = password
        ioScope.launch {
            val sock = socket ?: return@launch
            try {
                val command = SingleCommand(
                    "clientmove",
                    ProtocolRole.CLIENT,
                    CommandSingleParameter("cid", channelId.toString()),
                    CommandSingleParameter("clid", sock.clientId.toString()),
                )
                command.add(CommandSingleParameter("cpw", Ts3Crypt.hashPassword(password)))
                sock.executeCommand(command).get(JOIN_TIMEOUT_MS)
            } catch (error: Exception) {
                _lastError.value = "加入频道失败: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    /** 离开当前频道 = 移动到默认频道。 */
    fun leaveChannel() {
        ioScope.launch {
            val default = channelMap.values.firstOrNull { it.isDefault } ?: return@launch
            joinChannel(default.id, "")
        }
    }

    fun setMicMuted(muted: Boolean) {
        _micMuted.value = muted
        audioEngine.setMicMuted(muted)
    }

    fun setSpeakerMuted(muted: Boolean) {
        _speakerMuted.value = muted
        audioEngine.setSpeakerMuted(muted)
    }

    fun setPtt(pressed: Boolean) {
        audioEngine.setPtt(pressed)
    }

    /** 重新发布当前快照（ts3j 事件已维护状态，此方法为兼容保留）。 */
    fun refresh() {
        publishSnapshot()
    }

    // ---- TS3Listener（在 ts3j 网络线程回调） ----

    override fun onChannelList(event: ChannelListEvent) {
        channelMap[event.channelId] = event.map.toChannel(event.channelId)
        publishSnapshot()
    }

    override fun onClientJoin(event: ClientJoinEvent) {
        if (event.clientType != REGULAR_CLIENT_TYPE) return
        clientMap[event.clientId] = ClientUser(
            id = event.clientId,
            channelId = event.clientTargetId,
            nickname = event.clientNickname,
            isTalking = event.isClientTalking,
            isSelf = false,
        )
        publishSnapshot()
    }

    override fun onClientLeave(event: ClientLeaveEvent) {
        clientMap.remove(event.clientId)
        publishSnapshot()
    }

    override fun onClientMoved(event: ClientMovedEvent) {
        clientMap[event.clientId]?.let { existing ->
            clientMap[event.clientId] = existing.copy(channelId = event.targetChannelId)
            publishSnapshot()
        }
    }

    override fun onClientChanged(event: ClientUpdatedEvent) {
        clientMap[event.clientId]?.let { existing ->
            val talking = event.map.bool("client_flag_talking")
                ?: event.map.bool("status")
                ?: existing.isTalking
            clientMap[event.clientId] = existing.copy(isTalking = talking)
            publishSnapshot()
        }
    }

    override fun onChannelCreate(event: ChannelCreateEvent) {
        channelMap[event.channelId] = event.map.toChannel(event.channelId)
        publishSnapshot()
    }

    override fun onChannelDeleted(event: ChannelDeletedEvent) {
        channelMap.remove(event.channelId)
        publishSnapshot()
    }

    override fun onChannelEdit(event: ChannelEditedEvent) {
        channelMap[event.channelId]?.let { existing ->
            val name = event.map["channel_name"] ?: existing.name
            val hasPassword = event.map.bool("channel_flag_password") ?: existing.hasPassword
            val isDefault = event.map.bool("channel_flag_default") ?: existing.isDefault
            channelMap[event.channelId] = existing.copy(name = name, hasPassword = hasPassword, isDefault = isDefault)
            publishSnapshot()
        }
    }

    override fun onChannelMoved(event: ChannelMovedEvent) {
        channelMap[event.channelId]?.let { existing ->
            channelMap[event.channelId] = existing.copy(parentId = event.channelParentId, order = event.channelOrder)
            publishSnapshot()
        }
    }

    override fun onTextMessage(event: TextMessageEvent) = Unit

    override fun onClientPoke(event: ClientPokeEvent) = Unit

    override fun onDisconnected(event: DisconnectedEvent) {
        Log.d(TAG, "onDisconnected reasonId=${event.reasonId}")
        audioEngine.stop()
        closeSocket()
        channelMap.clear()
        clientMap.clear()
        publishSnapshot()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentChannelId.value = 0
        val retryable = event.reasonId !in TERMINAL_DISCONNECT_REASONS
        if (retryable && connectedOnce && !userRequestedDisconnect && lastConfig != null) {
            scheduleReconnect()
        }
    }

    // ---- 内部工具 ----

    /** 按有界退避策略调度一次自动重连。 */
    private fun scheduleReconnect() {
        val config = lastConfig ?: return
        val password = lastPassword
        reconnectJob?.cancel()
        reconnectJob = ioScope.launch {
            val index = minOf(reconnectAttempt, reconnectDelaysMs.size - 1)
            val delayMs = reconnectDelaysMs[index]
            reconnectAttempt++
            _reconnecting.value = true
            Log.d(TAG, "自动重连：第 $reconnectAttempt 次（${delayMs}ms 后）")
            delay(delayMs.toLong())
            if (userRequestedDisconnect) return@launch
            performConnect(config, password)
        }
    }

    private suspend fun ensureIdentity(): LocalIdentity {
        val stored = secureStore.getIdentity()
        if (stored != null) {
            try {
                return LocalIdentity.read(ByteArrayInputStream(stored.toByteArray(StandardCharsets.UTF_8)))
            } catch (e: Exception) {
                Log.w(TAG, "读取已存身份失败，重新生成", e)
            }
        }
        val identity = LocalIdentity.generateNew(10)
        secureStore.saveIdentity(identity.export())
        return identity
    }

    private fun closeSocket() {
        val sock = socket
        socket = null
        if (sock != null) {
            runCatching { sock.close() }
        }
    }

    private fun publishSnapshot() {
        val counts = clientMap.values.groupingBy { it.channelId }.eachCount()
        val ownId = _ownClientId.value
        _channels.value = channelMap.values.map { it.copy(clientCount = counts[it.id] ?: 0) }
        _clients.value = clientMap.values.map { it.copy(isSelf = it.id == ownId) }
        _currentChannelId.value = clientMap[ownId]?.channelId ?: 0
    }

    private fun String.toTs3jServerPassword(): String? =
        takeIf { it.isNotBlank() }?.let { Ts3Crypt.hashPassword(it) }

    private fun Map<String, String>.toChannel(id: Int): Channel = Channel(
        id = id,
        parentId = (get("pid") ?: get("cpid"))?.toIntOrNull() ?: 0,
        name = get("channel_name").orEmpty(),
        order = get("channel_order")?.toIntOrNull() ?: 0,
        isDefault = bool("channel_flag_default") ?: false,
        hasPassword = bool("channel_flag_password") ?: false,
        clientCount = 0,
    )

    private fun Map<String, String>.bool(key: String): Boolean? = when (get(key)) {
        "1" -> true
        "0" -> false
        else -> null
    }
}
