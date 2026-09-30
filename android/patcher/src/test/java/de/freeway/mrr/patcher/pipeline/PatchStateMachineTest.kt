package de.freeway.mrr.patcher.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchStateMachineTest {

    @Test
    fun allowsTheFullHappyPath() {
        val path = listOf(
            PatchState.IDLE to PatchState.CHECKING,
            PatchState.CHECKING to PatchState.UPDATE_AVAILABLE,
            PatchState.UPDATE_AVAILABLE to PatchState.DOWNLOADING,
            PatchState.DOWNLOADING to PatchState.VERIFYING,
            PatchState.VERIFYING to PatchState.READY_TO_PATCH,
            PatchState.READY_TO_PATCH to PatchState.PATCHING,
            PatchState.PATCHING to PatchState.VERIFYING_PATCH,
            PatchState.VERIFYING_PATCH to PatchState.SUCCESS,
        )
        path.forEach { (from, to) ->
            assertTrue("$from -> $to", PatchStateMachine.canTransition(from, to))
        }
    }

    @Test
    fun allowsVerifyOnlyPathFromUpToDate() {
        assertTrue(PatchStateMachine.canTransition(PatchState.UP_TO_DATE, PatchState.VERIFYING))
        assertTrue(PatchStateMachine.canTransition(PatchState.VERIFYING, PatchState.VERIFYING_PATCH))
        assertTrue(PatchStateMachine.canTransition(PatchState.VERIFYING_PATCH, PatchState.SUCCESS))
    }

    @Test
    fun allowsFailureFromEveryPipelineState() {
        val pipeline = listOf(
            PatchState.CHECKING,
            PatchState.UP_TO_DATE,
            PatchState.UPDATE_AVAILABLE,
            PatchState.DOWNLOADING,
            PatchState.VERIFYING,
            PatchState.READY_TO_PATCH,
            PatchState.PATCHING,
            PatchState.VERIFYING_PATCH,
        )
        pipeline.forEach { from ->
            assertTrue("$from -> FAILED", PatchStateMachine.canTransition(from, PatchState.FAILED))
        }
    }

    @Test
    fun allowsRetryEdgesFromFailedAndSuccess() {
        assertTrue(PatchStateMachine.canTransition(PatchState.FAILED, PatchState.CHECKING))
        assertTrue(PatchStateMachine.canTransition(PatchState.SUCCESS, PatchState.CHECKING))
        assertTrue(PatchStateMachine.canTransition(PatchState.IDLE, PatchState.CHECKING))
    }

    @Test
    fun rejectsIllegalEdges() {
        assertFalse(PatchStateMachine.canTransition(PatchState.IDLE, PatchState.DOWNLOADING))
        assertFalse(PatchStateMachine.canTransition(PatchState.CHECKING, PatchState.DOWNLOADING))
        assertFalse(PatchStateMachine.canTransition(PatchState.CHECKING, PatchState.SUCCESS))
        assertFalse(PatchStateMachine.canTransition(PatchState.VERIFYING, PatchState.DOWNLOADING))
        assertFalse(PatchStateMachine.canTransition(PatchState.SUCCESS, PatchState.PATCHING))
        assertFalse(PatchStateMachine.canTransition(PatchState.FAILED, PatchState.SUCCESS))
        assertFalse(PatchStateMachine.canTransition(PatchState.DOWNLOADING, PatchState.PATCHING))
        assertFalse(PatchStateMachine.canTransition(PatchState.UP_TO_DATE, PatchState.SUCCESS))
    }

    @Test
    fun rejectsSelfTransitions() {
        PatchState.entries.forEach { state ->
            assertFalse(state.name, PatchStateMachine.canTransition(state, state))
        }
    }
}
