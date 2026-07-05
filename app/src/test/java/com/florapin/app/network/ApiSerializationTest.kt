package com.florapin.app.network

import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import com.florapin.app.network.dto.SpeciesDto
import com.florapin.app.network.dto.SpeciesProposalDto
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
    fun parsesFlowerDtoWithResolvedSpecies() {
        val json = """
            {"id":"f1","ownerId":"o1","imageUrl":"u",
             "takenAt":"t","notes":"","visibility":"private",
             "species":"Rosa canina","speciesId":"sp-1",
             "speciesRef":{"id":"sp-1","scientificName":"Rosa canina",
             "commonName":"Églantier"},
             "createdAt":"t","updatedAt":"t"}
        """.trimIndent()

        val flower = moshi.adapter(FlowerDto::class.java).fromJson(json)!!
        assertEquals("sp-1", flower.speciesId)
        assertEquals("Églantier", flower.speciesRef!!.commonName)
    }

    @Test
    fun parsesSpeciesDto_emojiOptional() {
        val json = """
            {"id":"sp-1","scientificName":"Rosa canina","commonName":"Églantier",
             "family":"Rosaceae","description":""}
        """.trimIndent()
        val species = moshi.adapter(SpeciesDto::class.java).fromJson(json)!!
        assertEquals("Rosaceae", species.family)
        assertNull(species.emoji)
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
        // Absent du JSON → false par défaut (NODE-134).
        assertEquals(false, flower.needsIdentification)
        // Absent du JSON → true par défaut (NODE-137).
        assertEquals(true, flower.feedIncludeGps)
        // Cœurs absents → 0 / false par défaut (NODE-140).
        assertEquals(0, flower.likeCount)
        assertEquals(false, flower.likedByMe)
        // Réactions absentes → décompte vide / aucune réaction (TÂCHE 3.5).
        assertEquals(emptyMap<String, Int>(), flower.reactionCounts)
        assertNull(flower.myReaction)
    }

    @Test
    fun parsesFlowerDto_needsIdentification() {
        val json = """
            {"id":"f1","ownerId":"o1","imageUrl":"u","takenAt":"t",
             "notes":"","visibility":"shared","needsIdentification":true,
             "createdAt":"t","updatedAt":"t"}
        """.trimIndent()
        val flower = moshi.adapter(FlowerDto::class.java).fromJson(json)!!
        assertTrue(flower.needsIdentification)
    }

    @Test
    fun serializesProposeRequest() {
        val json = moshi
            .adapter(ProposeSpeciesRequest::class.java)
            .toJson(ProposeSpeciesRequest("Rosa canina"))
        assertTrue(json.contains("\"species\":\"Rosa canina\""))
    }

    @Test
    fun parsesSpeciesProposalDto() {
        val json = """
            {"id":"p1","flowerId":"f1","proposedBy":"u1",
             "species":"Rosa canina","status":"pending","createdAt":"t"}
        """.trimIndent()
        val p = moshi.adapter(SpeciesProposalDto::class.java).fromJson(json)!!
        assertEquals("f1", p.flowerId)
        assertEquals("pending", p.status)
    }
}
