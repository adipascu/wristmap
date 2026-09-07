package be.pascu.mapsforpebble.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import be.pascu.mapsforpebble.Navigator
import be.pascu.mapsforpebble.nav.GoogleMapsNotification

class NavNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Navigator.onListenerConnected()
        runCatching { activeNotifications }.getOrNull()?.forEach { onNotificationPosted(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && GoogleMapsNotification.isNavigation(sbn)) {
            Navigator.onMapsNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && GoogleMapsNotification.isFromMaps(sbn)) {
            Navigator.onMapsNotificationRemoved(sbn.key)
        }
    }
}
