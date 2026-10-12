package com.openlauncher.app.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object ValetPinSecurity {
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256

    data class PinRecord(val salt: String, val hash: String)

    fun create(pin: String): PinRecord {
        val saltBytes = ByteArray(16).also(SecureRandom()::nextBytes)
        return PinRecord(
            salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP),
            hash = hash(pin, saltBytes)
        )
    }

    fun verify(pin: String, encodedSalt: String, expectedHash: String): Boolean {
        if (pin.isBlank() || encodedSalt.isBlank() || expectedHash.isBlank()) return false
        return runCatching {
            val saltBytes = Base64.decode(encodedSalt, Base64.NO_WRAP)
            val actual = Base64.decode(hash(pin, saltBytes), Base64.NO_WRAP)
            val expected = Base64.decode(expectedHash, Base64.NO_WRAP)
            MessageDigest.isEqual(actual, expected)
        }.getOrDefault(false)
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec)
                .encoded
            Base64.encodeToString(encoded, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }
}
