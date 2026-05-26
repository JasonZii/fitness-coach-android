package com.example.fitnesscoach.record.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.data.local.AppDatabase
import com.example.fitnesscoach.data.local.TrainingRecordEntity
import com.example.fitnesscoach.exercise.data.ExerciseInfo
import com.example.fitnesscoach.exercise.data.exerciseList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordDetailScreen(
    navController: NavHostController,
    recordId: Int
) {
    val context = LocalContext.current
    val dao = remember(context) {
        AppDatabase.getInstance(context).trainingRecordDao()
    }
    var record by remember { mutableStateOf<TrainingRecordEntity?>(null) }
    val exercise = exerciseList.find { it.id == record?.exerciseId }

    LaunchedEffect(recordId) {
        record = withContext(Dispatchers.IO) {
            dao.getRecordById(recordId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF141418)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Training Record",
            color = Color(0xFF141418),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = record?.let { formatDateTime(it.createdAt) } ?: "Loading record details.",
            color = Color(0xFF747681),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(18.dp))

        RecordImageCard(
            exercise = exercise,
            title = exercise?.title ?: record?.exerciseName ?: "Loading..."
        )

        Spacer(modifier = Modifier.height(16.dp))

        RecordSummaryCard(
            record = record,
            exercise = exercise
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                record?.let {
                    navController.popBackStack()
                    navController.navigate(Routes.training(it.exerciseId)) {
                        launchSingleTop = true
                    }
                }
            },
            enabled = record != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111114),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFB8BAC5),
                disabledContentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Try Again",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RecordImageCard(
    exercise: ExerciseInfo?,
    title: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE7E9F2)),
            contentAlignment = Alignment.Center
        ) {
            if (exercise != null) {
                Image(
                    painter = painterResource(id = exercise.imageRes),
                    contentDescription = exercise.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = title,
                    color = Color(0xFF747681),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RecordSummaryCard(
    record: TrainingRecordEntity?,
    exercise: ExerciseInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPill(
                    text = "${record?.avgScore ?: 0} score",
                    containerColor = Color(0xFFEAF1FF),
                    contentColor = Color(0xFF3384FF)
                )
                MetricPill(
                    text = "${record?.repCount ?: 0} reps",
                    containerColor = Color(0xFFF0ECFF),
                    contentColor = Color(0xFF745CFF)
                )
            }

            Text(
                text = "Training Summary",
                color = Color(0xFF141418),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            RecordInfoRow(
                icon = Icons.Default.FitnessCenter,
                label = "Exercise",
                value = exercise?.title ?: record?.exerciseName ?: "--"
            )
            RecordInfoRow(
                icon = Icons.Default.AccessTime,
                label = "Duration",
                value = formatDuration(record?.durationSeconds ?: 0L)
            )
            RecordInfoRow(
                icon = Icons.Default.CheckCircle,
                label = "Correct Counts",
                value = record?.correctReps?.toString() ?: "--"
            )
            RecordInfoRow(
                icon = Icons.Default.Close,
                label = "Incorrect Counts",
                value = record?.incorrectReps?.toString() ?: "--"
            )
        }
    }
}

@Composable
private fun RecordInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFF7F8FC),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = Color(0xFFF0ECFF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF745CFF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(0xFF747681),
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = Color(0xFF141418),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetricPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor
    ) {
        Text(
            text = text,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(durationSeconds: Long): String {
    if (durationSeconds <= 0L) return "--"
    val minutes = durationSeconds / 60L
    val seconds = durationSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}
