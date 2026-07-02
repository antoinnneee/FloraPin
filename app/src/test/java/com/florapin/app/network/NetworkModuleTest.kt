package com.florapin.app.network

import com.florapin.app.network.auth.TokenStore
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Vérifie que le client authentifié est **partagé** : sans ce partage, chaque
 * ViewModel construisait son propre OkHttpClient/authenticator, permettant à deux
 * refresh concurrents de se disputer le même refresh token (rotation → 401 →
 * déconnexion, bug du feed « Partagées avec moi »).
 */
class NetworkModuleTest {

    private class FakeStore : TokenStore {
        override fun accessToken(): String? = null
        override fun refreshToken(): String? = null
        override fun save(accessToken: String, refreshToken: String) {}
        override fun clear() {}
    }

    private val baseUrl = "https://example.test/api/"

    @Test
    fun `createAuthenticated renvoie une instance partagee`() {
        NetworkModule.resetAuthenticatedForTest()
        val a = NetworkModule.createAuthenticated(FakeStore(), baseUrl)
        val b = NetworkModule.createAuthenticated(FakeStore(), baseUrl)
        assertSame("Le client authentifié doit être unique et partagé", a, b)
        NetworkModule.resetAuthenticatedForTest()
    }

    @Test
    fun `create (non authentifie) reste une nouvelle instance`() {
        // Le client NON authentifié n'est pas mis en cache (aucun refresh à
        // sérialiser) : chaque appel construit ses services.
        val a = NetworkModule.create()
        val b = NetworkModule.create()
        assertNotSame(a, b)
    }
}
