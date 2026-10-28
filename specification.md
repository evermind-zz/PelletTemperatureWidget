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

## Notes
- GitHub: https://github.com/whatever/pelletswaterheater (TBD).
- Testing: Moto G54.
- Worker: `TemperatureWorker` fetches data from `http://192.168.178.4:4321/<PASSWORD>/all`.
- License: GPLv3 or later.