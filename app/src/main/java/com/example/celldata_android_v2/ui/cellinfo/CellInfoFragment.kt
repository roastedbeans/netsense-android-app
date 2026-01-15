package com.example.celldata_android_v2.ui.cellinfo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.celldata_android_v2.MainAdapter
import com.example.celldata_android_v2.databinding.FragmentCellInfoBinding
import cz.mroczis.netmonster.core.model.cell.*

/**
 * CellInfoFragment
 *
 * UI component responsible for displaying real-time cellular network data.
 * This fragment manages the visual representation of collected network information
 * and handles lifecycle-aware data updates.
 *
 * Key Components:
 * - RecyclerView for efficient data display
 * - ViewModel integration for data management
 * - Permission handling
 * - Lifecycle management
 *
 * Display Features:
 * - Real-time updates of network parameters
 * - Scrollable list of cell information
 * - Optimized performance for continuous updates
 */
class CellInfoFragment : Fragment() {
    /**
     * ViewModel instance for cellular data management.
     * Handles data collection and updates.
     */
    private lateinit var cellInfoViewModel: CellInfoViewModel

    /**
     * ViewBinding instance for type-safe view access.
     * Null when fragment view is destroyed.
     */
    private var _binding: FragmentCellInfoBinding? = null
    private val binding get() = _binding!!

    /**
     * Adapter instance for RecyclerView.
     * Manages the display of cellular network data items.
     */
    private val adapter = MainAdapter()

    /**
     * Creates and initializes the fragment's UI.
     * Sets up the RecyclerView and data observation.
     *
     * Implementation:
     * 1. Initializes ViewModel
     * 2. Sets up view binding
     * 3. Configures RecyclerView
     * 4. Establishes data observation
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Get the shared ViewModel from MainActivity to ensure continuous data collection
        // regardless of which tab is visible
        val activity = requireActivity() as? com.example.celldata_android_v2.MainActivity
        cellInfoViewModel = activity?.getCellInfoViewModel() 
            ?: ViewModelProvider(this)[CellInfoViewModel::class.java]
        
        _binding = FragmentCellInfoBinding.inflate(inflater, container, false)

        setupRecyclerView()
        observeCellData()

        return binding.root
    }

    /**
     * Configures RecyclerView for optimal performance.
     *
     * Configuration:
     * - Linear layout for vertical scrolling
     * - Fixed size optimization
     * - Disabled item animations for better performance
     * - Custom adapter for cell data display
     */
    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CellInfoFragment.adapter
            // Add these lines for better scrolling performance
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    /**
     * Establishes observation of cellular data updates.
     * Updates the RecyclerView adapter when new data arrives.
     * 
     * This observer automatically triggers every 3 seconds when the ViewModel
     * collects new cell data, ensuring the UI stays synchronized with the latest information.
     */
    fun observeCellData() {
        cellInfoViewModel.cellData.observe(viewLifecycleOwner) { categorizedData ->
            // Check if we have NetMonster cells or need to use fallback
            if (categorizedData.allCells.isNotEmpty()) {
                // Use NetMonster cells
                val orderedCells = categorizedData.servingCells + categorizedData.secondaryCells + categorizedData.neighboringCells
                adapter.updateData(orderedCells)

                // Log RSRP information for monitoring
                if (categorizedData.servingCells.isNotEmpty()) {
                    val servingRsrp = categorizedData.servingCells.first().let { cell ->
                        when (cell) {
                            is CellLte -> cell.signal.rsrp?.toString() ?: "N/A"
                            is CellNr -> cell.signal.ssRsrp?.toString() ?: "N/A"
                            else -> "N/A"
                        }
                    }
                    Log.i("CellInfoFragment", "Current serving cell RSRP: $servingRsrp")
                }

                Log.i("CellInfoFragment", "Neighboring cells found: ${categorizedData.neighboringCells.size}")
            } else if (categorizedData.fallbackCells?.isNotEmpty() == true) {
                // Use fallback cells - show basic information
                Log.i("CellInfoFragment", "Using fallback Android API cells: ${categorizedData.fallbackCells.size}")

                // Show empty list for now, but we could create a simple text display
                adapter.updateData(emptyList())

                // Log detailed fallback information
                categorizedData.fallbackCells.forEach { cell ->
                    val status = if (cell.isRegistered) "SERVING" else "NEIGHBORING"
                    val signalInfo = cell.rsrp?.let { "RSRP: $it dBm" } ?: cell.rssi?.let { "RSSI: $it dBm" } ?: "No signal data"
                    Log.i("CellInfoFragment", "$status ${cell.networkType} - $signalInfo, PCI: ${cell.pci}, CellID: ${cell.cellId}")
                }

                // Show a basic message that cells were found via fallback
                Log.i("CellInfoFragment", "✅ Cell detection working! Found ${categorizedData.fallbackCells.size} cells via Android API fallback")
            } else {
                // No cells found at all
                adapter.updateData(emptyList())
                Log.w("CellInfoFragment", "No cell information available from any source")
            }
        }
    }

    /**
     * Lifecycle method: Fragment becomes visible.
     * Data collection is now managed at the Activity level, so no action needed here.
     * The ViewModel continues collecting data regardless of which tab is visible.
     */
    override fun onResume() {
        super.onResume()
        // Data collection is handled by MainActivity, so we just observe the data
        Log.d("CellInfoFragment", "Fragment resumed - observing existing data collection")
    }

    /**
     * Lifecycle method: Fragment is being paused.
     * Data collection continues at Activity level, so no action needed here.
     */
    override fun onPause() {
        super.onPause()
        // Data collection continues at Activity level, so we don't stop it here
        Log.d("CellInfoFragment", "Fragment paused - data collection continues at Activity level")
    }

    /**
     * Checks if required permissions are granted.
     * NetMonster requires BOTH ACCESS_FINE_LOCATION AND ACCESS_COARSE_LOCATION.
     */
    private fun hasPermissions(): Boolean {
        val context = requireContext()
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // For Android 10+, ACCESS_FINE_LOCATION is required
        // NetMonster works best with both permissions
        val hasLocationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            hasFineLocation // Android 10+ requires fine location
        } else {
            hasCoarseLocation || hasFineLocation // Older devices can use either
        }

        // Log permission status for debugging
        Log.d("CellInfoFragment", "Permission check - PhoneState: $hasPhoneState, FineLocation: $hasFineLocation, CoarseLocation: $hasCoarseLocation")
        
        return hasPhoneState && hasLocationPermission
    }

    /**
     * Callback for permission grant events.
     * Data collection is now managed at Activity level, so this is a no-op.
     * Kept for backward compatibility but does nothing.
     *
     * Usage:
     * Called from activity when permissions are granted by user (deprecated)
     */
    @Deprecated("Data collection is now managed at Activity level")
    fun onPermissionsGranted() {
        Log.d("CellInfoFragment", "Permissions granted - data collection handled by MainActivity")
        // No-op: Data collection is managed by MainActivity
    }

    /**
     * Lifecycle method: Fragment view is being destroyed.
     * Cleans up ViewBinding to prevent memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}