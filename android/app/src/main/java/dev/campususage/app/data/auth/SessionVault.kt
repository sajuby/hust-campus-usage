package dev.campususage.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound secrets. No credentials, tokens, or cookies are stored in Room or logs. */
class SessionVault(context: Context, private val alias:String = "CampusUsage.school.sessions.v1") : SessionStorage {
    private val directory = File(context.noBackupFilesDir, "school-sessions").apply { mkdirs() }
    private fun file(system: String): AtomicFile {
        require(system in setOf("waxy", "network", "main_utilities"))
        return AtomicFile(File(directory, "$system.enc"))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized override fun read(system: String): JSONObject? {
        val f = file(system)
        if (!f.baseFile.exists()) return null
        // An unavailable device key means reauthentication is required; never crash the UI.
        return runCatching {
            val bytes = f.readFully()
            require(bytes.size > 32 && bytes.take(4).toByteArray().contentEquals(byteArrayOf(67, 85, 83, 1)))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(4, 16)))
            cipher.updateAAD("campus/$system/v1".toByteArray())
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(16, bytes.size)), Charsets.UTF_8))
        }.getOrNull()
    }
    @Synchronized override fun write(system: String, value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("campus/$system/v1".toByteArray())
        require(cipher.iv.size == 12)
        val encrypted = cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(16 + encrypted.size).put(byteArrayOf(67, 85, 83, 1)).put(cipher.iv).put(encrypted).array()
        val f = file(system); val stream = f.startWrite()
        try { stream.write(bytes); f.finishWrite(stream) } catch (e: Exception) { f.failWrite(stream); throw e }
    }
    @Synchronized override fun clear(system: String) = file(system).delete()
}
