##############
# app/src/main/README.md
##############
# Pellets Water Heater

A native Android application for monitoring and logging temperature data from a pellets water heater system. The app fetches metadata from a server, allows user overrides, converts raw sensor data, logs it to CSV files, and provides a user-friendly interface for data visualization and sharing.

## Features

### F1: Metadata Parsing
Fetches JSON metadata from a server, merges with user-provided JSON, and caches locally. Supports dynamic sections (e.g., system, hk1) and field attributes (e.g., factor, unit).

### F2: Logging
Logs converted sensor data to CSV files with dynamic section headers, supports 5MB file rotation, and shares logs via FileProvider. Uses server-ordered fields and ISO 8601 timestamps.

### F3: UI Layout (Compose, Navigation Drawer)
Jetpack Compose UI with ModalNavigationDrawer. Screens: Data, Value Selection, Logs, Settings, About, Donate. Glance widget with scrollable LazyColumn. (In Progress - tests not yet run)

### F4: Preferences Migration to DataStore
Migrated from EncryptedSharedPreferences to Jetpack DataStore Preferences for all user settings. Thread-safe async operations via Flow.

### F5: Worker and Service Coordination
AtomicBoolean coordination flag in NetworkAccess singleton prevents concurrent requests between TemperatureWorker and LoggingService. Graceful "network busy" handling.

### F6: Repository Architecture
Clean separation into 6 repositories (Metadata, Values, Selections, Logging, Network, State) with interfaces for testability. Dependency injection via Koin.

### F7: Donate Screen
Donation options including PayPal.me link that opens in browser, Bitcoin address copy, and Patreon link.

### License Integration
About screen automatically displays all open source licenses from build-time generated report using the `dependency-license-report` Gradle plugin. All transitive dependencies verified Apache 2.0 / BSD-3-Clause (GPLv3 compatible).

- Dependency Injection: Koin for modular dependency management.
- Min SDK: 24 (Android 7.0), no desugaring required.
- Tech Stack: Kotlin 2.0.0, kotlinx.serialization 1.7.1, OkHttp 4.12.0, Koin 3.5.6, AndroidX, Jetpack DataStore, Jetpack Compose, Glance AppWidget.

## Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/whatever/pelletswaterheater
   ```
2. Open in Android Studio.
3. Sync Gradle:
   ```bash
   ./gradlew sync
   ```
4. Build and run on a device or emulator (min SDK 24).

## Usage
- **Fetch Metadata**: Call TemperatureViewModelNew.fetchMetadata(serverUrl) with your server URL.
- **Load User Metadata**: Use loadUserMetadata(jsonString) to override server metadata.
- **Log Data**: Pass raw data to updateValues(rawData) to convert and log to CSV.
- **Share Logs**: Use TemperatureLogger.getLatestLogUri(context) to share the latest CSV.
- **Donate**: Open Donate screen from navigation drawer, tap PayPal to open in browser.

## Testing
Run unit tests:
```bash
./gradlew testDebugUnitTest --info
```

Tests cover:
- ValueConverterTest: Metadata conversion logic.
- TemperatureLoggerTest: CSV logging, dynamic headers, file rotation.
- TemperatureViewModelNewTest: Metadata fetching, merging, and value updates.

CI runs on GitHub Actions (test, lint, assembleDebug).

## Contributing
- Report issues or submit PRs at https://github.com/whatever/pelletswaterheater.
- Follow Kotlin coding conventions and include unit tests for new features.

## License
GPLv3 or later. See LICENSE for details.