package com.boatspark.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.boatspark.app.core.model.Boat
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.ui.EmptyState
import java.time.Instant

@Composable
fun BoatListScreen(
    boats: List<CachedValue<Boat>>,
    onBoatSelected: (Long) -> Unit,
) {
    if (boats.isEmpty()) {
        EmptyState("No boats", "No boats are assigned to this account.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(boats, key = { it.value.id }) { cached ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onBoatSelected(cached.value.id) },
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(cached.value.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        cached.value.deviceIdentifier,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Updated ${formatTimestamp(Instant.ofEpochMilli(cached.refreshedAt).toString())}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

