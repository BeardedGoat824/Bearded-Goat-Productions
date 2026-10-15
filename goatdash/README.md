# GoatDash (native Android + Android Auto)

Connects to an ELM327-style OBD2 scanner, reads and clears trouble codes, and shows a
customizable live gauge dashboard on your phone **and** on your car's Android Auto screen.
Kotlin, Jetpack Compose, coroutines and the Android for Cars App Library.

## Features

- **Scanners:** Bluetooth Classic (most cheap ELM327 dongles), Bluetooth LE (Vgate iCar Pro
  BLE, OBDLink CX, Veepeak BLE…) and Wi-Fi (usually `192.168.0.10:35000`). Remembers the
  last one and reconnects automatically, including when Android Auto starts.
- **Trouble codes:** stored, pending and permanent codes, check-engine light status,
  plain-English descriptions for common generic codes, one-tap web lookup, share a report,
  and clear codes (with a warning about emissions readiness monitors).
- **Dashboard:** up to 6 pages, each a 1–4 × 1–3 grid. Every gauge picks its sensor,
  style (dial, bar, digital, graph), colour and an optional "turns red above" warning.
  mph/°F/psi or km/h/°C/kPa.
- **Android Auto:** the same pages drawn on the car screen. Buttons for next page,
  trouble codes (read-only while driving) and connect/disconnect. Swiping pages on the phone
  flips the car screen too.
- **Demo mode:** a built-in fake car (it speaks the real ELM327 protocol) so you can set
  everything up without leaving the couch.

### Sensors

RPM, speed, coolant, intake air, oil temp, outside temp, throttle, engine load, fuel level,
manifold pressure (MAP), barometric pressure, mass air flow (MAF), timing advance,
short/long-term fuel trim, fuel rate, ECU voltage, and battery voltage (read by the adapter,
works on every car). Sensors your car doesn't report show **N/A**.

## Android Auto setup (one time)

GoatDash is sideloaded, so Android Auto hides it until you allow unknown sources:

1. Android Auto settings → tap **Version** ~10 times to unlock developer settings.
2. ⋮ menu → **Developer settings** → enable **Unknown sources**.
3. Android Auto settings → **Customize launcher** → tick GoatDash.

Android Auto only lets *navigation* apps draw their own graphics, so GoatDash registers in
that category to get a canvas for the gauges. That's fine for a personal sideloaded app, but
Google Play would reject it as-is.

## Project layout

```
app/src/main/java/com/beardedgoat/goatdash/
  GoatDashApp.kt           shared OBD connection + dashboard store
  MainActivity.kt          phone entry point
  obd/                     ELM327 protocol, parsers, PIDs, code descriptions, connection manager
  obd/transport/           Bluetooth Classic, BLE, Wi-Fi and demo-car byte pipes
  dash/                    dashboard model (JSON), store, and the Canvas gauge renderer
  car/                     Android Auto service and screens
  ui/                      Compose screens: Dashboard, Codes, Customize, Connect
```

## Build

```
./gradlew testDebugUnitTest assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. GitHub Actions builds it on every push/PR
(see the *GoatDash-release-APK* artifact).
