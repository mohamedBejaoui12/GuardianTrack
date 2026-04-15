# PDF Requirements ↔ Code Mapping Quick Reference

## 🎯 Essential Components & Where to Find Them

### 2.1 Activity & Fragment
```
PDF Section 2.1: Activity & Fragment
├─ Single Activity with Navigation Component
│  └─ ✅ app/src/main/java/com/guardian/track/ui/MainActivity.kt
│
├─ DashboardFragment (manual alert + status)
│  └─ ✅ app/src/main/java/com/guardian/track/ui/dashboard/Dashboard.kt
│     • DashboardFragment (UI)
│     • DashboardViewModel (state management)
│     • triggerManualAlert() method
│
├─ HistoryFragment (list + export + swipe)
│  └─ ✅ app/src/main/java/com/guardian/track/ui/history/History.kt
│     • HistoryFragment (UI)
│     • HistoryViewModel (observes incidents)
│     • IncidentAdapter (RecyclerView)
│     • ItemTouchHelper (swipe-to-delete)
│     • DiffUtil (efficient list updates)
│
├─ SettingsFragment (configuration)
│  └─ ✅ app/src/main/java/com/guardian/track/ui/settings/Settings.kt
│     • SettingsFragment (UI)
│     • SettingsViewModel (manages preferences)
│     • SeekBar for threshold
│     • Toggle for SMS simulation
│     • EditText for emergency number
│
└─ Screen rotation survival (ViewModel state)
   └─ ✅ All ViewModels extend androidx.lifecycle.ViewModel
      • State preserved in ViewModelStore
      • Not destroyed on configuration change
      • Survives: rotation, language change, etc.
```

### 2.2 Service (Foreground + Fall Detection)
```
PDF Section 2.2: Service & Fall Detection Algorithm
├─ Foreground Service with persistent notification
│  └─ ✅ app/src/main/java/com/guardian/track/service/SurveillanceService.kt
│     • @AndroidEntryPoint (Hilt-enabled)
│     • onCreate() calls startForeground()
│     • Notification on CHANNEL_ID
│
├─ Fall Detection Algorithm (2-phase)
│  └─ ✅ SurveillanceService.sensorEventListener object
│     • Phase 1 — Free-fall: magnitude < 3 m/s² for >100ms
│     • Phase 2 — Impact: magnitude > threshold within 200ms
│     • Formula: magnitude = sqrt(ax² + ay² + az²)
│
├─ Parameterizable threshold
│  └─ ✅ fallThreshold variable
│     • Loaded from PreferencesManager.fallThreshold (DataStore)
│     • Observable in real-time
│     • Adjustable via SettingsFragment SeekBar
│
├─ Sensor on dedicated thread
│  └─ ✅ SurveillanceService.setupAccelerometer()
│     • HandlerThread sensorThread created
│     • SensorManager.registerListener() on sensorHandler
│     • Callbacks don't block main thread
│
├─ GPS coordinates in incident
│  └─ ✅ On fall detection:
│     • FusedLocationProviderClient.lastLocation.await()
│     • Latitude/longitude saved with incident
│     • Fallback: 0.0, 0.0 if permission denied
│
└─ SMS alert on critical event
   └─ ✅ app/src/main/java/com/guardian/track/util/SmsHelper.kt
      • SmsManager.sendTextMessage() or simulation
      • SMS Simulation mode (DEFAULT ON)
      • Notification as fallback
```

### 2.3 BroadcastReceiver (2 Receivers)
```
PDF Section 2.3: BroadcastReceiver
├─ ACTION_BATTERY_LOW receiver
│  └─ ✅ app/src/main/java/com/guardian/track/receiver/Receivers.kt
│     • BatteryReceiver class
│     • Enqueues BatteryCriticalWorker
│     • Shows urgent notification
│     • Creates BATTERY incident in Room
│
├─ ACTION_BOOT_COMPLETED receiver
│  └─ ✅ app/src/main/java/com/guardian/track/receiver/Receivers.kt
│     • BootReceiver class
│     • Enqueues BootSurveillanceWorker
│     • Restarts SurveillanceService after reboot
│
├─ Manifest registration (static)
│  └─ ✅ app/src/main/AndroidManifest.xml
│     • BatteryReceiver: <intent-filter ACTION_BATTERY_LOW>
│     • BootReceiver: <intent-filter ACTION_BOOT_COMPLETED>
│
└─ Android 12+ compatibility (WorkManager)
   └─ ✅ All receivers use WorkManager
      • Cannot start service directly in Android 12+
      • WorkManager handles version-specific behavior
      • setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
      • See: app/src/main/java/com/guardian/track/worker/Workers.kt
```

### 2.4 ContentProvider (Protected Data Sharing)
```
PDF Section 2.4: ContentProvider
├─ Main implementation
│  └─ ✅ app/src/main/java/com/guardian/track/provider/EmergencyContactProvider.kt
│
├─ URI contract
│  └─ ✅ CONTENT_URI = "content://com.guardian.track.provider/emergency_contacts"
│     • All contacts: /emergency_contacts
│     • Single: /emergency_contacts/{id}
│
├─ Exposed columns
│  └─ ✅ _id (INTEGER), name (TEXT), phone_number (TEXT)
│     • Matches Room EmergencyContactEntity
│     • MatrixCursor packs results
│
├─ Permission protection (Signature level)
│  └─ ✅ app/src/main/AndroidManifest.xml
│     • <permission android:name="com.guardian.track.READ_EMERGENCY_CONTACTS"
│                   android:protectionLevel="signature" />
│     • Only same-signed apps can read
│
├─ Read-only access (no external write)
│  └─ ✅ insert() / update() / delete() return null / 0
│     • External apps cannot modify contacts
│
└─ Query implementation
   └─ ✅ query() method:
      • Uses UriMatcher to determine CODE_ALL vs CODE_SINGLE
      • Calls EmergencyContactDao.getAllContactsSync()
      • Packs results into MatrixCursor
      • Returns cursor to requester
```

### 3.1 Local Storage
```
PDF Section 3.1: Local Storage (Room + DataStore + CSV)
├─ Room Database
│  └─ ✅ app/src/main/java/com/guardian/track/data/local/AppDatabase.kt
│     @Database(entities = [IncidentEntity, EmergencyContactEntity])
│     abstract class AppDatabase : RoomDatabase
│
├─ IncidentEntity (table)
│  └─ ✅ app/src/main/java/com/guardian/track/data/local/entity/Entities.kt
│     • id: PK auto-generated
│     • timestamp: Long
│     • type: String (FALL | BATTERY | MANUAL)
│     • latitude/longitude: Double
│     • isSynced: Boolean
│
├─ IncidentDao (queries)
│  └─ ✅ app/src/main/java/com/guardian/track/data/local/dao/Daos.kt
│     • insertIncident(): Long
│     • getAllIncidents(): Flow<List<IncidentEntity>>
│     • getUnsyncedIncidents(): List<IncidentEntity>
│     • markAsSynced(id): void
│     • getAllIncidentsOnce(): List (for export)
│
├─ DataStore Preferences
│  └─ ✅ app/src/main/java/com/guardian/track/data/local/PreferencesManager.kt
│     • fallThreshold: Flow<Float> (default 15.0)
│     • darkMode: Flow<Boolean>
│     • smsSimulationMode: Flow<Boolean> (default true)
│     • emergencyNumber: Flow<String>
│
└─ CSV Export
   └─ ✅ app/src/main/java/com/guardian/track/util/CsvExporter.kt
      • MediaStore for Scoped Storage (Android 10+)
      • File: /Documents/guardian_incidents_<timestamp>.csv
      • Columns: Date,Time,Type,Latitude,Longitude,Synced
      • Called from ui/history/History.kt
```

### 3.2 Remote Sync (Retrofit + Offline-First)
```
PDF Section 3.2: Offline-First Synchronization
├─ Retrofit API interface
│  └─ ✅ app/src/main/java/com/guardian/track/data/remote/api/GuardianApi.kt
│     • @POST("/incidents")
│     • postIncident(incident: IncidentDto): Response<Unit>
│     • Base URL from BuildConfig.API_BASE_URL
│
├─ IncidentDto (JSON shape)
│  └─ ✅ app/src/main/java/com/guardian/track/data/remote/dto/IncidentDto.kt
│     • timestamp, type, latitude, longitude (JSON fields)
│
├─ Offline-first strategy (repository logic)
│  └─ ✅ app/src/main/java/com/guardian/track/repository/Repositories.kt
│     • IncidentRepository.saveAndSync()
│     • Step 1: Always insert to Room first
│     • Step 2: Try Retrofit immediately
│     • Step 3: If fail, schedule WorkManager
│
├─ NetworkResult sealed class (state modeling)
│  └─ ✅ app/src/main/java/com/guardian/track/model/Models.kt
│     sealed class NetworkResult<out T> {
│         data class Success<T>(val data: T)
│         data class Error<T>(val message: String, val code: Int?)
│         object Loading
│     }
│
└─ WorkManager sync retry (deferred sync on network return)
   └─ ✅ app/src/main/java/com/guardian/track/worker/Workers.kt
      • SyncWorker (@HiltWorker)
      • Constraint: NetworkType.CONNECTED
      • Retries all isSynced = false incidents
      • Marks as synced on success
      • setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
```

### 4.1 Geolocation
```
PDF Section 4.1: Geolocation (FusedLocationProvider)
├─ FusedLocationProviderClient injection
│  └─ ✅ app/src/main/java/com/guardian/track/di/Modules.kt
│     @Provides @Singleton
│     fun provideFusedLocation(...): FusedLocationProviderClient
│        = LocationServices.getFusedLocationProviderClient(...)
│
├─ GPS fetched on incident
│  └─ ✅ app/src/main/java/com/guardian/track/service/SurveillanceService.kt
│     On fall detection:
│     • fusedLocation.lastLocation.await()
│     • Extract latitude, longitude
│     • Pass to incidentRepository.saveAndSync()
│
└─ Fallback on permission denied
   └─ ✅ Sentinel value: latitude = 0.0, longitude = 0.0
      • shouldShowRequestPermissionRationale() check
      • Dialog shown if can retry
      • Silent fallback if permanently denied
```

### 4.2 Sensor Management
```
PDF Section 4.2: Sensor Management
├─ SensorManager setup
│  └─ ✅ app/src/main/java/com/guardian/track/service/SurveillanceService.kt
│     • sensorManager = getSystemService(SENSOR_SERVICE)
│     • accelerometer = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER)
│
├─ Dedicated thread for callbacks
│  └─ ✅ SurveillanceService.setupAccelerometer()
│     • sensorThread = HandlerThread("SensorThread")
│     • sensorHandler = Handler(sensorThread.looper)
│     • registerListener(..., sensorHandler) on dedicated thread
│
└─ Prevent UI blocking
   └─ ✅ Callbacks processed on SensorThread, not main
      • Main thread stays responsive for UI
      • Results switch to Dispatcher.Default or Main via coroutines
```

### 4.3 Emergency Communication
```
PDF Section 4.3: Emergency Communication (SMS)
├─ SMS sending (or simulation)
│  └─ ✅ app/src/main/java/com/guardian/track/util/SmsHelper.kt
│     • checkSimulationMode() first
│     • If simMode: post notification + log
│     • Else: SmsManager.sendTextMessage()
│
├─ Emergency number from DataStore
│  └─ ✅ app/src/main/java/com/guardian/track/data/local/PreferencesManager.kt
│     • emergencyNumber: Flow<String>
│     • Persisted in DataStore
│     • User sets via SettingsFragment
│
└─ SMS Simulation mode (DEFAULT ON)
   └─ ✅ app/src/main/java/com/guardian/track/ui/settings/Settings.kt
      • smsSimulationMode: Flow<Boolean> (default = true)
      • Toggle in SettingsFragment UI
      • Query before sending actual SMS
```

### 4.4 Data Security
```
PDF Section 4.4: Data Security
├─ EncryptedSharedPreferences
│  └─ ✅ app/src/main/java/com/guardian/track/util/SecureStorage.kt
│     • saveEmergencyNumber(encrypted)
│     • getEmergencyNumber(decrypted)
│     • Uses AndroidX Security Crypto
│
├─ Dynamic permission requests
│  └─ ✅ Throughout codebase:
│     • ACCESS_FINE_LOCATION
│     • SEND_SMS
│     • POST_NOTIFICATIONS (Android 13+)
│     • ActivityCompat.requestPermissions() when needed
│
├─ ContentProvider protection
│  └─ ✅ Signature-level permission declared
│     • Only same-signed apps can read
│     • Enforced by OS
│
└─ local.properties not versioned
   └─ ✅ .gitignore includes local.properties
      • API_BASE_URL and secrets stored here
      • Never committed to git
```

### 5.0 Android 12+ Constraints
```
PDF Section 5: Android 12+ (API 31+) Specific Constraints
├─ Background service startup (BOOT_COMPLETED)
│  └─ ✅ WorkManager with setExpedited()
│     • app/src/main/java/com/guardian/track/receiver/BootReceiver.kt
│     • Cannot call startForegroundService() directly anymore
│     • WorkManager handles version-specific behavior
│
├─ Foreground Service type declaration
│  └─ ✅ app/src/main/AndroidManifest.xml
│     • android:foregroundServiceType="location|health"
│     • location: GPS access
│     • health: high-rate accelerometer
│
├─ POST_NOTIFICATIONS permission (Android 13+)
│  └─ ✅ Declared in Manifest
│     • <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
│     • Requested dynamically at runtime
│     • Graceful handling if denied
│
└─ EXACT_ALARM permission (Android 12)
   └─ ✅ Declared if used
      • Not strictly needed for this app (no AlarmManager)
      • But declared for completeness
```

### 6.1 MVVM Architecture
```
PDF Section 6.1: MVVM Architecture Pattern
├─ View Layer (Fragments)
│  └─ ✅ ui/dashboard/, ui/history/, ui/settings/
│     • Pure UI logic only
│     • Observe ViewModel StateFlow
│     • No business logic
│
├─ ViewModel Layer (@HiltViewModel)
│  └─ ✅ *ViewModel classes
│     • Hold UiState data class
│     • Receive user callbacks
│     • Call repository methods
│     • Emit state changes
│
├─ Repository Layer
│  └─ ✅ app/src/main/java/com/guardian/track/repository/Repositories.kt
│     • IncidentRepository
│     • ContactRepository
│     • Single source of truth
│
└─ Model Layer (Entity / DTO / Domain)
   └─ ✅ Three-model separation:
      • Entity: Room model (data/local/entity/)
      • DTO: JSON model (data/remote/dto/)
      • Domain: UI model (model/Models.kt)
```

### 6.2 Hilt Dependency Injection
```
PDF Section 6.2: Hilt Dependency Injection
├─ Application entry point (@HiltAndroidApp)
│  └─ ✅ app/src/main/java/com/guardian/track/GuardianApplication.kt
│     • Triggers Hilt code generation
│     • Implements Configuration.Provider for WorkManager
│
├─ DI Modules
│  └─ ✅ app/src/main/java/com/guardian/track/di/Modules.kt
│     • DatabaseModule (Room singleton)
│     • NetworkModule (Retrofit singleton)
│     • AppModule (Location, WorkManager)
│
├─ Provider methods (@Provides @Singleton)
│  └─ ✅ Each module provides:
│     • AppDatabase
│     • DAO instances
│     • Retrofit (GuardianApi)
│     • FusedLocationProviderClient
│     • WorkManager
│
├─ Injectable components (@AndroidEntryPoint, @HiltViewModel)
│  └─ ✅ Activities, Fragments, ViewModels, Services
│     • @HiltViewModel for all ViewModels
│     • @AndroidEntryPoint for Service/Fragment
│
└─ Worker factory
   └─ ✅ WorkManager configured in GuardianApplication
      • Feeds workerFactory to WorkManager.Configuration
      • Allows @HiltWorker + @AssistedInject
```

### 6.3 Coroutines & Flow
```
PDF Section 6.3: Coroutines & Flow (No Callbacks)
├─ Room Flow queries
│  └─ ✅ app/src/main/java/com/guardian/track/data/local/dao/Daos.kt
│     • getAllIncidents(): Flow<List<IncidentEntity>>
│     • getAllContacts(): Flow<List<EmergencyContactEntity>>
│     • Emit automatically on table changes
│
├─ Repository Flow transformations
│  └─ ✅ app/src/main/java/com/guardian/track/repository/Repositories.kt
│     • map { entities -> entities.map { entity -> incident } }
│     • Transform Entity → Incident domain model
│
├─ ViewModel StateFlow
│  └─ ✅ *ViewModel.kt files
│     • _uiState = MutableStateFlow(initialValue)
│     • Expose as (.asStateFlow())
│     • Update via _uiState.update { ... }
│
├─ Fragment collection
│  └─ ✅ ui/**/*Fragment.kt
│     • lifecycleScope.repeatOnLifecycle(Lifecycle.State.STARTED)
│     • collect { uiState -> updateUI() }
│     • Or: .stateIn() for StateFlow
│
├─ Coroutine scopes
│  └─ ✅ viewModelScope (lifecycle-aware cancellation)
│     • Service uses serviceScope (SupervisorJob)
│     • Ensures proper cleanup
│
└─ Dispatcher routing
   └─ ✅ Dispatchers.Default for CPU work
      • Sensor data processing
      • Dispatchers.Main.immediate for UI updates
```

### 6.4 UI & Material Design
```
PDF Section 6.4: UI & Material Design 3
├─ ViewBinding (mandatory)
│  └─ ✅ buildFeatures { viewBinding = true }
│     • Used in all Fragments
│     • Type-safe view access
│
├─ Material Design 3 theme
│  └─ ✅ app/src/main/res/values/themes.xml
│     • Theme.GuardianTrack applied
│
└─ Dark mode support (DayNight)
   └─ ✅ Theme uses @style/Theme.GuardianTrack
      • Supports DayNight theme switching
      • User can configure in SettingsFragment
```

---

## 🔍 Quick Search: "Where is X?"

| What You're Looking For | File Location |
|---|---|
| Main Activity | `ui/MainActivity.kt` |
| Fall detection algorithm | `service/SurveillanceService.kt` (sensorEventListener) |
| Offline-first logic | `repository/Repositories.kt` (saveAndSync) |
| WorkManager tasks | `worker/Workers.kt` (SyncWorker, BootWorker, BatteryWorker) |
| Room Database | `data/local/AppDatabase.kt` |
| Retrofit API | `data/remote/api/GuardianApi.kt` |
| DataStore config | `data/local/PreferencesManager.kt` |
| ContentProvider | `provider/EmergencyContactProvider.kt` |
| Permissions | `AndroidManifest.xml` |
| Hilt modules | `di/Modules.kt` |
| CSV export | `util/CsvExporter.kt` |
| SMS logic | `util/SmsHelper.kt` |
| Notifications | `util/NotificationHelper.kt` |
| Dashboard UI | `ui/dashboard/Dashboard.kt` |
| History UI | `ui/history/History.kt` |
| Settings UI | `ui/settings/Settings.kt` |
| BroadcastReceivers | `receiver/Receivers.kt` |
| Encrypted storage | `util/SecureStorage.kt` |

---

## 📊 Implementation Summary

| PDF Section | Status | Key Files | Points |
|---|---|---|---|
| 2.1 Activity & Fragment | ✅ | ui/MainActivity.kt, Dashboard/History/Settings | 20 |
| 2.2 Service + Fall Algorithm | ✅ | service/SurveillanceService.kt | 20 |
| 2.3 BroadcastReceiver | ✅ | receiver/Receivers.kt | 10 |
| 2.4 ContentProvider | ✅ | provider/EmergencyContactProvider.kt | 10 |
| 3.1 Local Storage | ✅ | data/local/ | 15 |
| 3.2 Remote Sync | ✅ | repository/, worker/, data/remote/ | 10 |
| 4.1 Geolocation | ✅ | di/Modules.kt, service/ | incl. 4.2 |
| 4.2 Sensors | ✅ | service/SurveillanceService.kt | incl. 4.1 |
| 4.3 Emergency SMS | ✅ | util/SmsHelper.kt | incl. 4.4 |
| 4.4 Security | ✅ | util/SecureStorage.kt, AndroidManifest.xml | incl. 4.3 |
| 5 Android 12+ | ✅ | receiver/, worker/, AndroidManifest.xml | incl. above |
| 6 Architecture | ✅ | di/Modules.kt, repository/, ui/ | incl. above |
| **TOTAL REQUIRED** | ✅ | - | **100** |
| BONUS: Jetpack Compose | ⏳ | -- | +5 |
| BONUS: ML Algorithm | ⏳ | -- | +3 |
| BONUS: Unit tests | ⏳ | test/ | +2 |

---

*This mapping is current as of April 15, 2026 and aligns exactly with the ENONCE PDF sections.*
