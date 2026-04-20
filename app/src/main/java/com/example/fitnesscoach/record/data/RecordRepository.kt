package com.example.fitnesscoach.record.data

import com.example.fitnesscoach.data.local.TrainingRecordDao
import com.example.fitnesscoach.data.local.TrainingRecordEntity
import kotlinx.coroutines.flow.Flow

class RecordRepository(
    private val dao: TrainingRecordDao
) {
    suspend fun insertRecord(record: TrainingRecordEntity): Long {
        return dao.insertRecord(record)
    }

    fun getAllRecords(): Flow<List<TrainingRecordEntity>> {
        return dao.getAllRecords()
    }

    suspend fun getRecordById(recordId: Int): TrainingRecordEntity? {
        return dao.getRecordById(recordId)
    }
}