package com.example.fitnesscoach.record.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnesscoach.data.local.TrainingRecordEntity
import com.example.fitnesscoach.record.data.RecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordViewModel(
    private val repository: RecordRepository
) : ViewModel() {

    private val _records = MutableStateFlow<List<TrainingRecordEntity>>(emptyList())
    val records: StateFlow<List<TrainingRecordEntity>> = _records.asStateFlow()

    private val _selectedRecord = MutableStateFlow<TrainingRecordEntity?>(null)
    val selectedRecord: StateFlow<TrainingRecordEntity?> = _selectedRecord.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllRecords().collect {
                _records.value = it
            }
        }
    }

    fun loadRecord(recordId: Int) {
        viewModelScope.launch {
            _selectedRecord.value = repository.getRecordById(recordId)
        }
    }

    fun saveRecord(
        exerciseId: String,
        exerciseName: String,
        repCount: Int,
        avgScore: Int,
        correctReps: Int,
        incorrectReps: Int,
        onSaved: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.insertRecord(
                TrainingRecordEntity(
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    repCount = repCount,
                    avgScore = avgScore,
                    correctReps = correctReps,
                    incorrectReps = incorrectReps
                )
            )
            onSaved(id)
        }
    }
}