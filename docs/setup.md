# What you need and how to set it up

## Hardware and apps

| Piece | Requirement |
|-------|-------------|
| Watch | Pebble Time 2 (`emery`). The watchapp uses the touchscreen, so older Pebbles are not built. |
| Phone | Android 8 or newer. Tested on a Pixel 6 with Android 16. |
| Pebble app | The Core Devices Pebble app (`coredevices.coreapp`, or a fork of it). It holds the Bluetooth link to the watch and implements PebbleKit Android 2, the interface the companion uses. Nothing else implements it, so the older Rebble or Gadgetbridge apps do not work. |
| Google Maps | The regular Google Maps Android app. Nothing to configure in it. |
| Internet | The phone downloads map tiles (about 20 to 100 KB per tile, cached for two weeks). Without a connection the map falls back to whatever is cached, the turn text still works. |

Nothing has to be installed on the watch beyond the watchapp itself, and nothing is changed
on the phone beyond the permissions below.

## Install

1. **Watchapp**: open `maps-for-pebble.pbw` from the latest GitHub release in the Pebble app. The app
   asks to install it on the connected watch.
2. **Companion**: install `maps-for-pebble.apk` from the same release. Android warns about an app
   from outside the Play Store, allow it. If the earlier Wristmap build is on the phone, uninstall
   it first. It is a separate package and both would mirror the same directions.
3. Open Maps Navigation once. The setup section lists the three permissions below, with a button for
   each that is still missing, and whether a Pebble app is installed:
   - **Notification access**. Android sends every notification to Maps Navigation, which only
     acts on the one Google Maps posts while navigating and ignores the rest.
   - **Location**. Needed to draw the map around you and to estimate where the turn is.
   - **Location all the time**. Directions start while Google Maps is in front, not Maps Navigation,
     so the companion's location service starts from the background. Android only gives GPS to
     such a service when background location is allowed. Without it the map shows
     "Waiting for GPS" whenever Maps Navigation itself is not in front, which is the whole trip in
     practice.
4. Start walking or cycling directions in Google Maps. The watchapp opens by itself.

## Settings

- **Vibrate turns in Morse code**: on by default. The watch taps out the manoeuvre when an
  instruction first appears and once more 40 m before the turn. Off gives one short pulse per
  new instruction instead.

## On the watch

- Swipe up to zoom in, down to zoom out. The map follows the finger at once and the phone
  sends a sharper frame within a few hundred milliseconds.
- Any touch lights the screen for five seconds.
- Back leaves the app. The other buttons do nothing.
- The clock is at the top right while navigating and large on the idle screen.
