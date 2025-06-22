##############
# app/src/main/README.md
##############
# Pellets Water Heater

A native Android application for monitoring and logging temperature data from a pellets water heater system. The app fetches metadata from a server, allows user overrides, converts raw sensor data, logs it to CSV files, and provides a user-friendly interface for data visualization and sharing.

## Features
- **Metadata Parsing (F1)**: Fetches JSON metadata from a server, merges with user-provided JSON, and caches locally. Supports dynamic sections (e.g., `system`, `hk1`) and field attributes (e.g., `factor`, `unit`).
- **Logging (F2)**: Logs converted sensor data to CSV files with dynamic section headers, supports 5MB file rotation, and shares logs via `FileProvider`. Uses server-ordered fields and ISO 8601 timestamps.
- **UI Layout (F3, In Progress)**: Planned Fragments with a navigation drawer for data display and log management.
- **Dependency Injection**: Uses Koin for modular dependency management.
- **Min SDK**: 24 (Android 7.0), no desugaring required.
- **Tech Stack**: Kotlin 2.0.0, `kotlinx.serialization` 1.7.1, OkHttp 4.12.0, Koin 3.5.6, AndroidX.

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
4. Update `app/src/main/java/com/example/pelletswaterheater/utils/TemperatureLogger.kt` with your GitHub repo URL.
5. Build and run on a device or emulator (min SDK 24).

## Usage
- **Fetch Metadata**: Call `TemperatureViewModelNew.fetchMetadata(serverUrl)` with your server URL.
- **Load User Metadata**: Use `loadUserMetadata(jsonString)` to override server metadata.
- **Log Data**: Pass raw data to `updateValues(rawData)` to convert and log to CSV.
- **Share Logs**: Use `TemperatureLogger.getLatestLogUri(context)` to share the latest CSV.
- **UI (Upcoming)**: Interact via Fragments and a navigation drawer (F3).

## Testing
Run unit tests:
```bash
./gradlew testDebugUnitTest --info
```
Tests cover:
- `ValueConverterTest`: Metadata conversion logic.
- `TemperatureLoggerTest`: CSV logging, dynamic headers, file rotation.
- `TemperatureViewModelNewTest`: Metadata fetching, merging, and value updates.

## Contributing
- Report issues or submit PRs at [https://github.com/whatever/pelletswaterheater](https://github.com/whatever/pelletswaterheater).
- Follow Kotlin coding conventions and include unit tests for new features.

## License
GPLv3 License. See [LICENSE](LICENSE) for details.
