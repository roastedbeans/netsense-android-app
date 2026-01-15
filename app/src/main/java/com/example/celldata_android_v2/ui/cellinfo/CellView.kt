package com.example.celldata_android_v2.ui.cellinfo

import com.example.celldata_android_v2.data.DatabaseProvider
import com.example.celldata_android_v2.data.CellInfoEntity
import com.example.celldata_android_v2.data.CellDataCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.LinearLayout
import cz.mroczis.netmonster.core.model.cell.*
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import cz.mroczis.netmonster.core.model.connection.SecondaryConnection
import cz.mroczis.netmonster.core.model.connection.NoneConnection
import cz.mroczis.netmonster.core.model.signal.SignalCdma
import cz.mroczis.netmonster.core.model.signal.SignalGsm
import cz.mroczis.netmonster.core.model.signal.SignalLte
import cz.mroczis.netmonster.core.model.signal.SignalNr
import cz.mroczis.netmonster.core.model.signal.SignalTdscdma
import cz.mroczis.netmonster.core.model.signal.SignalWcdma

/**
 * Specialized view component for displaying and processing cellular network data.
 * Implements detailed data collection for various cellular technologies with focus on 5G NR.
 *
 * 셀룰러 네트워크 데이터를 표시하고 처리하기 위한 특수 뷰 컴포넌트입니다.
 * 5G NR에 중점을 둔 다양한 셀룰러 기술에 대한 상세 데이터 수집을 구현합니다.
 *
 * Data Collection Points (데이터 수집 포인트):
 * - 5G NR specific parameters (5G NR 특정 매개변수)
 * - Signal quality metrics (신호 품질 메트릭)
 * - Network identification data (네트워크 식별 데이터)
 * - Cell configuration information (셀 구성 정보)
 */
class CellView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dao = DatabaseProvider.getDatabase(context).cellInfoDao()

    init {
        orientation = VERTICAL
    }

    private val transformer = object : ICellProcessor<Unit> {

        /**
         * processLte(cell: CellLte)
         * Processes and displays LTE network information.
         *
         * Collected Parameters:
         * - Network type and connection status
         * - Bandwidth and frequency information
         * - Cell identification (ECI, eNB, CID, TAC, PCI)
         * - Signal metrics (RSSI, RSRP, RSRQ, CQI, SNR)
         * - Timing advance and aggregated bands
         *
         * Usage: Automatically called by transformer when LTE cell is detected
         */
        override fun processLte(cell: CellLte) {
            cell.network?.let { addView("NETWORK", "LTE") }
            cell.band?.let { band ->
                addView("BAND", "${band.name} (#${band.number})")
            }

            // Prioritize RSRP information
            cell.signal.let { signal ->
                signal.rsrp?.let { addView("🔴 RSRP", "$it dBm") }
                val signalStrength = calculateLteSignalStrength(signal)
                addView("SIGNAL STRENGTH", signalStrength)
            }

            // Additional LTE-specific info
            cell.pci?.let { addView("PCI", it.toString()) }
            cell.tac?.let { addView("TAC", it.toString()) }
            cell.signal.rsrq?.let { addView("RSRQ", "$it dB") }
            cell.signal.snr?.let { addView("SNR", "$it dB") }
        }

        /**
         * processNr(cell: CellNr)
         * Specialized processor for 5G NR network data.
         *
         * Collected Parameters:
         * - 5G specific identifiers (NCI, TAC, PCI)
         * - Advanced signal metrics:
         *   * CSI-RSRP/RSRQ/SINR (Channel State Information)
         *   * SS-RSRP/RSRQ/SINR (Synchronization Signal)
         *
         * Usage: Automatically called by transformer for 5G NR cells
         */
        override fun processNr(cell: CellNr) {
            cell.network?.let { addView("NETWORK", "5G NR") }
            cell.band?.let { band ->
                addView("BAND", "${band.name} (#${band.number})")
            }

            // Prioritize RSRP information for 5G NR
            cell.signal.let { signal ->
                signal.ssRsrp?.let { addView("🔴 SS-RSRP", "$it dBm") }
                signal.csiRsrp?.let { addView("🔵 CSI-RSRP", "$it dBm") }
                val signalStrength = calculateNrSignalStrength(signal)
                addView("SIGNAL STRENGTH", signalStrength)
            }

            // Additional NR-specific info
            cell.pci?.let { addView("PCI", it.toString()) }
            cell.tac?.let { addView("TAC", it.toString()) }
            cell.signal.ssRsrq?.let { addView("SS-RSRQ", "$it dB") }
            cell.signal.ssSinr?.let { addView("SS-SINR", "$it dB") }
        }

        override fun processCdma(cell: CellCdma) {
            cell.network?.let { addView("NETWORK", "CDMA") }
            cell.band?.let { band ->
                addView("BAND", "${band.name} (#${band.number})")
            }

            // CDMA uses RSSI
            cell.signal.let { signal ->
                signal.cdmaRssi?.let { addView("📶 CD RSSI", "$it dBm") }
                signal.evdoRssi?.let { addView("📶 EV RSSI", "$it dBm") }
                val signalStrength = calculateCdmaSignalStrength(signal)
                addView("SIGNAL STRENGTH", signalStrength)
            }

            cell.nid?.let { addView("NID", it.toString()) }
            cell.bid?.let { addView("BID", it.toString()) }
        }

        override fun processGsm(cell: CellGsm) {
            cell.network?.let { addView("NETWORK", "GSM") }
            cell.band?.let { band ->
                addView("BAND", "${band.name} (#${band.number})")
            }

            // GSM uses RSSI instead of RSRP
            cell.signal.let { signal ->
                signal.rssi?.let { addView("📶 RSSI", "$it dBm") }
                val signalStrength = calculateGsmSignalStrength(signal)
                addView("SIGNAL STRENGTH", signalStrength)
            }

            cell.lac?.let { addView("LAC", it.toString()) }
            cell.cid?.let { addView("CID", it.toString()) }
        }

        override fun processTdscdma(cell: CellTdscdma) {
            cell.network?.let { addView("NETWORK", "TD-SCDMA") }
            cell.band?.let { band ->
                addView("BAND", "${band.name} (#${band.number})")
            }

            // TD-SCDMA uses RSCP
            cell.signal.let { signal ->
                signal.rscp?.let { addView("🔴 RSCP", "$it dBm") }
                val signalStrength = calculateTdscdmaSignalStrength(signal)
                addView("SIGNAL STRENGTH", signalStrength)
            }

            cell.cpid?.let { addView("CPID", it.toString()) }
            cell.lac?.let { addView("LAC", it.toString()) }
        }

        override fun processWcdma(cell: CellWcdma) {
            cell.network?.let { addView("NETWORK", "WCDMA") }
            cell.band?.let { band ->
                addView("BAND", "${band.name} (#${band.number})")
            }

            // WCDMA uses RSCP (similar to RSRP)
            cell.signal.let { signal ->
                signal.rscp?.let { addView("🔴 RSCP", "$it dBm") }
                val signalStrength = calculateWcdmaSignalStrength(signal)
                addView("SIGNAL STRENGTH", signalStrength)
            }

            cell.psc?.let { addView("PSC", it.toString()) }
            cell.lac?.let { addView("LAC", it.toString()) }
        }
    }

    /**
     * bind(cell: ICell)
     * Primary function for processing new cell data.
     *
     * Purpose:
     * - Processes incoming cell signal information
     * - Updates the visual display
     * - Triggers temporary cache storage
     *
     * Usage:
     * cellView.bind(cellData)
     *
     * @param cell ICell object containing network information
     */

    fun bind (cell: ICell) {
        removeAllViews()

        // Add cell type header based on connection status
        when (cell.connectionStatus) {
            is PrimaryConnection -> {
                addView("CELL TYPE", "SERVING CELL")
            }
            is SecondaryConnection -> {
                addView("CELL TYPE", "SECONDARY CELL")
            }
            is NoneConnection -> {
                addView("CELL TYPE", "NEIGHBORING CELL")
            }
            else -> {
                addView("CELL TYPE", "UNKNOWN")
            }
        }

        cell.let(transformer)

        val cellInfoEntity = mapToCellInfoEntity(cell)
        saveToCache(cellInfoEntity)
    }

    /**
     * saveToCache(cellInfoEntity: CellInfoEntity)
     * Stores cell information in temporary in-memory cache.
     *
     * Implementation:
     * - Uses thread-safe CellDataCache singleton
     * - Data is stored temporarily and will be saved to database only when export is pressed
     * - Data is lost when app is restarted
     *
     * Usage:
     * saveToCache(entityData)
     */
    private fun saveToCache(cellInfoEntity: CellInfoEntity) {
        Log.d("CellView", "Saving to cache: $cellInfoEntity")
        CellDataCache.addCellInfo(cellInfoEntity)
    }

    /**
     * mapToCellInfoEntity(cell: ICell)
     * Converts cell data into standardized database format.
     *
     * Implementation:
     * - Handles all cellular technologies (5G NR, LTE, CDMA, GSM, etc.)
     * - Extracts relevant parameters based on technology
     * - Standardizes data format for storage
     *
     * Usage:
     * val entity = mapToCellInfoEntity(cellData)
     *
     * @param cell Source cell information
     * @return CellInfoEntity formatted for database storage
     */

    private fun mapToCellInfoEntity(cell: ICell): CellInfoEntity {
        return when (cell) {
            is CellLte -> {
                val signal = cell.signal
                val network = cell.network
                val band = cell.band

                CellInfoEntity(
                    net = "LTE",
                    connectionStatus =  cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    signalStrength = calculateLteSignalStrength(signal),
                    timestamp = cell.timestamp.toString(),
                    frequency = band?.downlinkEarfcn?.toString().orEmpty(),
                    bandWidth = cell.bandwidth.toString(),
                    mcc = network?.mcc.orEmpty(),
                    mnc = network?.mnc.orEmpty(),
                    iso = network?.iso.orEmpty(),
                    eci = cell.eci.toString(),
                    eNb = cell.enb.toString(),
                    cid = cell.cid.toString(),
                    tac = cell.tac.toString(),
                    pci = cell.pci.toString(),
                    rssi = signal.rssi?.toString().orEmpty(),
                    rsrp = signal.rsrp?.toString().orEmpty(),
                    sinr = signal.snr?.toString().orEmpty(),
                    rsrq = signal.rsrq?.toString().orEmpty(),
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
                    timestamp = cell.timestamp.toString(),
                    frequency = band?.downlinkFrequency?.toString().orEmpty(),
                    bandWidth = estimateBandwidthFromArfcn(band?.downlinkArfcn),
                    mcc = network?.mcc.orEmpty(),
                    mnc = network?.mnc.orEmpty(),
                    iso = network?.iso.orEmpty(),
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = cell.tac.toString(),
                    pci = cell.pci.toString(),
                    rsrp = "N/A",
                    rsrq = "N/A",
                    sinr = "N/A",
                    rssi = "N/A",
                    ssRsrp = cell.signal.ssRsrp.toString(),
                    ssRsrq = cell.signal.ssRsrq.toString(),
                    ssSinr = cell.signal.ssSinr.toString(),
                    csiRsrp = cell.signal.csiRsrp.toString(),
                    csiRsrq = cell.signal.csiRsrq.toString(),
                    csiSinr = cell.signal.csiSinr.toString(),
                )
            }

            is CellCdma -> {
                val signal = cell.signal
                val network = cell.network
                // CDMA band information not typically available in CellInfoEntity format
                val frequency = cell.band?.channelNumber?.toString() ?: "N/A"

                CellInfoEntity(
                    net = "CDMA",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc.orEmpty(),
                    mnc = network?.mnc.orEmpty(),
                    iso = network?.iso.orEmpty(),
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = "N/A",
                    timestamp = cell.timestamp.toString(),
                    signalStrength = calculateCdmaSignalStrength(signal),
                    rssi = signal.cdmaRssi?.toString().orEmpty(),
                    rsrp = "N/A",
                    rsrq = "N/A",
                    sinr = signal.evdoSnr?.toString().orEmpty(),
                    ssRsrp = "N/A",
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
                // Use band channel number (UARFCN) as frequency identifier
                val frequency = cell.band?.let { "${it.name ?: "Band ${it.number ?: "N/A"}"} (UARFCN: ${it.channelNumber})" } ?: "N/A"

                CellInfoEntity(
                    net = "WCDMA",
                    connectionStatus = cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc.orEmpty(),
                    mnc = network?.mnc.orEmpty(),
                    iso = network?.iso.orEmpty(),
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = "N/A",
                    timestamp = cell.timestamp.toString(),
                    signalStrength = calculateWcdmaSignalStrength(signal),
                    rssi = signal.rssi?.toString().orEmpty(),
                    rsrp = "N/A",
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

            is CellTdscdma -> {
                val signal = cell.signal
                val network = cell.network
                // Use band channel number (UARFCN) as frequency identifier
                val frequency = cell.band?.let { "${it.name ?: "Band ${it.number ?: "N/A"}"} (UARFCN: ${it.channelNumber})" } ?: "N/A"

                CellInfoEntity(
                    net = "TDS-CDMA",
                    connectionStatus =  cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc.orEmpty(),
                    mnc = network?.mnc.orEmpty(),
                    iso = network?.iso.orEmpty(),
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = "N/A",
                    timestamp = cell.timestamp.toString(),
                    signalStrength = calculateTdscdmaSignalStrength(signal),
                    rssi = signal.rssi?.toString().orEmpty(),
                    rsrp = "N/A",
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
                // Use band channel number (ARFCN) as frequency identifier
                val frequency = cell.band?.let { "${it.name ?: "Band ${it.number ?: "N/A"}"} (ARFCN: ${it.channelNumber})" } ?: "N/A"

                CellInfoEntity(
                    net = "GSM",
                    connectionStatus =  cell.connectionStatus.javaClass.simpleName.orEmpty(),
                    frequency = frequency,
                    bandWidth = "N/A",
                    mcc = network?.mcc.orEmpty(),
                    mnc = network?.mnc.orEmpty(),
                    iso = network?.iso.orEmpty(),
                    eci = "N/A",
                    eNb = "N/A",
                    cid = "N/A",
                    tac = "N/A",
                    pci = "N/A",
                    timestamp = cell.timestamp.toString(),
                    signalStrength = calculateGsmSignalStrength(signal),
                    rssi = signal.rssi?.toString().orEmpty(),
                    rsrp = "N/A",
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
     * estimateBandwidthFromArfcn(downlinkArfcn: Int?)
     * Determines 5G NR bandwidth from channel number.
     *
     * Supported Bands:
     * - n78 (3.5 GHz): 100 MHz bandwidth
     * - n79 (4.8 GHz): 50 MHz bandwidth
     * - n260 (39 GHz): 200 MHz bandwidth
     *
     * Usage:
     * val bandwidth = estimateBandwidthFromArfcn(arfcnValue)
     *
     * @param downlinkArfcn Channel number to analyze
     * @return String describing bandwidth and band
     */
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
     * calculateLteSignalStrength(signal: SignalLte?)
     * Evaluates LTE signal quality on 0-4 scale.
     *
     * Signal Categories:
     * 4: Excellent (>= -80 dBm)
     * 3: Good (>= -90 dBm)
     * 2: Fair (>= -100 dBm)
     * 1: Poor (>= -110 dBm)
     * 0: No signal (< -110 dBm)
     *
     * Usage:
     * val strength = calculateLteSignalStrength(lteSignal)
     */
    private fun calculateLteSignalStrength(signal: SignalLte?): String {
        return (signal?.rsrp?.let { rsrp ->
            when {
                rsrp >= -80 -> 4  // Excellent
                rsrp >= -90 -> 3  // Good
                rsrp >= -100 -> 2 // Fair
                rsrp >= -110 -> 1 // Poor
                else -> 0           // No signal
            }
        } ?: 0).toString()
    }

    /**
     * calculateNrSignalStrength(signal: SignalNr?)
     * Evaluates 5G NR signal quality.
     *
     * Implementation:
     * - Uses SS-RSRP as primary metric
     * - Applies same scale as LTE (0-4)
     * - Optimized for 5G characteristics
     *
     * Usage:
     * val strength = calculateNrSignalStrength(nrSignal)
     */
    private fun calculateNrSignalStrength(signal: SignalNr?): String {
        return (signal?.ssRsrp?.let { ssRsrp ->
            when {
                ssRsrp >= -80 -> 4  // Excellent
                ssRsrp >= -90 -> 3  // Good
                ssRsrp >= -100 -> 2 // Fair
                ssRsrp >= -110 -> 1 // Poor
                else -> 0           // No signal
            }
        } ?: 0).toString()
    }

    /**
     * calculateCdmaSignalStrength(signal: SignalCdma?)
     * Evaluates CDMA signal quality.
     *
     * Thresholds:
     * 4: >= -75 dBm
     * 3: >= -85 dBm
     * 2: >= -95 dBm
     * 1: >= -100 dBm
     * 0: < -100 dBm
     */
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

    /**
     * addView(title: String, message: Any)
     * Creates and adds new information display item.
     *
     * Purpose:
     * - Creates visual representation of cell data
     * - Handles formatting and display
     * - Maintains consistent layout
     *
     * Usage:
     * addView("Parameter", value)
     */
    private fun addView(title: String, message: Any) {
        val view = CellItemSimple(context).apply {
            bind(title, message.toString())
        }
        addView(view)
    }

}