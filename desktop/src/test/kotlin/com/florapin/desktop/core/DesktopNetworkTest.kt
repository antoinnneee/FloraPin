package com.florapin.desktop.core

import com.florapin.app.network.auth.TokenStore
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Sépare bien les deux clients HTTP.
 *
 * Régression vécue : les photos étaient téléchargées avec le client de l'API,
 * qui appose un `Authorization: Bearer`. Or leurs URLs sont présignées
 * (`AWS4-HMAC-SHA256`), et le stockage répond alors
 * `400 InvalidRequest — request has multiple authentication types` : plus
 * aucune image ne s'affichait. Le défaut était invisible en développement, un
 * serveur de test ignorant poliment l'en-tête supplémentaire — d'où ce test.
 */
class DesktopNetworkTest {

    private lateinit var server: MockWebServer

    private val tokenStore = object : TokenStore {
        override fun accessToken() = "jeton-de-test"
        override fun refreshToken() = "refresh-de-test"
        override fun save(accessToken: String, refreshToken: String) = Unit
        override fun clear() = Unit
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        DesktopNetwork.create(tokenStore, server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `le client de contenu n'envoie aucun en-tete d'autorisation`() {
        server.enqueue(MockResponse().setBody("image"))

        DesktopNetwork.contentClient
            .newCall(Request.Builder().url(server.url("/photos/abc.webp?X-Amz-Signature=xyz")).build())
            .execute()
            .close()

        val sent = server.takeRequest()
        assertNull(
            sent.getHeader("Authorization"),
            "une URL présignée ne doit porter aucun en-tête d'autorisation",
        )
    }

    @Test
    fun `le client de l'API envoie bien le jeton`() {
        server.enqueue(MockResponse().setBody("[]"))

        DesktopNetwork.httpClient
            .newCall(Request.Builder().url(server.url("/api/v1/flowers")).build())
            .execute()
            .close()

        val sent = server.takeRequest()
        assertEquals("Bearer jeton-de-test", sent.getHeader("Authorization"))
    }

    @Test
    fun `les deux clients partagent le pool de connexions`() {
        // Dériver par `newBuilder()` plutôt que reconstruire garde un seul pool
        // et un seul dispatcher : deux piles réseau indépendantes doubleraient
        // les connexions ouvertes vers le même hôte.
        assertEquals(
            DesktopNetwork.httpClient.connectionPool,
            DesktopNetwork.contentClient.connectionPool,
        )
        assertEquals(
            DesktopNetwork.httpClient.dispatcher,
            DesktopNetwork.contentClient.dispatcher,
        )
    }

    /**
     * Le test qui verrouille réellement la régression : il emprunte le vrai
     * chemin de téléchargement d'une photo. Rebrancher [ImageStore] sur le
     * client de l'API le ferait échouer immédiatement.
     */
    @Test
    fun `le telechargement d'une photo ne porte pas le jeton de session`() {
        val cache = Files.createTempDirectory("florapin-cache").toFile()
        val previous = ImageStore.cacheDir
        ImageStore.cacheDir = cache
        try {
            server.enqueue(MockResponse().setBody("contenu-webp"))
            val url = server.url("/florapin/flowers/abc.webp?X-Amz-Algorithm=AWS4-HMAC-SHA256")

            val file = runBlocking { ImageStore.fetch(url.toString()) }

            val sent = server.takeRequest()
            assertNull(
                sent.getHeader("Authorization"),
                "le stockage refuse une URL présignée accompagnée d'un en-tête d'autorisation",
            )
            assertEquals("contenu-webp", file?.readText())
        } finally {
            ImageStore.cacheDir = previous
            cache.deleteRecursively()
        }
    }
}
