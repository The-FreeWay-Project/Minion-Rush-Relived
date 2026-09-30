package de.freeway.mrr.patcher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import de.freeway.mrr.patcher.data.PatchRepository
import de.freeway.mrr.patcher.pipeline.PatchManager
import de.freeway.mrr.patcher.pipeline.PatchProgress
import de.freeway.mrr.patcher.pipeline.PatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen renders: manager snapshot plus the installed version. */
data class PatcherUiState(
    val installedVersion: String,
    val serverVersion: String = "\u2013",
    val status: PatchState = PatchState.IDLE,
    val message: String = "Bereit \u2014 nach Updates suchen.",
    val availableVersion: String? = null,
    val serverOnline: Boolean? = null,
    val progress: PatchProgress? = null,
)

/**
 * Thin adapter over [PatchManager]: forwards the manager's state into a
 * [PatcherUiState] and maps user actions (primary button, first frame) onto
 * `check()` / `runPatch()`.
 *
 * [checkForUpdates] only runs from a resting state and [startPatch] only from
 * a checked result, so the state machine edges always hold.
 */
class PatcherViewModel(
    private val repository: PatchRepository,
    private val installedVersion: String,
    patchManager: PatchManager? = null,
) : ViewModel() {

    private val manager = patchManager ?: PatchManager(repository, installedVersion)

    private val _ui = MutableStateFlow(PatcherUiState(installedVersion = installedVersion))
    val ui: StateFlow<PatcherUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            manager.state.collect { snapshot ->
                _ui.update {
                    it.copy(
                        status = snapshot.state,
                        availableVersion = snapshot.availableVersion,
                        serverVersion = snapshot.serverVersion,
                        serverOnline = snapshot.serverOnline,
                        message = snapshot.message,
                        progress = snapshot.progress,
                    )
                }
            }
        }
    }

    /** First frame: an automatic check from the untouched IDLE state. */
    fun onUiReady() {
        if (_ui.value.status == PatchState.IDLE) {
            checkForUpdates()
        }
    }

    fun checkForUpdates() {
        val status = _ui.value.status
        if (status == PatchState.CHECKING || status in PIPELINE_STATES) {
            return
        }
        viewModelScope.launch { manager.check() }
    }

    fun startPatch() {
        val status = _ui.value.status
        if (status != PatchState.UP_TO_DATE && status != PatchState.UPDATE_AVAILABLE) {
            return
        }
        viewModelScope.launch { manager.runPatch() }
    }

    /** One button for every state: check from rest, patch from a result. */
    fun onPrimaryAction() {
        when (_ui.value.status) {
            PatchState.UP_TO_DATE, PatchState.UPDATE_AVAILABLE -> startPatch()
            else -> checkForUpdates()
        }
    }

    private companion object {
        val PIPELINE_STATES = setOf(
            PatchState.DOWNLOADING,
            PatchState.VERIFYING,
            PatchState.READY_TO_PATCH,
            PatchState.PATCHING,
            PatchState.VERIFYING_PATCH,
        )
    }
}

class PatcherViewModelFactory(
    private val repository: PatchRepository,
    private val installedVersion: String,
    private val patchManager: PatchManager? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PatcherViewModel::class.java)) {
            return PatcherViewModel(repository, installedVersion, patchManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
