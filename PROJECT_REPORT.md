# GuardianTrack Project Report

Date: April 15, 2026
Project: GuardianTrack
Platform: Android (Kotlin)

## 1. Executive Summary

GuardianTrack is an Android safety monitoring application designed to detect emergency situations (especially falls), record incidents locally, and notify remote systems and emergency contacts.

The application combines foreground monitoring, local persistence, offline-first synchronization, background workers, and secure preference handling to provide reliability in unstable network or device conditions.

## 2. Problem Statement

Personal safety monitoring apps often fail in one of the following conditions:

- No internet connectivity at incident time.
- App not open in foreground.
- Device reboot interrupts monitoring.
- Sensitive user settings are not protected.

GuardianTrack addresses these issues with an offline-first architecture and system-integrated Android components.

## 3. Objectives

- Detect probable falls in near real-time using accelerometer input.
- Allow manual emergency triggering by user action.
- Record every incident with timestamp and optional location.
- Sync incidents to backend API when internet is available.
- Preserve behavior through reboot and battery events.
- Provide user settings for threshold and emergency contact configuration.
- Export incident history for reporting and auditing.

## 4. Technical Stack

- Language: Kotlin
- Build system: Gradle Kotlin DSL
- Min SDK: 26
- Target SDK: 35
- DI: Hilt
- Local database: Room
- Networking: Retrofit + OkHttp
- Async and reactive: Coroutines + Flow
- Background jobs: WorkManager
- Preferences: DataStore
- Secure storage: EncryptedSharedPreferences
- Location: Fused Location Provider
- UI: XML Views + ViewBinding

## 5. Architecture Overview

Pattern: MVVM + Repository + DI

High-level layers:

- UI layer (Fragments, ViewModels): Dashboard, History, Settings.
- Domain/data access layer (Repositories): incident and contact orchestration.
- Local persistence layer (Room): incidents and emergency contacts.
- Remote layer (Retrofit API): incident upload endpoint.
- Background execution (WorkManager): sync retries and boot/battery workflows.
- System integration (Service, Receivers, Provider): continuous monitoring and platform events.

## 6. Core Functional Modules

### 6.1 Foreground Surveillance Service

- Foreground service continuously monitors accelerometer values.
- Two-phase fall detection approach:
  - free-fall candidate,
  - impact confirmation within a time window.
- On detection: records incident, attempts sync, sends alert notification, and triggers emergency SMS logic.

### 6.2 Incident Repository and Offline Sync

- Incident is always saved locally first.
- Immediate API sync attempt occurs after insert.
- If sync fails, unsynced records are retried via WorkManager with network constraints.

### 6.3 History and Export

- RecyclerView-based history list with sync state indicators.
- Swipe-to-delete support.
- CSV export to public Documents using MediaStore.

### 6.4 Settings and Security

- Adjustable fall threshold.
- SMS simulation mode (default enabled for safe testing).
- Emergency number persistence in DataStore.
- Encrypted backup of sensitive values in EncryptedSharedPreferences.

### 6.5 Boot and Battery Handling

- Boot receiver schedules restart work.
- Battery low receiver schedules incident logging and user notification.

### 6.6 Emergency Contact Content Provider

- Read-only provider exposes emergency contacts through content URIs.
- Access restricted by custom signature-level permission.

## 7. Permissions and Compliance

Manifest includes permissions for:

- location and network,
- foreground service operation,
- notification posting,
- SMS alerts,
- battery and boot-related behavior,
- vibration and protected provider access.

Runtime requests are handled in the main activity for required dangerous permissions.

## 8. Build and Run Instructions

1. Open in Android Studio.
2. Ensure Android SDK 35 and Java 17 are installed.
3. Add API base URL in local.properties:

   api.base.url=https://your-mock-api.mockapi.io/api/v1/

4. Build and run on a physical Android device for best sensor and SMS validation.
5. Grant runtime permissions when prompted.

## 9. Validation Checklist (Inclusion Test)

The project includes the following required implementation areas:

- [x] App module and Gradle configuration.
- [x] Foreground monitoring service.
- [x] Room entities, DAOs, and database.
- [x] Repository pattern implementation.
- [x] Retrofit API integration.
- [x] WorkManager workers for retries and system workflows.
- [x] Broadcast receivers for boot and battery events.
- [x] DataStore preference management.
- [x] Secure storage via EncryptedSharedPreferences.
- [x] UI screens for dashboard/history/settings.
- [x] Navigation resources and menus.
- [x] CSV export utility.
- [x] AndroidManifest permission and component declarations.
- [x] README project documentation.

## 10. Testing and Verification Performed

This report is accompanied by local verification commands to confirm:

- project structure integrity,
- key file inclusion,
- build/test task execution status.

The execution results are recorded in VERIFICATION_SUMMARY.md.

## 11. Current Limitations and Next Steps

- Add Room migration scripts for production upgrades.
- Expand unit and instrumentation test coverage.
- Add CI pipeline for automatic lint/build/test/report artifacts.
- Add stronger monitoring around provider performance and access auditing.

## 12. Conclusion

GuardianTrack delivers a robust baseline for mobile safety monitoring by combining continuous sensing, resilient local-first persistence, controlled background execution, and secure configuration handling in a modern Android architecture.

## 13. Reponses Aux Questions Du Jury

### 13.1 Pourquoi Flow plutot que LiveData entre Room et ViewModel ?

Nous avons choisi Flow pour trois raisons principales :

- Integration native avec Coroutines : toute la couche data (Room, DataStore, repository, workers) est deja orientee suspend/Flow.
- Composition plus puissante : dans Settings, plusieurs flux sont combines (`combine`) pour produire un seul `SettingsUiState`.
- Controle de cycle de vie explicite : la collecte est faite avec `repeatOnLifecycle`, ce qui permet de suspendre/reprendre proprement la consommation.

Exemple concret du projet :

- `IncidentRepository.getAllIncidents()` retourne `Flow<List<Incident>>`.
- `HistoryViewModel` convertit ce flux en `StateFlow` via `stateIn(...)`.
- Le Fragment collecte ce `StateFlow` uniquement en etat `STARTED`.

Quand aurions-nous choisi LiveData dans ce projet ?

- Si une ecran legacy XML devait observer un etat UI simple sans operation de composition/reactivite avancee.
- Si l'objectif etait de minimiser la courbe d'apprentissage pour une equipe non familiere avec Flow.
- Pour une couche de presentation uniquement, quand les donnees ne viennent pas d'une pipeline coroutine existante.

Dans GuardianTrack, le choix Flow est plus coherent de bout en bout.

### 13.2 Que faire si ACCESS_FINE_LOCATION est refusee definitivement ?

Cas vise : l'utilisateur refuse et coche "Ne plus demander" (`shouldShowRequestPermissionRationale == false`).

Etat actuel dans le projet :

- Si la permission manque, l'alerte manuelle n'essaie pas la localisation et l'UI affiche un message de permission requise.
- Les chemins de sauvegarde d'incident conservent un fallback fonctionnel (`latitude = 0.0`, `longitude = 0.0`) pour ne jamais perdre l'evenement critique.

Strategie de repli recommandee (et a formaliser dans l'UI) :

- Ne pas boucler sur les demandes systeme.
- Afficher un ecran explicatif avec action "Ouvrir les Parametres" vers les settings applicatifs.
- Continuer la logique de securite sans GPS (incident enregistre + sync + notification/SMS) pour privilegier la disponibilite.

Pourquoi : en contexte securitaire, perdre la position est moins grave que perdre totalement la trace d'un incident.

### 13.3 Limites de securite du ContentProvider et protection contre l'injection

Limites de securite de notre provider :

- Il est `exported=true`, donc il devient une surface d'attaque potentielle.
- La methode `query()` accepte des parametres `selection/selectionArgs/sortOrder` dans la signature Android, ce qui est classiquement un vecteur d'abus si ces champs sont evalues dynamiquement.

Ce qu'est une "content provider injection" :

- Une application malveillante tente de forcer une requete provider (selection/sort/uri) pour lire plus de donnees que prevu, contourner des controles ou provoquer des comportements inattendus.

Protections appliquees dans GuardianTrack :

- Permission custom `signature` (`com.guardian.track.READ_EMERGENCY_CONTACTS`) : seules les apps signees avec le meme certificat peuvent lire.
- `UriMatcher` strict (`/emergency_contacts` et `/emergency_contacts/{id}` seulement).
- Provider en lecture seule (insert/update/delete desactives).
- Aucune construction SQL dynamique a partir de `selection` utilisateur dans l'implementation actuelle.

Durcissements supplementaires recommandes :

- Ignorer explicitement `selection/sortOrder` non attendus avec rejection claire.
- Remplacer `allowMainThreadQueries()` dans le provider par un acces asynchrone dedie.

### 13.4 Restrictions Android 12+ sur les services en arriere-plan et gestion du boot

Depuis Android 12 (API 31), le systeme limite fortement le demarrage direct de services en arriere-plan depuis un `BroadcastReceiver` (dont `BOOT_COMPLETED`), surtout pour les foreground services hors cas exemptes.

Pourquoi c'est un probleme :

- Appeler directement `startForegroundService()` depuis le receiver peut echouer selon le contexte runtime et les restrictions de lancement.

Notre contournement propre :

- `BootReceiver` ne demarre pas le service directement.
- Il planifie un `BootSurveillanceWorker` via WorkManager (expedited si possible).
- Le worker relance ensuite `SurveillanceService` dans un cadre conforme aux contraintes plateforme.

Ce pattern est recommande car WorkManager gere les fenetres d'execution, la persistance et les contraintes OS de maniere fiable.

### 13.5 Pourquoi separer Entity, DTO et DomainModel ?

Cette separation evite le couplage entre couches et limite l'effet de bord des evolutions.

- Entity (Room) : schema local optimise stockage/sync.
- DTO (network) : contrat API pur.
- DomainModel : objet metier consomme par l'UI.

Valeur concrete dans notre code :

- `IncidentEntity` contient `timestamp`, `isSynced`, coordonnees brutes.
- `IncidentDto` ne transporte que ce qui est utile a l'endpoint `POST /incidents`.
- `Incident` (domaine) expose `formattedDate`/`formattedTime` pour affichage.

Benefice : si l'API change (ex: nouveau champ backend) ou si le format d'affichage change, la DB locale et l'UI ne cassent pas automatiquement.

### 13.6 WorkManager vs JobScheduler vs AlarmManager, et pourquoi WorkManager ici ?

Differences :

- WorkManager : API haut niveau, persistante, contraintes (reseau/charge), retry, compatibilite inter-versions.
- JobScheduler : API systeme (API 21+), puissante mais plus bas niveau, plus de code de plumbing.
- AlarmManager : declenchement temporel exact/inexact, mais pas ideal pour taches deferrees avec contraintes reseau et retry metier.

Justification dans GuardianTrack (synchronisation differee) :

- Besoin principal: "synchroniser quand le reseau revient" + resilence apres redemarrage/process kill.
- WorkManager fournit nativement `NetworkType.CONNECTED`, `Result.retry()`, et file unique (`enqueueUniqueWork`).
- Le cout de maintenance est plus faible qu'une implementation manuelle JobScheduler + gestion des retries.

Donc WorkManager est le choix le plus robuste et le plus propre pour notre scenario offline-first.
