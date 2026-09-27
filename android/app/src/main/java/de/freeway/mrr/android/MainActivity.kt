package de.freeway.mrr.android

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import de.freeway.mrr.android.ui.MrrViewModel
import de.freeway.mrr.android.ui.MrrViewModelFactory
import de.freeway.mrr.android.ui.Screen
import de.freeway.mrr.android.ui.screens.LoginScreen
import de.freeway.mrr.android.ui.screens.MainScreen
import de.freeway.mrr.android.ui.screens.RegisterScreen
import de.freeway.mrr.android.ui.screens.StateScreen
import de.freeway.mrr.android.ui.theme.MrrTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MrrApp).container
        setContent {
            MrrTheme {
                val viewModel: MrrViewModel =
                    viewModel(factory = MrrViewModelFactory(container.repository))
                val state by viewModel.ui.collectAsState()

                BackHandler(enabled = state.screen != Screen.Login) { viewModel.back() }

                when (state.screen) {
                    Screen.Login -> LoginScreen(
                        state = state,
                        onUsername = viewModel::onUsername,
                        onPassword = viewModel::onPassword,
                        onLogin = viewModel::login,
                        onOpenRegister = viewModel::openRegister,
                    )
                    Screen.Register -> RegisterScreen(
                        state = state,
                        onUsername = viewModel::onUsername,
                        onPassword = viewModel::onPassword,
                        onRegister = viewModel::register,
                        onBack = viewModel::back,
                    )
                    Screen.Main -> MainScreen(
                        state = state,
                        onCreatePlayer = viewModel::createPlayer,
                        onDeletePlayer = viewModel::deletePlayer,
                        onNewPlayerId = viewModel::onNewPlayerId,
                        onNewPlayerName = viewModel::onNewPlayerName,
                        onOpenState = viewModel::openState,
                        onLogout = viewModel::logout,
                        onClearMessages = viewModel::clearMessages,
                    )
                    Screen.State -> StateScreen(
                        state = state,
                        onExperience = viewModel::onExperienceInput,
                        onLevel = viewModel::onLevelInput,
                        onCoins = viewModel::onCoinsInput,
                        onSave = viewModel::saveState,
                        onBack = viewModel::back,
                        onClearMessages = viewModel::clearMessages,
                    )
                }
            }
        }
    }
}
