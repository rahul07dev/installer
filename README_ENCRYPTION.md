# APK Asset Encryption

This project implements automatic encryption of APK assets during the build process to protect sensitive files from being easily extracted.

## How It Works

1. **Build-time Encryption**: During the build process, assets in `src/main/assets/` are automatically encrypted using AES encryption.

2. **Runtime Decryption**: The app decrypts assets on-demand when they're needed using the `AssetEncryption` utility class.

3. **Transparent Operation**: The app works exactly the same as before, but assets are now protected.

## Security Features

- **AES-128 CBC Encryption**: Industry-standard encryption for asset protection
- **Selective Encryption**: Only specific file types are encrypted (APK, JSON, TXT, XML, properties)
- **ProGuard Integration**: Encryption classes and keys are obfuscated in release builds
- **Runtime Decryption**: Assets are decrypted in memory when needed

## File Structure

```
app/
├── src/main/assets/           # Original assets (encrypted during build)
├── build/encrypted-assets/    # Generated encrypted assets
└── src/main/java/com/coderx/installer/utils/
    ├── AssetEncryption.kt     # Main encryption/decryption utility
    └── SecurityUtils.kt       # Additional security utilities
```

## Usage

### Reading Encrypted Assets

```kotlin
// Read encrypted asset as ByteArray
val data = AssetEncryption.readEncryptedAsset(context, "app.apk")

// Get InputStream for encrypted asset
val inputStream = AssetEncryption.getDecryptedAssetInputStream(context, "app.apk")
```

### Build Process

The encryption happens automatically during build:

1. `encryptAssets` task runs before `preBuild`
2. Assets are encrypted and placed in `build/encrypted-assets/`
3. The build uses encrypted assets instead of originals

## Security Considerations

### Current Implementation
- Uses hardcoded encryption key (suitable for basic protection)
- AES-128 CBC encryption
- Fixed IV (not recommended for production)

### Production Recommendations

1. **Use NDK for Key Storage**: Store encryption keys in native code
2. **Implement Key Derivation**: Use PBKDF2 or similar for key generation
3. **Use AES-GCM**: More secure than CBC mode
4. **Random IVs**: Generate unique IV for each file
5. **Certificate Pinning**: Add network security for remote key retrieval

### Enhanced Security Example

```kotlin
// Generate secure key from password + device info
val salt = SecurityUtils.generateSalt()
val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
val key = SecurityUtils.deriveKeyFromPassword("your_password$deviceId", salt)

// Use AES-GCM for better security
val (encryptedData, iv) = SecurityUtils.encryptAESGCM(data, key)
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
        "apk", "json", "txt", "xml", "properties", "db", "zip" -> true
        else -> false
    }
}
```

### Changing Encryption Algorithm

Modify `AssetEncryption.kt`:

```kotlin
private const val TRANSFORMATION = "AES/GCM/NoPadding" // More secure
```

## Testing

1. Build the app normally - assets will be automatically encrypted
2. Install and run - the app should work exactly as before
3. Extract the APK and check `assets/` folder - files should have `.enc` extension
4. Verify encrypted files cannot be opened directly

## Troubleshooting

### Build Issues
- Ensure `buildSrc/` directory is properly created
- Check that Kotlin DSL is enabled in your project
- Verify all dependencies are correctly specified

### Runtime Issues
- Check logcat for decryption errors
- Ensure encrypted asset names match expected patterns
- Verify encryption keys are consistent between build and runtime

### Performance
- Decryption adds minimal overhead
- Consider caching decrypted data for frequently accessed assets
- Large files may benefit from streaming decryption