package com.boatspark.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.UserProfile
import com.boatspark.app.core.model.formatTimestamp
import java.time.Instant

@Composable
fun ProfileScreen(
    profile: CachedValue<UserProfile>?,
    version: String,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Text(
            profile?.value?.email ?: "Profile unavailable",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            if (profile?.value?.isActive == true) "Account active" else "Account status unavailable",
            style = MaterialTheme.typography.bodyMedium,
        )
        profile?.let {
            Text(
                "Updated ${formatTimestamp(Instant.ofEpochMilli(it.refreshedAt).toString())}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            "BoatSpark $version",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Button(onClick = onLogout, modifier = Modifier.padding(top = 32.dp)) {
            Text("Sign out")
        }
    }
}
