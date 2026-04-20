package com.example.fitnesscoach.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.room.Room
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.data.local.AppDatabase
import com.example.fitnesscoach.data.local.TrainingRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    navController: NavHostController,
    recordId: Int
) {
    val context = LocalContext.current

    val database = remember {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fitnesscoach_db"
        ).build()
    }

    val dao = database.trainingRecordDao()
    val scope = rememberCoroutineScope()

    var record by remember { mutableStateOf<TrainingRecordEntity?>(null) }

    LaunchedEffect(recordId) {
        scope.launch(Dispatchers.IO) {
            record = dao.getRecordById(recordId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Training Record",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = record?.exerciseName ?: "Loading...",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Training Summary",
                        style = MaterialTheme.typography.titleLarge
                    )

                    RecordInfoRow(label = "Exercise", value = record?.exerciseName ?: "--")
                    RecordInfoRow(label = "Score", value = record?.avgScore?.toString() ?: "--")
                    RecordInfoRow(label = "Repetition", value = record?.repCount?.toString() ?: "--")
                    RecordInfoRow(label = "Correct Counts", value = record?.correctReps?.toString() ?: "--")
                    RecordInfoRow(label = "Incorrect Counts", value = record?.incorrectReps?.toString() ?: "--")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { }) {
                    Text("Save")
                }

                TextButton(onClick = { }) {
                    Text("Discard")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { navController.navigate(Routes.HOME) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Home")
                }

                Button(
                    onClick = { navController.navigate(Routes.EXERCISE_LIBRARY) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Try Again")
                }

                Button(
                    onClick = { navController.navigate(Routes.RECORD_LIST) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("History")
                }
            }
        }
    }
}

@Composable
private fun RecordInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}