# GuardianTrack — Personal Security App

Android security app for ISET Rades — Advanced Native Android Development (Kotlin).

## Architecture

MVVM + Repository pattern + Hilt DI + Coroutines/Flow

```
UI (Fragments) → ViewModels → Repositories → Room / Retrofit / DataStore
```

## Setup Instructions

### 1. Clone and open in Android Studio

```bash
git clone <your-repo-url>
```
Open Android Studio → Open → select the `GuardianTrack` folder.

### 2. Configure local.properties

Create `local.properties` in the **root** of the project (never committed to git):

```properties
sdk.dir=/home/YOUR_USERNAME/Android/Sdk
api.base.url=https://YOUR_PROJECT.mockapi.io/api/v1/
```

**Setting up MockAPI (free):**
1. Go to [mockapi.io](https://mockapi.io) and create a free project
2. Add a resource called `incidents` with schema:
   - `timestamp` (Number)
   - `type` (String)
   - `latitude` (Number)
   - `longitude` (Number)
3. Copy the base URL (e.g. `https://abc123.mockapi.io/api/v1/`) into `local.properties`

### 3. Build and run

```bash
./gradlew assembleDebug
```

Or press **Run** in Android Studio.

## Required Permissions (all requested at runtime)

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS coordinates for incidents |
| `SEND_SMS` | Emergency SMS (simulation mode ON by default) |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `RECEIVE_BOOT_COMPLETED` | Restart service after reboot |

## Key Files

| File | Purpose |
|---|---|
| `SurveillanceService.kt` | Foreground service + fall detection algorithm |
| `Receivers.kt` | Battery + Boot BroadcastReceivers |
| `Workers.kt` | WorkManager workers (sync, battery, boot) |
| `EmergencyContactProvider.kt` | ContentProvider (signature-protected) |
| `Repositories.kt` | Offline-first data layer |
| `PreferencesManager.kt` | DataStore preferences |
| `SecureStorage.kt` | EncryptedSharedPreferences |

## Security Notes

- `local.properties` is excluded from version control (see `.gitignore`)
- The API base URL is injected via `BuildConfig` — not hardcoded
- Emergency phone number is stored encrypted via `EncryptedSharedPreferences`
- `EmergencyContactProvider` is protected by a `signature`-level custom permission
- SMS simulation mode is **ON by default** to prevent accidental real SMS

## SMS Simulation Mode

The app ships with SMS simulation **enabled**. In this mode:
- No real SMS is sent
- A local notification is shown instead
- The would-be message is logged to Logcat

To test real SMS: go to Settings → disable "SMS Simulation Mode" (and ensure `SEND_SMS` permission is granted).
