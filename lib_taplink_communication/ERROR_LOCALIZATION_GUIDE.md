# Error Code Localization Guide

## Overview

The Taplink SDK error codes have been refactored to support internationalization (i18n) by extracting error descriptions and solutions to Android string resource files. This allows the SDK to display error messages in different languages based on the device's locale settings.

## Architecture

### 1. Resource Files Structure

```
lib_taplink_communication/src/main/res/
├── values/
│   └── error_strings.xml          # English (default)
└── values-zh/
    └── error_strings.xml          # Chinese (Simplified)
```

### 2. Key Components

- **InnerErrorCode.kt**: Simplified enum that only stores error codes
- **ErrorStringHelper.kt**: Utility class that loads localized strings from resources
- **error_strings.xml**: Resource files containing translated descriptions and solutions

## Implementation Details

### InnerErrorCode Changes

**Before:**
```kotlin
object C20 : InnerErrorCode("C20", "SDK 未初始化", "在调用 connect() 之前先调用 TaplinkSDK.init() 初始化 SDK")
```

**After:**
```kotlin
object C20 : InnerErrorCode("C20")
```

The description and solution are now dynamically loaded from resource files.

### ErrorStringHelper

The `ErrorStringHelper` class provides:
- **getDescription(code)**: Returns localized error description
- **getSolution(code)**: Returns localized solution (optional)
- **Fallback mechanism**: Uses hardcoded English strings when resources are unavailable

### Resource Naming Convention

String resources follow this naming pattern:
- Description: `error_{code}_description` (e.g., `error_c20_description`)
- Solution: `error_{code}_solution` (e.g., `error_c20_solution`)

## Supported Languages

### English (Default)
- File: `values/error_strings.xml`
- Used when device locale doesn't match any specific language folder

### Chinese (Simplified)
- File: `values-zh/error_strings.xml`
- Used when device locale is set to Chinese

## Usage Examples

### Getting Error Information

```kotlin
val errorCode = InnerErrorCode.C20
println("Code: ${errorCode.code}")
println("Description: ${errorCode.description}")
println("Solution: ${errorCode.solution}")
```

### Adding New Languages

To add support for a new language (e.g., Spanish):

1. Create folder: `values-es/`
2. Create file: `values-es/error_strings.xml`
3. Translate all string resources:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="error_c20_description">SDK no inicializado</string>
    <string name="error_c20_solution">Llame a TaplinkSDK.init() para inicializar el SDK antes de llamar a connect()</string>
    <!-- ... other translations ... -->
</resources>
```

## Error Code Categories

### Success Response (0)
- **0**: Interface response successful

### Security Restriction Errors (S Series)
- **S01-S09**: IP validation, timestamp, signature, permissions, encryption

### Client Errors (C Series)
- **C01-C41**: Parameter validation, connection issues, device problems

### Merchant Configuration Errors (M Series)
- **M01-M04**: Application and merchant binding issues

### Payment Channel Errors (P Series)
- **P01**: Bank channel exceptions

### System Errors (E Series)
- **E01-E04**: System busy, network errors, exceptions

### Risk Control Interception (R Series)
- **R01**: Risk control system rejections

### Other Errors
- **996**: System timeout
- **Unknown**: Dynamic error codes not predefined

## Benefits

1. **Internationalization**: Automatic language switching based on device locale
2. **Maintainability**: Centralized error message management
3. **Consistency**: Uniform error message format across the SDK
4. **Extensibility**: Easy to add new languages without code changes
5. **Fallback Safety**: Always provides English messages when resources fail

## Migration Notes

- **Backward Compatibility**: All existing error code constants remain unchanged
- **Runtime Behavior**: Error descriptions and solutions are now loaded dynamically
- **Performance**: Minimal impact due to resource caching by Android system
- **Testing**: Verify error messages in different device locales

## Best Practices

1. **Always provide English translations** as the fallback language
2. **Keep translations consistent** across all supported languages
3. **Test error messages** in different locales during development
4. **Update all language files** when adding new error codes
5. **Use professional translation services** for production applications

## Future Enhancements

- Support for additional languages (Japanese, Korean, etc.)
- Dynamic error message formatting with parameters
- Error message analytics and usage tracking
- Integration with translation management systems