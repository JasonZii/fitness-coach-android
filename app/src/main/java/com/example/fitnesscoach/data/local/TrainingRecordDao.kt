package com.example.fitnesscoach.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TrainingRecordEntity): Long

    @Query("SELECT * FROM training_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<TrainingRecordEntity>>

    @Query("SELECT * FROM training_records WHERE ownerName = :ownerName ORDER BY createdAt DESC")
    fun getRecordsByOwner(ownerName: String): Flow<List<TrainingRecordEntity>>

    @Query("SELECT * FROM training_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Int): TrainingRecordEntity?

    @Query("SELECT COUNT(DISTINCT date(createdAt / 1000, 'unixepoch', 'localtime')) FROM training_records")
    fun getTrainingDayCount(): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT date(createdAt / 1000, 'unixepoch', 'localtime')) " +
            "FROM training_records WHERE ownerName = :ownerName"
    )
    fun getTrainingDayCountByOwner(ownerName: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM training_records")
    fun getTotalDurationSeconds(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM training_records WHERE ownerName = :ownerName")
    fun getTotalDurationSecondsByOwner(ownerName: String): Flow<Long>

    @Query("DELETE FROM training_records")
    suspend fun deleteAllRecords()
}
