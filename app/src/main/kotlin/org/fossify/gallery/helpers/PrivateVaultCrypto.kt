package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Version 1: AES-256-GCM with a fresh 96-bit IV per file. The random data key
 * is wrapped first by AndroidKeyStore AES-GCM and then by a password-derived
 * AES-GCM key. Password and unwrapped key exist only during an unlocked session.
 * All wrapping keys and file formats are versioned. Losing AndroidKeyStore
 * invalidates the vault; no silent key regeneration is allowed after setup.
 */
class PrivateVaultCrypto(private val context: Context) {
    private val prefs = context.getSharedPreferences("encrypted_vault_v1", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val dir get() = File(context.filesDir, "private_vault_v1").apply { mkdirs() }
    private val alias = "org.fossify.gallery.vault.master.v1"

    val isSetUp get() = prefs.contains("wrapped_key_v1")
    val hasEncryptedFiles get() = dir.listFiles()?.any { it.name.endsWith(".gvlt") } == true

    fun files(): List<File> = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".gvlt") }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun setup(password: CharArray): ByteArray {
        require(password.size >= 6) { "密码至少需要 6 位" }
        check(!isSetUp && !hasEncryptedFiles) { "已有隐私空间，不能重置密钥" }
        val key = ByteArray(32).also(random::nextBytes)
        try {
            persistWrappedKey(key, password)
            return key
        } catch (ex: Exception) {
            key.fill(0)
            throw ex
        }
    }

    fun unlock(password: CharArray): ByteArray {
        val salt = decode(prefs.getString("salt_v1", null) ?: error("隐私空间未设置密码"))
        val wrapped = decode(prefs.getString("wrapped_key_v1", null) ?: error("隐私空间密钥缺失"))
        val passwordKey = derive(password, salt)
        val keystoreWrapped = decryptBytes(wrapped, passwordKey)
        val dataKey = decryptBytes(keystoreWrapped, getKeystoreKey(create = false))
        check(dataKey.size == 32) { "无效的隐私空间密钥" }
        return dataKey
    }

    fun changePassword(dataKey: ByteArray, password: CharArray) {
        require(password.size >= 6) { "密码至少需要 6 位" }
        check(isSetUp && dataKey.size == 32)
        persistWrappedKey(dataKey, password)
    }

    private fun persistWrappedKey(dataKey: ByteArray, password: CharArray) {
        val salt = ByteArray(16).also(random::nextBytes)
        val passwordKey = derive(password, salt)
        val inner = encryptBytes(dataKey, getKeystoreKey(create = true))
        val outer = encryptBytes(inner, passwordKey)
        check(prefs.edit()
            .putInt("key_version", 1)
            .putString("salt_v1", encode(salt))
            .putString("wrapped_key_v1", encode(outer))
            .commit()) { "保存密钥失败" }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun getKeystoreKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "设备加密密钥已丢失；无法解锁原有隐私空间" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptBytes(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(data)
    }

    private fun decryptBytes(data: ByteArray, key: SecretKey): ByteArray {
        require(data.size > 12 + 16) { "密文无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data, 12, data.size - 12)
    }

    /**
     * Import never changes/deletes the SAF source. Ciphertext is staged and
     * renamed only after a complete authenticated encryption.
     */
    fun import(uri: Uri, mime: String?, dataKey: ByteArray): File {
        check(dataKey.size == 32)
        val type = when {
            mime?.startsWith("image/") == true -> "image"
            mime?.startsWith("video/") == true -> "video"
            else -> "file"
        }
        val result = File(dir, "${UUID.randomUUID()}.$type.gvlt")
        val part = File(dir, result.name + ".part")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"))
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(part).use { output ->
                    output.write(byteArrayOf(0x47, 0x56, 0x4c, 0x54, 1))
                    output.write(cipher.iv)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) cipher.update(buffer, 0, n)?.let(output::write)
                    }
                    output.write(cipher.doFinal())
                    output.fd.sync()
                }
            } ?: error("无法读取选中的文件")
            check(part.renameTo(result)) { "无法完成加密文件保存" }
            return result
        } finally {
            part.delete()
        }
    }

    /**
     * Plaintext staging remains app-private, is never handed to Glide,
     * MediaStore, backups or external apps, and is deleted when preview closes
     * or the Activity stops. A crash can leave a cache file, so the Activity
     * purges stale previews before any future unlock.
     * A plaintext file is only exposed to the viewer after GCM authentication.
     */
    fun decryptForPreview(source: File, target: File, dataKey: ByteArray) {
        check(dataKey.size == 32 && source.parentFile?.canonicalFile == dir.canonicalFile)
        val stage = File(target.parentFile, target.name + ".part")
        try {
            FileInputStream(source).use { input ->
                val header = ByteArray(17)
                check(input.read(header) == header.size &&
                    header.copyOfRange(0, 5).contentEquals(byteArrayOf(0x47, 0x56, 0x4c, 0x54, 1))) {
                    "不支持的加密文件版本"
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"),
                    GCMParameterSpec(128, header.copyOfRange(5, 17)))
                FileOutputStream(stage).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > 0) cipher.update(buf, 0, n)?.let(output::write)
                    }
                    // GCM tag checked here; never expose .part before success.
                    output.write(cipher.doFinal())
                }
            }
            check(stage.renameTo(target)) { "无法打开预览" }
        } finally {
            stage.delete()
        }
    }

    fun delete(file: File) {
        check(file.parentFile?.canonicalFile == dir.canonicalFile && file.name.endsWith(".gvlt"))
        check(file.delete()) { "删除失败" }
    }

    private fun encode(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun decode(data: String) = Base64.decode(data, Base64.NO_WRAP)
}
