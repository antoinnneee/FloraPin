package com.florapin.app.network.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** TokenStore en mémoire (un access token présent, pas de refresh nécessaire). */
private class StubTokenStore(private val access: String?) : TokenStore {
    override fun accessToken() = access
    override fun refreshToken(): String? = null
    override fun save(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

/**
 * Vérifie le périmètre d'attachement du JWT par [AuthInterceptor] (I13) : les
 * endpoints d'auth publics restent anonymes, mais `auth/email/verification`
 * (qui exige un JWT) reçoit bien le token.
 */
class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call(path: String, access: String?): String? {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(StubTokenStore(access)))
            .build()
        client.newCall(Request.Builder().url(server.url(path)).build())
            .execute()
            .close()
        return server.takeRequest().getHeader("Authorization")
    }

    @Test
    fun attachesToken_onProtectedRoutes() {
        assertEquals("Bearer tok", call("/api/v1/flowers", "tok"))
    }

    @Test
    fun attachesToken_onEmailVerificationRequest() {
        // Régression I13 : ce endpoint /auth/ EXIGE un JWT — il doit être attaché.
        assertEquals(
            "Bearer tok",
            call("/api/v1/auth/email/verification", "tok"),
        )
    }

    @Test
    fun omitsToken_onPublicAuthRoutes() {
        assertNull(call("/api/v1/auth/login", "tok"))
        assertNull(call("/api/v1/auth/register", "tok"))
        assertNull(call("/api/v1/auth/refresh", "tok"))
        assertNull(call("/api/v1/auth/logout", "tok"))
        assertNull(call("/api/v1/auth/forgot-password", "tok"))
        assertNull(call("/api/v1/auth/reset-password", "tok"))
        // Validation par token email : public, distinct de « verification ».
        assertNull(call("/api/v1/auth/email/verify", "tok"))
    }
}
