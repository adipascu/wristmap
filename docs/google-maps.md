# How Google Maps is read

There is no API. Google Maps posts an ongoing notification while it navigates (id 1, category
navigation, ongoing) and updates it on every change of distance or instruction. Wristmap is a
`NotificationListenerService`, which is the Android mechanism apps such as smartwatch
companions use to see notifications. Android requires the user to grant it explicitly.

## Reading one notification

`GoogleMapsNotification.read` gathers text from two places and the icon from one:

1. The notification extras (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT` and friends).
2. The notification's custom layout. Google Maps uses `RemoteViews`. Wristmap recovers the
   builder, inflates the big content view in Google Maps' own resource context, reapplies the
   remote views, and walks the resulting view tree collecting every `TextView`'s text. The
   `ImageView` named `nav_notification_icon` (or `right_icon`, or the lock screen variant)
   holds the manoeuvre arrow, which is captured as a bitmap. If that fails the large icon is
   used.

Nothing depends on the exact layout, only on text content, which is why the reader has
survived Google's layout changes. The approach follows GMapsParser and Maps Nav.

## Interpreting the text

`NavParser` classifies each line by what it looks like rather than where it came from:

- A distance on its own ("200 m", "1,2 km", "350 ft", "0.3 mi", with thousands separators
  such as "1,000 ft" handled) is the distance to the turn.
- "distance · instruction" on one line is split.
- A line with a clock time, a duration and a distance ("14 min · 2.1 km · 10:45") is the trip
  summary. A line that is only a clock time, such as "Arrive 11:55 PM", is the arrival time.
- Everything else is the instruction. The street is what follows "onto", "toward", "sur",
  "vers", "op", "naar", "richting" and the German equivalents. The manoeuvre is guessed from
  English, French, Dutch and German keywords, but the arrow the watch shows is Google's own
  icon, so the guess only matters for the Morse cue and the fallback arrow.
- Google's action labels ("Exit navigation" and its translations) and bullet glyphs are
  ignored.

A real walking notification in Brussels looks like this, three text views:

```
140 m · Turn right onto Rue du Houblon/Hopstraat
Arrive 11:55 PM
Exit navigation
```

## Start, update, end

- The first plausible Maps navigation notification (id 1, or one with a distance, or one with
  an icon) starts a session: the location service starts, the watchapp is launched and the
  state is pushed.
- Every update is pushed to the watch if the instruction changed, and a new frame is requested.
  The watchapp is only relaunched when a new instruction arrives, so pressing back on the
  watch to look at something else is respected until the next turn.
- Removing the notification (ending navigation in Maps) stops the session: the watch is told to
  clear, and two seconds later the watchapp is closed unless a new session started meanwhile.

## What Google Maps does not give

- The route. The turn position on the map is an estimate along the road network.
- The mode (walking, cycling, driving). Wristmap does not care, it mirrors whatever is
  navigating. Cycling only differs in the zoom cap above 4 m/s.
- Lane guidance and the full step list.
- Anything on iOS. Notifications are sandboxed there, so this stays Android only.
