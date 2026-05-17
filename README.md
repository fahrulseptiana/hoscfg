# HOSCfg — MIUI Home Customizer

Xposed module for customizing MIUI Home (launcher) drawer on HyperOS.

## Features

- **Hide Search Bar** — Remove search bar from the app drawer
- **Drawer Background** — Custom background color & transparency  
- **Icon Label Color** — Custom color for app name labels
- **Hide No SIM Icon** — Remove no-SIM indicator from status bar & "Emergency calls only" text
- **Settings Toggle** — "Hide Search Bar" toggle injected into MIUI Home drawer settings

## Requirements

- HyperOS / MIUI 14+
- LSPosed (or compatible Xposed framework)
- Root access

## Installation

1. Install the APK
2. Enable module in **LSPosed Manager**
3. Enable scope for:
   - **MIUI Home** (`com.miui.home`)
   - **SystemUI** (`com.android.systemui`)
4. Reboot

## Building

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT
