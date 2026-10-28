# Pellets Water Heater App Specification

## Overview
A native Android app to monitor and log temperature data from a pellets water heater. It fetches metadata, converts sensor data via `TemperatureWorker`, logs to CSV, and provides a Jetpack Compose UI with a navigation drawer and widget.

## Features

### F1: Metadata Parsing
- **Description**: Fetch JSON metadata, merge with user JSON, cache locally.
- **Requirements**:
  - Parse server JSON from `http://192.168.178.4:4321/<PASSWORD>/all?` with sections (e.g., `system`, `hk1`, `pu1`, `ww1`, `pe1`, `circ1`) and fields (e.g., `L_ambient`).
  - Attributes: `value`, `unit`, `factor`, `offset`, `name`, `format`, `min`, `max`, `decimals`.
  - Merge user JSON, prioritize user fields, drop unmapped server fields.
  - Cache in `metadata.json`.
  - Convert raw values (e.g., `225` to `22.5C`).
- **Implementation**: `MetadataUtils`, `ValueConverter`, `TemperatureViewModelNew`, `ContextProvider`. Tests: `ValueConverterTest`, `TemperatureViewModelNewTest`.
- **Status**: Completed (2025-06-21).

### F2: Logging
- **Description**: Log converted data to CSV with dynamic headers.
- **Requirements**:
  - CSV rows with server-ordered fields (`keyOrder`).
  - Headers: section (e.g., `Machine parts:,system,,hk1`), field, name, unit.
  - ISO 8601 timestamps (e.g., `2025-06-23T00:47:56Z`).
  - 5MB rotation (`temp_log.csv`, `temp_log_1.csv`).
  - Share via `FileProvider`.
  - Handle missing values (`,,`).
  - Log logical values (e.g., `22.5C`) based on `ValueSelectionScreen` Checkbox 1.
- **Implementation**: `TemperatureLoggerCsvImpl`, `FileProvider`. Tests: `TemperatureLoggerTest`.
- **Status**: Completed (2025-06-22).

### F3: UI Layout (Compose, Navigation Drawer)
- **Description**: Jetpack Compose UI with `ModalNavigationDrawer`, replacing XML-based `MainActivity` and `SettingsActivity`.
- **Requirements**:
  - Compose UI, no XML.
  - Screens:
    - **Data**: Main screen, shown on launch. `LazyColumn` for selected logical values (value, unit, optional section+name). Replaces `ShowDataFragment`.
    - **Value Selection**: Configures fields for logging, widget, `DataScreen`, and display style. Replaces `ValueSelectionFragment`. Includes:
      - `LazyColumn` with dynamic sections (e.g., `system`, `hk1`, `pu1`, `ww1`, `pe1`, `circ1`), always expanded.
      - Fixed header: "Log", "Widget", "Show Data", "Section+Name", "Value", "Section+Name", "Name".
      - Columns:
        - Checkbox 1: Enable logging to CSV.
        - Checkbox 2: Show on widget.
        - Checkbox 3: Show on `DataScreen`.
        - Checkbox 4: Show section+name (e.g., `system Außentemperatur`) on widget/`DataScreen`.
        - Text: Logical value (e.g., `22.5C`), section+name (e.g., `system.L_ambient`), name (e.g., `Außentemperatur`).
      - "Select All" checkboxes across all fields per column.
      - "Save" button persists selections to `SharedPreferences`.
      - "Refresh" enqueues `TemperatureWorker`.
    - **Logs**: `LazyColumn` for CSV files with "Share" (`getLatestLogUri`) and "Delete".
    - **Settings**: `TextField` for user JSON (`loadUserMetadata`), "Reset" (`resetMetadata`). Replaces `SettingsFragment`.
    - **About**: App name, version, licenses.
    - **Donate**: PayPal link (`https://paypal.me/youraccount`).
  - Widget: `Glance` widget with scrollable `LazyColumn`, showing fields selected via Checkbox 2, `value` + `unit` if Checkbox 4 unchecked, remembers top visible item.
  - Min SDK 24, Material 3, Compose Navigation.
  - Checkbox selections filter `TemperatureViewModelNew.convertedValues` Flow for `DataScreen`, widget, and logs.
- **Wireframes**:
  - **Data Screen**:
    ```
    +------------------+
    | [☰] Data        |
    | LazyColumn:      |
    | 22.5C  Außentemperatur      |
    | 24.0C  hk1.L_roomtemp_act   |
    | [Refresh]        |
    +------------------+
    ```
  - **Value Selection Screen**:
    ```
    +------------------+
    | [☰] Value Selection |
    | Log Widget Data Section+Name Value   Section+Name        Name            |
    | [x] [ ]   [x]  [x]          22.5C  system.L_ambient    Außentemperatur |
    | [ ] [ ]   [x]  [x]          24.0C  hk1.L_roomtemp_act  Raumtemperatur  |
    | [Refresh][Save]  |
    +------------------+
    ```
  - **Logs Screen**:
    ```
    +------------------+
    | [☰] Logs        |
    | LazyColumn:      |
    | temp_log.csv [Share][Delete] |
    | temp_log_1.csv [Share][Delete] |
    +------------------+
    ```
  - **Settings Screen**:
    ```
    +------------------+
    | [☰] Settings    |
    | User JSON:       |
    | [TextField]      |
    | [Apply][Reset]   |
    +------------------+
    ```
  - **About Screen**:
    ```
    +------------------+
    | [☰] About       |
    | Pellets Water Heater |
    | v0.2.0           |
    | Licenses: [List] |
    +------------------+
    ```
  - **Donate Screen**:
    ```
    +------------------+
    | [☰] Donate      |
    | Support us!      |
    | [PayPal Button]  |
    +------------------+
    ```
- **Implementation**: In Progress.
- **Status**: In Progress (tests not yet run).

### F4: Preferences Migration to DataStore
- **Description**: Migrate from `EncryptedSharedPreferences` to Jetpack DataStore Preferences for all user settings.
- **Requirements**:
  - Migrate existing preferences: server URL, widget dimensions, colors, logging interval, gateway fingerprint, metadata filename, selections JSON.
  - Provide `PreferencesRepository` interface with DataStore implementation.
  - Thread-safe, async operations via `Flow`.
  - Backward compatible migration.
- **Implementation**: `PreferencesRepository`, `PreferencesRepositoryImpl`, `AppPreferences` (DataStore schema), `AppPreferencesSerializer` (migration from `EncryptedSharedPreferences`). Updated `TemperatureUtils`, `SettingsViewModel`, `TemperatureViewModelNew`, `TemperatureWidget.RefreshWidgetAction`, DI module.
- **Status**: Completed (2025-10-27).

### F5: Worker and Service Coordination
- **Description**: Coordinate network access between `TemperatureWorker` (periodic) and `LoggingService` (foreground) to prevent concurrent requests.
- **Requirements**:
  - Single coordination flag in `NetworkAccess` singleton.
  - `AtomicBoolean` `networkInProgress` for thread-safe access.
  - `withNetworkLock()` returning `Result<T>` for graceful handling.
  - "network busy" signal when lock unavailable.
  - Worker and Service handle "network busy" without counting as failure.
- **Implementation**: `NetworkAccess` with `AtomicBoolean`, `getDataFromServerResult()`, `withNetworkLock()`, updated `fetchMetadata()`, `fetchValues()`, `getDataFromNetworkWithRetry()`, `LoggingService.startLogging()`, `TemperatureWorker.doWork()`.
- **Status**: Completed (2025-10-27).

### F6: ViewModel Split into Repository Architecture
- **Description**: Decompose `TemperatureViewModelNew` into focused repositories with clean separation of concerns.
- **Requirements**:
  - Separate repositories for each domain: Metadata, Values, Selections, Logging, Network, State.
  - Repository interfaces for testability.
  - Dependency injection via Koin for all repositories.
  - `TemperatureViewModelNew` delegates to repositories, no inline business logic.
- **Implementation**:
  - `MetadataRepository` / `MetadataRepositoryImpl` - metadata fetching, caching, merging, file operations
  - `ValueRepository` / `ValueRepositoryImpl` - value conversion, updates, test data loading
  - `SelectionRepository` / `SelectionRepositoryImpl` - user selections (log, widget, showData, showSectionName) persisted via DataStore
  - `LoggingRepository` / `LoggingRepositoryImpl` - CSV logging coordination
  - `NetworkRepository` / `NetworkRepositoryImpl` - network operations with retry logic
  - `StateRepository` / `StateRepositoryImpl` - system stats (state, error, timestamp)
  - Updated DI module (`AppModule.kt`) with all repository bindings
  - Updated UI screens (`DataScreen`, `MetadataScreen`, `SettingsScreen`, `StatsBarSection`) to use new API
  - `TemperatureWidget` updated to use `widgetValues` flow directly
- **Status**: Completed (2025-10-27).

### F7: Donate Screen with PayPal.me
- **Description**: Donation screen with browser intent to PayPal.me URL.
- **Requirements**:
  - `DonateScreen` composable with donation options (PayPal, Bitcoin, Patreon).
  - PayPal option opens PayPal.me URL in browser via `LocalUriHandler`.
  - Bitcoin address copyable to clipboard.
  - Patreon link opens in browser.
- **Implementation**: `DonateScreen.kt`, `DonateComponent.kt`, `DrawerContent.kt` navigation entry.
- **Status**: Completed (2025-10-27).

## Repository Architecture

The app follows a clean repository pattern:

```
UI (Compose Screens)
    |
    v
ViewModels (TemperatureViewModelNew, SettingsViewModel)
    |
    v
Repositories (Interfaces + Implementations)
    |-- MetadataRepository    -> Server metadata, caching, user overrides, file operations
    |-- ValueRepository       -> Value conversion, updates, test data
    |-- SelectionRepository   -> User checkbox selections (log/widget/data/section)
    |-- LoggingRepository     -> CSV logging coordination
    |-- NetworkRepository     -> HTTP requests with retry and rate limiting
    |-- StateRepository       -> System stats (state, error, timestamp)
    |
    v
Data Sources
    |-- NetworkAccess         -> OkHttp with mutex and coordination flag
    |-- PreferencesRepository -> DataStore Preferences (migrated from EncryptedSharedPreferences)
    |-- TemperatureLogger     -> CSV file I/O with rotation
    |-- ContextProvider       -> File system, metadata files
    |-- FileProvider          -> Content URIs for sharing
```

## Technical Requirements
- **Min SDK**: 24.
- **Language**: Kotlin 2.0.0.
- **Libraries**:
  - `kotlinx.serialization`:1.7.1
  - `okhttp`:4.12.0
  - `koin-android`:3.5.6
  - `androidx.compose.ui`:1.7.0, `material3`:1.3.0, `navigation-compose`:2.7.7
  - `androidx.work:work-runtime-ktx`:2.9.1, `glance-appwidget`:1.1.0
  - `androidx.datastore:datastore-preferences`:1.1.1
- **Testing**: JUnit 5, MockK, MockWebServer; manual for F3 on Moto G54.
- **CSV Format**:
  ```
  # File generated by PelletsWaterHeater app
  Machine parts:,system,,hk1,
  ,L_ambient,L_errors,L_roomtemp_act
  ,Außentemperatur,,Raumtemperatur aktuell
  timestamp,C,,C
  2025-06-23T00:47:56Z,22.5,0,24.0
  ```

## Development Plan
1. Iteration 1: F1 (Completed).
2. Iteration 2: F2 (Completed).
3. Iteration 3: F3 (In Progress - tests not yet run).
4. Iteration 4: F4 DataStore Migration (Completed).
5. Iteration 5: F5 Worker+Service Coordination (Completed).
6. Iteration 6: F6 Repository Architecture (Completed).
7. Iteration 7: F7 Donate Screen (Completed).
8. Iteration 8: UI tests, enhancements.

## Open Source License Compliance

The app includes an **About** screen that automatically displays all open source licenses used by the project. This is handled at build time using the `com.github.jk1.dependency-license-report` Gradle plugin.

### How It Works
1. **Build-time generation**: The Gradle plugin runs during `assemble` / `licenseReport` task and generates `license-report.json` into `src/main/assets/`.
2. **APK packaging**: Assets are automatically included in the APK.
3. **Runtime parsing**: `DependencyParser` reads `license-report.json` from assets using `kotlinx.serialization` and converts it to `List<SoftwareComponent>`.
4. **Display**: `AboutScreen` renders the component list with name, version, license, and link.

### Regenerating Licenses
When dependencies change (add/remove/update):
1. Update dependencies in `build.gradle.kts`
2. Run `./gradlew licenseReport` (or just `./gradlew assemble`)
3. The plugin regenerates `src/main/assets/license-report.json` with current dependencies
4. Next build includes updated licenses automatically

No manual JSON editing or separate scripts needed. The Gradle plugin is the single source of truth.

### License Compatibility
All 160+ transitive dependencies are licensed under **Apache 2.0** or **BSD-3-Clause**, which are compatible with the project's **GPLv3** license.

### Implementation Files
- `build.gradle.kts` - Plugin configuration (`licenseReport` block)
- `utils/SoftwareComponent.kt` - Data class for license info
- `utils/DependencyParser.kt` - Asset JSON parser using kotlinx.serialization
- `ui/AboutScreen.kt` - Composable displaying the license list

## Implementation Details

### API / Network Details
- **Base URL Format**: `http://192.168.178.4:4321/<PASSWORD>/all?` (trailing `?` required by gateway)
- **Gateway Fingerprint**: Optional SHA-256 fingerprint stored in DataStore (`gatewayFingerprint` key) for TLS pinning. If set, `NetworkAccess` verifies server certificate against it.
- **Retry Logic**: `NetworkRepository.fetchDataWithRetry()` implements exponential backoff:
  - Max attempts: configurable (default 3)
  - Initial delay: 1 second
  - Multiplier: 2x per attempt
  - Timeout per request: 10 seconds (connect) / 15 seconds (read)
- **Rate Limiting**: `NetworkAccess` enforces 3-second minimum interval between requests using `AtomicLong lastAccessTime`.
- **Charset Detection**: Response body decoded with UTF-8 by default. Falls back to ISO-8859-1 if UTF-8 decoding fails (handles legacy gateway responses).
- **Error Handling**: Returns `Result<T>`; "network busy" is a special sentinel when coordination lock is unavailable (not a true error).
- **Endpoint Paths**:
  - Metadata: `http://<host>:<port>/<password>/all?`
  - Values: Same endpoint returns both metadata and current values in single response

### Worker / Service Coordination
- **WiFi Subnet Check**: `TemperatureWorker.doWork()` and `LoggingService` verify device is on same subnet (`192.168.178.x`) before attempting network calls. If not, skips fetch and returns success (not failure) to avoid unnecessary retries.
- **Logging Interval**: Configurable via DataStore (`loggingIntervalMs` key), minimum 10 seconds (10000ms). Default 300000ms (5 minutes). `LoggingService` runs as foreground service with periodic timer.
- **Foreground Notification**: `LoggingService` shows persistent notification (channel `logging_service`, importance `LOW`) with:
  - Title: "Pellets Water Heater Logging"
  - Text: "Logging temperature data..."
  - Action: Tap opens app
  - Stop action stops the service
- **Coordination Mechanism**: Single `AtomicBoolean networkInProgress` in `NetworkAccess` singleton. Both Worker and Service call `withNetworkLock()` which:
  - Atomically sets flag to true if false, returns `Result.success(block())`
  - If already true, returns `Result.failure(Exception("network busy"))`
  - Caller handles "network busy" as non-fatal (logs, skips, schedules next cycle)

### CSV / Logging Format Details
- **Machine Parts Header Row**: First row identifies sections (e.g., `Machine parts:,system,,hk1,` means columns 2-3 are `system`, column 4 is `hk1`)
- **Field Names Row**: Second row lists JSON field keys per section (e.g., `,L_ambient,L_errors,L_roomtemp_act`)
- **Display Names Row**: Third row shows human-readable names (e.g., `,Außentemperatur,,Raumtemperatur aktuell`)
- **Units Row**: Fourth row shows units (e.g., `timestamp,°C,,°C`)
- **Data Rows**: ISO 8601 timestamp + logical values (e.g., `2025-06-23T00:47:56Z,22.5,0,24.0`)
- **Key Order**: `keyOrder` list from server response determines column order. Stored hash (`storedOrderHash` in DataStore) detects column changes for header rewriting.
- **5MB Rotation**: `TemperatureLoggerCsvImpl` rotates at 5MB:
  - Current: `temp_log.csv`
  - Rotated: `temp_log_1.csv`, `temp_log_2.csv`, etc. (max 5 files)
  - Rotation happens before write if current file + new row > 5MB
- **Missing Values**: Empty cells (`,,`) for unavailable fields
- **Test Data File**: `test-values.json` in `src/test/resources/` (unit tests) and `filesDir` (runtime) contains sample raw sensor data for offline testing

### UI / Selection Details
- **Four Checkboxes per Field** (in `ValueSelectionScreen`):
  1. **Log** - Include in CSV logging (`Selection.log`)
  2. **Widget** - Show in Glance widget (`Selection.widget`)
  3. **Show Data** - Display on DataScreen (`Selection.showData`)
  4. **Section+Name** - Show "section.name" prefix (e.g., `system Außentemperatur`) on widget/DataScreen (`Selection.showSectionName`)
- **Select All**: Header row has "Select All" checkbox per column toggles all visible fields
- **Glance Widget**:
  - Scrollable `LazyColumn` (vertical)
  - Shows fields where `Selection.widget == true`
  - Format: `value unit` (e.g., `22.5 °C`) if `showSectionName == false`, else `section value unit`
  - Remembers top visible item across updates via `GlanceState`
  - Refresh: `RefreshWidgetAction` broadcasts `TemperatureWorker` intent
- **Default Colors** (DataStore keys):
  - Background: `#F3E5F5` (light purple)
  - Text: `#000000` (black)
  - Stored as hex strings with alpha (e.g., `FFF3E5F5`)

### ValueConverter Logic
- **Input**: Raw string value from server (e.g., `"225"`), `MetadataEntry.Field` with `factor`, `offset`, `format`, `decimals`, `unit`
- **String Passthrough**: If `format == "string"` or `factor == null`, returns raw value as-is (labels, status strings)
- **Format Mapping**:
  - `"int"`: Parse as integer, apply `(raw * factor) + offset`, format as integer
  - `"float"` / `"double"`: Parse as double, apply conversion, format with `decimals` precision
  - Default (numeric): Same as float
- **Localized Separator**: Uses `Locale.getDefault()` for decimal separator (`,` in German, `.` in US)
- **Decimals Handling**: `String.format(Locale.getDefault(), "%." + decimals + "f", value)`
- **Unit Appending**: Appends `unit` from metadata (e.g., `°C`, `bar`) with space prefix
- **Edge Cases**:
  - Empty/null raw value → returns empty string
  - Non-numeric raw value with numeric format → returns raw value
  - Factor = 0 → treated as 1 (no scaling)

### Offline-First Metadata
- **Default Metadata**: `metadata.json` (root project file, ~14KB) embedded in APK at `src/main/assets/metadata.json`
- **Fallback Chain**:
  1. Try server fetch (`TemperatureWorker` / `MetadataRepository`)
  2. On failure, load cached `metadata.json` from internal storage (`ContextProvider`)
  3. If no cache, parse embedded asset `metadata.json` as last resort
  4. User JSON overrides applied on top (via `SettingsScreen` → `MetadataRepository.loadUserMetadata()`)
- **Embedding**: Add `metadata.json` to `app/src/main/assets/` and ensure Gradle copies it (`assets` source set)

### Test Resources
- `testdata.json` location: `app/src/test/resources/testdata.json` (unit tests)
- `server.json`: `app/src/test/resources/server.json` (mock server response)
- `user.json`: `app/src/test/resources/user.json` (mock user override)
- Used by: `ValueConverterTest`, `TemperatureLoggerTest`, `TemperatureViewModelNewTest`

## Notes
- GitHub: https://github.com/whatever/pelletswaterheater (TBD).
- Testing: Moto G54.
- Worker: `TemperatureWorker` fetches data from `http://192.168.178.4:4321/<PASSWORD>/all`.
- License: GPLv3 or later.