package com.boatspark.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.BoatAlerts
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.ui.EmptyState

@Composable
fun AlertsScreen(cachedAlerts: CachedValue<BoatAlerts>?) {
    val response = cachedAlerts?.value
    when {
        response == null -> EmptyState("Alerts unavailable", "No cached alert data is available.")
        response.alerts.isEmpty() -> EmptyState("No recent alerts", "${response.boatName} has no alerts in the last seven days.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Text(response.boatName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Recent alerts",
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(response.alerts, key = { it.id }) { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(alert.alertDescription, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatTimestamp(alert.publishedAt),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

