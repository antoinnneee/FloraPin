package com.florapin.app.network

import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.FlowerDto
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie le mapping JSON ↔ DTO (adapters Moshi générés). */
class ApiSerializationTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun parsesFlowerDto() {
        val json = """
            {"id":"f1","ownerId":"o1","imageUrl":"https://x/y.jpg",
             "latitude":48.8584,"longitude":2.2945,"accuracyM":5.0,
             "takenAt":"2026-06-21T09:00:00Z","notes":"","visibility":"private",
             "species":"Rosa canina","tags":["jardin"],
             "createdAt":"2026-06-21T09:00:00Z","updatedAt":"2026-06-21T09:00:00Z"}
        """.trimIndent()

        val flower = moshi.adapter(FlowerDto::class.java).fromJson(json)!!
        assertEquals("f1", flower.id)
        assertEquals(48.8584, flower.latitude!!, 1e-9)
        assertEquals("Rosa canina", flower.species)
        assertEquals(listOf("jardin"), flower.tags)
    }

    @Test
    fun serializesCreateRequest_omitsNulls() {
        val json = moshi
            .adapter(CreateFlowerRequest::class.java)
            .toJson(CreateFlowerRequest(takenAt = "2026-06-21T09:00:00Z"))

        assertTrue(json.contains("\"takenAt\""))
        // Champs nuls non émis par défaut.
        assertTrue(!json.contains("latitude"))
    }

    @Test
    fun flowerDto_defaultsTagsToEmpty() {
        val json = """
            {"id":"f1","ownerId":"o1","imageUrl":"u","takenAt":"t",
             "notes":"","visibility":"private","createdAt":"t","updatedAt":"t"}
        """.trimIndent()
        val flower = moshi.adapter(FlowerDto::class.java).fromJson(json)!!
        assertEquals(emptyList<String>(), flower.tags)
        assertNull(flower.latitude)
    }
}
