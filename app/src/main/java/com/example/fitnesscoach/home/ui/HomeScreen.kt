package com.example.fitnesscoach.home.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes

@Composable
fun HomeScreen(navController: NavHostController) {
    val background = Color(0xFFF7F8FC)
    val primaryText = Color(0xFF141418)
    val secondaryText = Color(0xFF747681)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        HomeHeader(
            primaryText = primaryText,
            secondaryText = secondaryText
        )

        Spacer(modifier = Modifier.height(24.dp))

        ReadyToTrainCard()

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = { navController.navigate(Routes.EXERCISE_LIBRARY) },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111114),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Let's Get Started!",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeHeader(
    primaryText: Color,
    secondaryText: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Good Morning,",
            color = secondaryText,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "User Name",
            color = primaryText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReadyToTrainCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF745CFF),
                            Color(0xFF3384FF)
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ready to train?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Start your exercise session with real-time feedback.",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapabilityIcon(icon = Icons.Default.FitnessCenter)
                    CapabilityIcon(icon = Icons.Default.CheckCircle)
                    CapabilityIcon(icon = Icons.Default.History)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CapabilityStat(
                    value = "Five",
                    label = "Exercises"
                )
                CapabilityStat(
                    value = "Live",
                    label = "Feedback"
                )
                CapabilityStat(
                    value = "History",
                    label = "Progress"
                )
            }
        }
    }
}

@Composable
private fun CapabilityIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun CapabilityStat(
    value: String,
    label: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            lineHeight = 14.sp
        )
    }
}
