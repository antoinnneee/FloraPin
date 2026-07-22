package com.florapin.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DefaultAvatarsTest {
    @Test
    fun `le catalogue contient onze avatars uniques`() {
        assertEquals(11, DefaultAvatars.all.size)
        assertEquals(11, DefaultAvatars.all.map { it.id }.distinct().size)
        assertEquals(11, DefaultAvatars.all.map { it.resourceId }.distinct().size)
    }

    @Test
    fun `un meme utilisateur conserve le meme avatar`() {
        val first = DefaultAvatars.assignedTo("user-123")
        val second = DefaultAvatars.assignedTo("user-123")

        assertEquals(first, second)
        assertTrue(first in DefaultAvatars.all)
    }

    @Test
    fun `les avatars restent des WebP binaires compatibles avec openRawResource`() {
        DefaultAvatars.all.forEach { avatar ->
            val file = avatarResourceFile(avatar.id)
            val header = Files.newInputStream(file).use { it.readNBytes(WEBP_HEADER_SIZE) }

            assertEquals("En-tête incomplet pour ${avatar.id}", WEBP_HEADER_SIZE, header.size)
            assertEquals(
                "Conteneur RIFF invalide pour ${avatar.id}",
                "RIFF",
                header.decodeAscii(offset = 0),
            )
            assertEquals(
                "Format WebP invalide pour ${avatar.id}",
                "WEBP",
                header.decodeAscii(offset = 8),
            )
        }
    }

    private fun avatarResourceFile(id: String): Path {
        val relativePath = Path.of(
            "src",
            "main",
            "res",
            "drawable-nodpi",
            "avatar_default_$id.webp",
        )
        return sequenceOf(relativePath, Path.of("app").resolve(relativePath))
            .firstOrNull(Files::isRegularFile)
            ?: throw AssertionError("Ressource avatar introuvable : $relativePath")
    }

    private fun ByteArray.decodeAscii(offset: Int): String =
        String(this, offset, FOURCC_SIZE, StandardCharsets.US_ASCII)

    private companion object {
        const val WEBP_HEADER_SIZE = 12
        const val FOURCC_SIZE = 4
    }
}
