package de.freeway.mrr.patcher.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

import de.freeway.mrr.patcher.api.PatchManifest
import de.freeway.mrr.patcher.data.PatchRepository
import de.freeway.mrr.patcher.pipeline.PatchState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatcherViewModelActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepository(private val manifest: PatchManifest) : PatchRepository {
        var fetches = 0

        override suspend fun fetchManifest(installedVersion: String): PatchManifest {
            fetches++
            return manifest
        }
    }

    private fun manifest(version: String) = PatchManifest(
        version = version,
        channel = "stable",
        platform = "android",
        serverVersion = "0.3.0",
        message = "MRR is up to date.",
        files = emptyList(),
    )

    @Test
    fun uiReadyTriggersAutomaticCheckFromIdle() {
        val repository = FakeRepository(manifest("A1.0.0"))
        val vm = PatcherViewModel(repository, "A1.0.0")
        assertEquals(PatchState.IDLE, vm.ui.value.status)

        vm.onUiReady()

        assertEquals(1, repository.fetches)
        assertEquals(PatchState.UP_TO_DATE, vm.ui.value.status)
    }

    @Test
    fun uiReadyDoesNotDoubleCheck() {
        val repository = FakeRepository(manifest("A1.0.0"))
        val vm = PatcherViewModel(repository, "A1.0.0")

        vm.onUiReady()
        vm.onUiReady()

        assertEquals(1, repository.fetches)
    }

    @Test
    fun primaryActionFromUpdateAvailableStartsThePatchRun() {
        val repository = FakeRepository(manifest("A9.9.9"))
        val vm = PatcherViewModel(repository, "A1.0.0")
        vm.checkForUpdates()
        assertEquals(PatchState.UPDATE_AVAILABLE, vm.ui.value.status)

        vm.onPrimaryAction()

        // The manifest carries no files, so the pipeline ends in FAILED.
        assertEquals(PatchState.FAILED, vm.ui.value.status)
        assertEquals(1, repository.fetches)
    }

    @Test
    fun primaryActionFromSuccessChecksAgain() {
        val repository = FakeRepository(manifest("A1.0.0"))
        val vm = PatcherViewModel(repository, "A1.0.0")
        vm.onUiReady()
        assertEquals(PatchState.UP_TO_DATE, vm.ui.value.status)
        vm.startPatch()
        assertEquals(PatchState.SUCCESS, vm.ui.value.status)

        vm.onPrimaryAction()

        assertEquals(2, repository.fetches)
        assertEquals(PatchState.UP_TO_DATE, vm.ui.value.status)
    }

    @Test
    fun startPatchIsIgnoredWithoutACheckedResult() {
        val repository = FakeRepository(manifest("A9.9.9"))
        val vm = PatcherViewModel(repository, "A1.0.0")

        vm.startPatch()

        assertEquals(PatchState.IDLE, vm.ui.value.status)
        assertEquals(0, repository.fetches)
    }
}
