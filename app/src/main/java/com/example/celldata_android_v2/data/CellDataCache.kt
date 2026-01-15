package com.example.celldata_android_v2.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Collections

/**
 * Thread-safe temporary in-memory storage for cell information.
 * 
 * This cache stores cell data temporarily until it is exported to the database.
 * Data is stored in memory and will be lost when the app is restarted.
 * 
 * Thread Safety:
 * - Uses Collections.synchronizedList() for thread-safe access
 * - All operations are synchronized to prevent concurrent modification issues
 * - LiveData provides reactive updates for real-time UI synchronization
 * 
 * Usage:
 * - Add data: CellDataCache.addCellInfo(entity)
 * - Retrieve data: CellDataCache.getAllCellInfo()
 * - Observe changes: CellDataCache.cellInfoLiveData.observe(...)
 * - Clear data: CellDataCache.clear()
 */
object CellDataCache {
    /**
     * Thread-safe list to store cell information entities.
     * Newest entries are added at the beginning (index 0) for efficient retrieval.
     * Uses Collections.synchronizedList() for thread-safe access.
     */
    private val cellInfoList: MutableList<CellInfoEntity> = Collections.synchronizedList(mutableListOf())

    /**
     * LiveData for reactive updates when cell information is added or modified.
     * Observers can subscribe to this to receive real-time updates.
     * Initialized with empty list to ensure observers receive initial state.
     */
    private val _cellInfoLiveData = MutableLiveData<List<CellInfoEntity>>(emptyList())
    val cellInfoLiveData: LiveData<List<CellInfoEntity>> = _cellInfoLiveData

    /**
     * Adds a new cell information entity to the cache.
     * New entries are added at the beginning to maintain newest-first ordering.
     * Automatically notifies observers via LiveData for real-time updates.
     * 
     * @param entity The CellInfoEntity to add to the cache
     */
    fun addCellInfo(entity: CellInfoEntity) {
        synchronized(cellInfoList) {
            cellInfoList.add(0, entity) // Add at beginning for newest-first order
            // Notify observers immediately for real-time updates
            _cellInfoLiveData.postValue(cellInfoList.toList())
        }
    }

    /**
     * Retrieves all cell information entities from the cache.
     * Returns a copy of the list to prevent external modification.
     * 
     * @return List of CellInfoEntity ordered by insertion time (newest first)
     */
    fun getAllCellInfo(): List<CellInfoEntity> {
        return synchronized(cellInfoList) {
            cellInfoList.toList() // Return a copy to prevent external modification
        }
    }

    /**
     * Clears all data from the temporary cache.
     * This does not affect the database.
     * Notifies observers that the cache has been cleared.
     */
    fun clear() {
        synchronized(cellInfoList) {
            cellInfoList.clear()
            // Notify observers that cache is cleared
            _cellInfoLiveData.postValue(emptyList())
        }
    }

    /**
     * Returns the number of entries currently in the cache.
     * 
     * @return The size of the cache
     */
    fun size(): Int {
        return synchronized(cellInfoList) {
            cellInfoList.size
        }
    }

    /**
     * Checks if the cache is empty.
     * 
     * @return true if cache is empty, false otherwise
     */
    fun isEmpty(): Boolean {
        return synchronized(cellInfoList) {
            cellInfoList.isEmpty()
        }
    }
}

