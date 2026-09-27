package de.freeway.mrr.android.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

import de.freeway.mrr.android.api.AccountResponse
import de.freeway.mrr.android.api.LoginResponse
import de.freeway.mrr.android.api.MrrApiException
import de.freeway.mrr.android.api.PlayerResponse
import de.freeway.mrr.android.api.PlayerStateRequest
import de.freeway.mrr.android.api.PlayerStateResponse
import de.freeway.mrr.android.api.ProfilePlayerResponse
import de.freeway.mrr.android.api.ProfileResponse
import de.freeway.mrr.android.data.MrrRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MrrViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepository(loggedIn: Boolean = false) : MrrRepository {

        var loggedInFlag: Boolean = loggedIn
        var loginError: MrrApiException? = null
        var registerError: MrrApiException? = null
        var profileError: MrrApiException? = null
        var logoutCalled = false
        var savedState: PlayerStateRequest? = null

        var profile = ProfileResponse(
            accountId = 1,
            username = "alice",
            player = ProfilePlayerResponse(
                playerId = "player-0001",
                displayName = "alice",
                level = 1,
                coins = 0,
            ),
        )

        var playersList = listOf(
            PlayerResponse(
                id = 1,
                playerId = "player-0001",
                displayName = "alice",
                createdAt = "2026-09-27T10:00:00Z",
            ),
        )

        var state = PlayerStateResponse(
            playerId = 1,
            experience = 0,
            level = 1,
            coins = 0,
            updatedAt = "2026-09-27T10:00:00Z",
        )

        override val isLoggedIn: Boolean
            get() = loggedInFlag

        override suspend fun register(username: String, password: String): AccountResponse {
            registerError?.let { throw it }
            return AccountResponse(accountId = 9, username = username)
        }

        override suspend fun login(username: String, password: String): LoginResponse {
            loginError?.let { throw it }
            loggedInFlag = true
            return LoginResponse(accessToken = "tok", tokenType = "bearer", expiresIn = 86400)
        }

        override suspend fun logout() {
            logoutCalled = true
            loggedInFlag = false
        }

        override suspend fun profile(): ProfileResponse {
            profileError?.let { throw it }
            return profile
        }

        override suspend fun players(): List<PlayerResponse> = playersList

        override suspend fun createPlayer(playerId: String, displayName: String): PlayerResponse {
            val created = PlayerResponse(
                id = playersList.size + 1L,
                playerId = playerId,
                displayName = displayName,
                createdAt = "2026-09-27T12:00:00Z",
            )
            playersList = playersList + created
            return created
        }

        override suspend fun deletePlayer(playerId: String) {
            playersList = playersList.filterNot { it.playerId == playerId }
        }

        override suspend fun getPlayerState(): PlayerStateResponse = state

        override suspend fun putPlayerState(experience: Int, level: Int, coins: Int): PlayerStateResponse {
            savedState = PlayerStateRequest(experience, level, coins)
            state = state.copy(experience = experience, level = level, coins = coins)
            return state
        }
    }

    @Test
    fun startsOnLoginScreenWhenLoggedOut() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = false))
        assertEquals(Screen.Login, viewModel.ui.value.screen)
    }

    @Test
    fun loginWithValidInputNavigatesToMainWithProfile() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = false))
        viewModel.onUsername("alice")
        viewModel.onPassword("secret")
        viewModel.login()
        val state = viewModel.ui.value
        assertEquals(Screen.Main, state.screen)
        assertNotNull(state.profile)
        assertEquals("alice", state.profile?.username)
        assertEquals(1, state.players.size)
        assertNull(state.error)
        assertEquals("", state.password)
    }

    @Test
    fun blankLoginShowsErrorWithoutCallingApi() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = false))
        viewModel.login()
        val state = viewModel.ui.value
        assertEquals(Screen.Login, state.screen)
        assertNotNull(state.error)
    }

    @Test
    fun failedLoginShowsErrorAndStaysOnLogin() {
        val repository = FakeRepository(loggedIn = false)
        repository.loginError = MrrApiException.Unauthorized("Invalid username or password")
        val viewModel = MrrViewModel(repository)
        viewModel.onUsername("alice")
        viewModel.onPassword("nope")
        viewModel.login()
        val state = viewModel.ui.value
        assertEquals(Screen.Login, state.screen)
        assertTrue(state.error!!.contains("Invalid username or password"))
    }

    @Test
    fun sessionExpiryDuringRestoreRoutesToLogin() {
        val repository = FakeRepository(loggedIn = true)
        repository.profileError = MrrApiException.Unauthorized("Invalid or expired session token")
        val viewModel = MrrViewModel(repository)
        val state = viewModel.ui.value
        assertEquals(Screen.Login, state.screen)
        assertTrue(state.notice!!.contains("Session expired"))
        assertNull(state.error)
    }

    @Test
    fun successfulRestoreStaysOnMain() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = true))
        val state = viewModel.ui.value
        assertEquals(Screen.Main, state.screen)
        assertEquals("alice", state.profile?.username)
        assertEquals(1, state.players.size)
    }

    @Test
    fun logoutReturnsToLoginAndNotifiesFake() {
        val repository = FakeRepository(loggedIn = true)
        val viewModel = MrrViewModel(repository)
        viewModel.logout()
        val state = viewModel.ui.value
        assertTrue(repository.logoutCalled)
        assertEquals(Screen.Login, state.screen)
        assertTrue(state.notice!!.contains("Signed out"))
    }

    @Test
    fun openStatePrefillsFormFromResponse() {
        val repository = FakeRepository(loggedIn = true)
        repository.state = PlayerStateResponse(
            playerId = 1,
            experience = 100,
            level = 3,
            coins = 75,
            updatedAt = "2026-09-27T10:05:00Z",
        )
        val viewModel = MrrViewModel(repository)
        viewModel.openState()
        val state = viewModel.ui.value
        assertEquals(Screen.State, state.screen)
        assertEquals("100", state.experienceInput)
        assertEquals("3", state.levelInput)
        assertEquals("75", state.coinsInput)
    }

    @Test
    fun invalidStateInputShowsErrorAndDoesNotSave() {
        val repository = FakeRepository(loggedIn = true)
        val viewModel = MrrViewModel(repository)
        viewModel.openState()
        viewModel.onLevelInput("not-a-number")
        viewModel.saveState()
        val state = viewModel.ui.value
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("whole numbers"))
        assertNull(repository.savedState)
        assertEquals(Screen.State, state.screen)
    }

    @Test
    fun saveStatePersistsValuesAndUpdatesUi() {
        val repository = FakeRepository(loggedIn = true)
        val viewModel = MrrViewModel(repository)
        viewModel.openState()
        viewModel.onExperienceInput("100")
        viewModel.onLevelInput("3")
        viewModel.onCoinsInput("75")
        viewModel.saveState()
        assertEquals(PlayerStateRequest(100, 3, 75), repository.savedState)
        val state = viewModel.ui.value
        assertEquals(100, state.state?.experience)
        assertEquals(3, state.state?.level)
        assertEquals(75, state.state?.coins)
        assertTrue(state.notice!!.contains("stored"))
    }

    @Test
    fun createPlayerAppendsAndClearsForm() {
        val repository = FakeRepository(loggedIn = true)
        val viewModel = MrrViewModel(repository)
        viewModel.onNewPlayerId("p-new")
        viewModel.onNewPlayerName("Fresh")
        viewModel.createPlayer()
        val state = viewModel.ui.value
        assertEquals(2, state.players.size)
        assertEquals("", state.newPlayerId)
        assertEquals("", state.newPlayerName)
        assertTrue(state.notice!!.contains("created"))
    }

    @Test
    fun blankCreatePlayerShowsError() {
        val repository = FakeRepository(loggedIn = true)
        val viewModel = MrrViewModel(repository)
        viewModel.createPlayer()
        assertNotNull(viewModel.ui.value.error)
        assertEquals(1, viewModel.ui.value.players.size)
    }

    @Test
    fun registerSuccessReturnsToLoginWithNotice() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = false))
        viewModel.onUsername("newbie")
        viewModel.onPassword("secret")
        viewModel.register()
        val state = viewModel.ui.value
        assertEquals(Screen.Login, state.screen)
        assertTrue(state.notice!!.contains("Account created"))
        assertEquals("newbie", state.username)
        assertEquals("", state.password)
    }

    @Test
    fun registerFailureShowsErrorAndStaysOnRegister() {
        val repository = FakeRepository(loggedIn = false)
        repository.registerError = MrrApiException.Conflict("Username already registered")
        val viewModel = MrrViewModel(repository)
        viewModel.openRegister()
        viewModel.onUsername("alice")
        viewModel.onPassword("secret")
        viewModel.register()
        val state = viewModel.ui.value
        assertEquals(Screen.Register, state.screen)
        assertTrue(state.error!!.contains("Username already registered"))
    }

    @Test
    fun backFromRegisterReturnsToLogin() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = false))
        viewModel.openRegister()
        assertEquals(Screen.Register, viewModel.ui.value.screen)
        viewModel.back()
        assertEquals(Screen.Login, viewModel.ui.value.screen)
    }

    @Test
    fun backFromStateReturnsToMain() {
        val viewModel = MrrViewModel(FakeRepository(loggedIn = true))
        viewModel.openState()
        assertEquals(Screen.State, viewModel.ui.value.screen)
        viewModel.back()
        assertEquals(Screen.Main, viewModel.ui.value.screen)
    }
}
