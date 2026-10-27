# Quick Reference - Pellets Water Heater

## Key Files for Common Changes

### Business Logic
| Task | File |
|------|------|
| Add/modify metadata fields | `app/src/main/java/com/example/pelletswaterheater/utils/MetadataUtils.kt` |
| Change value conversion logic | `app/src/main/java/com/example/pelletswaterheater/utils/ValueConverter.kt` |
| Modify CSV logging format | `app/src/main/java/com/example/pelletswaterheater/utils/TemperatureLogger.kt` |
| Adjust worker scheduling | `app/src/main/java/com/example/pelletswaterheater/TemperatureUtils.kt` |
| Change widget UI | `app/src/main/java/com/example/pelletswaterheater/TemperatureWidget.kt` |

### UI Screens
| Screen | File |
|--------|------|
| Main data display | `app/src/main/java/com/example/pelletswaterheater/ui/DataScreen.kt` |
| Value selection (checkboxes) | `app/src/main/java/com/example/pelletswaterheater/ui/ValueSelectionScreen.kt` |
| Logs list | `app/src/main/java/com/example/pelletswaterheater/ui/LogsScreen.kt` |
| Settings | `app/src/main/java/com/example/pelletswaterheater/ui/SettingsScreen.kt` |
| Metadata edit/import | `app/src/main/java/com/example/pelletswaterheater/ui/MetadataScreen.kt` |
| About | `app/src/main/java/com/example/pelletswaterheater/ui/AboutScreen.kt` |
| Donate | `app/src/main/java/com/example/pelletswaterheater/ui/DonateScreen.kt` |

### ViewModels
| Purpose | File |
|---------|------|
| Core data (metadata, values, selections) | `app/src/main/java/com/example/pelletswaterheater/viewmodel/TemperatureViewModelNew.kt` |
| Settings, logging service | `app/src/main/java/com/example/pelletswaterheater/SettingsViewModel.kt` |

## Data Model Cheatsheet

### MetadataJson
```kotlin
data class MetadataJson(
    val sections: Map<String, Map<String, MetadataEntry>>,
    val error: String?
)

sealed class MetadataEntry
data class MetadataField(
    val value: String,
    val unit: String?,
    val factor: Double?,
    val offset: Double?,
    val name: String?,
    val format: String?,      // "0:Aus|1:Ein"
    val min: String?,
    val max: String?,
    val decimals: Int?
) : MetadataEntry()

data class MetadataLabel(val label: String) : MetadataEntry()
```

### ConvertedValue
```kotlin
data class ConvertedValue(
    val logical: String,   // "22.5"
    val name: String,      // "Außentemperatur"
    val unit: String?      // "°C"
)
```

### Selection (per field, in SharedPreferences)
```kotlin
data class Selection(
    val key: String,           // "system.L_ambient"
    val log: Boolean,          // CSV logging
    val widget: Boolean,       // Show on Glance widget
    val showData: Boolean,     // Show on DataScreen
    val showSectionName: Boolean // Show "system.Außentemperatur"
)
```

### WidgetDataWrapper
```kotlin
data class WidgetDataWrapper(
    val snapshot: List<ConvertedValueWrapper>,  // filtered by widget checkbox
    val state: String,
    val error: String?,
    val timestampSuccess: String
)

data class ConvertedValueWrapper(
    val convertedValue: ConvertedValue,
    val section: String,
    val showSectionName: Boolean
)
```

## Common Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "com.example.pelletswaterheater.utils.ValueConverterTest"

# Run tests with info logging
./gradlew testDebugUnitTest --info

# Lint
./gradlew lintDebug

# Clean build
./gradlew clean assembleDebug

# Generate dependency license report
./gradlew generateLicenseReport
```

## Server API
- **Endpoint**: `http://<IP>:<PORT>/<PASSWORD>/all?`
- **Response**: JSON with sections (system, hk1, pu1, ww1, pe1, circ1)
- **Value fetch**: Same endpoint returns current values

## SharedPreferences Keys
- `server_url` - Server URL with password
- `log_<key>` - Logging enabled per field
- `widget_<key>` - Widget display per field
- `showData_<key>` - DataScreen display per field
- `showSectionName_<key>` - Section+name display per field
- `storedOrderHash` - CSV header rotation detection
- `background_color`, `text_color` - Widget colors (hex ARGB)
- `gateway_fingerprint` - Network fingerprint

## Test Resources
- `app/src/test/resources/server.json` - Full server metadata sample
- `app/src/test/resources/user.json` - User override sample
- `app/src/test/resources/testdata.json` - Test values for DataScreen