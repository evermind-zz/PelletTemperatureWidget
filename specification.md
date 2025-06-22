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
  - Convert raw values (e.g., `225` to `22.5°C`).
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
  - Log logical values (e.g., `22.5°C`) based on `ValueSelectionScreen` Checkbox 1.
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
      - Fixed header: “Log”, “Widget”, “Show Data”, “Section+Name”, “Value”, “Section+Name”, “Name”.
      - Columns:
        - Checkbox 1: Enable logging to CSV.
        - Checkbox 2: Show on widget.
        - Checkbox 3: Show on `DataScreen`.
        - Checkbox 4: Show section+name (e.g., `system Außentemperatur`) on widget/`DataScreen`.
        - Text: Logical value (e.g., `22.5°C`), section+name (e.g., `system.L_ambient`), name (e.g., `Außentemperatur`).
      - “Select All” checkboxes across all fields per column.
      - “Save” button persists selections to SharedPreferences.
      - “Refresh” enqueues `TemperatureWorker`.
    - **Logs**: `LazyColumn` for CSV files with “Share” (`getLatestLogUri`) and “Delete”.
    - **Settings**: `TextField` for user JSON (`loadUserMetadata`), “Reset” (`resetMetadata`). Replaces `SettingsFragment`.
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
    | 22.5°C  Außentemperatur      |
    | 24.0°C  hk1.L_roomtemp_act   |
    | [Refresh]        |
    +------------------+
    ```
  - **Value Selection Screen**:
    ```
    +------------------+
    | [☰] Value Selection |
    | Log Widget Data Section+Name Value   Section+Name        Name            |
    | [x] [ ]   [x]  [x]          22.5°C  system.L_ambient    Außentemperatur |
    | [ ] [ ]   [x]  [x]          24.0°C  hk1.L_roomtemp_act  Raumtemperatur  |
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
- **Implementation**: Planned.
- **Status**: In Progress.

## Technical Requirements
- **Min SDK**: 24.
- **Language**: Kotlin 2.0.0.
- **Libraries**:
  - `kotlinx.serialization:1.7.1`
  - `okhttp:4.12.0`
  - `koin-android:3.5.6`
  - `androidx.compose.ui:1.7.0`, `material3:1.3.0`, `navigation-compose:2.7.7`
  - `androidx.work:work-runtime-ktx:2.9.1`, `glance-appwidget:1.1.0`
- **Testing**: JUnit 5, MockK, MockWebServer; manual for F3 on Moto G54.
- **CSV Format**:
  ```
  # File generated by PelletsWaterHeater app
  Machine parts:,system,,hk1,
  ,L_ambient,L_errors,L_roomtemp_act
  ,Außentemperatur,,Raumtemperatur aktuell
  timestamp,°C,,°C
  2025-06-23T00:47:56Z,22.5,0,24.0
  ```

## Development Plan
1. Iteration 1: F1 (Completed).
2. Iteration 2: F2 (Completed).
3. Iteration 3: F3 (In Progress).
4. Iteration 4: UI tests, enhancements.

## Notes
- GitHub: `https://github.com/whatever/pelletswaterheater` (TBD).
- Testing: Moto G54.
- Worker: `TemperatureWorker` fetches data from `http://192.168.178.4:4321/<PASSWORD>/all`.