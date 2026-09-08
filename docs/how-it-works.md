# How it works

Google Maps has no API for a running navigation. The only thing it exposes is the ongoing
notification it posts while navigating, which carries the next instruction as text and the
manoeuvre icon. Everything else Maps Navigation shows is built on the phone from that notification,
the phone's GPS and OpenStreetMap data, and pushed to the watch as a bitmap.

```
Google Maps ──notification──▶ NavNotificationListenerService
                                      │ Event.Posted
                                      ▼
NavigationService ──GPS fix──▶     Navigator  ◀── WatchListenerService (hello, zoom level)
                                      │
                    ┌─────────────────┼──────────────────┐
                    ▼                 ▼                  ▼
              GoogleMaps         RoutePreview        MapRenderer ─▶ FrameEncoder
              Notification +     (walk the road      (heading-up      (2 bit per
              NavParser           graph, snap to      4 colour         pixel rows)
                                  the named street)   canvas)              │
                    └─────────────────┴──────────────────┴────────────────┘
                                      │ WatchLink, PebbleKit Android 2
                                      ▼
                              Pebble app (Bluetooth)
                                      ▼
                         Maps Navigation watchapp on the Pebble Time 2
```

## The companion, piece by piece

All of it lives in `android/app/src/main/java/be/pascu/mapsforpebble`.

**`Navigator`** is the process-wide coordinator. Every external event (a Maps notification
posted or removed, the location service starting, stopping or failing) is queued into one
channel and handled in order by a single coroutine under a session mutex, so a stale
notification can never overwrite a fresher one and a service stop cannot be reordered with a
start. Consecutive posts for the same notification are folded so a slow Bluetooth link never
builds a backlog. GPS fixes and watch messages only set state and ask for a new frame.

**The frame loop** renders and sends at most one frame every 1.5 s, or every 250 ms after a
zoom request. Each frame: pick the zoom, load the tiles around the position, build the route
preview, draw the map, pack it, send it. Missing tiles are fetched in the background and the
loop is poked again when they land, so a frame never waits on the network.

**`GoogleMapsNotification` and `NavParser`** turn the notification into a `NavState`
(distance, instruction, street, arrival time, manoeuvre). See [google-maps.md](google-maps.md).

**`TileStore` and `Mvt`** download OpenFreeMap vector tiles at zoom 14 (each about 1.5 km
across at Brussels' latitude, 2.4 km at the equator), decode the Mapbox Vector Tile protobuf with a small hand-written
reader, and keep the roads, named roads, water and waterways of each tile as float arrays in
tile-local coordinates. Tiles are cached on disk for two weeks and in memory within an 8 MB
budget. The TileJSON at `https://tiles.openfreemap.org/planet` names the current tileset.

**`RoadGraph`** turns the loaded road geometry into a graph. Every vertex becomes a node, and
two vertices at the same position become the same node, which is what OpenStreetMap gives at a
real junction. On top of that, a way that ends within a metre and a half of the middle of
another way splits it and joins there, which is how a side street meeting a road between its
vertices becomes a junction. Positions are kept relative to the corner of the loaded tiles so
the graph survives from frame to frame, and it is rebuilt only when the set of tiles changes.
It answers shortest path queries from one node under a cost ceiling, with one node blocked.

**`RoutePreview`** estimates where the next turn is. Google says "in 200 m, turn left onto
Rue X" and never gives the route. The companion snaps the GPS position to the nearest road
(preferring one that runs in the direction of travel), then asks the graph for the shortest way
to any node within 25 m of a road named "Rue X". It leaves by the nearest node ahead and blocks
the nearest node behind, so the line cannot set off backwards through you. The candidate whose
length is closest to the announced 200 m wins, as long as it is within 35 per cent or 30 m of
it, and its geometry becomes the blue line. Google measures its distance along its own route,
so a path of about the right length ending on the right street is usually that route, turns
included. Past the turn the line carries on along the named street for 60 m, on the side the
manoeuvre implies. With no match it falls back to following the current road for the announced
distance, continuing through intersections onto the road that keeps the same heading within 80
degrees, and snapping the marker onto a matching street within 60 m. It also names the road you
are on. Off the road network it draws a straight line ahead instead.

The search knows the shape of the network and nothing else about it. Stairs are left out, since
no route is drawn up a staircase, but the tiles carry no direction of travel and no turn
restrictions, so a one way street is walkable either way here and a shortest path can take a
turn that is not allowed. The line is a good reconstruction, not Google's own geometry.

**`MapRenderer`** draws a heading-up map on an Android canvas with anti-aliasing off so every
pixel is exactly one of four colours: white, black (roads with white cores by class), light
grey (water) and blue (the path to the turn, the turn marker and the named street). It adds the
position triangle, a scale bar, a compass and the current road name. **`FrameEncoder`** packs
it as 2 bits per pixel, most significant bits first, which is exactly the watch's
palettised bitmap layout, so the watch copies rows straight into a `GBitmap`.

**`WatchLink`** sends everything over PebbleKit Android 2. The watch reports its inbox size
when it starts (8,200 bytes on the Pebble Time 2 with the Core Devices app), so a whole
7,600 byte frame goes in one message and arrives in 200 to 400 ms. Smaller inboxes get the
frame in ordered chunks. Every call is raced against a 20 s timeout because the PebbleKit
binder call itself cannot be cancelled.

**`AutoZoom`** picks the zoom (14 to 18) so the turn is on screen, capped at 17 above 4 m/s
so cycling gets a wider view. A swipe on the watch overrides it for the rest of the trip.

**`MorseCue`** builds the haptic pattern for a manoeuvre, see the table in the README.

**`SunAltitude`** works out how high the sun stands over a position at a moment, from the
standard low precision solar model, and calls it dark below six degrees under the horizon, the
civil twilight threshold. `Navigator` recomputes it on every fix and only asks the watch to
keep the backlight on once it is dark, so the setting costs nothing in daylight.

## The watchapp

`watchapp/src/c`, C against the Core Devices SDK 4.33.

- `nav_state.c` keeps the text fields and the 40x40 arrow icon, and reports which changed.
- `map_frame.c` receives frames: a header with size, frame id, total bytes and zoom, then
  chunks that must arrive in order. A complete frame is copied into a palettised `GBitmap`.
  While the finger is down it also resamples the current frame around the position marker so
  the zoom reacts before the phone has rendered anything.
- `main.c` subscribes to the raw touch stream (zoom and backlight), the minute tick (clock)
  and AppMessage, and sends the hello and the zoom level. While the phone asks for the
  backlight to stay on it also samples the accelerometer at 25 Hz and lights the screen
  whenever the watch is held to be read. That takes two axes: the face has to point up, with
  the averaged z axis below minus 500 mg, and the wrist has to be raised, with the averaged y
  axis below minus 200 mg. Either one relaxing past minus 350 mg or minus 80 mg darkens it
  again, so an arm at rest is off while a glance at the map is on. The gaps
  between the pairs keep the last state so a bump does not flicker the light, and five samples
  per callback put the reaction at about a fifth of a second.
- `ui.c` draws the three bands: arrow, distance, street and clock on top, the map, and at the
  bottom the time and distance left on the left with the arrival time on the right.
- `haptics.c` plays a received vibration pattern.
- `arrows.c` draws the vector fallback arrows when no icon was captured.

## Why a bitmap and not vector data on the watch

A z14 tile has thousands of road segments. Decoding protobuf, clipping, rotating and rasterising
that on the watch every second would be slow and would need memory the app does not have,
while the phone does it in a few milliseconds. One 7.6 KB frame every 1.5 s, or every 250 ms while zooming, is well within what
the Bluetooth link carries, and it keeps the watchapp small and dumb: it never has to know what
a road is.
