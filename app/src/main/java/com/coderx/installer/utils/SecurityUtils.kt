package com.coderx.installer.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    
    private const val GCM_TAG_LENGTH = 16 // 128 bits
    private const val IV_LENGTH = 12 // 96 bits for GCM
    
    /**
     * Generates a more secure key from a password using PBKDF2
     */
    fun deriveKeyFromPassword(password: String, salt: ByteArray, iterations: Int = 10000): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Generates a random salt
     */
    fun generateSalt(length: Int = 16): ByteArray {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }
    
    /**
     * Creates SHA-256 hash of data
     */
    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Generates a random AES key
     */
    fun generateAESKey(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256) // AES-256
        return keyGenerator.generateKey().encoded
    }
    
    /**
     * AES-GCM encryption with random IV
     */
    fun encryptAESGCM(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        
        // Generate random IV
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedData = cipher.doFinal(data)
        
        // Combine IV + encrypted data
        val result = ByteArray(IV_LENGTH + encryptedData.size)
        System.arraycopy(iv, 0, result, 0, IV_LENGTH)
        System.arraycopy(encryptedData, 0, result, IV_LENGTH, encryptedData.size)
        
        return result
    }
    
    /**
     * AES-GCM decryption
     */
    fun decryptAESGCM(encryptedDataWithIv: ByteArray, key: ByteArray): ByteArray {
        if (encryptedDataWithIv.size < IV_LENGTH + GCM_TAG_LENGTH) {
            throw IllegalArgumentException("Encrypted data is too short")
        }
        
        // Extract IV
        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(encryptedDataWithIv, 0, iv, 0, IV_LENGTH)
        
        // Extract encrypted data
        val encryptedData = ByteArray(encryptedDataWithIv.size - IV_LENGTH)
        System.arraycopy(encryptedDataWithIv, IV_LENGTH, encryptedData, 0, encryptedData.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(encryptedData)
    }
    
    /**
     * Secure key derivation using device-specific information
     */
    fun deriveDeviceSpecificKey(basePassword: String, deviceId: String, appSignature: String): ByteArray {
        val combinedInput = "$basePassword:$deviceId:$appSignature"
        val salt = sha256(deviceId.toByteArray()).toByteArray()
        return deriveKeyFromPassword(combinedInput, salt, 15000)
    }
    
    /**
     * Validates GCM encrypted data format
     */
    fun validateGCMData(data: ByteArray): Boolean {
        return data.size >= IV_LENGTH + GCM_TAG_LENGTH + 1
    }
}