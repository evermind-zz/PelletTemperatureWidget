##############
# app/src/main/CHANGELOG.md
##############
# Changelog

All notable changes to the Pellets Water Heater app are documented here.

## [Unreleased]
### Added
- Planned F3: UI layout with Fragments and navigation drawer.
- License Integration: AboutScreen auto-populates OSS licenses from build-time generated report.
  - Uses `dependency-license-report` Gradle plugin to generate `license-report.json` at build time.
  - `DependencyParser` reads from assets using `kotlinx.serialization`.
  - `SoftwareComponent` data class for license info.
  - All 160+ transitive deps verified Apache 2.0 / BSD-3-Clause (GPLv3 compatible).
  - Documented in specification.md.

## [0.3.0] - 2025-10-27
### Added
- F4: DataStore Migration for Preferences (`PreferencesRepository`, `AppPreferences`, migration from `EncryptedSharedPreferences`).
  - Migrated all preference keys: server URL, widget dimensions, colors, logging interval, gateway fingerprint, metadata filename, selections JSON.
  - Thread-safe async operations via `Flow`.
- F5: Worker and Service Coordination in `NetworkAccess`.
  - `AtomicBoolean` `networkInProgress` flag for thread-safe coordination.
  - `withNetworkLock()` returning `Result<T>` for graceful "network busy" handling.
  - Updated `TemperatureWorker` and `LoggingService` to handle coordination without counting as failure.
- F6: Repository Architecture - Split `TemperatureViewModelNew` into 6 focused repositories.
  - `MetadataRepository` / `MetadataRepositoryImpl` - metadata fetching, caching, merging, file operations.
  - `ValueRepository` / `ValueRepositoryImpl` - value conversion, updates, test data loading.
  - `SelectionRepository` / `SelectionRepositoryImpl` - user selections persisted via `DataStore`.
  - `LoggingRepository` / `LoggingRepositoryImpl` - CSV logging coordination.
  - `NetworkRepository` / `NetworkRepositoryImpl` - HTTP requests with retry logic.
  - `StateRepository` / `StateRepositoryImpl` - system stats (state, error, timestamp).
  - Updated DI module with all repository bindings.
- F7: Donate Screen with PayPal.me browser intent.
  - `DonateScreen` with PayPal, Bitcoin, Patreon options.
  - PayPal opens PayPal.me URL in browser via `LocalUriHandler`.
  - Bitcoin address copyable to clipboard.

### Changed
- Removed legacy `TemperatureViewModel` and `SettingsActivity` (dead code).
- Fixed `NetworkAccess` mutex - moved 3 second rate-limit delay outside lock using `AtomicLong`.
- Fixed CSV Logger header hash instability - replaced `keyOrder.hashCode()` with stable string-based hash.
- Removed `DebugStateFlow` from production code.
- `TemperatureWidget` now uses `widgetValues` flow directly.
- Updated all UI screens (`DataScreen`, `MetadataScreen`, `SettingsScreen`, `StatsBarSection`) to use new repository API.

### Fixed
- `NetworkAccess` mutex no longer sleeps inside lock (prevents blocking other coroutines).
- CSV header generation uses stable hash across JVM restarts.
- `DebugStateFlow` wrapper removed, replaced with plain `MutableStateFlow`.

## [0.2.0] - 2025-06-22
### Added
- F2: Logging feature (`TemperatureLoggerCsvImpl`).
  - Logs converted values to CSV with dynamic section headers (e.g., `Machine parts:,system,,hk1,`).
  - Supports 5MB file rotation and `FileProvider` for sharing.
  - Uses `isoTimestamp()` for min SDK 24 compatibility.
  - Dynamic header generation based on `keyOrder` field counts.
- Koin dependency injection setup (`AppModule.kt`, `PelletsWaterHeaterApplication.kt`).
- Test case for two system values in `TemperatureLoggerTest`.

### Changed
- Renamed `TemperatureLoggerImpl` to `TemperatureLoggerCsvImpl`.
- Updated section header label to `Machine parts:`.
- Added constants for log file naming (`logfileName`, `logfileSuffix`).
- Refined CSV header format and test assertions.

## [0.1.0] - 2025-06-21
### Added
- F1: Metadata parsing feature.
  - `MetadataUtils` for parsing server and user JSON with `kotlinx.serialization`.
  - `ValueConverter` for converting raw values based on metadata attributes.
  - `TemperatureViewModelNew` for managing metadata and converted values.
  - `ContextProvider` for file operations.
  - `ILogger` with `Logger` for Android and console logging.
- Initial unit tests (`ValueConverterTest`, `TemperatureViewModelNewTest`).
- Sample JSON resources (`server.json`, `user.json`).

### Fixed
- Metadata merging to prioritize user fields and drop unmapped server fields.
- Error handling for JSON parsing and file operations.