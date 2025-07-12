import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

abstract class AssetEncryptionTask : DefaultTask() {
    
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    private val secretKey = "MySecretKey12345" // Same as in AssetEncryption.kt
    private val gcmTagLength = 16 // 128 bits
    private val ivLength = 12 // 96 bits for GCM
    
    @TaskAction
    fun encryptAssets() {
        val inputDirectory = inputDir.get().asFile
        val outputDirectory = outputDir.get().asFile
        
        // Clear output directory
        if (outputDirectory.exists()) {
            outputDirectory.deleteRecursively()
        }
        outputDirectory.mkdirs()
        
        // Copy and encrypt all files
        inputDirectory.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(inputDirectory)
                val outputFile = File(outputDirectory, "${relativePath.path}.enc")
                
                // Create parent directories if needed
                outputFile.parentFile?.mkdirs()
                
                try {
                    if (shouldEncrypt(file)) {
                        println("Encrypting with AES-GCM: ${file.name}")
                        val encryptedData = encryptFileGCM(file)
                        outputFile.writeBytes(encryptedData)
                    } else {
                        println("Copying without encryption: ${file.name}")
                        file.copyTo(outputFile, overwrite = true)
                    }
                } catch (e: Exception) {
                    println("Error processing ${file.name}: ${e.message}")
                    // Copy original file if encryption fails
                    file.copyTo(File(outputDirectory, relativePath.path), overwrite = true)
                }
            }
        }
    }
    
    private fun shouldEncrypt(file: File): Boolean {
        val extension = file.extension.lowercase()
        // Encrypt specific file types - add more as needed
        return when (extension) {
            "apk", "json", "txt", "xml", "properties", "db", "zip" -> true
            else -> false
        }
    }
    
    private fun encryptFileGCM(file: File): ByteArray {
        val data = file.readBytes()
        
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        // Generate random IV for each file
        val iv = ByteArray(ivLength)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(gcmTagLength * 8, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)
        val encryptedData = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        val result = ByteArray(ivLength + encryptedData.size)
        System.arraycopy(iv, 0, result, 0, ivLength)
        System.arraycopy(encryptedData, 0, result, ivLength, encryptedData.size)
        
        return result
    }
}