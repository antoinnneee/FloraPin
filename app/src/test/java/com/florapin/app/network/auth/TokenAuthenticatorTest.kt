package com.florapin.app.network.auth

import com.florapin.app.network.dto.TokenPair
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** TokenStore en mémoire pour les tests. */
private class FakeTokenStore(
    private var access: String?,
    private var refresh: String?,
) : TokenStore {
    override fun accessToken() = access
    override fun refreshToken() = refresh
    override fun save(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }
    override fun clear() {
        access = null
        refresh = null
    }
}

class TokenAuthenticatorTest {

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

    @Test
    fun refreshesOn401_thenRetriesWithNewToken() {
        // 1re réponse : 401 (token expiré) ; 2e : 200 après refresh.
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val store = FakeTokenStore(access = "old", refresh = "r1")
        val refresher = object : TokenRefresher {
            override fun refresh(refreshToken: String): TokenPair? {
                assertEquals("r1", refreshToken)
                return TokenPair(accessToken = "new", refreshToken = "r2")
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, refresher))
            .build()

        val request = Request.Builder().url(server.url("/api/v1/flowers")).build()
        val response = client.newCall(request).execute()
        response.close()

        assertEquals(200, response.code)

        // 1re requête avec l'ancien token, 2e (rejouée) avec le nouveau.
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer old", first.getHeader("Authorization"))
        assertEquals("Bearer new", second.getHeader("Authorization"))

        // Le store a été mis à jour par la rotation.
        assertEquals("new", store.accessToken())
        assertEquals("r2", store.refreshToken())
    }

    @Test
    fun clearsTokens_whenRefreshFails() {
        server.enqueue(MockResponse().setResponseCode(401))

        val store = FakeTokenStore(access = "old", refresh = "r1")
        val refresher = object : TokenRefresher {
            override fun refresh(refreshToken: String): TokenPair? = null
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, refresher))
            .build()

        val request = Request.Builder().url(server.url("/api/v1/flowers")).build()
        client.newCall(request).execute().close()

        // Refresh échoué : session purgée.
        assertEquals(null, store.accessToken())
        assertEquals(null, store.refreshToken())
    }

    @Test
    fun runBlockingImportIsAvailable() {
        // Garde-fou : la dépendance coroutines de test reste disponible.
        runBlocking { assertEquals(2, 1 + 1) }
    }
}
