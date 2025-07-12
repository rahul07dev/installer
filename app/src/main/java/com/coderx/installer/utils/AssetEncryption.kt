package com.coderx.installer.utils

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AssetEncryption {
    private const val TAG = "AssetEncryption"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 16 // 128 bits
    private const val IV_LENGTH = 12 // 96 bits for GCM
    
    // This key should be obfuscated in production - consider using NDK or key derivation
    private const val SECRET_KEY = "MySecretKey12345" // 16 bytes for AES-128
    
    /**
     * Encrypts data using AES-GCM encryption with random IV
     */
    fun encrypt(data: ByteArray): ByteArray {
        return try {
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            // Generate random IV for each encryption
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)
            val encryptedData = cipher.doFinal(data)
            
            // Prepend IV to encrypted data for storage
            val result = ByteArray(IV_LENGTH + encryptedData.size)
            System.arraycopy(iv, 0, result, 0, IV_LENGTH)
            System.arraycopy(encryptedData, 0, result, IV_LENGTH, encryptedData.size)
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Decrypts data using AES-GCM decryption
     */
    fun decrypt(encryptedDataWithIv: ByteArray): ByteArray {
        return try {
            if (encryptedDataWithIv.size < IV_LENGTH) {
                throw IllegalArgumentException("Encrypted data is too short to contain IV")
            }
            
            // Extract IV from the beginning of the data
            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(encryptedDataWithIv, 0, iv, 0, IV_LENGTH)
            
            // Extract encrypted data
            val encryptedData = ByteArray(encryptedDataWithIv.size - IV_LENGTH)
            System.arraycopy(encryptedDataWithIv, IV_LENGTH, encryptedData, 0, encryptedData.size)
            
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec)
            
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Reads and decrypts an encrypted asset file
     */
    fun readEncryptedAsset(context: Context, fileName: String): ByteArray {
        return try {
            val encryptedFileName = "${fileName}.enc"
            Log.d(TAG, "Attempting to read encrypted asset: $encryptedFileName")
            val inputStream = context.assets.open(encryptedFileName)
            val encryptedData = inputStream.readBytes()
            inputStream.close()
            
            Log.d(TAG, "Read ${encryptedData.size} bytes of encrypted data")
            
            if (!validateEncryptedData(encryptedData)) {
                throw IllegalArgumentException("Invalid encrypted data format")
            }
            
            decrypt(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read encrypted asset: $fileName", e)
            throw e
        }
    }
    
    /**
     * Reads and decrypts an encrypted asset file as InputStream
     */
    fun getDecryptedAssetInputStream(context: Context, fileName: String): InputStream {
        val decryptedData = readEncryptedAsset(context, fileName)
        return decryptedData.inputStream()
    }
    
    /**
     * Validates if the encrypted data format is correct
     */
    fun validateEncryptedData(encryptedData: ByteArray): Boolean {
        return try {
            // Check minimum size (IV + some encrypted data + GCM tag)
            encryptedData.size >= IV_LENGTH + GCM_TAG_LENGTH + 1
        } catch (e: Exception) {
            false
        }
    }
}