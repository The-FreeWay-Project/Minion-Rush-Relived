package de.freeway.mrr.patcher.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

import de.freeway.mrr.patcher.api.PatchManifest
import de.freeway.mrr.patcher.data.PatchApiException
import de.freeway.mrr.patcher.pipeline.PatchState
import de.freeway.mrr.patcher.data.PatchRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatcherViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepository(
        private val manifest: PatchManifest? = null,
        private val error: Exception? = null,
    ) : PatchRepository {
        var lastInstalled: String? = null

        override suspend fun fetchManifest(installedVersion: String): PatchManifest {
            lastInstalled = installedVersion
            error?.let { throw it }
            return requireNotNull(manifest) { "fake has no manifest" }
        }
    }

    private fun manifest(version: String, message: String) = PatchManifest(
        version = version,
        channel = "stable",
        platform = "android",
        serverVersion = "0.3.0",
        message = message,
        files = emptyList(),
    )

    private fun viewModel(repository: PatchRepository, installed: String = "A1.0.0") =
        PatcherViewModel(repository, installed)

    @Test
    fun initialStateShowsIdleWithInstalledVersion() {
        val state = viewModel(FakeRepository()).ui.value
        assertEquals("A1.0.0", state.installedVersion)
        assertEquals(PatchState.IDLE, state.status)
        assertEquals("\u2013", state.serverVersion)
    }

    @Test
    fun checkWithUpToDateManifestShowsUpToDate() {
        val repository = FakeRepository(
            manifest("A1.0.0", "MRR Patcher is up to date.")
        )
        val vm = viewModel(repository)

        vm.checkForUpdates()

        val state = vm.ui.value
        assertEquals(PatchState.UP_TO_DATE, state.status)
        assertEquals("MRR Patcher is up to date.", state.message)
        assertEquals("0.3.0", state.serverVersion)
        assertEquals("A1.0.0", repository.lastInstalled)
    }

    @Test
    fun checkWithNewerManifestShowsUpdateAvailable() {
        val vm = viewModel(
            FakeRepository(manifest("A1.1.0", "Update to A1.1.0 available."))
        )

        vm.checkForUpdates()

        val state = vm.ui.value
        assertEquals(PatchState.UPDATE_AVAILABLE, state.status)
        assertEquals("Update to A1.1.0 available.", state.message)
        assertEquals("0.3.0", state.serverVersion)
    }

    @Test
    fun checkWithServerFailureShowsError() {
        val vm = viewModel(FakeRepository(error = PatchApiException.Server(500)))

        vm.checkForUpdates()

        val state = vm.ui.value
        assertEquals(PatchState.FAILED, state.status)
        assertTrue(state.message.contains("500"))
    }

    @Test
    fun checkWithNetworkFailureShowsError() {
        val vm = viewModel(
            FakeRepository(error = PatchApiException.Network(Exception("refused")))
        )

        vm.checkForUpdates()

        val state = vm.ui.value
        assertEquals(PatchState.FAILED, state.status)
        assertTrue(state.message.contains("erreichbar"))
    }

    @Test
    fun checkWithUnparsableManifestVersionShowsError() {
        val vm = viewModel(
            FakeRepository(manifest("banana", "whatever"))
        )

        vm.checkForUpdates()

        val state = vm.ui.value
        assertEquals(PatchState.FAILED, state.status)
        assertTrue(state.message.contains("Manifest"))
    }

    @Test
    fun checkSendsInstalledVersionToServer() {
        val repository = FakeRepository(manifest("A1.0.0", "MRR Patcher is up to date."))
        val vm = viewModel(repository, installed = "A0.9.0")

        vm.checkForUpdates()

        assertEquals("A0.9.0", repository.lastInstalled)
        assertEquals(PatchState.UPDATE_AVAILABLE, vm.ui.value.status)
    }
}

