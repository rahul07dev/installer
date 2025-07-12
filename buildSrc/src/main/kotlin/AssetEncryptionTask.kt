import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

abstract class AssetEncryptionTask : DefaultTask() {
    
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    private val secretKey = "MySecretKey12345" // Same as in AssetEncryption.kt
    private val iv = "MyInitVector1234" // Same as in AssetEncryption.kt
    
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
                        println("Encrypting: ${file.name}")
                        val encryptedData = encryptFile(file)
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
            "apk", "json", "txt", "xml", "properties" -> true
            else -> false
        }
    }
    
    private fun encryptFile(file: File): ByteArray {
        val data = file.readBytes()
        
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
        val ivParameterSpec = IvParameterSpec(iv.toByteArray())
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        
        return cipher.doFinal(data)
    }
}