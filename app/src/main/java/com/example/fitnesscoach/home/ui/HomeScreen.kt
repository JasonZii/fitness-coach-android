package com.example.fitnesscoach.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.user.viewmodel.UserViewModel

@Composable
fun HomeScreen(
    navController: NavHostController,
    userViewModel: UserViewModel
) {
    val background = Color(0xFFF7F8FC)
    val primaryText = Color(0xFF141418)
    val secondaryText = Color(0xFF747681)
    val displayName = if (userViewModel.isLoggedIn && userViewModel.username.isNotBlank()) {
        userViewModel.username
    } else {
        "Guest"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HomeHeader(
            userName = displayName,
            primaryText = primaryText,
            secondaryText = secondaryText
        )

        Button(
            onClick = { navController.navigate(Routes.EXERCISE_LIBRARY) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111114),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add New Exercise",
                fontWeight = FontWeight.SemiBold
            )
        }

        TrainingDataCard()

        ShortcutSection(
            onFavoritesClick = { navController.navigate(Routes.EXERCISE_LIBRARY) },
            onDownloadsClick = { navController.navigate(Routes.EXERCISE_LIBRARY) },
            onEvaluationClick = { navController.navigate(Routes.RECORD_LIST) }
        )

        BadgesSection()

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

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun HomeHeader(
    userName: String,
    primaryText: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Welcome back,",
                color = secondaryText,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = userName,
                color = primaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HeaderIconButton(
            icon = Icons.Default.Search,
            contentDescription = "Search"
        )
        Spacer(modifier = Modifier.width(8.dp))
        HeaderIconButton(
            icon = Icons.Default.Notifications,
            contentDescription = "Notifications"
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFFE7E9F2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile avatar",
                tint = Color(0xFF656A78),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFF202126),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun TrainingDataCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
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
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Training Data",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your weekly activity overview",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticValue("420", "Total Minutes")
                StatisticValue("18", "Training Days")
                StatisticValue("6.8k", "Calories Burned")
            }
        }
    }
}

@Composable
private fun StatisticValue(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 22.sp,
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

@Composable
private fun ShortcutSection(
    onFavoritesClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onEvaluationClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutCard(
            title = "My Favorites",
            icon = Icons.Default.Favorite,
            iconColor = Color(0xFFE95D7D),
            onClick = onFavoritesClick,
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = "My Downloads",
            icon = Icons.Default.ArrowDownward,
            iconColor = Color(0xFF3384FF),
            onClick = onDownloadsClick,
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = "Evaluation Center",
            icon = Icons.Default.CheckCircle,
            iconColor = Color(0xFF29A37A),
            onClick = onEvaluationClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(116.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Text(
                text = title,
                color = Color(0xFF202126),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BadgesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "My Badges",
            color = Color(0xFF141418),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BadgeIcon(
                label = "Power",
                icon = Icons.Default.FitnessCenter,
                tint = Color(0xFF745CFF),
                modifier = Modifier.weight(1f)
            )
            BadgeIcon(
                label = "Streak",
                icon = Icons.Default.Star,
                tint = Color(0xFFFFA726),
                modifier = Modifier.weight(1f)
            )
            BadgeIcon(
                label = "Form",
                icon = Icons.Default.CheckCircle,
                tint = Color(0xFF29A37A),
                modifier = Modifier.weight(1f)
            )
            BadgeIcon(
                label = "Heart",
                icon = Icons.Default.Favorite,
                tint = Color(0xFFE95D7D),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BadgeIcon(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color(0xFF747681),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
