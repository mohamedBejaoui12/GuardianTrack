# GuardianTrack

GuardianTrack is an Android safety monitoring app that detects possible falls, records incidents locally, and syncs them to a remote API when network is available.

The app is built around an offline-first flow:
- incidents are always saved to Room first,
- immediate API sync is attempted,
- failed uploads are retried automatically by WorkManager.

## What This Project Does

- Monitors device accelerometer data in a foreground service to detect possible falls.
- Supports manual emergency alerts from the dashboard.
- Records incidents with timestamp, type, GPS coordinates, and sync status.
- Sends emergency SMS alerts (simulation mode enabled by default for safer testing).
- Shows high-priority incident notifications.
- Restarts monitoring after device reboot.
- Captures low-battery events via broadcast receiver and stores them as incidents.
- Lets users review history, swipe to delete, and export incidents to CSV.
- Exposes emergency contacts through a protected ContentProvider.

## Core Features

### 1) Real-Time Surveillance Service
- `SurveillanceService` runs as a foreground service.
- Uses a two-phase fall detection logic:
	- free-fall window (low acceleration), then
	- impact threshold check in a short time window.
- On detected fall:
	- reads last known location,
	- stores incident,
	- attempts sync,
	- sends SMS alert,
	- posts urgent notification.

### 2) Incident Management
- Room database stores incident records (`FALL`, `MANUAL`, `BATTERY`).
- History screen displays all incidents with sync state.
- Swipe-to-delete supported.
- CSV export writes to public Documents using MediaStore.

### 3) Offline-First Sync
- Retrofit API call is attempted after each new incident.
- Unsynced incidents are retried by `SyncWorker` when network is available.
- Work is enqueued uniquely to avoid duplicate sync jobs.

### 4) Settings & Preferences
- Fall detection threshold can be tuned (seek bar).
- SMS simulation mode is configurable.
- Emergency phone number can be saved.
- Preferences use DataStore.
- Sensitive data copy is stored using `EncryptedSharedPreferences`.

### 5) System Event Handling
- `BootReceiver` schedules surveillance restart through WorkManager after reboot.
- `BatteryReceiver` schedules battery incident recording and shows alert notification.

### 6) Protected Data Sharing
- `EmergencyContactProvider` exposes contacts through:
	- `content://com.guardian.track.provider/emergency_contacts`
	- `content://com.guardian.track.provider/emergency_contacts/{id}`
- Read access is protected by signature-level custom permission.

## Tech Stack

- Language: Kotlin
- Build: Gradle Kotlin DSL
- Min SDK: 26
- Target/Compile SDK: 35
- Architecture: MVVM + Repository + DI
- DI: Hilt
- Local DB: Room
- Networking: Retrofit + OkHttp
- Async: Kotlin Coroutines + Flow
- Background Jobs: WorkManager
- Preferences: DataStore
- Secure Storage: AndroidX Security Crypto (`EncryptedSharedPreferences`)
- Location: Fused Location Provider
- UI: XML Views + ViewBinding (+ Compose dependencies available)

## Project Architecture

- `ui/`:
	- `DashboardFragment`: manual alert + incident summary
	- `HistoryFragment`: incident list + CSV export
	- `SettingsFragment`: threshold, simulation mode, emergency number
- `service/`:
	- foreground monitoring service with accelerometer processing
- `repository/`:
	- incident and contact repositories (single source of truth)
- `data/local/`:
	- Room database, entities, DAO interfaces
- `data/remote/`:
	- Retrofit API and DTOs
- `worker/`:
	- sync, boot restart, battery incident workers
- `receiver/`:
	- battery low and boot completed receivers
- `provider/`:
	- emergency contacts ContentProvider
- `di/`:
	- Hilt modules for DB, network, location, WorkManager

## Permissions Used

- Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- Foreground service: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_HEALTH`
- Sensor/health support: `ACTIVITY_RECOGNITION`, `HIGH_SAMPLING_RATE_SENSORS`
- SMS: `SEND_SMS`
- Notifications: `POST_NOTIFICATIONS`
- Device events: `RECEIVE_BOOT_COMPLETED`
- Network: `INTERNET`, `ACCESS_NETWORK_STATE`
- Vibration: `VIBRATE`

## API Configuration

Base URL is injected into `BuildConfig.API_BASE_URL` from `local.properties`.

Add this to your `local.properties`:

```properties
api.base.url=https://your-mock-api.mockapi.io/api/v1/
```

Expected MockAPI resources used by the app:
- `GET /incidents`
- `POST /incidents`
- `DELETE /incidents/{id}`
- `GET /emergency_contacts`
- `POST /emergency_contacts`
- `DELETE /emergency_contacts/{id}`

The app keeps data locally in Room first, then mirrors incidents and contacts to MockAPI when network calls succeed.

## How To Run

1. Open project in Android Studio.
2. Make sure SDK 35 and JDK 17 are available.
3. Set `api.base.url` in `local.properties`.
4. Build and run on a physical device (recommended for sensors/SMS/location).
5. Grant runtime permissions when prompted.

## Build Commands

```bash
./gradlew assembleDebug
./gradlew test
```

## Notes

- SMS simulation mode defaults to ON to avoid accidental real SMS during testing.
- Foreground service + sensor behavior can vary on emulators; physical device testing is recommended.
- Database migration is currently destructive fallback for development.

## Future Improvements

- Add proper Room migration strategy.
- Add unit/UI/instrumented tests.
- Add explicit settings to control service auto-start behavior.
- Add stronger production hardening around provider threading and security audits.
