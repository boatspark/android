package com.boatspark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.boatspark.app.ui.BoatSparkApp
import com.boatspark.app.ui.theme.BoatSparkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as BoatSparkApplication).container)
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.let(viewModel::completeSignIn)
            ?: viewModel.showMessage("Sign-in was cancelled")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pollWhileForeground()
            }
        }

        setContent {
            BoatSparkTheme {
                BoatSparkApp(
                    viewModel = viewModel,
                    onSignIn = {
                        lifecycleScope.launch {
                            runCatching { viewModel.createSignInIntent() }
                                .onSuccess(signInLauncher::launch)
                                .onFailure { viewModel.showMessage(it.message ?: "Unable to start sign-in") }
                        }
                    },
                )
            }
        }
    }
}
