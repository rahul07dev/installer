# APK Asset Encryption with AES-GCM

This project implements automatic encryption of APK assets during the build process using **AES-GCM encryption** to protect sensitive files from being easily extracted.

## How It Works

1. **Build-time Encryption**: During the build process, assets in `src/main/assets/` are automatically encrypted using AES-GCM encryption with random IVs.

2. **Runtime Decryption**: The app decrypts assets on-demand when they're needed using the `AssetEncryption` utility class.

3. **Transparent Operation**: The app works exactly the same as before, but assets are now protected with authenticated encryption.

## Security Features

- **AES-GCM Encryption**: Authenticated encryption providing both confidentiality and integrity
- **Random IVs**: Each file gets a unique random IV for maximum security
- **Authenticated Encryption**: GCM mode prevents tampering and provides authentication
- **Selective Encryption**: Only specific file types are encrypted (APK, JSON, TXT, XML, properties, DB, ZIP)
- **ProGuard Integration**: Encryption classes and keys are obfuscated in release builds
- **Runtime Decryption**: Assets are decrypted in memory when needed

## Security Advantages of AES-GCM

### vs AES-CBC:
- **Authentication**: GCM provides built-in authentication, preventing tampering
- **No Padding**: GCM doesn't require padding, eliminating padding oracle attacks
- **Parallel Processing**: GCM can be parallelized for better performance
- **Random IVs**: Each encryption uses a unique random IV

### Security Properties:
- **Confidentiality**: Data is encrypted and unreadable
- **Integrity**: Any tampering is detected during decryption
- **Authenticity**: Ensures data hasn't been modified

## File Structure

```
app/
├── src/main/assets/           # Original assets (encrypted during build)
├── build/encrypted-assets/    # Generated encrypted assets with random IVs
└── src/main/java/com/coderx/installer/utils/
    ├── AssetEncryption.kt     # AES-GCM encryption/decryption utility
    └── SecurityUtils.kt       # Advanced security utilities
```

## Usage

### Reading Encrypted Assets

```kotlin
// Read encrypted asset as ByteArray
val data = AssetEncryption.readEncryptedAsset(context, "app.apk")

// Get InputStream for encrypted asset
val inputStream = AssetEncryption.getDecryptedAssetInputStream(context, "app.apk")

// Validate encrypted data format
val isValid = AssetEncryption.validateEncryptedData(encryptedData)
```

### Advanced Security Features

```kotlin
// Generate secure device-specific key
val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
val appSignature = getAppSignature()
val secureKey = SecurityUtils.deriveDeviceSpecificKey("your_password", deviceId, appSignature)

// Use AES-GCM with custom key
val encryptedData = SecurityUtils.encryptAESGCM(data, secureKey)
val decryptedData = SecurityUtils.decryptAESGCM(encryptedData, secureKey)
```

## Build Process

The encryption happens automatically during build:

1. `encryptAssets` task runs before `preBuild`
2. Each asset is encrypted with AES-GCM using a random IV
3. IV is prepended to encrypted data for storage
4. Encrypted assets are placed in `build/encrypted-assets/`
5. The build uses encrypted assets instead of originals

## Data Format

Each encrypted file follows this format:
```
[12-byte Random IV][Encrypted Data + 16-byte GCM Tag]
```

- **IV (12 bytes)**: Random initialization vector for GCM
- **Encrypted Data**: AES-GCM encrypted content
- **GCM Tag (16 bytes)**: Authentication tag (included in encrypted data)

## Security Considerations

### Current Implementation
- Uses AES-GCM with 128-bit keys
- Random IV for each file encryption
- Built-in authentication prevents tampering
- Hardcoded key (suitable for basic protection)

### Production Recommendations

1. **Use NDK for Key Storage**: Store encryption keys in native code
2. **Device-Specific Keys**: Derive keys from device information
3. **Key Rotation**: Implement key rotation mechanism
4. **Certificate Pinning**: Add network security for remote key retrieval
5. **Root Detection**: Add anti-tampering measures

### Enhanced Security Example

```kotlin
// Generate device-specific key
val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
val signature = packageInfo.signatures[0].toCharsString()

val secureKey = SecurityUtils.deriveDeviceSpecificKey(
    basePassword = "your_secret_password",
    deviceId = deviceId,
    appSignature = signature
)

// Use for encryption/decryption
val encryptedData = SecurityUtils.encryptAESGCM(data, secureKey)
```

## Build Configuration

The encryption is configured in `app/build.gradle.kts`:

```kotlin
val encryptAssetsTask = tasks.register<AssetEncryptionTask>("encryptAssets") {
    inputDir.set(file("src/main/assets"))
    outputDir.set(file("build/encrypted-assets"))
}
```

## Customization

### Adding File Types to Encrypt

Edit `AssetEncryptionTask.kt`:

```kotlin
private fun shouldEncrypt(file: File): Boolean {
    val extension = file.extension.lowercase()
    return when (extension) {
        "apk", "json", "txt", "xml", "properties", "db", "zip", "pdf" -> true
        else -> false
    }
}
```

### Using Different Key Sizes

Modify the key generation:

```kotlin
// For AES-256
val key = SecurityUtils.generateAESKey() // Generates 256-bit key
val secretKeySpec = SecretKeySpec(key, "AES")
```

## Testing

1. Build the app normally - assets will be automatically encrypted with AES-GCM
2. Install and run - the app should work exactly as before
3. Extract the APK and check `assets/` folder - files should have `.enc` extension
4. Verify encrypted files cannot be opened directly
5. Check that tampering with encrypted files causes decryption to fail

## Performance

- **Encryption**: Slightly slower than CBC due to authentication
- **Decryption**: Comparable performance with built-in integrity checking
- **Memory**: Minimal overhead for IV storage
- **Security**: Significantly better than CBC mode

## Troubleshooting

### Build Issues
- Ensure `buildSrc/` directory is properly created
- Check that Kotlin DSL is enabled in your project
- Verify all dependencies are correctly specified

### Runtime Issues
- Check logcat for decryption errors
- Ensure encrypted asset names match expected patterns
- Verify encryption keys are consistent between build and runtime
- Check for GCM authentication failures (indicates tampering)

### Security Validation
- Use `validateGCMData()` to check encrypted data format
- Monitor for authentication failures during decryption
- Implement proper error handling for tampered files

## Migration from CBC

If upgrading from CBC mode:

1. The new system is backward compatible during build
2. Old CBC encrypted files will need re-encryption
3. Update any custom decryption code to use new methods
4. Test thoroughly to ensure all assets decrypt correctly

This AES-GCM implementation provides significantly better security than the previous CBC mode while maintaining the same ease of use and transparent operation.