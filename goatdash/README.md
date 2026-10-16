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
- **Shift light:** turn any RPM gauge into a shift light. Pick the RPM, and at or above it the
  whole gauge flashes a contrasting colour (automatic, or one you choose). There's a *Test
  flash* button, and the demo car winds out to ~6500 rpm once a lap so you can see it.
- **Android Auto:** the same pages drawn full screen on the car display (GoatDash's buttons
  float on top and fade away; Android Auto's own system bar always stays). Buttons for next
  page, trouble codes (read-only while driving) and connect/disconnect. Swiping pages on the
  phone flips the car screen too.
- **Music controls:** put a *Music controls* tile on any page to see what's playing (song,
  artist, album art, app) and control it. Pick which buttons it shows: previous, back 15s,
  play/pause, forward 30s, next. Optionally add the same buttons as a column on the car
  screen. Works with any music or podcast app; needs one-time "notification access", which
  is how Android lets an app control other apps' playback.
- **Demo mode:** a built-in fake car (it speaks the real ELM327 protocol) so you can set
  everything up without leaving the couch.

### Sensors

RPM, speed, coolant, intake air, oil temp, outside temp, throttle, engine load, fuel level,
manifold pressure (MAP), barometric pressure, mass air flow (MAF), timing advance,
short/long-term fuel trim, fuel rate, ECU voltage, and battery voltage (read by the adapter,
works on every car). Sensors your car doesn't report show **N/A**.

## Getting it onto Android Auto

Android Auto only shows apps built with the Android for Cars App Library (like GoatDash) when
they're installed from a trusted source such as Google Play. The **Unknown sources**
developer setting does *not* help here: Google documents that it only covers media,
messaging and parked apps. A sideloaded APK still works fully on the phone, but won't appear
in the car's app launcher.

The supported way to get it onto the car without a public Play Store listing is Google Play's
**internal testing** (or **Internal App Sharing**). Google's docs say both skip the Play review
process:

1. Create a Google Play Console developer account (one-time fee).
2. Create the GoatDash app there and upload a signed build to the *Internal testing* track
   (or share it with *Internal App Sharing*).
3. Add your own Google account as a tester and install GoatDash from the Play Store link.
4. Android Auto settings → **Customize launcher** → make sure GoatDash is ticked.

Android Auto only lets *navigation* apps draw their own graphics, so GoatDash registers in
that category to get a canvas for the gauges. That's fine for a private testing track, but a
public Play Store release would likely be rejected as-is.

### On a rooted phone (experimental)

Android Auto decides whether to trust an app by which app installed it. With root you can
install GoatDash so Android records Google Play as its installer. Reports are mixed on newer
Android versions, so treat this as an experiment:

```
adb push app-release.apk /data/local/tmp/goatdash.apk
adb shell su -c "pm install -r -i com.android.vending /data/local/tmp/goatdash.apk"
adb shell dumpsys package com.beardedgoat.goatdash | grep -i installer   # should mention com.android.vending
```

Then force-stop Android Auto (Settings → Apps → Android Auto → Force stop), reconnect, and check
**Customize launcher**. Always install updates the same way: installing an update by tapping
the APK makes the phone's normal package installer the "installer" again, which hides GoatDash.

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
