package com.example.celldata_android_v2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.celldata_android_v2.databinding.ActivityMainBinding
import com.example.celldata_android_v2.ui.cellinfo.CellInfoFragment
import com.example.celldata_android_v2.ui.cellinfo.CellInfoViewModel
import com.example.celldata_android_v2.ui.celllogger.CellLoggerFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cellInfoViewModel: CellInfoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel at Activity level so it persists across fragment changes
        // This ensures data collection runs continuously regardless of which tab is visible
        cellInfoViewModel = ViewModelProvider(this)[CellInfoViewModel::class.java]

        setupBottomNavigation()
        setupWindowInsets()
        createNotificationChannel()

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(CellInfoFragment())
        }

        checkAndRequestPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()
        if (hasRequiredPermissions()) {
            startBackgroundService()
            // Start data collection when permissions are granted
            // This runs continuously regardless of which tab is visible
            cellInfoViewModel.startUpdates()
            Log.d("MainActivity", "Started continuous data collection")
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop data collection when activity is stopped (app goes to background)
        // This conserves resources when app is not visible
        cellInfoViewModel.stopUpdates()
        Log.d("MainActivity", "Stopped data collection")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name) // Add to strings.xml: "Cell Data Collection"
            val descriptionText = getString(R.string.channel_description) // Add to strings.xml: "Background cellular data collection service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startBackgroundService() {
        val serviceIntent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START_SERVICE
        }
        startForegroundService(serviceIntent)
    }

    private fun setupBottomNavigation() {
        binding.navView.setOnItemSelectedListener { item ->
            Log.d("MainActivity", "Selected item ID: ${item.itemId}")
            when (item.itemId) {
                R.id.navigation_cell_info -> {
                    loadFragment(CellInfoFragment())
                    true
                }
                R.id.navigation_cell_logger -> {
                    loadFragment(CellLoggerFragment())
                    true
                }
                else -> {
                    loadFragment(CellInfoFragment())
                    true
                }
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            binding.fragmentContainer.updatePadding(
                top = insets.top,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // NetMonster requires BOTH ACCESS_FINE_LOCATION AND ACCESS_COARSE_LOCATION
        // Request both for maximum compatibility
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.READ_PHONE_STATE)

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Add background location for Android 10+ if needed for background service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Note: ACCESS_BACKGROUND_LOCATION requires special handling
            // Only request if user has already granted foreground location
        }

        if (!hasRequiredPermissions() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else if (hasRequiredPermissions()) {
            // Start data collection immediately when permissions are already granted
            cellInfoViewModel.startUpdates()
            Log.d("MainActivity", "Started data collection - permissions already granted")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startBackgroundService()
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        // NetMonster requires BOTH ACCESS_FINE_LOCATION AND ACCESS_COARSE_LOCATION
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // For Android 10+, ACCESS_FINE_LOCATION is required
        // For older devices, at least ACCESS_COARSE_LOCATION is needed
        val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasFineLocation // Android 10+ requires fine location
        } else {
            hasCoarseLocation || hasFineLocation // Older devices can use either
        }

        val basicPermissions = hasPhoneState && hasLocationPermission

        // Check notification permission on Android 13+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            basicPermissions && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            basicPermissions
        }
    }

    /**
     * Gets the shared ViewModel instance for use in fragments.
     * This ensures both CellInfoFragment and CellLoggerFragment observe the same data source.
     */
    fun getCellInfoViewModel(): CellInfoViewModel {
        return cellInfoViewModel
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Start data collection when permissions are granted
                    cellInfoViewModel.startUpdates()
                    Log.d("MainActivity", "Started data collection - permissions granted by user")
                    startBackgroundService()
                }
            }
            PERMISSION_REQUEST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startBackgroundService()
                }
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_NOTIFICATIONS = 101
        const val CHANNEL_ID = "netsense_service_channel"
    }
}