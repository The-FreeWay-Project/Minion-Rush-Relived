package de.freeway.mrr.patcher

import java.io.File

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

import de.freeway.mrr.patcher.api.PatchClient
import de.freeway.mrr.patcher.data.DefaultPatchRepository
import de.freeway.mrr.patcher.pipeline.PatchApplier
import de.freeway.mrr.patcher.pipeline.PatchManager
import de.freeway.mrr.patcher.pipeline.PatchWorkspace
import de.freeway.mrr.patcher.ui.PatcherScreen
import de.freeway.mrr.patcher.ui.PatcherViewModel
import de.freeway.mrr.patcher.ui.PatcherViewModelFactory
import de.freeway.mrr.patcher.ui.theme.PatcherTheme

/**
 * MRR Patcher composition root and single screen. Server URL from
 * BuildConfig.MRR_BASE_URL (one configuration point), installed version from
 * BuildConfig.VERSION_NAME, patch workspace from the app's private files dir
 * (`filesDir/patch-workspace`) - nothing outside it is ever written.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = DefaultPatchRepository(PatchClient.create(BuildConfig.MRR_BASE_URL))
        val workspace = PatchWorkspace(File(filesDir, "patch-workspace"))
        val manager = PatchManager(
            repository = repository,
            installedVersion = BuildConfig.VERSION_NAME,
            baseUrl = BuildConfig.MRR_BASE_URL,
            applier = PatchApplier(workspace),
        )
        setContent {
            PatcherTheme {
                val viewModel: PatcherViewModel = viewModel(
                    factory = PatcherViewModelFactory(repository, BuildConfig.VERSION_NAME, manager),
                )
                val state by viewModel.ui.collectAsState()
                LaunchedEffect(Unit) { viewModel.onUiReady() }
                PatcherScreen(
                    state = state,
                    onAction = viewModel::onPrimaryAction,
                )
            }
        }
    }
}
