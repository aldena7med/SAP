# Separate App Sound — Build & Setup Guide

## What this app does

Routes audio from a specific app (e.g. Spotify, YouTube) to a chosen output device (Bluetooth speaker, earbuds), independently from the rest of the phone's audio — exactly like Samsung's "Separate App Sound" in Sound settings.

---

## Requirements

| Item | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1.1 or newer |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 34 (Android 14) |
| Target device | Android 14+ |

---

## Build Steps

### 1. Open the project

```
File → Open → select the SeparateAppSound/ folder
```

Wait for Gradle sync to complete (~1–2 min first time).

### 2. Set up SDK

If prompted about missing SDK:
```
File → Project Structure → SDK Location
Set Android SDK to your local path (e.g. ~/Library/Android/sdk)
```

### 3. Build APK

**Debug APK (for sideloading/testing):**
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Release APK:**
```
Build → Generate Signed Bundle / APK → APK
```
You'll need to create a keystore if you don't have one.

### 4. Install on device

Enable Developer Options on your phone:
```
Settings → About phone → tap Build number 7 times
Settings → Developer Options → USB Debugging ON
```

Then either:
- Use Android Studio: Run → Run 'app' (green play button)
- Or via ADB: `adb install app/build/outputs/apk/debug/app-debug.apk`

---

## ⚡ Enable Full Per-App Audio Routing (IMPORTANT)

Android's per-app audio routing requires a system permission not granted by default. Grant it once via ADB:

### Step 1: Install ADB
- Windows: Download [platform-tools](https://developer.android.com/studio/releases/platform-tools)
- Mac: `brew install android-platform-tools`
- Linux: `sudo apt install adb`

### Step 2: Connect phone via USB and run:
```bash
adb shell pm grant com.separateappsound android.permission.MODIFY_AUDIO_ROUTING
```

### Step 3 (optional — for deeper routing via Shizuku):
1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) from Play Store
2. Activate it via ADB: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
3. Open Shizuku → grant Separate App Sound permission

> **Without this permission:** The app still works for Bluetooth communication devices (calls, voice), but full media audio separation requires the permission above.

---

## Add Quick Settings Tile

1. Pull down notification shade twice (fully expanded)
2. Tap the **pencil / edit** icon
3. Find **"App Sound Route"** tile and drag it to your active tiles
4. Tap it to toggle routing on/off, or long-press to open the app

---

## How to use the app

1. **Tap "Add route"** (blue + button)
2. **Choose an app** — known audio apps (Spotify, YouTube, etc.) are shown first with an "Audio" badge
3. **Choose output device** — your connected Bluetooth devices appear here; "Connected" = currently available
4. The route is saved and **automatically activated**
5. Toggle the switch on each route card to enable/disable routing
6. Tap ⭐ to save a route as a favorite for quick access
7. Tap ✏️ to change which device an app routes to
8. Tap 🗑 to remove a route

---

## How Favorites work

Routes marked as favorites:
- Are shown with a filled blue star ⭐
- Are restored automatically after phone restart (if routing service was running)
- Will be prominently displayed at the top in a future update

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Bluetooth device not showing | Pair device in phone's Bluetooth settings first, then tap "Refresh devices" in app menu |
| Audio not routing | Grant MODIFY_AUDIO_ROUTING via ADB (see above) |
| App crashes on launch | Ensure Android 14 (API 34) is installed on device |
| Quick Tile not appearing | Restart phone after installing |
| "No active routes" in tile | Enable at least one route toggle in the app first |

---

## Project Structure

```
SeparateAppSound/
├── app/src/main/java/com/separateappsound/
│   ├── model/
│   │   ├── AppRoute.kt          # Route data model
│   │   ├── AudioDevice.kt       # Device data model
│   │   └── AppInfo.kt           # App list model
│   ├── service/
│   │   ├── AudioRoutingService.kt  # Foreground service (keeps routing alive)
│   │   └── BootReceiver.kt      # Restores routing after reboot
│   ├── tile/
│   │   └── SoundRoutingTileService.kt  # Quick Settings tile
│   ├── ui/
│   │   ├── MainActivity.kt      # Main screen
│   │   ├── MainViewModel.kt     # ViewModel
│   │   ├── RouteAdapter.kt      # Route list adapter
│   │   ├── AppPickerActivity.kt # Step 1: choose app
│   │   ├── AppPickerAdapter.kt  # App list adapter
│   │   ├── DevicePickerActivity.kt  # Step 2: choose device
│   │   └── DevicePickerAdapter.kt   # Device list adapter
│   └── util/
│       ├── AudioRoutingManager.kt   # Core routing logic
│       ├── BluetoothDeviceManager.kt # BT device discovery
│       ├── RouteRepository.kt       # Persist routes to SharedPrefs
│       └── AppListHelper.kt         # Load installed apps
└── app/src/main/res/
    ├── layout/     # XML layouts (Samsung One UI style)
    ├── drawable/   # Vector icons
    ├── values/     # Colors, themes, strings
    └── menu/       # Menu items
```

---

## Technical notes

- **minSdk 34** — Android 14 required; uses `AudioManager.setCommunicationDevice()` (API 31+) and `AudioPlaybackCallback` for playback detection
- **AudioPlaybackConfiguration** — detects which app is actively playing audio and applies routing
- **MODIFY_AUDIO_ROUTING** — system permission for full `AudioPolicy`-level per-stream routing; granted once via ADB, survives app updates
- Routes persist in `SharedPreferences` as JSON via Gson
- Service is `START_STICKY` — relaunches if killed by system
- `BootReceiver` restores active routes on device restart
