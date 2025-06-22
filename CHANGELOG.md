##############
# app/src/main/CHANGELOG.md
##############
# Changelog

All notable changes to the Pellets Water Heater app are documented here.

## [Unreleased]
### Added
- Planned F3: UI layout with Fragments and navigation drawer.

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