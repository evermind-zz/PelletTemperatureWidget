# Project Context for AI Assistants

## One-Line Summary
Android app (min SDK 24) monitoring a pellets water heater via JSON API, with Compose UI, CSV logging, and Glance widget.

## Tech Stack
- **Language**: Kotlin 2.0.0
- **UI**: Jetpack Compose (Material3), Navigation Compose, ModalNavigationDrawer
- **DI**: Koin 3.5.6
- **Networking**: OkHttp 4.12.0
- **Serialization**: kotlinx.serialization 1.6.3
- **Background**: WorkManager 2.9.1 (periodic + one-time), Foreground Service
- **Widget**: Glance 1.1.1
- **Storage**: EncryptedSharedPreferences, FileProvider
- **Testing**: JUnit 5, MockK, MockWebServer, Mockito

## Architecture Pattern
**MVVM + Repository-lite** - ViewModel holds StateFlows, Worker calls ViewModel, Compose collects StateFlows.

## Entry Points
1. `MainActivity` - Compose navigation host
2. `TemperatureWorker` - WorkManager (10min periodic + manual)
3. `LoggingService` - Foreground service for interval logging
4. `TemperatureWidget` / `TemperatureWidgetReceiver` - Glance widget
5. `PelletsWaterHeaterApplication` - Koin init

## Critical Files (read these first)
1. `app/src/main/java/com/example/pelletswaterheater/viewmodel/TemperatureViewModelNew.kt` - Core logic
2. `app/src/main/java/com/example/pelletswaterheater/utils/MetadataUtils.kt` - JSON parsing
3. `app/src/main/java/com/example/pelletswaterheater/utils/ValueConverter.kt` - Value conversion
4. `app/src/main/java/com/example/pelletswaterheater/utils/TemperatureLogger.kt` - CSV logging
5. `app/src/main/java/com/example/pelletswaterheater/TemperatureWorker.kt` - Background sync
6. `app/src/main/java/com/example/pelletswaterheater/TemperatureUtils.kt` - WorkManager, prefs, utils

## Data Flow Summary
```
Server JSON (sections: system/hk1/pu1/ww1/pe1/circ1)
    → MetadataUtils.parseServerJson() → MetadataJson
User JSON (overrides)
    → MetadataUtils.mergeMetadata() → prioritized metadata
Raw values (from server /all endpoint)
    → ValueConverter.convert() → ConvertedValue(logical, name, unit)
Selections (SharedPreferences)
    → Filter convertedValues for: DataScreen, Widget, CSV Logging
TemperatureWorker (periodic/on-demand)
    → fetchValues() → updateValues() → logValues() (if doLogging)
```

## Key Conventions
- **StateFlow** for all observable state in ViewModels
- **Koin** for all dependencies (single instances)
- **CoroutineScope** with SupervisorJob + Dispatchers.Default in ViewModels
- **SharedPreferences** (encrypted) for user settings + selections
- **FileProvider** for CSV sharing (authority: `${applicationId}.fileprovider`)
- **German locale** for number formatting (comma decimal separator)
- **ISO 8601 UTC** timestamps in CSV (`yyyy-MM-dd'T'HH:mm:ss'Z'`)

## Common Extension Points
| Need | Where to Edit |
|------|--------------|
| New sensor field | Auto-handled by MetadataJsonSerializer |
| New conversion type | `ValueConverter.convert()` |
| New CSV column | `TemperatureLoggerCsvImpl.writeHeaders()` + `buildCsvRow()` |
| New UI screen | Add Composable + route in `MainActivity.AppNavigation()` + `DrawerContent` |
| New widget data | `TemperatureViewModelNew.getWidgetValues()` + `TemperatureWidgetContent()` |
| New setting | `SettingsViewModel` + `SettingsScreen` + `TemperatureUtils` prefs |

## Testing Notes
- Unit tests only (no Robolectric/Instrumentation)
- Mock Context, ContextProvider, ILogger, OkHttpClient via MockK/Mockito
- Run: `./gradlew testDebugUnitTest --info`
- Test resources in `app/src/test/resources/`

## Known Technical Debt
- `SettingsActivity` in manifest but unused
- Widget size config removed but prefs remain
- Logging interval not fully persisted (min 10s hardcoded)
- Some unused methods in `TemperatureViewModelNew` (metadata file management)
- No UI tests

## Debugging Tips
- `Logger` interface routes to `android.util.Log` with tag = class name
- Enable `BuildConfig.DEBUG` StateFlow debugging in `TemperatureViewModelNew`
- Worker logs via `ILogger` with `whichWorker` tag
- CSV files in `context.filesDir/logs/` (accessible via Device File Explorer)