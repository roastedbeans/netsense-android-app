package com.example.celldata_android_v2.data

import androidx.room.*
import com.example.celldata_android_v2.data.CellInfoEntity

@Dao
interface CellInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCellInfo(cellInfo: CellInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCellInfo(cellInfoList: List<CellInfoEntity>)

    @Query("SELECT * FROM cell_info ORDER BY id DESC")
    suspend fun getAllCellInfo(): List<CellInfoEntity>

    @Query("SELECT * FROM cell_info WHERE mcc = :mcc")
    suspend fun getCellInfoByMcc(mcc: String): List<CellInfoEntity>

    @Delete
    suspend fun deleteCellInfo(cellInfo: CellInfoEntity)

    @Query("DELETE FROM cell_info")
    suspend fun deleteAllCellInfo()
}

