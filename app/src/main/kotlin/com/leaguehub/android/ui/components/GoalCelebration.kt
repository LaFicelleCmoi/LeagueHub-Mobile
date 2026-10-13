package com.leaguehub.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguehub.android.R
import com.leaguehub.android.ui.common.GoalWatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Célébration quand le club favori marque en direct. */
@Composable
fun GoalCelebration(goals: Flow<GoalWatcher.Goal>) {
    var current by remember { mutableStateOf<GoalWatcher.Goal?>(null) }
    LaunchedEffect(goals) {
        goals.collect {
            current = it
            delay(4_000)
            current = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = current != null,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(),
        ) {
            val goal = current ?: return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚽", fontSize = 48.sp)
                    Text(
                        stringResource(R.string.goal_celebration),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${goal.match.home.team.shortName} ${goal.match.home.score ?: 0} – ${goal.match.away.score ?: 0} ${goal.match.away.team.shortName}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                    goal.scorer?.let { Text(it, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
