package com.example.fitnesscoach.data.local


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_records")
data class TrainingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val exerciseId: String,
    val exerciseName: String,
    val repCount: Int,
    val avgScore: Int,
    val correctReps: Int,
    val incorrectReps: Int,
    val createdAt: Long = System.currentTimeMillis()
)