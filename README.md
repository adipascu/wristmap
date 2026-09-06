# Wristmap

Google Maps walking and cycling directions on a Pebble Time 2, with a small map that shows
where you are and where the next turn is.

Start directions in Google Maps on Android. The watchapp opens by itself and shows the next
manoeuvre (Google's own arrow icon, the distance and the street), a heading-up map of the
streets around you with your position, the estimated position of the next turn, the street you
have to turn into, and the trip summary (time left, distance left, arrival time).

Google Maps has no public API for a running navigation. Everything comes from the ongoing
navigation notification it posts, so this is Android only.

## How it works

```
Google Maps (phone)
  posts the ongoing navigation notification (text plus a maneuver icon)
        │
        ▼
Wristmap companion (Android, this repo)
  NotificationListenerService reads the text and the icon
  LocationManager gives position, speed and bearing
  OpenFreeMap vector tiles (OpenStreetMap data, zoom 14) are decoded on the phone
  RoutePreview walks the announced distance along the current road on the OSM graph
    and snaps the end point to the street named in the instruction
  MapRenderer draws a 4 colour, heading-up map on a Canvas and packs it as 2 bits per pixel
        │  PebbleKit Android 2 AppMessages (one 8 KB message per chunk)
        ▼
Wristmap watchapp (C, this repo)
  top bar: arrow, distance, street
  map: the phone-rendered bitmap in a palettised GBitmap
  bottom bar: time left, distance left, arrival time
```

The next turn is an estimate. Google only tells us "in 200 m, turn left onto Rue X". The
companion snaps your GPS position to the nearest road, follows that road for 200 m through
OpenStreetMap intersections that continue in roughly the same direction, and if a street named
"Rue X" is within 60 m of the end point it snaps the turn marker onto it. The path it followed
is drawn in blue, the turn as a blue dot, and the named street is highlighted in blue as well.
When you are off the road network it draws a straight line ahead instead.

Zoom follows the distance to the turn so that the turn is on screen (zoom 14 to 18, capped at
17 when moving faster than 4 m/s, so cycling gets a wider view). Up and down on the watch
override it for the rest of the trip.

## Install

Two parts: the watchapp on the watch and the companion on the phone.

1. **Watchapp**: open `wristmap.pbw` from the latest release in the Pebble app, or build it
   (see below) and sideload it.
2. **Companion**: install `wristmap.apk` from the latest release. Open it once and grant
   notification access and location. The app lists what is still missing.
3. Start walking or cycling directions in Google Maps. The watchapp pops up on its own.

On the watch: up and down zoom the map, select toggles between the map and a big arrow view,
back leaves the app. A short vibration announces every new instruction.

The companion needs the Core Devices Pebble app (`coredevices.coreapp`). It is the only Android
app that implements PebbleKit Android 2, which the companion uses to talk to the watch.

## Build

Watchapp, with the Core Devices SDK (`uv tool install pebble-tool --python 3.13`, then
`pebble sdk install latest`):

```
cd watchapp
pebble build
pebble install --emulator emery
```

Companion, with an Android SDK that has platform 37:

```
cd android
./gradlew assembleDebug testDebugUnitTest
adb install app/build/outputs/apk/debug/app-debug.apk
```

`scripts/emu-send.py` drives the watchapp in the emulator without a phone: it sends a demo
navigation state, a synthetic test pattern, or a frame the companion wrote to its cache
directory (`last-frame.wmf`, pull it with `adb shell run-as be.pascu.wristmap cat cache/last-frame.wmf`).

```
~/.local/share/uv/tools/pebble-tool/bin/python scripts/emu-send.py --demo --pattern
~/.local/share/uv/tools/pebble-tool/bin/python scripts/emu-send.py --frame last-frame.wmf
```

## Protocol

Both sides use raw integer AppMessage keys, defined in `watchapp/src/c/protocol.h` and
`android/.../pebble/Protocol.kt`. The watchapp UUID is `58b9be94-3f7b-4338-94e5-90890b2ab7a0`
and the companion package `be.pascu.wristmap` is whitelisted in `watchapp/package.json`.

Phone to watch:

| Key | Name | Type | Meaning |
|----:|------|------|---------|
| 1 | `NAV_ACTIVE` | uint8 | 1 while navigating, 0 clears the screen |
| 2 | `MANEUVER` | uint8 | fallback arrow when no icon was captured |
| 3 | `ARROW_BITMAP` | bytes | 40x40, 1 bit per pixel, 5 bytes per row, MSB first |
| 4..9 | `DISTANCE`, `STREET`, `INSTRUCTION`, `ETA`, `DIST_REMAIN`, `TIME_REMAIN` | cstring | display text |
| 20, 21 | `MAP_WIDTH`, `MAP_HEIGHT` | uint16 | frame size, sent with the first chunk |
| 22 | `MAP_FRAME` | uint8 | frame id, chunks of another frame are dropped |
| 23 | `MAP_TOTAL` | uint32 | total packed bytes |
| 24, 25 | `MAP_OFFSET`, `MAP_DATA` | uint32, bytes | one chunk |

Watch to phone:

| Key | Name | Type | Meaning |
|----:|------|------|---------|
| 40 | `HELLO` | uint8 | sent when the watchapp starts |
| 41 | `INBOX_MAX` | uint32 | `app_message_inbox_size_maximum()`, sizes the chunks |
| 42, 43 | `MAP_VIEW_WIDTH`, `MAP_VIEW_HEIGHT` | uint16 | the map area the watch has |
| 44 | `ZOOM` | uint8 | 1 zoom in, 2 zoom out |

Map frames are packed 2 bits per pixel, 4 pixels per byte, most significant bits first, rows
byte aligned, exactly the layout of `GBitmapFormat2BitPalette`. Palette: 0 white, 1 black,
2 light grey (water), 3 blue (route preview, turn, next street).

## Findings worth writing down

- **The Core Devices SDK breaks when its virtualenv and pebble-tool use different Pythons.**
  `pebble build` runs waf with `SDKs/<version>/.venv/bin/python` and `PYTHONPATH` set to
  pebble-tool's own `sys.path`. A venv created by Homebrew Python 3.14 combined with a
  pebble-tool installed under uv's Python 3.13 dies with `No module named '_struct'`. Recreate
  the venv with the same interpreter (`uv venv --python 3.13 .venv`, then install
  `freetype-py sh pypng` into it).
- **PebbleKit Android 2 1.3.0 needs `compileSdk 37` and Kotlin 2.4.** Its AAR metadata refuses
  compileSdk 36 and its classes carry Kotlin 2.4 metadata, which Kotlin 2.2 cannot read.
- **8 KB AppMessages need two things**: the phone must advertise the `Supports8kAppMessage`
  capability (the Core Devices app does) and the app must be built with SDK version 5.63 or
  later (SDK 4.33 builds emery apps as 5.106). Otherwise `app_message_inbox_size_maximum()`
  falls back to about 2 KB, so the companion always sizes chunks from the value the watch
  reports in `HELLO` instead of assuming.
- **OpenFreeMap tiles need the dated URL from the TileJSON.** `https://tiles.openfreemap.org/planet/14/x/y.pbf`
  answers 200 with an empty body. The TileJSON at `/planet` names the current tileset, for
  example `.../planet/20260830_080001_pt/{z}/{x}/{y}.pbf`. The companion caches the TileJSON
  for a day and tiles for two weeks.
- **Absolute Web Mercator coordinates do not fit in a float.** At zoom 14 with 4096 units per
  tile the world is 67 million units wide, so a float near Brussels has a resolution of 4
  units, about 3 m. Tiles keep tile-local coordinates and the renderer translates per tile.
- **Palettised Pebble bitmaps are packed most significant bits first**, unlike the legacy 1 bit
  format where bit 0 is the leftmost pixel. Verified in `raw_image_get_value_for_bitdepth()`
  in PebbleOS and on the emulator.
- **Anti-aliasing is the enemy of a 4 colour display.** With every paint set to
  `isAntiAlias = false` every pixel lands exactly on a palette entry and the map stays crisp.

## Credits

- The notification reading approach (recover the RemoteViews, walk every TextView, take the
  icon of `nav_notification_icon`) follows [3v1n0/GMapsParser](https://github.com/3v1n0/GMapsParser)
  and [konsumer/pebble-map-android](https://github.com/konsumer/pebble-map-android).
- Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, served by
  [OpenFreeMap](https://openfreemap.org) © [OpenMapTiles](https://www.openmaptiles.org/).
- [PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2) and the
  [Core Devices Pebble SDK](https://developer.repebble.com/).

## License

MIT, see `LICENSE`.
