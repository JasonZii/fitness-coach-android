package com.example.fitnesscoach.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrainingRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingRecordDao(): TrainingRecordDao
}