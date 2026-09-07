package be.pascu.mapsforpebble

import android.content.Context
import android.content.SharedPreferences

class Preferences(
    context: Context,
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("maps_for_pebble", Context.MODE_PRIVATE)

    var hapticCues: Boolean
        get() = preferences.getBoolean(HAPTIC_CUES, true)
        set(value) = preferences.edit().putBoolean(HAPTIC_CUES, value).apply()

    var keepBacklightOnWhileCycling: Boolean
        get() = preferences.getBoolean(KEEP_BACKLIGHT_ON_WHILE_CYCLING, true)
        set(value) = preferences.edit().putBoolean(KEEP_BACKLIGHT_ON_WHILE_CYCLING, value).apply()

    companion object {
        private const val HAPTIC_CUES = "haptic_cues"
        private const val KEEP_BACKLIGHT_ON_WHILE_CYCLING = "keep_backlight_on_while_cycling"
    }
}
