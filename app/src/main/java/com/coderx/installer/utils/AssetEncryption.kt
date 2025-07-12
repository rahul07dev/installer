package com.coderx.installer.utils

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AssetEncryption {
    private const val TAG = "AssetEncryption"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    
    // This key should be obfuscated in production - consider using NDK or key derivation
    private const val SECRET_KEY = "MySecretKey12345" // 16 bytes for AES-128
    private const val IV = "MyInitVector1234" // 16 bytes IV
    
    /**
     * Encrypts data using AES encryption
     */
    fun encrypt(data: ByteArray): ByteArray {
        return try {
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            val ivParameterSpec = IvParameterSpec(IV.toByteArray())
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Decrypts data using AES decryption
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        return try {
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            val ivParameterSpec = IvParameterSpec(IV.toByteArray())
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            
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
            val inputStream = context.assets.open(encryptedFileName)
            val encryptedData = inputStream.readBytes()
            inputStream.close()
            
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
}