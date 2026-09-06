package be.pascu.wristmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.rebble.pebblekit2.common.PebbleKitIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var notificationAccess: TextView
    private lateinit var locationAccess: TextView
    private lateinit var backgroundLocationAccess: TextView
    private lateinit var grantBackgroundLocation: Button
    private lateinit var pebbleApp: TextView
    private lateinit var navStatus: TextView
    private lateinit var linkStatus: TextView
    private lateinit var preview: ImageView
    private lateinit var previewEmpty: TextView
    private lateinit var grantNotificationAccess: Button
    private lateinit var grantLocation: Button
    private var shownPreviewVersion = -1

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        val content = findViewById<View>(R.id.content)
        val basePadding = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(basePadding + bars.left, basePadding + bars.top, basePadding + bars.right, basePadding + bars.bottom)
            insets
        }
        notificationAccess = findViewById(R.id.notification_access)
        locationAccess = findViewById(R.id.location_access)
        backgroundLocationAccess = findViewById(R.id.background_location_access)
        grantBackgroundLocation = findViewById(R.id.grant_background_location)
        pebbleApp = findViewById(R.id.pebble_app)
        navStatus = findViewById(R.id.nav_status)
        linkStatus = findViewById(R.id.link_status)
        preview = findViewById(R.id.preview)
        previewEmpty = findViewById(R.id.preview_empty)
        grantNotificationAccess = findViewById(R.id.grant_notification_access)
        grantLocation = findViewById(R.id.grant_location)

        grantNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        grantLocation.setOnClickListener {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionRequest.launch(permissions.toTypedArray())
        }
        grantBackgroundLocation.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
        findViewById<Switch>(R.id.haptic_cues).apply {
            isChecked = Navigator.preferences.hapticCues
            setOnCheckedChangeListener { _, checked -> Navigator.preferences.hapticCues = checked }
        }
        findViewById<Button>(R.id.send_demo).setOnClickListener { Navigator.sendDemo() }
        findViewById<Button>(R.id.stop_demo).setOnClickListener { Navigator.stopDemo() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Navigator.status.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notificationAccess.text = getString(if (listenerEnabled) R.string.notification_access_granted else R.string.notification_access_missing)
        grantNotificationAccess.visibility = if (listenerEnabled) View.GONE else View.VISIBLE
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        locationAccess.text = getString(if (locationGranted) R.string.location_granted else R.string.location_missing)
        grantLocation.visibility = if (locationGranted) View.GONE else View.VISIBLE
        val backgroundGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        backgroundLocationAccess.text = getString(if (backgroundGranted) R.string.background_location_granted else R.string.background_location_missing)
        backgroundLocationAccess.visibility = if (locationGranted) View.VISIBLE else View.GONE
        grantBackgroundLocation.visibility = if (locationGranted && !backgroundGranted) View.VISIBLE else View.GONE
        val pebbleApps = packageManager.queryIntentServices(Intent(PebbleKitIntents.SEND_DATA), 0).map { it.serviceInfo.packageName }.distinct()
        pebbleApp.text = if (pebbleApps.isEmpty()) getString(R.string.pebble_app_missing) else getString(R.string.pebble_app_found, pebbleApps.joinToString())
    }

    private fun render(status: Navigator.Status) {
        val state = status.navState
        navStatus.text = buildString {
            append(if (status.listenerConnected) getString(R.string.listener_connected) else getString(R.string.listener_waiting))
            append('\n')
            if (state.active) {
                append(getString(R.string.navigating)).append('\n')
                append("${state.distance}  ${state.instruction}").append('\n')
                append("${state.timeRemain}  ${state.distRemain}  ${state.eta}").append('\n')
                append(getString(R.string.raw_lines)).append(' ').append(status.rawLines.joinToString(" | "))
            } else {
                append(getString(R.string.idle))
            }
        }
        linkStatus.text = listOf(status.serviceText, status.locationText, status.watchText, status.navSend, status.frameText)
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (status.previewVersion != shownPreviewVersion) {
            shownPreviewVersion = status.previewVersion
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(Navigator.previewFile.path) }
                if (bitmap != null) {
                    preview.setImageDrawable(BitmapDrawable(resources, bitmap).apply { isFilterBitmap = false })
                    previewEmpty.visibility = View.GONE
                }
            }
        }
    }
}
