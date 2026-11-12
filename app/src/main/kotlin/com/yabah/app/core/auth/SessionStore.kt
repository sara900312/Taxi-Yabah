package com.yabah.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.yabah.app.core.net.YabahJson
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val userId: String,
    val phone: String? = null,
) {
    fun expiresSoon(nowEpochSec: Long, marginSec: Long = 60): Boolean = nowEpochSec + marginSec >= expiresAtEpochSec
    override fun toString(): String = "Session(userId=$userId, expiresAt=$expiresAtEpochSec)" // never log tokens
}

interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES-256-GCM with a non-exportable key in the Android Keystore. */
class KeystoreSessionCipher(private val alias: String = "yabah_session_key_v1") : SessionCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + c.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return c.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }
}

/**
 * Persists the session ONLY in encrypted form. If the Keystore is unusable the session lives in memory for
 * this process and the person signs in again next time; tokens are never written in plain text.
 */
class SessionStore(private val prefs: SharedPreferences?, private val cipher: SessionCipher?) {
    private var memory: Session? = null

    fun load(): Session? {
        memory?.let { return it }
        val stored = prefs?.getString(KEY, null) ?: return null
        return try {
            val plain = cipher!!.decrypt(Base64.decode(stored, Base64.NO_WRAP))
            YabahJson.decodeFromString(Session.serializer(), plain.decodeToString()).also { memory = it }
        } catch (_: Exception) {
            prefs.edit().remove(KEY).apply() // key rotated / corrupted: fail closed
            null
        }
    }

    fun save(session: Session) {
        memory = session
        val c = cipher ?: return
        try {
            val blob = c.encrypt(YabahJson.encodeToString(Session.serializer(), session).encodeToByteArray())
            prefs?.edit()?.putString(KEY, Base64.encodeToString(blob, Base64.NO_WRAP))?.apply()
        } catch (_: Exception) {
            prefs?.edit()?.remove(KEY)?.apply()
        }
    }

    fun clear() {
        memory = null
        prefs?.edit()?.remove(KEY)?.apply()
    }

    companion object {
        private const val KEY = "session_v1"
        fun create(context: Context): SessionStore {
            val prefs = context.getSharedPreferences("yabah_secure", Context.MODE_PRIVATE)
            val cipher = runCatching { KeystoreSessionCipher().also { it.decrypt(it.encrypt(byteArrayOf(1))) } }.getOrNull()
            return SessionStore(prefs, cipher)
        }
    }
}
