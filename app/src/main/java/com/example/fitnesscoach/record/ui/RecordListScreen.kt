package com.example.fitnesscoach.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes

data class HistoryItem(
    val id: String,
    val number: Int,
    val date: String,
    val exercise: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(navController: NavHostController) {
    val historyList = listOf(
        HistoryItem("1", 1, "2025-10-03", "Squat"),
        HistoryItem("2", 2, "2025-10-04", "Plank"),
        HistoryItem("3", 3, "2025-10-06", "Lunge"),
        HistoryItem("4", 4, "2025-10-06", "Plank"),
        HistoryItem("5", 5, "2025-10-06", "Plank"),
        HistoryItem("6", 6, "2025-10-06", "Plank"),
        HistoryItem("7", 7, "2025-10-07", "Squat")
    )

    var searchText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Training History",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search exercise") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "No.",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Date",
                    modifier = Modifier.weight(1.8f),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Exercise",
                    modifier = Modifier.weight(1.8f),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(historyList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("record_detail/${item.id}")
                            },
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.number.toString(),
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = item.date,
                                modifier = Modifier.weight(1.8f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = item.exercise,
                                modifier = Modifier.weight(1.8f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { navController.navigate(Routes.EXERCISE_LIBRARY) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Library")
                }

                Button(
                    onClick = { navController.navigate(Routes.HOME) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Home")
                }

                Button(
                    onClick = { navController.navigate(Routes.USER) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Profile")
                }
            }
        }
    }
}