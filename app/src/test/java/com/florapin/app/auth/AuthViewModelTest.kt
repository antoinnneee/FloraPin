package com.florapin.app.auth

import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.auth.TokenStore
import com.florapin.app.network.dto.AuthResponse
import com.florapin.app.network.dto.LoginRequest
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.RegisterRequest
import com.florapin.app.network.dto.TokenPair
import com.florapin.app.network.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private class MemTokenStore : TokenStore {
    private var a: String? = null
    private var r: String? = null
    private var uid: String? = null
    private var name: String? = null
    override fun accessToken() = a
    override fun refreshToken() = r
    override fun userId() = uid
    override fun saveUserId(userId: String) { uid = userId }
    override fun displayName() = name
    override fun saveDisplayName(displayName: String) { name = displayName }
    override fun save(accessToken: String, refreshToken: String) {
        a = accessToken; r = refreshToken
    }
    override fun clear() { a = null; r = null; uid = null; name = null }
}

private val USER = UserDto("u1", "a@b.c", "Alice", "2026-06-21T09:00:00Z")

private class FakeAuthApi(private val failLogin: Boolean = false) : AuthApi {
    override suspend fun register(body: RegisterRequest): AuthResponse =
        AuthResponse(USER, "acc", "ref")
    override suspend fun login(body: LoginRequest): AuthResponse {
        if (failLogin) {
            throw HttpException(Response.error<AuthResponse>(401, "".toResponseBody()))
        }
        return AuthResponse(USER, "acc", "ref")
    }
    override suspend fun refresh(body: RefreshRequest): TokenPair = TokenPair("a", "r")
    override suspend fun logout(body: RefreshRequest): Response<Unit> =
        Response.success(null)
    override suspend fun me(): UserDto = USER
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(failLogin: Boolean = false): AuthViewModel {
        val session = SessionManager(FakeAuthApi(failLogin), MemTokenStore())
        return AuthViewModel(session)
    }

    @Test
    fun login_success_emitsSuccess() = runTest {
        val vm = viewModel()
        vm.login("a@b.c", "password")
        assertEquals(AuthUiState.Loading, vm.state.value)

        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is AuthUiState.Success)
        assertEquals("Alice", (state as AuthUiState.Success).user.displayName)
    }

    @Test
    fun login_failure_emitsError() = runTest {
        val vm = viewModel(failLogin = true)
        vm.login("a@b.c", "bad")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Identifiants invalides.", (state as AuthUiState.Error).message)
    }

    @Test
    fun register_success_emitsSuccess() = runTest {
        val vm = viewModel()
        vm.register("a@b.c", "password", "Alice")
        advanceUntilIdle()
        assertTrue(vm.state.value is AuthUiState.Success)
    }

    @Test
    fun login_persistsUserIdAndDisplayName() = runTest {
        val store = MemTokenStore()
        val session = SessionManager(FakeAuthApi(), store)
        session.login("a@b.c", "password")

        assertEquals("u1", store.userId())
        assertEquals("Alice", store.displayName())
    }

    @Test
    fun register_persistsDisplayName() = runTest {
        val store = MemTokenStore()
        val session = SessionManager(FakeAuthApi(), store)
        session.register("a@b.c", "password", "Alice")

        assertEquals("Alice", store.displayName())
    }

    @Test
    fun fetchCurrentUser_refreshesPersistedProfile() = runTest {
        val store = MemTokenStore()
        val session = SessionManager(FakeAuthApi(), store)

        val user = session.fetchCurrentUser()

        assertEquals("Alice", user.displayName)
        assertEquals("u1", store.userId())
        assertEquals("Alice", store.displayName())
    }

    @Test
    fun logout_clearsDisplayName() = runTest {
        val store = MemTokenStore()
        SessionManager(FakeAuthApi(), store).login("a@b.c", "password")
        assertEquals("Alice", store.displayName())

        SessionManager(FakeAuthApi(), store).logout()
        assertEquals(null, store.displayName())
    }

    @Test
    fun logout_clearsTokens_andInvokesCallback() = runTest {
        val store = MemTokenStore().apply { save("acc", "ref") }
        val vm = AuthViewModel(SessionManager(FakeAuthApi(), store))

        var done = false
        vm.logout { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(null, store.refreshToken())
        assertEquals(AuthUiState.Idle, vm.state.value)
    }

    @Test
    fun logout_purgesLocalData() = runTest {
        val store = MemTokenStore().apply { save("acc", "ref") }
        var cleared = false
        val cleaner = com.florapin.app.network.auth.SessionDataCleaner { cleared = true }
        val session = SessionManager(FakeAuthApi(), store, cleaner)

        AuthViewModel(session).logout { }
        advanceUntilIdle()

        assertTrue(cleared)
    }
}
