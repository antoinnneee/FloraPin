package com.florapin.app.profile

import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.auth.TokenStore
import com.florapin.app.network.dto.AuthResponse
import com.florapin.app.network.dto.DeleteAccountRequest
import com.florapin.app.network.dto.ForgotPasswordRequest
import com.florapin.app.network.dto.LoginRequest
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.RegisterRequest
import com.florapin.app.network.dto.ResetPasswordRequest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private val USER = UserDto("u1", "alice@flora.pin", "Alice", "2026-06-21T09:00:00Z")

private class MemTokenStore(displayName: String? = null) : TokenStore {
    private var a: String? = null
    private var r: String? = null
    private var name: String? = displayName
    var cleared = false
        private set
    override fun accessToken() = a
    override fun refreshToken() = r
    override fun displayName() = name
    override fun saveDisplayName(displayName: String) { name = displayName }
    override fun save(accessToken: String, refreshToken: String) { a = accessToken; r = refreshToken }
    override fun clear() { a = null; r = null; name = null; cleared = true }
}

private class FakeAuthApi(
    private val failMe: Boolean = false,
    private val deleteStatus: Int = 204,
) : AuthApi {
    override suspend fun register(body: RegisterRequest): AuthResponse = AuthResponse(USER, "acc", "ref")
    override suspend fun login(body: LoginRequest): AuthResponse = AuthResponse(USER, "acc", "ref")
    override suspend fun refresh(body: RefreshRequest): TokenPair = TokenPair("a", "r")
    override suspend fun logout(body: RefreshRequest): Response<Unit> = Response.success(null)
    override suspend fun me(): UserDto {
        if (failMe) throw RuntimeException("offline")
        return USER
    }
    override suspend fun deleteAccount(body: DeleteAccountRequest): Response<Unit> =
        if (deleteStatus in 200..299) Response.success(null)
        else Response.error("".toResponseBody(), okhttp3.Response.Builder()
            .code(deleteStatus)
            .message("err")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://localhost/users/me").build())
            .build())
    override suspend fun forgotPassword(body: ForgotPasswordRequest): Response<Unit> =
        Response.success(null)
    override suspend fun resetPassword(body: ResetPasswordRequest): Response<Unit> =
        Response.success(null)
    override suspend fun requestEmailVerification(): Response<Unit> = Response.success(null)
    override suspend fun verifyEmail(body: com.florapin.app.network.dto.VerifyEmailRequest): Response<Unit> =
        Response.success(null)
    override suspend fun changeEmail(body: com.florapin.app.network.dto.ChangeEmailRequest): UserDto = USER
}

/** Stats fixes ; le reste de l'API n'est pas sollicité par le profil. */
private class FakeIdentificationApi(
    private val accepted: Int = 3,
) : com.florapin.app.network.api.IdentificationApi {
    override suspend fun request(flowerId: String) = Response.success<Unit>(null)
    override suspend fun cancel(flowerId: String) = Response.success<Unit>(null)
    override suspend fun listToIdentify() = emptyList<com.florapin.app.network.dto.FlowerDto>()
    override suspend fun propose(
        flowerId: String,
        body: com.florapin.app.network.dto.ProposeSpeciesRequest,
    ) = throw NotImplementedError()
    override suspend fun listProposals(flowerId: String) =
        emptyList<com.florapin.app.network.dto.SpeciesProposalDto>()
    override suspend fun acceptProposal(flowerId: String, proposalId: String) =
        throw NotImplementedError()
    override suspend fun rejectProposal(flowerId: String, proposalId: String) =
        Response.success<Unit>(null)
    override suspend fun proposalStats() =
        com.florapin.app.network.dto.ProposalStatsDto(accepted)
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
        val vm = ProfileViewModel(MemTokenStore(displayName = "Alice"), SessionManager(FakeAuthApi(), MemTokenStore()), FakeIdentificationApi())

        // Avant l'exécution de la coroutine de refresh : nom persisté, pas d'email.
        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("", vm.state.value.email)
    }

    @Test
    fun refresh_fillsEmailFromNetwork() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(store, SessionManager(FakeAuthApi(), store), FakeIdentificationApi())

        advanceUntilIdle()

        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("alice@flora.pin", vm.state.value.email)
        assertEquals(false, vm.state.value.loading)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun loadStats_fillsAcceptedProposals() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(
            store,
            SessionManager(FakeAuthApi(), store),
            FakeIdentificationApi(accepted = 5),
        )

        advanceUntilIdle()

        assertEquals(5, vm.state.value.acceptedProposals)
    }

    @Test
    fun refresh_failure_keepsPersistedNameAndSetsError() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(store, SessionManager(FakeAuthApi(failMe = true), store), FakeIdentificationApi())

        advanceUntilIdle()

        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("", vm.state.value.email)
        assertEquals("Impossible de rafraîchir le profil.", vm.state.value.error)
    }

    @Test
    fun deleteAccount_success_clearsSessionAndCallsOnDeleted() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(store, SessionManager(FakeAuthApi(), store), FakeIdentificationApi())
        advanceUntilIdle()

        var deleted = false
        vm.deleteAccount("pw") { deleted = true }
        advanceUntilIdle()

        assertTrue(deleted)
        assertTrue(store.cleared)
        assertEquals(null, vm.state.value.deleteError)
    }

    @Test
    fun deleteAccount_wrongPassword_setsErrorAndKeepsSession() = runTest {
        val store = MemTokenStore(displayName = "Alice")
        val vm = ProfileViewModel(store, SessionManager(FakeAuthApi(deleteStatus = 401), store), FakeIdentificationApi())
        advanceUntilIdle()
        // Le refresh initial appelle clear() ? non : seul deleteAccount le fait.
        val clearedBefore = store.cleared

        var deleted = false
        vm.deleteAccount("pw") { deleted = true }
        advanceUntilIdle()

        assertFalse(deleted)
        assertEquals(clearedBefore, store.cleared)
        assertEquals("Mot de passe incorrect.", vm.state.value.deleteError)
        assertFalse(vm.state.value.deleting)
    }
}
