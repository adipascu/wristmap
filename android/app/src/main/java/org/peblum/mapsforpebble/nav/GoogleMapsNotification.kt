package org.peblum.mapsforpebble.nav

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

data class NotificationRead(
    val raw: RawNotification,
    val arrow: ByteArray?,
)

object GoogleMapsNotification {
    const val PACKAGE = "com.google.android.apps.maps"

    private val ignoredLines =
        setOf("google maps", "maps", "navigation", "exit navigation", "quitter la navigation", "navigatie afsluiten", "•", "·")
    private val iconViewNames = setOf("nav_notification_icon", "right_icon", "lockscreen_notification_icon")
    private val extraKeys =
        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        )

    fun isFromMaps(sbn: StatusBarNotification): Boolean = sbn.packageName == PACKAGE

    fun isNavigation(sbn: StatusBarNotification): Boolean = isFromMaps(sbn) && sbn.isOngoing

    fun read(
        context: Context,
        sbn: StatusBarNotification,
    ): NotificationRead? {
        val lines = LinkedHashSet<String>()
        var icon: Bitmap? = null

        sbn.notification.extras?.let { extras ->
            for (key in extraKeys) addLine(lines, extras.getCharSequence(key))
        }

        try {
            val mapsContext = context.createPackageContext(sbn.packageName, Context.CONTEXT_IGNORE_SECURITY)
            val builder = Notification.Builder.recoverBuilder(context, sbn.notification)
            val views = builder.createBigContentView() ?: builder.createContentView()
            if (views != null) {
                val inflater = mapsContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val group = inflater.inflate(views.layoutId, null) as? ViewGroup
                if (group != null) {
                    @Suppress("DEPRECATION")
                    views.reapply(mapsContext, group)
                    icon = walk(mapsContext, group, lines)
                }
            }
        } catch (e: Throwable) {
            icon = null
        }

        if (icon == null) {
            icon =
                runCatching {
                    sbn.notification
                        .getLargeIcon()
                        ?.loadDrawable(context)
                        ?.let { toBitmap(it) }
                }.getOrNull()
        }

        if (lines.isEmpty()) return null
        val rerouting = lines.any { it.contains("rerout", ignoreCase = true) || it.contains("recalcul", ignoreCase = true) }
        return NotificationRead(RawNotification(lines.toList(), rerouting), icon?.let { packArrow(it) })
    }

    private fun packArrow(source: Bitmap): ByteArray? =
        try {
            val software = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
            val scaled = Bitmap.createScaledBitmap(software, ManeuverIcon.SIZE, ManeuverIcon.SIZE, true)
            val pixels = IntArray(ManeuverIcon.SIZE * ManeuverIcon.SIZE)
            scaled.getPixels(pixels, 0, ManeuverIcon.SIZE, 0, 0, ManeuverIcon.SIZE, ManeuverIcon.SIZE)
            if (scaled !== software) scaled.recycle()
            if (software !== source) software.recycle()
            ManeuverIcon.packPixels(pixels)
        } catch (e: Throwable) {
            null
        }

    private fun addLine(
        into: MutableSet<String>,
        text: CharSequence?,
    ) {
        val value = text?.toString()?.trim().orEmpty()
        if (value.isNotEmpty() && value.lowercase() !in ignoredLines) into.add(value)
    }

    private fun walk(
        mapsContext: Context,
        view: View,
        lines: MutableSet<String>,
    ): Bitmap? {
        var icon: Bitmap? = null
        when (view) {
            is TextView -> {
                addLine(lines, view.text)
            }

            is ImageView -> {
                if (entryName(mapsContext, view.id) in iconViewNames) {
                    icon = view.drawable?.let { toBitmap(it) }
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = walk(mapsContext, view.getChildAt(i), lines)
                if (icon == null) icon = child
            }
        }
        return icon
    }

    private fun entryName(
        context: Context,
        id: Int,
    ): String? =
        try {
            if (id > 0) context.resources.getResourceEntryName(id) else null
        } catch (e: Exception) {
            null
        }

    private fun toBitmap(drawable: Drawable): Bitmap? =
        try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 48
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 48
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Throwable) {
            null
        }
}
