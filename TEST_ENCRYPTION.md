# Testing the AES-GCM Encryption System

This document provides comprehensive testing instructions for the AES-GCM asset encryption system.

## 🧪 Testing Checklist

### 1. Build Process Testing

**Test the encryption task:**
```bash
# Clean and build to test encryption
./gradlew clean
./gradlew encryptAssets
./gradlew assembleDebug
```

**Verify encrypted assets:**
- Check `app/build/encrypted-assets/` directory exists
- Verify `app.apk.enc` file is created
- Confirm file size is reasonable (original + IV + GCM tag)

### 2. Asset Verification

**Check encrypted file format:**
```bash
# Navigate to encrypted assets directory
cd app/build/encrypted-assets/

# Check file exists and has reasonable size
ls -la app.apk.enc

# Verify it's not the original APK (should be binary encrypted data)
file app.apk.enc
```

### 3. Runtime Testing

**Install and test the app:**
1. Install the APK on a device/emulator
2. Grant installation permissions when prompted
3. Click "Update" button
4. Verify the app can decrypt and install the embedded APK

**Check logs for encryption/decryption:**
```bash
adb logcat | grep -E "(AssetEncryption|AppInstaller|AssetEncryptionTask)"
```

### 4. Security Verification

**Verify assets are encrypted in final APK:**
1. Extract the built APK: `unzip app-debug.apk`
2. Check `assets/` folder contains only `.enc` files
3. Verify original files are not present
4. Confirm encrypted files cannot be opened directly

### 5. Error Handling Testing

**Test various failure scenarios:**
- Remove the encrypted asset and test error handling
- Corrupt the encrypted file and verify authentication failure
- Test with insufficient permissions

## 🔍 Expected Results

### Successful Build Output:
```
AssetEncryptionTask: Starting encryption process
Input directory: /path/to/app/src/main/assets
Output directory: /path/to/app/build/encrypted-assets
Encrypting with AES-GCM: app.apk
Successfully encrypted app.apk (X -> Y bytes)
AssetEncryptionTask: Encryption process completed
```

### Successful Runtime Logs:
```
D/AssetEncryption: Attempting to read encrypted asset: app.apk.enc
D/AssetEncryption: Read XXXX bytes of encrypted data
D/AppInstaller: Successfully decrypted APK, size: XXXX bytes
I/AppInstaller: Installing APK: com.rto1p8.app, current version: 0, new version: X
```

### Security Verification:
- Original `app.apk` should NOT be in final APK assets
- Only `app.apk.enc` should be present
- Encrypted file should be unreadable binary data
- File size should be: original_size + 12 (IV) + 16 (GCM tag)

## 🚨 Troubleshooting

### Build Issues:
- **"Input directory does not exist"**: Normal if no assets folder exists
- **Encryption fails**: Check file permissions and available disk space
- **Task not running**: Verify buildSrc is properly configured

### Runtime Issues:
- **"Encrypted APK file not found"**: Check if encryption task ran successfully
- **"Decryption failed"**: Verify encryption key consistency
- **"Invalid encrypted data format"**: Check file wasn't corrupted during build

### Performance Issues:
- Large APK files may take longer to encrypt/decrypt
- Monitor memory usage during decryption
- Consider chunked processing for very large files

## ✅ Success Criteria

The encryption system is working correctly if:

1. ✅ Build completes without errors
2. ✅ Encrypted assets are generated in `build/encrypted-assets/`
3. ✅ Final APK contains only `.enc` files in assets
4. ✅ App successfully decrypts and installs embedded APK
5. ✅ No original unencrypted files are accessible
6. ✅ Tampering with encrypted files causes authentication failure
7. ✅ App functions exactly as before encryption was added

## 🔐 Security Notes

- The current implementation uses a hardcoded key for demonstration
- For production, consider using NDK or device-specific key derivation
- AES-GCM provides both encryption and authentication
- Random IVs ensure each encryption is unique
- GCM authentication prevents tampering

This encryption system provides strong protection for your APK assets while maintaining full functionality of your application.