package com.florapin.app.ui.components

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Vérifie le mapping commun des erreurs réseau (TÂCHE 6.16). */
class NetworkErrorMapperTest {

    private fun http(code: Int): HttpException =
        HttpException(Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType())))

    @Test
    fun `UnknownHost est classe hors-ligne et reste reessayable`() {
        val info = networkErrorInfo(UnknownHostException("no host"))
        assertEquals(NetworkErrorKind.OFFLINE, info.kind)
        assertTrue(info.isRetryable)
    }

    @Test
    fun `IOException generique est classe hors-ligne`() {
        val info = networkErrorInfo(IOException("socket closed"))
        assertEquals(NetworkErrorKind.OFFLINE, info.kind)
        assertTrue(info.isRetryable)
    }

    @Test
    fun `timeout est un serveur injoignable, pas un mode avion`() {
        val info = networkErrorInfo(SocketTimeoutException("timeout"))
        assertEquals(NetworkErrorKind.SERVER_UNREACHABLE, info.kind)
        assertTrue(info.isRetryable)
    }

    @Test
    fun `5xx est un serveur injoignable reessayable`() {
        val info = networkErrorInfo(http(503))
        assertEquals(NetworkErrorKind.SERVER_UNREACHABLE, info.kind)
        assertTrue(info.isRetryable)
    }

    @Test
    fun `4xx est une erreur client definitive, non reessayable`() {
        val info = networkErrorInfo(http(404))
        assertEquals(NetworkErrorKind.CLIENT, info.kind)
        assertFalse(info.isRetryable)
    }

    @Test
    fun `surcharge httpMessage prime sur le message par defaut`() {
        val info = networkErrorInfo(http(409)) { code ->
            if (code == 409) "Un compte existe déjà avec cet email." else null
        }
        assertEquals("Un compte existe déjà avec cet email.", info.message)
        assertEquals(NetworkErrorKind.CLIENT, info.kind)
    }

    @Test
    fun `httpMessage nul retombe sur le message generique par code`() {
        val info = networkErrorInfo(http(500)) { null }
        assertEquals(NetworkErrorKind.SERVER_UNREACHABLE, info.kind)
    }

    @Test
    fun `exception hors reseau tombe en inconnu mais reste reessayable`() {
        val info = networkErrorInfo(IllegalStateException("boom"))
        assertEquals(NetworkErrorKind.UNKNOWN, info.kind)
        assertEquals("boom", info.message)
        assertTrue(info.isRetryable)
    }

    @Test
    fun `networkErrorMessage renvoie le meme texte que networkErrorInfo`() {
        val e = UnknownHostException("x")
        assertEquals(networkErrorInfo(e).message, networkErrorMessage(e))
    }
}
