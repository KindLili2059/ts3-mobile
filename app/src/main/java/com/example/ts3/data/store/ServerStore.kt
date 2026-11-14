package com.example.ts3.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ts3.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

/**
 * 服务器书签存储（非敏感数据）。使用 DataStore 持久化。
 * 密码不保存在这里，而是由 [SecureStore] 加密保存。
 */
class ServerStore(private val context: Context) {

    private object Keys {
        val SERVERS = stringPreferencesKey("servers")
        val LAST_CONNECTED_ID = stringPreferencesKey("last_connected_id")
    }

    /** 所有服务器书签，按最近使用时间降序 */
    val servers: Flow<List<ServerConfig>> = context.serverDataStore.data.map { prefs ->
        parseServers(prefs[Keys.SERVERS])
    }

    val lastConnectedId: Flow<String?> = context.serverDataStore.data.map { prefs ->
        prefs[Keys.LAST_CONNECTED_ID]
    }

    suspend fun upsert(config: ServerConfig) {
        context.serverDataStore.edit { prefs ->
            val list = parseServers(prefs[Keys.SERVERS]).toMutableList()
            val idx = list.indexOfFirst { it.id == config.id }
            if (idx >= 0) {
                list[idx] = config
            } else {
                list.add(config)
            }
            prefs[Keys.SERVERS] = serialize(list.sortedByDescending { it.lastUsedAt })
        }
    }

    suspend fun delete(serverId: String) {
        context.serverDataStore.edit { prefs ->
            val list = parseServers(prefs[Keys.SERVERS]).filterNot { it.id == serverId }
            prefs[Keys.SERVERS] = serialize(list)
        }
    }

    suspend fun markConnected(serverId: String) {
        context.serverDataStore.edit { prefs ->
            prefs[Keys.LAST_CONNECTED_ID] = serverId
            val list = parseServers(prefs[Keys.SERVERS]).map {
                if (it.id == serverId) it.copy(lastUsedAt = System.currentTimeMillis()) else it
            }
            prefs[Keys.SERVERS] = serialize(list.sortedByDescending { it.lastUsedAt })
        }
    }

    private fun serialize(list: List<ServerConfig>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("host", c.host)
                    .put("port", c.port)
                    .put("nickname", c.nickname)
                    .put("savePassword", c.savePassword)
                    .put("lastUsedAt", c.lastUsedAt)
            )
        }
        return arr.toString()
    }

    private fun parseServers(json: String?): List<ServerConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ServerConfig(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    host = o.optString("host"),
                    port = o.optInt("port", 9987),
                    nickname = o.optString("nickname"),
                    savePassword = o.optBoolean("savePassword", false),
                    lastUsedAt = o.optLong("lastUsedAt", 0L),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
