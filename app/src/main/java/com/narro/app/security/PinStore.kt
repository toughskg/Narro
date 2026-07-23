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
import javax.crypto.Mac
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

internal enum class PinVerifierStrategy {
    KEYSTORE_HMAC,
    LEGACY_PBKDF2,
}

internal fun pinVerifierStrategy(iterations: Int): PinVerifierStrategy =
    if (iterations == FAST_VERIFIER_MARKER) {
        PinVerifierStrategy.KEYSTORE_HMAC
    } else {
        PinVerifierStrategy.LEGACY_PBKDF2
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
            hash = deriveFast(pin, salt),
            iterations = FAST_VERIFIER_MARKER,
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
        var upgradedHash: ByteArray? = null
        val matches = try {
            val candidate = when (pinVerifierStrategy(record.iterations)) {
                PinVerifierStrategy.KEYSTORE_HMAC -> deriveFast(pin, record.salt)
                PinVerifierStrategy.LEGACY_PBKDF2 -> deriveLegacy(
                    pin,
                    record.salt,
                    record.iterations,
                )
            }
            val equal = MessageDigest.isEqual(record.hash, candidate)
            if (equal && pinVerifierStrategy(record.iterations) == PinVerifierStrategy.LEGACY_PBKDF2) {
                upgradedHash = deriveFast(pin, record.salt)
            }
            equal
        } finally {
            pin.fill('\u0000')
        }
        if (matches) {
            write(
                record.copy(
                    hash = upgradedHash ?: record.hash,
                    iterations = FAST_VERIFIER_MARKER,
                    failures = 0,
                    lockedUntil = 0L,
                ),
            )
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
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(HMAC_KEY_ALIAS)
                deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun deriveLegacy(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun deriveFast(pin: CharArray, salt: ByteArray): ByteArray {
        val digits = ByteArray(pin.size) { index -> pin[index].code.toByte() }
        return try {
            Mac.getInstance(HMAC_SHA256).run {
                init(hmacKey())
                update(salt)
                doFinal(digits)
            }
        } finally {
            digits.fill(0)
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
                val version = input.readInt()
                require(version == LEGACY_RECORD_VERSION || version == RECORD_VERSION)
                val iterations = input.readInt()
                if (version == LEGACY_RECORD_VERSION) {
                    require(iterations in 1..MAX_LEGACY_ITERATIONS)
                } else {
                    require(iterations == FAST_VERIFIER_MARKER)
                }
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

    private fun hmacKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(HMAC_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                HMAC_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setKeySize(HMAC_KEY_BITS)
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
        private const val MAX_FAILURES = 5
        private const val LOCK_MILLIS = 30_000L
        private const val LEGACY_RECORD_VERSION = 1
        private const val RECORD_VERSION = 2
        private const val MAX_LEGACY_ITERATIONS = 2_000_000
        private const val KEY_ALIAS = "narro_pin_record_key_v1"
        private const val HMAC_KEY_ALIAS = "narro_pin_hmac_key_v2"
        private const val HMAC_KEY_BITS = 256
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val CIPHER = "AES/GCM/NoPadding"
    }
}

private const val FAST_VERIFIER_MARKER = 0
