# NetSense: Mobile Communication Data Collection and Analysis

NetSense is an Android application for collecting and analyzing cellular network data from LTE, 5G (NR), and legacy networks (GSM, WCDMA, CDMA, TD-SCDMA). The app enables researchers and network engineers to monitor cell tower information, detect potential fake base stations, and analyze mobile network performance.

## Features

- **Multi-Network Support**: Collects data from LTE, 5G NR, GSM, WCDMA, CDMA, and TD-SCDMA networks
- **Real-Time Monitoring**: Displays live cell tower information with automatic 3-second refresh intervals
- **Cell Classification**: Categorizes cells as Serving (primary), Secondary (carrier aggregation), or Neighboring
- **Background Collection**: Foreground service continues data collection when the app is backgrounded
- **Data Export**: Export collected data to CSV format for offline analysis
- **Comprehensive Metrics**: Captures signal strength, frequency, bandwidth, cell IDs, and network identifiers

## Prerequisites

- **Android Studio**: Arctic Fox or later recommended
- **Kotlin**: 1.9.24+
- **Android Device**: Physical device running Android 7.0 (API 24) or later with cellular connectivity
- **Permissions**: The app requires the following permissions:
  - `ACCESS_FINE_LOCATION` (required for Android 10+)
  - `ACCESS_COARSE_LOCATION`
  - `READ_PHONE_STATE`
  - `ACCESS_BACKGROUND_LOCATION` (Android 10+ for background collection)
  - `POST_NOTIFICATIONS` (Android 13+ for foreground service notification)

## Setup and Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   ```
2. Open the project in Android Studio.
3. Sync the project to download dependencies.
4. Build and run the app on a physical Android device (emulators may not support cellular data collection).

## Network Information Collected

The app collects the following network metrics:

| **Network Type** | **Parameter** | **Description** |
| ---------------- | ------------- | --------------- |
| **LTE** | RSRP (Reference Signal Received Power) | Measures signal strength |
| | RSRQ (Reference Signal Received Quality) | Measures signal quality |
| | SINR (Signal-to-Interference-plus-Noise Ratio) | Measures signal-to-noise ratio |
| | ECI, eNB, CID, TAC, PCI | Cell identification parameters |
| | Frequency, Bandwidth | Operating frequency and bandwidth |
| **5G NR** | SS-RSRP, SS-RSRQ, SS-SINR | Synchronization Signal metrics |
| | CSI-RSRP, CSI-RSRQ, CSI-SINR | Channel State Information metrics |
| | NR-ARFCN | New Radio frequency channel number |
| | PCI (Physical Cell ID) | Cell tower identifier |
| **General** | MCC (Mobile Country Code) | Country identifier |
| | MNC (Mobile Network Code) | Network operator identifier |
| | Cell ID | Unique cell tower identifier |
| | Connection Status | Serving, Secondary, or Neighboring |
| | Signal Strength (RSSI) | Overall signal strength |

## Architecture

The app follows MVVM architecture with the following components:

- **MainActivity**: Single-activity host managing permissions and fragment navigation
- **BackgroundService**: Foreground service for continuous data collection
- **CellInfoFragment**: Real-time cell data visualization in a scrollable list
- **CellLoggerFragment**: Tabular view with export and data management features
- **CellInfoViewModel**: Core data collection logic using NetMonster library with Android API fallback
- **Room Database**: Persistent storage with CellInfoEntity and CellInfoDao
- **CellDataCache**: In-memory cache for real-time display performance

## How It Works

1. The app uses the [NetMonster library](https://github.com/mroczis/netmonster-core) as the primary data source, with fallback to Android's native TelephonyManager APIs
2. Cell data is collected every 3 seconds using a polling-based approach
3. Data is cached in memory for real-time display and can be persisted to SQLite via Room
4. A foreground service ensures continuous collection even when the app is in the background
5. Users can export collected data to CSV format for offline analysis

## Usage

1. Launch the app on a physical Android device
2. Grant the required permissions (location, phone state, notifications)
3. The app will automatically start collecting cell data
4. **Cell Info Tab**: View real-time cell tower information in a scrollable list
   - Serving cells (primary connection) are displayed with their associated neighboring cells
   - Data refreshes automatically every 3 seconds
5. **Cell Logger Tab**: View collected data in a table format
   - Use **Export Data** to save data as CSV to the Documents folder
   - Use **Delete Old Data** to clear all cached cell information
6. The app continues collecting data in the background via a foreground service notification

## Tech Stack

- **Language**: Kotlin 1.9.24
- **Min SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK**: API 36 (Android 15)
- **Architecture**: MVVM with ViewBinding
- **Database**: Room 2.6.1
- **UI**: Material Design 3, RecyclerView, Navigation Component
- **Lifecycle**: ViewModel, LiveData 2.8.7

## Credits

This project uses the [NetMonster Library](https://github.com/mroczis/netmonster-core) for collecting and processing cell tower data. Special thanks to Michal Mroček and the NetMonster team for providing a comprehensive tool for network information analysis.

The NetMonster Library is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

1. Fork the repository
2. Create a new branch:
   ```bash
   git checkout -b feature-name
   ```
3. Commit your changes and push the branch:
   ```bash
   git push origin feature-name
   ```
4. Submit a pull request

## License

This project is licensed under the MIT License.

---

For any issues or feature requests, please open an issue in the repository.

