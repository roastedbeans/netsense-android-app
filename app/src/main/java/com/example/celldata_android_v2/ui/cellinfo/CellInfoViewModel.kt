@file:Suppress("DEPRECATION") // CDMA APIs deprecated but needed for fallback support on older devices

package com.example.celldata_android_v2.ui.cellinfo

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.celldata_android_v2.data.CellDataCache
import com.example.celldata_android_v2.data.CellInfoEntity
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.cell.*
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import cz.mroczis.netmonster.core.model.connection.SecondaryConnection
import cz.mroczis.netmonster.core.model.connection.NoneConnection
import cz.mroczis.netmonster.core.model.signal.SignalLte
import cz.mroczis.netmonster.core.model.signal.SignalNr
import cz.mroczis.netmonster.core.model.signal.SignalCdma
import cz.mroczis.netmonster.core.model.signal.SignalGsm
import cz.mroczis.netmonster.core.model.signal.SignalWcdma
import cz.mroczis.netmonster.core.model.signal.SignalTdscdma

/**
 * Simplified cell data structure for fallback Android API usage
 */
data class SimpleCellData(
    val networkType: String,
    val isRegistered: Boolean,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssi: Int?,
    val snr: Int?,
    val pci: Int?,
    val cellId: String?
)

/**
 * Data class to hold categorized cell information
 */
data class CategorizedCellData(
    val servingCells: List<ICell>,
    val neighboringCells: List<ICell>,
    val secondaryCells: List<ICell>,
    val allCells: List<ICell>,
    val fallbackCells: List<SimpleCellData>? = null // For when NetMonster fails
)

/**
 * CellInfoViewModel
 *
 * Core data collection component for cellular network analysis. This ViewModel manages
 * real-time collection of cellular network data across different network technologies,
 * focusing on continuous monitoring and data updates for research purposes.
 *
 * Key Features:
 * - Periodic data collection (3-second intervals)
 * - Permission management for secure data access
 * - Error handling and logging
 * - Lifecycle-aware data management
 * - Categorization of serving vs neighboring cells
 *
 * Research Applications:
 * - Network behavior monitoring
 * - Signal strength analysis (RSRP focus)
 * - Cell tower tracking
 * - Connection state analysis
 */
class CellInfoViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * LiveData containing categorized cell information.
     * Separates serving cells, neighboring cells, and secondary cells.
     *
     * Usage:
     * viewModel.cellData.observe(lifecycleOwner) { categorizedData ->
     *     // Process serving cells: categorizedData.servingCells
     *     // Process neighboring cells: categorizedData.neighboringCells
     *     // Process secondary cells: categorizedData.secondaryCells
     * }
     */
    private val _cellData = MutableLiveData<CategorizedCellData>()
    val cellData: LiveData<CategorizedCellData> = _cellData

    /**
     * Handler for managing periodic updates on the main thread.
     * Used to schedule regular data collection every 3 seconds (REFRESH_INTERVAL).
     * Uses polling approach that works reliably on all Android versions.
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * NetMonster instance for accessing cellular network information.
     * Provides access to detailed cell information across different technologies.
     */
    private val netMonster = NetMonsterFactory.get(getApplication())

    /**
     * TelephonyManager instance for accessing cell information.
     */
    private val telephonyManager: TelephonyManager by lazy {
        getApplication<Application>().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private var isUpdating = false

    /**
     * Verifies required permissions for data collection.
     *
     * NetMonster requires ALL of these permissions:
     * - ACCESS_FINE_LOCATION: Required for cell information access
     * - ACCESS_COARSE_LOCATION: Also required by NetMonster library
     * - READ_PHONE_STATE: For detailed cell information
     *
     * @return Boolean indicating if all required permissions are granted
     */
    private fun hasRequiredPermissions(): Boolean {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        // NetMonster requires BOTH location permissions
        val hasFineLocation = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // For Android 10+, ACCESS_FINE_LOCATION is mandatory
        // NetMonster works best with both permissions
        val hasLocationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            hasFineLocation // Android 10+ requires fine location
        } else {
            hasCoarseLocation || hasFineLocation // Older devices can use either, but prefer both
        }

        return hasPhoneState && hasLocationPermission
    }

    /**
     * Initiates periodic data collection.
     * Uses polling approach that works on all Android versions.
     * Note: Modern API 29+ callback-based approach can be added later with proper reflection handling.
     *
     * Usage:
     * viewModel.startUpdates()
     */
    fun startUpdates() {
        if (!isUpdating) {
            isUpdating = true
            updateData()
        }
    }

    /**
     * Processes cell info updates from both callback and polling methods.
     */
    private fun processCellInfoUpdate(cellInfos: List<CellInfo>?) {
        if (cellInfos.isNullOrEmpty()) {
            // Try NetMonster if Android API returns empty
            updateData()
            return
        }

        // Convert Android CellInfo to SimpleCellData
        val fallbackCells = convertCellInfoToSimpleData(cellInfos)
        
        // Try NetMonster first, then use fallback
        try {
            val netMonsterCells = netMonster.getCells()
            if (netMonsterCells.isNotEmpty()) {
                val categorizedData = categorizeCells(netMonsterCells)
                _cellData.postValue(categorizedData)
                Log.d("CellInfo", "Updated from NetMonster: ${netMonsterCells.size} cells")
            } else {
                // Use Android API fallback
                val categorizedData = CategorizedCellData(
                    servingCells = emptyList(),
                    neighboringCells = emptyList(),
                    secondaryCells = emptyList(),
                    allCells = emptyList(),
                    fallbackCells = fallbackCells
                )
                _cellData.postValue(categorizedData)
                Log.d("CellInfo", "Updated from Android API: ${fallbackCells.size} cells")
            }
        } catch (e: Exception) {
            Log.e("CellInfo", "Error processing cell info", e)
        }

            // Schedule next update
            handler.postDelayed({ updateData() }, REFRESH_INTERVAL)
    }

    /**
     * Converts Android CellInfo list to SimpleCellData list.
     */
    private fun convertCellInfoToSimpleData(cellInfos: List<CellInfo>): List<SimpleCellData> {
        val cells = mutableListOf<SimpleCellData>()
        for (cellInfo in cellInfos) {
            when (cellInfo) {
                is CellInfoLte -> {
                    val signalStrength = cellInfo.cellSignalStrength
                    cells.add(SimpleCellData(
                        networkType = "LTE",
                        isRegistered = cellInfo.isRegistered,
                        rsrp = signalStrength.rsrp.takeIf { it != CellInfo.UNAVAILABLE },
                        rsrq = signalStrength.rsrq.takeIf { it != CellInfo.UNAVAILABLE },
                        rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                        snr = signalStrength.rssnr.takeIf { it != CellInfo.UNAVAILABLE },
                        pci = cellInfo.cellIdentity.pci.takeIf { it != CellInfo.UNAVAILABLE },
                        cellId = cellInfo.cellIdentity.ci.takeIf { it != CellInfo.UNAVAILABLE }?.toString()
                    ))
                }
                is CellInfoNr -> {
                    val signalStrength = cellInfo.cellSignalStrength
                    val identity = cellInfo.cellIdentity as CellIdentityNr
                    cells.add(SimpleCellData(
                        networkType = "5G NR",
                        isRegistered = cellInfo.isRegistered,
                        rsrp = null, // NR uses SS-RSRP which may not be accessible
                        rsrq = null,
                        rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                        snr = null,
                        pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
                        cellId = identity.nci.takeIf { it != CellInfo.UNAVAILABLE.toLong() }?.toString()
                    ))
                }
                // Add other cell types as needed...
            }
        }
        return cells
    }

    /**
     * Stops the data collection process.
     * Cleans up handler callbacks and stops updates.
     *
     * Usage:
     * viewModel.stopUpdates()
     */
    fun stopUpdates() {
        isUpdating = false
        handler.removeCallbacksAndMessages(null)
        Log.d("CellInfo", "Stopped cell info updates")
    }

    /**
     * Core data collection function.
     * Performs periodic collection of cell information with error handling.
     *
     * Implementation:
     * 1. Checks permissions
     * 2. Collects cell data using NetMonster
     * 3. Updates LiveData with new information
     * 4. Schedules next update
     * 5. Handles potential errors
     *
     * Error Handling:
     * - SecurityException: Permission-related errors
     * - General Exception: Other potential errors
     */
    fun updateData() {
        if (!isUpdating) return

        if (hasRequiredPermissions()) {
            try {
                Log.d("CellInfo", "Attempting to get cells from NetMonster...")
                Log.d("CellInfo", "Permissions - FineLocation: ${ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}, CoarseLocation: ${ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
                
                var cells = try {
                    netMonster.getCells()
                } catch (e: SecurityException) {
                    Log.e("CellInfo", "SecurityException from NetMonster.getCells() - missing permissions", e)
                    emptyList()
                } catch (e: Exception) {
                    Log.e("CellInfo", "Exception from NetMonster.getCells()", e)
                    emptyList()
                }
                
                Log.d("CellInfo", "NetMonster returned ${cells.size} cells")

                // If NetMonster returns no cells, try fallback to native Android APIs
                var fallbackCells: List<SimpleCellData>? = null
                if (cells.isEmpty()) {
                    Log.w("CellInfo", "NetMonster returned no cells, trying native Android APIs...")
                    fallbackCells = getCellsFromAndroidAPI()
                    
                    // If we have fallback cells, categorize them properly
                    if (!fallbackCells.isNullOrEmpty()) {
                        val servingCells = fallbackCells.filter { it.isRegistered }
                        val neighboringCells = fallbackCells.filter { !it.isRegistered }
                        Log.d("CellInfo", "Fallback: ${servingCells.size} serving, ${neighboringCells.size} neighboring")
                    }
                }

                // Categorize cells
                val categorizedData = if (fallbackCells != null && cells.isEmpty()) {
                    // Use fallback cells when NetMonster fails
                    CategorizedCellData(
                        servingCells = emptyList(), // NetMonster cells (empty)
                        neighboringCells = emptyList(), // NetMonster cells (empty)
                        secondaryCells = emptyList(), // NetMonster cells (empty)
                        allCells = emptyList(), // NetMonster cells (empty)
                        fallbackCells = fallbackCells
                    )
                } else {
                    categorizeCells(cells).copy(fallbackCells = fallbackCells)
                }
                
                // Save all collected cells to cache for Cell Logger tab
                // This ensures data is available even when Cell Info tab is not visible
                if (cells.isNotEmpty()) {
                    cells.forEach { cell ->
                        val entity = convertCellToEntity(cell)
                        CellDataCache.addCellInfo(entity)
                        
                        // Log signal strength availability for neighboring cells
                        if (cell.connectionStatus is NoneConnection) {
                            val signalInfo = when (cell) {
                                is CellLte -> "RSRP: ${cell.signal.rsrp ?: "N/A"}, RSRQ: ${cell.signal.rsrq ?: "N/A"}, RSSI: ${cell.signal.rssi ?: "N/A"}"
                                is CellNr -> "SS-RSRP: ${cell.signal.ssRsrp ?: "N/A"}, SS-RSRQ: ${cell.signal.ssRsrq ?: "N/A"}, CSI-RSRP: ${cell.signal.csiRsrp ?: "N/A"}"
                                is CellWcdma -> "RSCP: ${cell.signal.rscp ?: "N/A"}, Ec/No: ${cell.signal.ecno ?: "N/A"}, RSSI: ${cell.signal.rssi ?: "N/A"}"
                                is CellGsm -> "RSSI: ${cell.signal.rssi ?: "N/A"}, BER: ${cell.signal.bitErrorRate ?: "N/A"}"
                                is CellCdma -> "CDMA RSSI: ${cell.signal.cdmaRssi ?: "N/A"}, EVDO RSSI: ${cell.signal.evdoRssi ?: "N/A"}"
                                else -> "Unknown signal type"
                            }
                            Log.d("CellInfo", "Neighboring ${cell.javaClass.simpleName} cell - PCI: ${when(cell) { is CellLte -> cell.pci; is CellNr -> cell.pci; is CellWcdma -> cell.psc; else -> "N/A"}}, Signal: $signalInfo")
                        }
                    }
                    Log.d("CellInfo", "Saved ${cells.size} cells to cache for Cell Logger (${categorizedData.neighboringCells.size} neighboring)")
                }
                
                _cellData.postValue(categorizedData)

                Log.d("CellInfo", "Updated cells - Total: ${cells.size}, Serving: ${categorizedData.servingCells.size}, Neighboring: ${categorizedData.neighboringCells.size}, Secondary: ${categorizedData.secondaryCells.size}")

                if (cells.isEmpty() && fallbackCells?.isEmpty() != false) {
                    Log.w("CellInfo", "No cells found from any method! This might indicate:")
                    Log.w("CellInfo", "- No SIM card inserted")
                    Log.w("CellInfo", "- Device has no cellular radio")
                    Log.w("CellInfo", "- Running on emulator without cellular support")
                    Log.w("CellInfo", "- Device is in airplane mode")
                    Log.w("CellInfo", "- No cellular signal available")
                    Log.w("CellInfo", "- Android 15 compatibility issues with cell APIs")
                } else {
                    // Log NetMonster cell information
                    if (cells.isNotEmpty()) {
                        categorizedData.servingCells.forEach { cell ->
                            val rsrp = getCellRsrp(cell)
                            Log.d("CellInfo", "Serving cell RSRP: $rsrp, Network: ${cell.network}, Band: ${cell.band}")
                        }

                        categorizedData.neighboringCells.forEach { cell ->
                            val rsrp = getCellRsrp(cell)
                            Log.d("CellInfo", "Neighboring cell RSRP: $rsrp, Network: ${cell.network}, Band: ${cell.band}")
                        }
                    }

                    // Log fallback cell information
                    fallbackCells?.forEach { cell ->
                        val status = if (cell.isRegistered) "SERVING" else "NEIGHBORING"
                        val rsrpInfo = cell.rsrp?.let { "RSRP: $it dBm" } ?: cell.rssi?.let { "RSSI: $it dBm" } ?: "No signal data"
                        Log.d("CellInfo", "$status ${cell.networkType} cell - $rsrpInfo, PCI: ${cell.pci}, CellID: ${cell.cellId}")
                    }
                }
            } catch (securityException: SecurityException) {
                Log.e("CellInfo", "Security exception when accessing cell data", securityException)
                stopUpdates()
            } catch (e: Exception) {
                Log.e("CellInfo", "Error updating cells", e)
                e.printStackTrace()
            }

            handler.postDelayed({ updateData() }, REFRESH_INTERVAL)
            } else {
            Log.w("CellInfo", "Required permissions not granted - checking individual permissions:")
            val fineLocation = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseLocation = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val phoneState = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            Log.w("CellInfo", "ACCESS_FINE_LOCATION: $fineLocation")
            Log.w("CellInfo", "ACCESS_COARSE_LOCATION: $coarseLocation")
            Log.w("CellInfo", "READ_PHONE_STATE: $phoneState")
            Log.w("CellInfo", "⚠️ NetMonster requires BOTH ACCESS_FINE_LOCATION AND ACCESS_COARSE_LOCATION")
            stopUpdates()
        }
    }

    /**
     * Categorizes cells into serving, neighboring, and secondary cells
     */
    private fun categorizeCells(cells: List<ICell>): CategorizedCellData {
        val servingCells = cells.filter { it.connectionStatus is PrimaryConnection }
        val neighboringCells = cells.filter { it.connectionStatus is NoneConnection }
        val secondaryCells = cells.filter { it.connectionStatus is SecondaryConnection }

        return CategorizedCellData(
            servingCells = servingCells,
            neighboringCells = neighboringCells,
            secondaryCells = secondaryCells,
            allCells = cells
        )
    }

    /**
     * Extracts RSRP value from a cell, handling different network types
     */
    private fun getCellRsrp(cell: ICell): String {
        return when (cell) {
            is CellLte -> cell.signal.rsrp?.toString() ?: "N/A"
            is CellNr -> cell.signal.ssRsrp?.toString() ?: "N/A"
            else -> "N/A"
        }
    }

    /**
     * Converts ICell to CellInfoEntity for storage in cache.
     * This ensures Cell Logger tab receives data even when Cell Info tab is not visible.
     */
    private fun convertCellToEntity(cell: ICell): CellInfoEntity {
        return when (cell) {
            is CellLte -> {
                val signal = cell.signal
                val network = cell.network
                val band = cell.band
                CellInfoEntity(
                    net = "LTE",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    signalStrength = calculateLteSignalStrength(signal),
                    timestamp = cell.timestamp?.toString() ?: "",
                    frequency = band?.downlinkEarfcn?.toString() ?: "",
                    bandWidth = cell.bandwidth?.toString() ?: "",
                    mcc = network?.mcc ?: "",
                    mnc = network?.mnc ?: "",
                    iso = network?.iso ?: "",
                    eci = cell.eci?.toString() ?: "",
                    eNb = cell.enb?.toString() ?: "",
                    cid = cell.cid?.toString() ?: "",
                    tac = cell.tac?.toString() ?: "",
                    pci = cell.pci?.toString() ?: "",
                    rssi = signal.rssi?.toString() ?: "",
                    rsrp = signal.rsrp?.toString() ?: "",
                    sinr = signal.snr?.toString() ?: "",
                    rsrq = signal.rsrq?.toString() ?: "",
                    ssRsrp = "N/A",
                    ssRsrq = "N/A",
                    ssSinr = "N/A",
                    csiRsrp = "N/A",
                    csiRsrq = "N/A",
                    csiSinr = "N/A",
                )
            }
            is CellNr -> {
                val signal = cell.signal
                val network = cell.network
                val band = cell.band
                CellInfoEntity(
                    net = "5G NR",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    signalStrength = calculateNrSignalStrength(signal),
                    timestamp = cell.timestamp?.toString() ?: "",
                    frequency = band?.downlinkFrequency?.toString() ?: "",
                    bandWidth = estimateBandwidthFromArfcn(band?.downlinkArfcn),
                    mcc = network?.mcc ?: "",
                    mnc = network?.mnc ?: "",
                    iso = network?.iso ?: "",
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = cell.tac?.toString() ?: "",
                    pci = cell.pci?.toString() ?: "",
                    rsrp = "N/A",
                    rsrq = "N/A",
                    sinr = "N/A",
                    rssi = "N/A",
                    ssRsrp = signal.ssRsrp?.toString() ?: "",
                    ssRsrq = signal.ssRsrq?.toString() ?: "",
                    ssSinr = signal.ssSinr?.toString() ?: "",
                    csiRsrp = signal.csiRsrp?.toString() ?: "",
                    csiRsrq = signal.csiRsrq?.toString() ?: "",
                    csiSinr = signal.csiSinr?.toString() ?: "",
                )
            }
            is CellCdma -> {
                val signal = cell.signal
                val network = cell.network
                val frequency = cell.band?.channelNumber?.toString() ?: "N/A"
                CellInfoEntity(
                    net = "CDMA",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc ?: "",
                    mnc = network?.mnc ?: "",
                    iso = network?.iso ?: "",
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = "N/A", // CDMA doesn't use PCI
                    timestamp = cell.timestamp?.toString() ?: "",
                    signalStrength = calculateCdmaSignalStrength(signal),
                    rssi = signal.cdmaRssi?.toString() ?: "", // CDMA RSSI
                    rsrp = signal.evdoRssi?.toString() ?: "N/A", // EVDO RSSI (repurposing rsrp field)
                    rsrq = signal.cdmaEcio?.toString() ?: "N/A", // CDMA ECIO (repurposing rsrq field)
                    sinr = signal.evdoSnr?.toString() ?: "N/A", // EVDO SNR
                    ssRsrp = signal.evdoEcio?.toString() ?: "N/A", // EVDO ECIO (repurposing ssRsrp field)
                    ssRsrq = "N/A",
                    ssSinr = "N/A",
                    csiRsrp = "N/A",
                    csiRsrq = "N/A",
                    csiSinr = "N/A",
                )
            }
            is CellWcdma -> {
                val signal = cell.signal
                val network = cell.network
                val frequency = cell.band?.let { "${it.name ?: "Band ${it.number ?: "N/A"}"} (UARFCN: ${it.channelNumber})" } ?: "N/A"
                CellInfoEntity(
                    net = "WCDMA",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc ?: "",
                    mnc = network?.mnc ?: "",
                    iso = network?.iso ?: "",
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = cell.psc?.toString() ?: "N/A", // WCDMA uses PSC instead of PCI
                    timestamp = cell.timestamp?.toString() ?: "",
                    signalStrength = calculateWcdmaSignalStrength(signal),
                    rssi = signal.rssi?.toString() ?: "",
                    rsrp = signal.rscp?.toString() ?: "N/A", // RSCP (Received Signal Code Power) - similar to RSRP
                    rsrq = signal.ecno?.toString() ?: "N/A", // Ec/No (Energy per Bit to Noise Power Density Ratio)
                    sinr = signal.ecio?.toString() ?: "N/A", // ECIO (Energy per Chip to Interference Power Ratio)
                    ssRsrp = signal.bitErrorRate?.toString() ?: "N/A", // Bit Error Rate (repurposing ssRsrp field)
                    ssRsrq = "N/A",
                    ssSinr = "N/A",
                    csiRsrp = "N/A",
                    csiRsrq = "N/A",
                    csiSinr = "N/A",
                )
            }
            is CellTdscdma -> {
                val signal = cell.signal
                val network = cell.network
                val frequency = cell.band?.let { "${it.name ?: "Band ${it.number ?: "N/A"}"} (UARFCN: ${it.channelNumber})" } ?: "N/A"
                CellInfoEntity(
                    net = "TDS-CDMA",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc ?: "",
                    mnc = network?.mnc ?: "",
                    iso = network?.iso ?: "",
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = cell.cpid?.toString() ?: "N/A", // TD-SCDMA uses CPID
                    timestamp = cell.timestamp?.toString() ?: "",
                    signalStrength = calculateTdscdmaSignalStrength(signal),
                    rssi = signal.rssi?.toString() ?: "",
                    rsrp = signal.rscp?.toString() ?: "N/A", // RSCP (repurposing rsrp field)
                    rsrq = "N/A",
                    sinr = "N/A",
                    ssRsrp = "N/A",
                    ssRsrq = "N/A",
                    ssSinr = "N/A",
                    csiRsrp = "N/A",
                    csiRsrq = "N/A",
                    csiSinr = "N/A",
                )
            }
            is CellGsm -> {
                val signal = cell.signal
                val network = cell.network
                val frequency = cell.band?.let { "${it.name ?: "Band ${it.number ?: "N/A"}"} (ARFCN: ${it.channelNumber})" } ?: "N/A"
                CellInfoEntity(
                    net = "GSM",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc ?: "",
                    mnc = network?.mnc ?: "",
                    iso = network?.iso ?: "",
                    eci = "N/A",
                    eNb = "N/A",
                    cid = cell.cid?.toString() ?: "N/A",
                    tac = cell.lac?.toString() ?: "N/A", // GSM uses LAC instead of TAC
                    pci = "N/A", // GSM doesn't use PCI
                    timestamp = cell.timestamp?.toString() ?: "",
                    signalStrength = calculateGsmSignalStrength(signal),
                    rssi = signal.rssi?.toString() ?: "",
                    rsrp = "N/A",
                    rsrq = signal.bitErrorRate?.toString() ?: "N/A", // Bit Error Rate (repurposing rsrq field)
                    sinr = signal.timingAdvance?.toString() ?: "N/A", // Timing Advance (repurposing sinr field)
                    ssRsrp = "N/A",
                    ssRsrq = "N/A",
                    ssSinr = "N/A",
                    csiRsrp = "N/A",
                    csiRsrq = "N/A",
                    csiSinr = "N/A",
                )
            }
            else -> CellInfoEntity(
                net = "Unknown",
                connectionStatus = "",
                signalStrength = "",
                timestamp = "",
                frequency = "",
                bandWidth = "",
                mcc = "",
                mnc = "",
                iso = "",
                eci = "",
                eNb = "",
                cid = "",
                tac = "",
                pci = "",
                rssi = "",
                rsrp = "",
                rsrq = "",
                sinr = "",
                ssRsrp = "",
                ssRsrq = "",
                ssSinr = "",
                csiRsrp = "",
                csiRsrq = "",
                csiSinr = ""
            )
        }
    }

    /**
     * Helper methods for calculating signal strength (0-4 scale)
     */
    private fun calculateLteSignalStrength(signal: SignalLte?): String {
        return (signal?.rsrp?.let { rsrp ->
            when {
                rsrp >= -80 -> 4
                rsrp >= -90 -> 3
                rsrp >= -100 -> 2
                rsrp >= -110 -> 1
                else -> 0
            }
        } ?: 0).toString()
    }

    private fun calculateNrSignalStrength(signal: SignalNr?): String {
        return (signal?.ssRsrp?.let { ssRsrp ->
            when {
                ssRsrp >= -80 -> 4
                ssRsrp >= -90 -> 3
                ssRsrp >= -100 -> 2
                ssRsrp >= -110 -> 1
                else -> 0
            }
        } ?: 0).toString()
    }

    private fun calculateCdmaSignalStrength(signal: SignalCdma?): String {
        return (signal?.cdmaRssi?.let { rssi ->
            when {
                rssi >= -75 -> 4
                rssi >= -85 -> 3
                rssi >= -95 -> 2
                rssi >= -100 -> 1
                else -> 0
            }
        } ?: 0).toString()
    }

    private fun calculateWcdmaSignalStrength(signal: SignalWcdma?): String {
        return (signal?.rssi?.let { rssi ->
            when {
                rssi >= -75 -> 4
                rssi >= -85 -> 3
                rssi >= -95 -> 2
                rssi >= -100 -> 1
                else -> 0
            }
        } ?: 0).toString()
    }

    private fun calculateTdscdmaSignalStrength(signal: SignalTdscdma?): String {
        return (signal?.rssi?.let { rssi ->
            when {
                rssi >= -75 -> 4
                rssi >= -85 -> 3
                rssi >= -95 -> 2
                rssi >= -100 -> 1
                else -> 0
            }
        } ?: 0).toString()
    }

    private fun calculateGsmSignalStrength(signal: SignalGsm?): String {
        return (signal?.rssi?.let { rssi ->
            when {
                rssi >= -75 -> 4
                rssi >= -85 -> 3
                rssi >= -95 -> 2
                rssi >= -100 -> 1
                else -> 0
            }
        } ?: 0).toString()
    }

    private fun estimateBandwidthFromArfcn(downlinkArfcn: Int?): String {
        return if (downlinkArfcn == null) {
            "N/A"
        } else {
            when (downlinkArfcn) {
                in 151600..160600 -> "100 MHz (n78, 3.5 GHz)"
                in 173800..178800 -> "50 MHz (n79, 4.8 GHz)"
                in 422000..434000 -> "200 MHz (n260, 39 GHz)"
                else -> "Unknown Bandwidth"
            }
        }
    }

    /**
     * Fallback method using Android's native TelephonyManager APIs
     * when NetMonster fails to get cell information (likely due to Android 15 compatibility issues)
     */
    private fun getCellsFromAndroidAPI(): List<SimpleCellData> {
        val context = getApplication<Application>()
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val cells = mutableListOf<SimpleCellData>()

        // Check device state first
        checkDeviceState(telephonyManager)

        try {
            // Method 1: Try getAllCellInfo() - primary method
            val cellInfoList = telephonyManager.allCellInfo
            Log.d("CellInfo", "allCellInfo returned: ${if (cellInfoList == null) "null" else "${cellInfoList.size} cells"}")
            
            if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                Log.d("CellInfo", "Got ${cellInfoList.size} cells from allCellInfo")

                for (cellInfo in cellInfoList) {
                    when (cellInfo) {
                        is CellInfoLte -> {
                            val signalStrength = cellInfo.cellSignalStrength
                            val identity = cellInfo.cellIdentity
                            
                            // Modern way to get band information (API 30+)
                            // CellIdentityLte.getBands() returns IntArray (non-null) in Kotlin
                            val bandInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val bandsArray = identity.bands
                                if (bandsArray.isNotEmpty()) {
                                    bandsArray.joinToString(", ") { "Band $it" }
                                } else {
                                    // Fallback: use EARFCN if bands array is empty
                                    val earfcn = identity.earfcn
                                    if (earfcn != CellInfo.UNAVAILABLE) {
                                        "EARFCN $earfcn"
                                    } else {
                                        "N/A"
                                    }
                                }
                            } else {
                                // Fallback: use EARFCN to identify band (pre-API 30)
                                val earfcn = identity.earfcn
                                if (earfcn != CellInfo.UNAVAILABLE) {
                                    "EARFCN $earfcn"
                                } else {
                                    "N/A"
                                }
                            }
                            
                            val cell = SimpleCellData(
                                networkType = "LTE",
                                isRegistered = cellInfo.isRegistered,
                                rsrp = signalStrength.rsrp.takeIf { it != CellInfo.UNAVAILABLE },
                                rsrq = signalStrength.rsrq.takeIf { it != CellInfo.UNAVAILABLE },
                                rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                                snr = signalStrength.rssnr.takeIf { it != CellInfo.UNAVAILABLE },
                                pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
                                cellId = identity.ci.takeIf { it != CellInfo.UNAVAILABLE }?.toString()
                            )
                            cells.add(cell)
                            Log.d("CellInfo", "LTE cell - Band: $bandInfo, PCI: ${cell.pci}, RSRP: ${cell.rsrp}")
                        }
                        is CellInfoNr -> {
                            val signalStrength = cellInfo.cellSignalStrength
                            val identity = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                            val cell = SimpleCellData(
                                networkType = "5G NR",
                                isRegistered = cellInfo.isRegistered,
                                rsrp = null, // NR signal strength access might be restricted
                                rsrq = null,
                                rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                                snr = null,
                                pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
                                cellId = identity.nci.takeIf { it != CellInfo.UNAVAILABLE.toLong() }?.toString()
                            )
                            cells.add(cell)
                        }
                        is CellInfoGsm -> {
                            val signalStrength = cellInfo.cellSignalStrength
                            val cell = SimpleCellData(
                                networkType = "GSM",
                                isRegistered = cellInfo.isRegistered,
                                rsrp = null,
                                rsrq = null,
                                rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                                snr = null,
                                pci = null,
                                cellId = cellInfo.cellIdentity.cid.takeIf { it != CellInfo.UNAVAILABLE }?.toString()
                            )
                            cells.add(cell)
                        }
                        is CellInfoCdma -> {
                            @Suppress("DEPRECATION") // CDMA APIs deprecated but needed for fallback
                            val signalStrength = cellInfo.cellSignalStrength
                            @Suppress("DEPRECATION") // CDMA APIs deprecated but needed for fallback
                            val identity = cellInfo.cellIdentity
                            val cell = SimpleCellData(
                                networkType = "CDMA",
                                isRegistered = cellInfo.isRegistered,
                                rsrp = null,
                                rsrq = null,
                                rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                                snr = null,
                                pci = null,
                                cellId = "${identity.basestationId}-${identity.networkId}"
                            )
                            cells.add(cell)
                        }
                        is CellInfoWcdma -> {
                            val signalStrength = cellInfo.cellSignalStrength
                            val cell = SimpleCellData(
                                networkType = "WCDMA",
                                isRegistered = cellInfo.isRegistered,
                                rsrp = null,
                                rsrq = null,
                                rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                                snr = null,
                                pci = cellInfo.cellIdentity.psc.takeIf { it != CellInfo.UNAVAILABLE },
                                cellId = cellInfo.cellIdentity.cid.takeIf { it != CellInfo.UNAVAILABLE }?.toString()
                            )
                            cells.add(cell)
                        }
                        else -> {
                            Log.d("CellInfo", "Unsupported cell type: ${cellInfo.javaClass.simpleName}")
                        }
                    }
                }
            } else {
                Log.w("CellInfo", "allCellInfo returned null or empty")
                
                // Method 2: Try getCellLocation() as fallback (deprecated but might work)
                try {
                    @Suppress("DEPRECATION")
                    val cellLocation = telephonyManager.cellLocation
                    if (cellLocation != null) {
                        Log.d("CellInfo", "getCellLocation() returned: ${cellLocation.javaClass.simpleName}")
                        // Note: CellLocation doesn't provide detailed info, but confirms device has cellular
                    } else {
                        Log.w("CellInfo", "getCellLocation() returned null")
                    }
                } catch (e: Exception) {
                    Log.w("CellInfo", "Error calling getCellLocation()", e)
                }
                
                // Method 3: Try ServiceState for network registration info (API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val serviceState = telephonyManager.serviceState
                        if (serviceState != null) {
                            Log.d("CellInfo", "ServiceState available - State: ${serviceState.state}, Operator: ${serviceState.operatorAlphaLong}")
                            // ServiceState can provide some cell info but limited
                        }
                    } catch (e: Exception) {
                        Log.w("CellInfo", "Error getting ServiceState", e)
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e("CellInfo", "SecurityException getting cells from Android API - permissions issue", e)
        } catch (e: Exception) {
            Log.e("CellInfo", "Error getting cells from Android API", e)
            e.printStackTrace()
        }

        Log.d("CellInfo", "Android API fallback returned ${cells.size} cells")
        return cells
    }
    
    /**
     * Checks device state to diagnose why cell info might not be available
     */
    private fun checkDeviceState(telephonyManager: TelephonyManager) {
        try {
            // Check if device has telephony capability
            val hasTelephony = telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_NONE
            Log.d("CellInfo", "Device has telephony: $hasTelephony, PhoneType: ${telephonyManager.phoneType}")
            
            // Check SIM state
            val simState = telephonyManager.simState
            Log.d("CellInfo", "SIM State: $simState (${when(simState) {
                TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                TelephonyManager.SIM_STATE_READY -> "READY"
                else -> "UNKNOWN"
            }})")
            
            // Check network operator
            val networkOperator = telephonyManager.networkOperator
            val networkOperatorName = telephonyManager.networkOperatorName
            Log.d("CellInfo", "Network Operator: $networkOperator ($networkOperatorName)")
            
            // Check if device is in airplane mode (requires additional permission on some devices)
            try {
                val isAirplaneMode = Settings.Global.getInt(
                    getApplication<Application>().contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    0
                ) != 0
                Log.d("CellInfo", "Airplane mode: $isAirplaneMode")
            } catch (e: Exception) {
                Log.w("CellInfo", "Could not check airplane mode", e)
            }
            
        } catch (e: Exception) {
            Log.e("CellInfo", "Error checking device state", e)
        }
    }


    /**
     * Lifecycle cleanup method.
     * Ensures proper cleanup when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        stopUpdates()
    }

    /**
     * Companion object containing configuration constants.
     * REFRESH_INTERVAL: Time between data collection cycles (3000ms = 3 seconds)
     */
    companion object {
        private const val REFRESH_INTERVAL = 3000L
    }
}