package com.florapin.app.profile

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private val USER = UserDto("u1", "alice@flora.pin", "Alice", "2026-06-21T09:00:00Z")

private class MemTokenStore(displayName: String? = null) : TokenStore {
    private var a: String? = null
    private var r: String? = null
    private var name: String? = displayName
    override fun accessToken() = a
    override fun refreshToken() = r
    override fun displayName() = name
    override fun saveDisplayName(displayName: String) { name = displayName }
    override fun save(accessToken: String, refreshToken: String) { a = accessToken; r = refreshToken }
    override fun clear() { a = null; r = null; name = null }
}

private class FakeAuthApi(private val failMe: Boolean = false) : AuthApi {
    override suspend fun register(body: RegisterRequest): AuthResponse = AuthResponse(USER, "acc", "ref")
    override suspend fun login(body: LoginRequest): AuthResponse = AuthResponse(USER, "acc", "ref")
    override suspend fun refresh(body: RefreshRequest): TokenPair = TokenPair("a", "r")
    override suspend fun logout(body: RefreshRequest): Response<Unit> = Response.success(null)
    override suspend fun me(): UserDto {
        if (failMe) throw RuntimeException("offline")
        return USER
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialState_showsPersistedDisplayName() = runTest {
        val vm = ProfileViewModel(MemTokenStore(displayName = "Alice"), SessionManager(FakeAuthApi(), MemTokenStore()))

        // Avant l'exécution de la coroutine de refresh : nom persisté, pas d'email.
        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("", vm.state.value.email)
    }

    @Test
    fun refresh_fillsEmailFromNetwork() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(store, SessionManager(FakeAuthApi(), store))

        advanceUntilIdle()

        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("alice@flora.pin", vm.state.value.email)
        assertEquals(false, vm.state.value.loading)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun refresh_failure_keepsPersistedNameAndSetsError() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(store, SessionManager(FakeAuthApi(failMe = true), store))

        advanceUntilIdle()

        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("", vm.state.value.email)
        assertEquals("Impossible de rafraîchir le profil.", vm.state.value.error)
    }
}
