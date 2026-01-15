package com.example.celldata_android_v2.ui.celllogger

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TableRow
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.celldata_android_v2.data.DatabaseProvider
import com.example.celldata_android_v2.data.CellInfoEntity
import com.example.celldata_android_v2.data.CellDataCache
import com.example.celldata_android_v2.databinding.FragmentCellLoggerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import android.util.Log

/**
 * Data class to represent a grouped row: serving cell with its neighboring cells
 */
data class GroupedCellRow(
    val servingCell: CellInfoEntity,
    val neighboringCells: List<CellInfoEntity> // Up to MAX_NEIGHBORING_CELLS
)

/**
 * CellLoggerFragment
 * 
 * Displays cell information data from temporary in-memory cache.
 * 
 * Data Flow:
 * - Cell data is collected and stored in CellDataCache (temporary storage)
 * - This fragment reads from CellDataCache and displays it in a table
 * - Data is only saved to database when export button is pressed
 * - Data is lost when app is restarted (expected behavior)
 * 
 * Key Features:
 * - Real-time display of cell data from cache
 * - Periodic updates every 3 seconds
 * - Export functionality saves cache to database before exporting
 * - Delete functionality clears both cache and database
 * - Neighboring cells are displayed as columns in the same row as serving cell
 */
class CellLoggerFragment : Fragment() {

    private var _binding: FragmentCellLoggerBinding? = null
    private val binding get() = _binding!!

    private val displayedCellInfo = mutableListOf<CellInfoEntity>() // Cache for displayed rows (UI state)
    
    companion object {
        private const val MAX_NEIGHBORING_CELLS = 3 // Maximum number of neighboring cells to display per serving cell
        private const val REQUEST_CODE_STORAGE_PERMISSION = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCellLoggerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupDeleteButton()
        setupExportButton()

        // Load initial data
        loadCellInfoData()

        // Observe CellDataCache for real-time updates
        observeCellDataChanges()

        // Keep periodic refresh as fallback (every 2 seconds) in case observer misses updates
        lifecycleScope.launch {
            while (true) {
                delay(2000) // Fallback refresh every 2 seconds
                loadCellInfoData()
            }
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible
        loadCellInfoData()
    }

    /**
     * Observes CellDataCache LiveData for real-time updates.
     * This provides immediate UI updates when new cell data is added to the cache.
     */
    private fun observeCellDataChanges() {
        CellDataCache.cellInfoLiveData.observe(viewLifecycleOwner) { cellInfoList ->
            // Update table immediately when data changes
            if (_binding != null) {
                Log.d("CellLogger", "Real-time update: ${cellInfoList.size} entries")
                populateTable(cellInfoList)
                displayedCellInfo.clear()
                displayedCellInfo.addAll(cellInfoList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Loads cell information data from temporary cache and updates the UI.
     * This method is used as a fallback refresh mechanism.
     * Primary updates come from the LiveData observer for real-time updates.
     */
    private fun loadCellInfoData() {
        // In-memory access is fast, no need for coroutine
        val cellInfoList = getCellInfoFromCache()

        // Compare list sizes first (quick check)
        val sizeChanged = cellInfoList.size != displayedCellInfo.size
        
        // Compare content if sizes match (or if size changed, we know content changed)
        val contentChanged = sizeChanged || cellInfoList != displayedCellInfo

        if (contentChanged) {
            Log.d("CellLogger", "Fallback refresh: ${displayedCellInfo.size} -> ${cellInfoList.size} entries")
            
            // Update UI on main thread
            if (_binding != null) {
                populateTable(cellInfoList)
                displayedCellInfo.clear()
                displayedCellInfo.addAll(cellInfoList) // Cache the current list
            }
        }
    }

    /**
     * Retrieves cell information from temporary in-memory cache.
     * Data is stored temporarily and will be saved to database only when export is pressed.
     * 
     * @return List of CellInfoEntity ordered by insertion time (newest first)
     */
    private fun getCellInfoFromCache(): List<CellInfoEntity> {
        // In-memory access is fast, no need for background thread
        return CellDataCache.getAllCellInfo()
    }

    /**
     * Groups cells by timestamp and connection status.
     * Returns a list of GroupedCellRow where each row contains one serving cell
     * and up to MAX_NEIGHBORING_CELLS neighboring cells collected at the same time.
     * 
     * @param cellInfoList List of all cell information entities
     * @return List of grouped rows (serving cell + neighboring cells)
     */
    private fun groupCellsByTimestamp(cellInfoList: List<CellInfoEntity>): List<GroupedCellRow> {
        // Group cells by timestamp
        val groupedByTimestamp = cellInfoList.groupBy { it.timestamp }
        
        val groupedRows = mutableListOf<GroupedCellRow>()
        
        // For each timestamp group, separate serving and neighboring cells
        groupedByTimestamp.forEach { (_, cells) ->
            val servingCells = cells.filter { 
                it.connectionStatus.contains("Primary", ignoreCase = true) || 
                it.connectionStatus.contains("Connected", ignoreCase = true) ||
                it.connectionStatus.contains("Serving", ignoreCase = true)
            }
            val neighboringCells = cells.filter { 
                it.connectionStatus.contains("None", ignoreCase = true) ||
                it.connectionStatus.contains("Neighbor", ignoreCase = true) ||
                (!it.connectionStatus.contains("Primary", ignoreCase = true) && 
                 !it.connectionStatus.contains("Connected", ignoreCase = true) &&
                 !it.connectionStatus.contains("Serving", ignoreCase = true) &&
                 !it.connectionStatus.contains("Secondary", ignoreCase = true))
            }
            
            // If no serving cells found, use the first cell as serving (fallback)
            val actualServingCells = if (servingCells.isEmpty() && cells.isNotEmpty()) {
                listOf(cells.first())
            } else {
                servingCells
            }
            
            // Create one row per serving cell
            actualServingCells.forEach { servingCell ->
                // Take up to MAX_NEIGHBORING_CELLS neighboring cells
                val neighborCells = neighboringCells.take(MAX_NEIGHBORING_CELLS)
                groupedRows.add(GroupedCellRow(servingCell, neighborCells))
            }
        }
        
        return groupedRows
    }

    /**
     * Populates the table with cell information data.
     * Groups neighboring cells with serving cells in the same row.
     * 
     * Note: TableLayout requires full rebuilds for updates. This method:
     * - Clears existing data rows (keeps header)
     * - Groups cells by timestamp and connection status
     * - Rebuilds all rows with grouped data
     * - Optimized to run on main thread for immediate UI updates
     * 
     * @param cellInfoList List of cell information entities to display (newest first)
     */
    private fun populateTable(cellInfoList: List<CellInfoEntity>) {
        val table = binding.cellInfoTable

        // Quick check: if list is empty and table is already empty, skip rebuild
        val currentRowCount = table.childCount
        if (cellInfoList.isEmpty() && currentRowCount <= 1) {
            return // Already empty, no need to rebuild
        }

        // Clear existing rows (except the header row at index 0)
        if (currentRowCount > 1) {
            // Remove all rows except the header (index 0)
            table.removeViews(1, currentRowCount - 1)
        }

        // Group cells by timestamp and connection status
        val groupedRows = groupCellsByTimestamp(cellInfoList)

        // Add new rows (one per serving cell, with neighboring cells as columns)
        for (groupedRow in groupedRows) {
            val row = TableRow(requireContext())
            row.layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            )

            val servingCell = groupedRow.servingCell
            val neighboringCells = groupedRow.neighboringCells

            // Add serving cell fields (consolidated signal metrics)
            // Timestamp is first column
            addTextViewToRow(row, servingCell.timestamp)
            addTextViewToRow(row, servingCell.net)
            addTextViewToRow(row, servingCell.connectionStatus)
            addTextViewToRow(row, servingCell.frequency)
            addTextViewToRow(row, servingCell.bandWidth)
            addTextViewToRow(row, servingCell.mcc)
            addTextViewToRow(row, servingCell.mnc)
            addTextViewToRow(row, servingCell.iso)
            addTextViewToRow(row, servingCell.eci)
            addTextViewToRow(row, servingCell.eNb)
            addTextViewToRow(row, servingCell.cid)
            addTextViewToRow(row, servingCell.tac)
            addTextViewToRow(row, servingCell.pci)
            // Consolidated signal metrics (replaces RSSI, RSRP, RSRQ, SINR, SS-RSRP, SS-RSRQ, SS-SINR, CSI-RSRP, CSI-RSRQ, CSI-SINR)
            addTextViewToRow(row, getConsolidatedRsrp(servingCell)) // Consolidated RSRP (includes RSRP, SS-RSRP, CSI-RSRP, RSCP, RSSI)
            addTextViewToRow(row, getConsolidatedRsrq(servingCell)) // Consolidated RSRQ (includes RSRQ, SS-RSRQ, CSI-RSRQ, Ec/No, ECIO)
            addTextViewToRow(row, getConsolidatedSinr(servingCell)) // Consolidated SINR (includes SINR, SS-SINR, CSI-SINR, ECIO, SNR)

            // Add neighboring cell columns (for up to MAX_NEIGHBORING_CELLS)
            for (i in 0 until MAX_NEIGHBORING_CELLS) {
                val neighborCell = if (i < neighboringCells.size) neighboringCells[i] else null
                
                // Add neighboring cell consolidated RSRP, PCI, consolidated RSRQ, consolidated SINR, Frequency
                addTextViewToRow(row, neighborCell?.let { getConsolidatedRsrp(it) } ?: "N/A") // nc1_rsrp, nc2_rsrp, nc3_rsrp (consolidated)
                addTextViewToRow(row, neighborCell?.pci ?: "N/A")  // nc1_pci, nc2_pci, nc3_pci
                addTextViewToRow(row, neighborCell?.let { getConsolidatedRsrq(it) } ?: "N/A") // nc1_rsrq, nc2_rsrq, nc3_rsrq (consolidated)
                addTextViewToRow(row, neighborCell?.let { getConsolidatedSinr(it) } ?: "N/A")  // nc1_sinr, nc2_sinr, nc3_sinr (consolidated)
                addTextViewToRow(row, neighborCell?.frequency ?: "N/A") // nc1_freq, nc2_freq, nc3_freq
            }

            table.addView(row)
        }
    }

    /**
     * Consolidates all signal strength metrics (RSRP, SS-RSRP, CSI-RSRP, RSCP, RSSI) 
     * into a single "RSRP" value based on network type.
     * 
     * Priority: RSRP (LTE) > SS-RSRP (5G) > CSI-RSRP (5G) > RSSI (CDMA/GSM/WCDMA)
     */
    private fun getConsolidatedRsrp(cell: CellInfoEntity): String {
        return when {
            cell.rsrp.isNotEmpty() && cell.rsrp != "N/A" -> cell.rsrp // LTE RSRP or WCDMA RSCP
            cell.ssRsrp.isNotEmpty() && cell.ssRsrp != "N/A" -> cell.ssRsrp // 5G SS-RSRP
            cell.csiRsrp.isNotEmpty() && cell.csiRsrp != "N/A" -> cell.csiRsrp // 5G CSI-RSRP
            cell.rssi.isNotEmpty() && cell.rssi != "N/A" -> cell.rssi // CDMA/GSM/WCDMA RSSI fallback
            else -> "N/A"
        }
    }

    /**
     * Consolidates all signal quality metrics (RSRQ, SS-RSRQ, CSI-RSRQ, Ec/No, ECIO)
     * into a single "RSRQ" value based on network type.
     * 
     * Priority: RSRQ (LTE) > SS-RSRQ (5G) > CSI-RSRQ (5G) > Ec/No/ECIO (WCDMA/CDMA)
     */
    private fun getConsolidatedRsrq(cell: CellInfoEntity): String {
        return when {
            cell.rsrq.isNotEmpty() && cell.rsrq != "N/A" -> cell.rsrq // LTE RSRQ or WCDMA Ec/No or CDMA ECIO
            cell.ssRsrq.isNotEmpty() && cell.ssRsrq != "N/A" -> cell.ssRsrq // 5G SS-RSRQ
            cell.csiRsrq.isNotEmpty() && cell.csiRsrq != "N/A" -> cell.csiRsrq // 5G CSI-RSRQ
            else -> "N/A"
        }
    }

    /**
     * Consolidates all SINR metrics (SINR, SS-SINR, CSI-SINR, ECIO, EVDO SNR)
     * into a single "SINR" value based on network type.
     * 
     * Priority: SINR (LTE) > SS-SINR (5G) > CSI-SINR (5G) > ECIO/SNR (WCDMA/CDMA)
     */
    private fun getConsolidatedSinr(cell: CellInfoEntity): String {
        return when {
            cell.sinr.isNotEmpty() && cell.sinr != "N/A" -> cell.sinr // LTE SINR or WCDMA ECIO or CDMA EVDO SNR
            cell.ssSinr.isNotEmpty() && cell.ssSinr != "N/A" -> cell.ssSinr // 5G SS-SINR
            cell.csiSinr.isNotEmpty() && cell.csiSinr != "N/A" -> cell.csiSinr // 5G CSI-SINR
            else -> "N/A"
        }
    }

    private fun addTextViewToRow(row: TableRow, text: String?) {
        val textView = TextView(requireContext()).apply {
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
            this.text = text ?: "N/A"
        }
        row.addView(textView)
    }

    /**
     * Sets up the delete button to clear temporary cache data.
     * Also clears the database to ensure consistency.
     */
    private fun setupDeleteButton() {
        val deleteButton: Button = binding.deleteDataButton
        deleteButton.setOnClickListener {
            lifecycleScope.launch {
                // Clear temporary cache
                CellDataCache.clear()
                Log.d("CellLogger", "Cleared temporary cache")
                
                // Also clear database for consistency
                withContext(Dispatchers.IO) {
                    val database = DatabaseProvider.getDatabase(requireContext())
                    database.cellInfoDao().deleteAllCellInfo()
                    Log.d("CellLogger", "Cleared database")
                }
                
                // Reload the table after deleting data
                loadCellInfoData()
            }
        }
    }

    /**
     * Sets up the export button to save temporary cache data to database and export to CSV.
     * Before exporting, all temporary data is saved to the database for persistence.
     */
    private fun setupExportButton() {
        val exportButton: Button = binding.exportDataButton
        exportButton.setOnClickListener {
            lifecycleScope.launch {
                val context = requireContext()

                // Check and request permissions
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        requestStoragePermission()
                        return@launch
                    }
                } else if (!hasStoragePermission(context)) {
                    requestStoragePermission()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    try {
                        // Get data from temporary cache
                        val tempData = CellDataCache.getAllCellInfo()
                        
                        // Log the number of rows
                        Log.d("ExportButton", "Found ${tempData.size} entries in cache")

                        if (tempData.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "No data to export.", Toast.LENGTH_LONG).show()
                            }
                            return@withContext
                        }

                        // Save temporary data to database before exporting
                        val database = DatabaseProvider.getDatabase(context)
                        database.cellInfoDao().insertAllCellInfo(tempData)
                        Log.d("ExportButton", "Saved ${tempData.size} entries to database")

                        // Show user feedback
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Saving ${tempData.size} entries to database before export...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        // Group cells by timestamp for CSV export (same as table display)
                        val groupedRows = groupCellsByTimestamp(tempData)
                        
                        // Create CSV header with neighboring cell columns
                        // Timestamp is first column, SignalStrength is removed
                        // Signal metrics are consolidated: RSRP (includes RSRP/SS-RSRP/CSI-RSRP/RSCP/RSSI), RSRQ (includes RSRQ/SS-RSRQ/CSI-RSRQ/Ec-No/ECIO), SINR (includes SINR/SS-SINR/CSI-SINR/ECIO/SNR)
                        val csvHeader = StringBuilder().apply {
                            append("Timestamp,Net,ConnectionStatus,Frequency,BandWidth,MCC,MNC,ISO,ECI,eNb,CID,TAC,PCI,RSRP,RSRQ,SINR")
                            // Add neighboring cell columns
                            for (i in 1..MAX_NEIGHBORING_CELLS) {
                                append(",nc${i}_rsrp,nc${i}_pci,nc${i}_rsrq,nc${i}_sinr,nc${i}_freq")
                            }
                            append("\n")
                        }
                        
                        // Create CSV data rows
                        val csvData = StringBuilder().apply {
                            append(csvHeader)
                            groupedRows.forEach { groupedRow ->
                                val servingCell = groupedRow.servingCell
                                val neighboringCells = groupedRow.neighboringCells
                                
                                // Add serving cell data
                                // Timestamp is first column, SignalStrength is removed
                                // Signal metrics are consolidated
                                append(
                                    "${servingCell.timestamp},${servingCell.net},${servingCell.connectionStatus}," +
                                            "${servingCell.frequency},${servingCell.bandWidth},${servingCell.mcc},${servingCell.mnc},${servingCell.iso},${servingCell.eci}," +
                                            "${servingCell.eNb},${servingCell.cid},${servingCell.tac},${servingCell.pci}," +
                                            "${getConsolidatedRsrp(servingCell)},${getConsolidatedRsrq(servingCell)},${getConsolidatedSinr(servingCell)}"
                                )
                                
                                // Add neighboring cell data (up to MAX_NEIGHBORING_CELLS)
                                // Use consolidated signal metrics
                                for (i in 0 until MAX_NEIGHBORING_CELLS) {
                                    val neighborCell = if (i < neighboringCells.size) neighboringCells[i] else null
                                    append(
                                        ",${neighborCell?.let { getConsolidatedRsrp(it) } ?: "N/A"}," +
                                        "${neighborCell?.pci ?: "N/A"}," +
                                        "${neighborCell?.let { getConsolidatedRsrq(it) } ?: "N/A"}," +
                                        "${neighborCell?.let { getConsolidatedSinr(it) } ?: "N/A"}," +
                                        "${neighborCell?.frequency ?: "N/A"}"
                                    )
                                }
                                append("\n")
                            }
                        }

                        // Save file
                        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cell_data_export.csv")
                        file.writeText(csvData.toString())

                        Log.d("ExportButton", "File saved to: ${file.absolutePath}")

                        // Notify user
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Data exported to: ${file.absolutePath}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ExportButton", "Failed to export data", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Failed to export data: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }


    // Helper function to check storage permissions
    private fun hasStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:" + requireContext().packageName)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Handle error, e.g., if Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION is not available
                Toast.makeText(requireContext(), "Unable to open permission settings.", Toast.LENGTH_SHORT).show()
            }
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE_PERMISSION
            )
        }
    }
}
