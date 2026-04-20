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

    @Query("SELECT * FROM training_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Int): TrainingRecordEntity?

    @Query("DELETE FROM training_records")
    suspend fun deleteAllRecords()
}