package com.florapin.desktop.core

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Les URLs de photos sont présignées : leur signature change à chaque réponse
 * de l'API, alors que l'objet visé, lui, ne bouge pas. Si la clé de cache
 * incluait la signature, chaque rafraîchissement retéléchargerait toute la
 * photothèque — le compagnon perdrait précisément l'avantage qui le rend
 * agréable sur un poste fixe.
 */
class ImageCacheKeyTest {

    @Test
    fun `deux signatures differentes pour la meme photo donnent la meme cle`() {
        val a = ImageStore.cacheKey(
            "https://s3.example.com/florapin/photos/abc123.webp?X-Amz-Signature=aaa&X-Amz-Date=1",
        )
        val b = ImageStore.cacheKey(
            "https://s3.example.com/florapin/photos/abc123.webp?X-Amz-Signature=bbb&X-Amz-Date=2",
        )

        assertEquals(a, b)
    }

    @Test
    fun `deux photos distinctes ont des cles distinctes`() {
        val a = ImageStore.cacheKey("https://s3.example.com/photos/abc123.webp?sig=1")
        val b = ImageStore.cacheKey("https://s3.example.com/photos/def456.webp?sig=1")

        assertNotEquals(a, b)
    }

    @Test
    fun `la miniature et la pleine resolution ne se confondent pas`() {
        val full = ImageStore.cacheKey("https://s3.example.com/photos/abc.webp?sig=1")
        val thumb = ImageStore.cacheKey("https://s3.example.com/photos/abc-thumb.webp?sig=1")

        assertNotEquals(full, thumb)
    }

    @Test
    fun `l'extension d'origine est conservee`() {
        assertTrue(ImageStore.cacheKey("https://x.test/a/b.webp?s=1").endsWith(".webp"))
        assertTrue(ImageStore.cacheKey("https://x.test/a/b.jpg").endsWith(".jpg"))
        assertTrue(ImageStore.cacheKey("https://x.test/tiles/3/4/5.png?key=k").endsWith(".png"))
    }

    @Test
    fun `une url sans extension reste une cle valide`() {
        val key = ImageStore.cacheKey("https://x.test/objets/abcdef")

        assertTrue(key.isNotBlank())
        assertTrue(key.none { it in """<>:"/\|?*""" }, "clé non utilisable comme nom de fichier")
    }

    @Test
    fun `une url malformee ne fait pas echouer le cache`() {
        // Le cache ne doit jamais devenir un point de panne : une URL douteuse
        // se replie sur la partie avant le point d'interrogation.
        val key = ImageStore.cacheKey("pas une url du tout ?sig=1")

        assertTrue(key.isNotBlank())
    }

    @Test
    fun `la cle est purement hexadecimale hors extension`() {
        val key = ImageStore.cacheKey("https://x.test/a/b.webp")
        val digest = key.substringBefore('.')

        assertEquals(64, digest.length, "empreinte SHA-256 attendue")
        assertTrue(digest.all { it in "0123456789abcdef" })
    }
}
