package com.example.ts3.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure")

/**
 * 敏感数据安全存储。
 * 使用 Android Keystore 中的 AES/GCM 密钥对内容加密后再写入 DataStore，
 * 满足计划书「服务器密码及客户端身份私钥等敏感数据不应明文存储」的要求。
 */
class SecureStore(private val context: Context) {

    private object Keys {
        val IDENTITY = stringPreferencesKey("identity")
    }

    private fun passwordKey(serverId: String) = stringPreferencesKey("password:$serverId")

    // ---- 身份 ----

    suspend fun getIdentity(): String? =
        decryptOrNull(context.secureDataStore.data.first()[Keys.IDENTITY])

    suspend fun saveIdentity(identity: String) {
        context.secureDataStore.edit { it[Keys.IDENTITY] = encrypt(identity) }
    }

    // ---- 服务器密码 ----

    suspend fun getPassword(serverId: String): String? {
        val prefs = context.secureDataStore.data
        return decryptOrNull(prefs.first()[passwordKey(serverId)])
    }

    suspend fun savePassword(serverId: String, password: String) {
        context.secureDataStore.edit { it[passwordKey(serverId)] = encrypt(password) }
    }

    suspend fun removePassword(serverId: String) {
        context.secureDataStore.edit { it.remove(passwordKey(serverId)) }
    }

    // ---- AES/GCM 加解密 ----

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ct = Base64.encodeToString(
            cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
        )
        return "$iv:$ct"
    }

    private fun decryptOrNull(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val parts = encoded.split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ts3_secure_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
