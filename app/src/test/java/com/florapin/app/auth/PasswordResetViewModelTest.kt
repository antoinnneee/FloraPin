package com.florapin.app.auth

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
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private val RESET_USER = UserDto("u1", "a@b.c", "Alice", "2026-06-21T09:00:00Z")

private class ResetStubTokenStore : TokenStore {
    override fun accessToken() = null
    override fun refreshToken() = null
    override fun displayName() = null
    override fun saveDisplayName(displayName: String) {}
    override fun save(accessToken: String, refreshToken: String) {}
    override fun clear() {}
}

private fun errorResponse(code: Int): Response<Unit> = Response.error(
    "".toResponseBody(),
    okhttp3.Response.Builder()
        .code(code)
        .message("err")
        .protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("http://localhost/auth").build())
        .build(),
)

private class ResetFakeAuthApi(
    private val forgotStatus: Int = 200,
    private val resetStatus: Int = 200,
) : AuthApi {
    override suspend fun register(body: RegisterRequest): AuthResponse = AuthResponse(RESET_USER, "a", "r")
    override suspend fun login(body: LoginRequest): AuthResponse = AuthResponse(RESET_USER, "a", "r")
    override suspend fun refresh(body: RefreshRequest): TokenPair = TokenPair("a", "r")
    override suspend fun logout(body: RefreshRequest): Response<Unit> = Response.success(null)
    override suspend fun me(): UserDto = RESET_USER
    override suspend fun deleteAccount(body: DeleteAccountRequest): Response<Unit> = Response.success(null)
    override suspend fun forgotPassword(body: ForgotPasswordRequest): Response<Unit> =
        if (forgotStatus == 200) Response.success(null) else errorResponse(forgotStatus)
    override suspend fun resetPassword(body: ResetPasswordRequest): Response<Unit> =
        if (resetStatus == 200) Response.success(null) else errorResponse(resetStatus)
    override suspend fun requestEmailVerification(): Response<Unit> = Response.success(null)
    override suspend fun verifyEmail(body: com.florapin.app.network.dto.VerifyEmailRequest): Response<Unit> =
        Response.success(null)
    override suspend fun changeEmail(body: com.florapin.app.network.dto.ChangeEmailRequest): UserDto = RESET_USER
}

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(api: AuthApi) =
        PasswordResetViewModel(SessionManager(api, ResetStubTokenStore()))

    @Test
    fun requestReset_success_setsRequestSent() = runTest {
        val model = vm(ResetFakeAuthApi())
        model.requestReset("a@b.c")
        advanceUntilIdle()

        assertTrue(model.state.value.requestSent)
        assertFalse(model.state.value.loading)
        assertEquals(null, model.state.value.error)
    }

    @Test
    fun resetPassword_success_setsResetDone() = runTest {
        val model = vm(ResetFakeAuthApi())
        model.resetPassword("tok", "nouveauPass1")
        advanceUntilIdle()

        assertTrue(model.state.value.resetDone)
        assertEquals(null, model.state.value.error)
    }

    @Test
    fun resetPassword_invalidToken_setsError() = runTest {
        val model = vm(ResetFakeAuthApi(resetStatus = 401))
        model.resetPassword("bad", "nouveauPass1")
        advanceUntilIdle()

        assertFalse(model.state.value.resetDone)
        assertEquals("Lien invalide ou expiré. Refaites une demande.", model.state.value.error)
    }
}
