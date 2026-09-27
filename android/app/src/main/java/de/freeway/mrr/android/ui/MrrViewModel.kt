package de.freeway.mrr.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.freeway.mrr.android.api.MrrApiException
import de.freeway.mrr.android.api.ProfileResponse
import de.freeway.mrr.android.api.PlayerResponse
import de.freeway.mrr.android.api.PlayerStateResponse
import de.freeway.mrr.android.data.MrrRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object Main : Screen
    data object State : Screen
}

data class MrrUiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val username: String = "",
    val password: String = "",
    val profile: ProfileResponse? = null,
    val players: List<PlayerResponse> = emptyList(),
    val state: PlayerStateResponse? = null,
    val experienceInput: String = "0",
    val levelInput: String = "1",
    val coinsInput: String = "0",
    val newPlayerId: String = "",
    val newPlayerName: String = "",
)

/**
 * Single ViewModel driving all four screens (Login, Register, Main, State).
 * State is held in one [MrrUiState] flow; screens are switched via
 * [MrrUiState.screen] and any 401 routes back to [Screen.Login].
 */
class MrrViewModel(
    private val repository: MrrRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MrrUiState())
    val ui: StateFlow<MrrUiState> = _ui.asStateFlow()

    init {
        if (repository.isLoggedIn) {
            _ui.update { it.copy(screen = Screen.Main, loading = true) }
            launchOp {
                val profile = repository.profile()
                _ui.update { it.copy(profile = profile) }
                val list = repository.players()
                _ui.update { it.copy(players = list) }
            }
        }
    }

    fun onUsername(value: String) =
        _ui.update { it.copy(username = value, error = null, notice = null) }

    fun onPassword(value: String) =
        _ui.update { it.copy(password = value, error = null, notice = null) }

    fun onNewPlayerId(value: String) =
        _ui.update { it.copy(newPlayerId = value, error = null, notice = null) }

    fun onNewPlayerName(value: String) =
        _ui.update { it.copy(newPlayerName = value, error = null, notice = null) }

    fun onExperienceInput(value: String) =
        _ui.update { it.copy(experienceInput = value, error = null, notice = null) }

    fun onLevelInput(value: String) =
        _ui.update { it.copy(levelInput = value, error = null, notice = null) }

    fun onCoinsInput(value: String) =
        _ui.update { it.copy(coinsInput = value, error = null, notice = null) }

    fun clearMessages() = _ui.update { it.copy(error = null, notice = null) }

    fun login() {
        val s = _ui.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _ui.update { it.copy(error = "Please enter username and password") }
            return
        }
        launchOp {
            repository.login(s.username.trim(), s.password)
            val profile = repository.profile()
            val list = repository.players()
            _ui.update {
                it.copy(screen = Screen.Main, profile = profile, players = list, password = "")
            }
        }
    }

    fun openRegister() =
        _ui.update { it.copy(screen = Screen.Register, error = null, notice = null, password = "") }

    fun register() {
        val s = _ui.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _ui.update { it.copy(error = "Please enter username and password") }
            return
        }
        launchOp {
            repository.register(s.username.trim(), s.password)
            _ui.update {
                MrrUiState(
                    screen = Screen.Login,
                    username = s.username.trim(),
                    notice = "Account created — please sign in.",
                )
            }
        }
    }

    fun logout() {
        launchOp {
            repository.logout()
            _ui.update { MrrUiState(screen = Screen.Login, notice = "Signed out.") }
        }
    }

    fun openState() {
        launchOp {
            val state = repository.getPlayerState()
            _ui.update {
                it.copy(
                    screen = Screen.State,
                    state = state,
                    experienceInput = state.experience.toString(),
                    levelInput = state.level.toString(),
                    coinsInput = state.coins.toString(),
                )
            }
        }
    }

    fun back() {
        when (_ui.value.screen) {
            Screen.Register -> _ui.update { it.copy(screen = Screen.Login, error = null, notice = null) }
            Screen.State -> _ui.update { it.copy(screen = Screen.Main, error = null, notice = null) }
            else -> Unit
        }
    }

    fun saveState() {
        val s = _ui.value
        val experience = s.experienceInput.toIntOrNull()
        val level = s.levelInput.toIntOrNull()
        val coins = s.coinsInput.toIntOrNull()
        when {
            experience == null || level == null || coins == null ->
                _ui.update { it.copy(error = "Experience, level and coins must be whole numbers") }
            experience < 0 || level < 1 || coins < 0 ->
                _ui.update { it.copy(error = "Experience/coins must be >= 0 and level >= 1") }
            else -> launchOp {
                val state = repository.putPlayerState(experience, level, coins)
                _ui.update { it.copy(state = state, notice = "Save state stored.") }
            }
        }
    }

    fun createPlayer() {
        val s = _ui.value
        if (s.newPlayerId.isBlank() || s.newPlayerName.isBlank()) {
            _ui.update { it.copy(error = "Player ID and display name are required") }
            return
        }
        launchOp {
            repository.createPlayer(s.newPlayerId.trim(), s.newPlayerName.trim())
            val list = repository.players()
            _ui.update {
                it.copy(players = list, newPlayerId = "", newPlayerName = "", notice = "Player created.")
            }
        }
    }

    fun deletePlayer(playerId: String) {
        launchOp {
            repository.deletePlayer(playerId)
            val list = repository.players()
            _ui.update { it.copy(players = list, notice = "Player deleted.") }
        }
    }

    private fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, notice = null) }
            try {
                block()
            } catch (e: MrrApiException) {
                handleError(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "Unexpected error") }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    private fun handleError(e: MrrApiException) {
        if (e is MrrApiException.Unauthorized) {
            if (_ui.value.screen == Screen.Login) {
                _ui.update { it.copy(error = e.message) }
            } else {
                _ui.update {
                    MrrUiState(screen = Screen.Login, notice = "Session expired — please sign in again.")
                }
            }
        } else {
            _ui.update { it.copy(error = e.message) }
        }
    }
}

class MrrViewModelFactory(
    private val repository: MrrRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MrrViewModel::class.java)) {
            return MrrViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
