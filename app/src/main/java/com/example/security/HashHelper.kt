package com.example.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object HashHelper {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        
        // Save as iterationCount:salt:hash
        return "$ITERATIONS:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        try {
            val parts = storedHash.split(":")
            if (parts.size != 3) return false
            val iterations = parts[0].toInt()
            val salt = Base64.decode(parts[1], Base64.NO_WRAP)
            val hash = Base64.decode(parts[2], Base64.NO_WRAP)

            val testHash = pbkdf2(password.toCharArray(), salt, iterations, hash.size * 8)
            
            var diff = hash.size xor testHash.size
            for (i in 0 until Math.min(hash.size, testHash.size)) {
                diff = diff or (hash[i].toInt() xor testHash[i].toInt())
            }
            return diff == 0
        } catch (e: Exception) {
            return false
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }
}
