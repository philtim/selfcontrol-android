# FocusTime

An Android app that helps you stay focused by blocking distracting apps and websites during timed focus sessions.

## Features

- **App Blocking** -- Select apps to block from a curated list of common distractions or from all installed apps. When you try to open a blocked app during a session, a full-screen overlay redirects you back to focus.
- **Website Blocking** -- Block distracting websites via DNS interception through a local VPN. Supports exact domains (`instagram.com`) and wildcard patterns (`*.youtube.com`).
- **Timed Sessions** -- Start focus sessions from 30 minutes to 8 hours, or set a custom duration. A countdown timer shows remaining time on the home screen.
- **Master Password** -- Optionally set a master password (intended to be shared with an accountability partner) to allow ending sessions early. Passwords are stored as SHA-256 hashes.
- **Uninstall Protection** -- Uses Android Device Admin to prevent uninstalling the app while a session is active.
- **Boot Persistence** -- Active sessions survive device reboots. Services automatically restart and expired sessions are cleaned up.

## Screenshots

_Coming soon_

## Architecture

The app follows **MVVM** with a clean separation of concerns:

```
com.t7lab.focustime/
├── data/
│   ├── db/            # Room database (entities, DAOs, converters)
│   ├── preferences/   # DataStore for session state & password hash
│   └── repository/    # Repository layer (SessionRepository, BlocklistRepository)
├── di/                # Hilt dependency injection modules
├── service/
│   ├── AppMonitorService    # Foreground service detecting blocked app launches
│   ├── FocusVpnService      # Local VPN service intercepting DNS queries
│   ├── SessionManager       # Orchestrates session start/stop and service lifecycle
│   ├── BootReceiver         # Restarts services after device reboot
│   └── FocusDeviceAdminReceiver  # Device admin for uninstall protection
├── ui/
│   ├── home/          # Main screen with session controls and countdown
│   ├── apppicker/     # App selection with search and curated list
│   ├── urlmanager/    # Domain blocklist management
│   ├── settings/      # Master password configuration
│   ├── components/    # Reusable composables (DurationPicker, BlockedItemChip)
│   ├── navigation/    # Jetpack Navigation route definitions
│   └── theme/         # Material 3 theming
└── util/              # Domain validation, time formatting, domain matching
```

### How Blocking Works

**App blocking**: `AppMonitorService` runs as a foreground service and polls `UsageStatsManager` every 500ms to detect when a blocked app moves to the foreground. It then launches `BlockedOverlayActivity` on top of it.

**URL blocking**: `FocusVpnService` establishes a local VPN that intercepts all DNS traffic (UDP port 53). It parses DNS query packets, checks the queried domain against the blocklist (with wildcard support via `DomainMatcher`), and returns `0.0.0.0` for blocked domains. Allowed queries are forwarded to an upstream DNS server.

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose with Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Async | Kotlin Coroutines & Flows |
| Build | Gradle 8.7 with Kotlin DSL and version catalogs |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

## Prerequisites

- Android Studio Ladybug (2024.2) or later
- JDK 17
- Android SDK with API level 35 installed

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Running Locally

### On an emulator

1. Open the project in Android Studio.
2. Go to **Tools > Device Manager** and create a virtual device with API 26 or higher.
3. Click **Run** (or `Shift+F10`) to build and launch the app on the emulator.

> **Note:** VPN-based URL blocking requires the emulator to accept the VPN prompt. App blocking works normally, but some system apps may behave differently on emulators compared to physical devices.

### On a physical device

1. Enable **Developer Options** on your device (tap *Build Number* seven times in Settings > About Phone).
2. Enable **USB Debugging** in Developer Options.
3. Connect the device via USB and approve the debugging prompt.
4. Select your device in Android Studio's device dropdown and click **Run**.

### Granting runtime permissions

After installing, the app will guide you through granting the required permissions. You can also enable them manually:

1. **Usage Access** -- Settings > Apps > Special app access > Usage access > FocusTime
2. **Display Over Other Apps** -- Settings > Apps > Special app access > Display over other apps > FocusTime
3. **VPN** -- Prompted automatically when starting a session with URL blocking
4. **Device Admin** -- Prompted automatically when starting a session
5. **Notifications** -- Prompted on first session start (Android 13+)

### From the command line

```bash
# Build, install, and launch on a connected device or emulator
./gradlew installDebug
adb shell am start -n com.t7lab.focustime/.MainActivity
```

## Permissions

The app requires several permissions to function. All are requested at runtime when needed:

| Permission | Reason |
|-----------|--------|
| Usage Access | Detect which app is in the foreground to enforce app blocking |
| VPN | Establish a local VPN to intercept and filter DNS queries |
| Display Over Other Apps | Show blocking overlay on top of blocked apps |
| Device Admin | Prevent uninstalling the app during active sessions |
| Notifications | Show persistent notification while a session is active |
| Boot Completed | Restart blocking services after device reboot |

## License

All rights reserved.
