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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import com.example.fitnesscoach.R
import com.example.fitnesscoach.exercise.data.exerciseList
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.fitnesscoach.exercise.data.exerciseList
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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

//    val imageRes = when (record?.exerciseId) {
//        "squat" -> R.drawable.squat
//        "dumbbell_lateral_raise" -> R.drawable.dumbbell_lateral_raise
//        "bicep_curl" -> R.drawable.bicep_curl
//        "right_leg_lunge_to_knee_raise" -> R.drawable.right_leg_lunge_to_knee_raise
//        "standing_dumbbell_shoulder_press" -> R.drawable.standing_dumbbell_shoulder_press
//        else -> null
//    }

    val exercise = exerciseList.find { it.id == record?.exerciseId }


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

//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//                .padding(horizontal = 20.dp, vertical = 16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (exercise != null) {
                    Image(
                        painter = painterResource(id = exercise.imageRes),
//                        contentDescription = record?.exerciseName ?: "Exercise Image",
                        contentDescription = exercise.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
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

//                    RecordInfoRow(label = "Exercise", value = record?.exerciseName ?: "--")
                    RecordInfoRow(label = "Exercise", value = exercise?.title ?: record?.exerciseName ?: "--")
                    RecordInfoRow(label = "Score", value = record?.avgScore?.toString() ?: "--")
                    RecordInfoRow(label = "Repetition", value = record?.repCount?.toString() ?: "--")
                    RecordInfoRow(label = "Correct Counts", value = record?.correctReps?.toString() ?: "--")
                    RecordInfoRow(label = "Incorrect Counts", value = record?.incorrectReps?.toString() ?: "--")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

//            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        record?.let {
                            val exerciseId = it.exerciseId
                            navController.popBackStack()

                            navController.navigate(Routes.training(it.exerciseId)) {
//                                popUpTo(Routes.RECORD_DETAIL) { inclusive = true }

//                                popUpTo(Routes.RECORD_LIST) { saveState = true }
//                                launchSingleTop = true

                                launchSingleTop = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Try Again")
                }

//                Button(
//                    onClick = { navController.popBackStack() },
//                    modifier = Modifier.weight(1f)
//                ) {
//                    Text("History")
//                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
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