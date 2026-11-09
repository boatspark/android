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
import com.boatspark.app.core.auth.SignInCancelledException
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
            ?: viewModel.showMessage(SignInCancelledException.MESSAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pollWhileForeground()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.signInIntents.collect(signInLauncher::launch)
            }
        }

        setContent {
            BoatSparkTheme {
                BoatSparkApp(
                    viewModel = viewModel,
                    onSignIn = viewModel::startSignIn,
                )
            }
        }
    }
}
