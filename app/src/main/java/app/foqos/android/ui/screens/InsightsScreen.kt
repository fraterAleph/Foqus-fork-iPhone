package app.foqos.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foqos.android.session.InsightsCalculator
import app.foqos.android.session.SessionTimeCalculator
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.components.EmptyState
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.ui.components.SectionTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: FoqosViewModel, onClose: () -> Unit) {
    val sessions by viewModel.recentSessions.collectAsState()
    val summary = remember(sessions) { InsightsCalculator.summarise(sessions) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FoqosCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Stat(
                            label = "Focused",
                            value = SessionTimeCalculator.formatCompact(summary.totalFocusMs),
                            modifier = Modifier.weight(1f),
                        )
                        Stat(
                            label = "Sessions",
                            value = summary.sessionCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        Stat(
                            label = "Streak",
                            value = "${summary.currentStreakDays}d",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item { SectionTitle("Last four weeks") }
            item { Heatmap(summary.days) }

            item { SectionTitle("Recent sessions") }

            if (sessions.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing recorded yet",
                        description = "Finished sessions show up here with the time they held.",
                    )
                }
            }

            items(sessions, key = { it.session.id }) { entry ->
                FoqosCard {
                    Text(entry.profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        dateFormat.format(Date(entry.session.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        SessionTimeCalculator.formatDuration(
                            SessionTimeCalculator.elapsedFocusMs(entry.session)
                        ) + if (entry.session.isActive) " · running" else "",
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Heatmap(days: List<InsightsCalculator.DayBucket>) {
    val maxFocus = days.maxOfOrNull { it.focusMs }?.coerceAtLeast(1) ?: 1
    val weeks = days.chunked(7)

    FoqosCard {
        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { day ->
                    val intensity = day.focusMs.toFloat() / maxFocus
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.12f + 0.78f * intensity
                                )
                            )
                    ) {}
                }
                repeat(7 - week.size) {
                    Column(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                }
            }
        }
        Text(
            "Darker squares are days with more focus time.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
