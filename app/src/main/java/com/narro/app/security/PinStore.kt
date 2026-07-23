package com.narro.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

sealed interface PinVerification {
    data object Success : PinVerification
    data class Failed(val remainingAttempts: Int) : PinVerification
    data class Locked(val remainingSeconds: Long) : PinVerification
    data object Unavailable : PinVerification
}

class PinStore(context: Context) {
    private val recordFile = context.noBackupFilesDir.resolve("security/pin_record.bin")
    private val random = SecureRandom()

    fun hasPin(): Boolean = recordFile.isFile

    fun register(pin: CharArray) {
        require(pin.size == PIN_LENGTH && pin.all(Char::isDigit))
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val record = Record(
            salt = salt,
            hash = derive(pin, salt, ITERATIONS),
            iterations = ITERATIONS,
            failures = 0,
            lockedUntil = 0L,
        )
        pin.fill('\u0000')
        write(record)
    }

    fun verify(pin: CharArray, now: Long = System.currentTimeMillis()): PinVerification {
        val record = read() ?: return PinVerification.Unavailable
        if (record.lockedUntil > now) {
            pin.fill('\u0000')
            return PinVerification.Locked((record.lockedUntil - now + 999L) / 1_000L)
        }
        val candidate = derive(pin, record.salt, record.iterations)
        pin.fill('\u0000')
        if (MessageDigest.isEqual(record.hash, candidate)) {
            write(record.copy(failures = 0, lockedUntil = 0L))
            return PinVerification.Success
        }
        val failures = record.failures + 1
        return if (failures >= MAX_FAILURES) {
            write(record.copy(failures = 0, lockedUntil = now + LOCK_MILLIS))
            PinVerification.Locked(LOCK_MILLIS / 1_000L)
        } else {
            write(record.copy(failures = failures))
            PinVerification.Failed(MAX_FAILURES - failures)
        }
    }

    fun clear() {
        recordFile.delete()
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun write(record: Record) {
        val plain = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(RECORD_VERSION)
                output.writeInt(record.iterations)
                output.writeInt(record.salt.size)
                output.write(record.salt)
                output.writeInt(record.hash.size)
                output.write(record.hash)
                output.writeInt(record.failures)
                output.writeLong(record.lockedUntil)
            }
            bytes.toByteArray()
        }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain)
        recordFile.parentFile?.mkdirs()
        val temp = recordFile.resolveSibling("${recordFile.name}.tmp")
        DataOutputStream(temp.outputStream().buffered()).use { output ->
            output.writeInt(cipher.iv.size)
            output.write(cipher.iv)
            output.write(encrypted)
        }
        if (!temp.renameTo(recordFile)) {
            temp.copyTo(recordFile, overwrite = true)
            temp.delete()
        }
    }

    private fun read(): Record? {
        if (!recordFile.isFile) return null
        return runCatching {
            val encryptedPayload = DataInputStream(recordFile.inputStream().buffered()).use { input ->
                val ivLength = input.readInt()
                require(ivLength in 12..32)
                val iv = ByteArray(ivLength).also(input::readFully)
                iv to input.readBytes()
            }
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encryptedPayload.first))
            val plain = cipher.doFinal(encryptedPayload.second)
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                require(input.readInt() == RECORD_VERSION)
                val iterations = input.readInt()
                val salt = ByteArray(input.readInt().also { require(it in 16..64) }).also(input::readFully)
                val hash = ByteArray(input.readInt().also { require(it in 16..64) }).also(input::readFully)
                Record(salt, hash, iterations, input.readInt(), input.readLong())
            }
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private data class Record(
        val salt: ByteArray,
        val hash: ByteArray,
        val iterations: Int,
        val failures: Int,
        val lockedUntil: Long,
    )

    companion object {
        private const val PIN_LENGTH = 4
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
        private const val ITERATIONS = 600_000
        private const val MAX_FAILURES = 5
        private const val LOCK_MILLIS = 30_000L
        private const val RECORD_VERSION = 1
        private const val KEY_ALIAS = "narro_pin_record_key_v1"
        private const val CIPHER = "AES/GCM/NoPadding"
    }
}
