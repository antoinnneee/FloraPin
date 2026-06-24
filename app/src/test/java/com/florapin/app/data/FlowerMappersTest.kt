package com.florapin.app.data

import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.SpeciesRefDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowerMappersTest {

    private val epoch = Instant.parse("2026-06-21T09:00:00Z").toEpochMilli()

    @Test
    fun toCreateRequest_convertsDateAndGps() {
        val entity = FlowerEntity(
            id = 1,
            imagePath = "/p.jpg",
            latitude = 48.8584,
            longitude = 2.2945,
            accuracyMeters = 5f,
            createdAt = epoch,
            notes = "jolie",
        )

        val request = entity.toCreateRequest()
        assertEquals("2026-06-21T09:00:00Z", request.takenAt)
        assertEquals(48.8584, request.latitude!!, 1e-9)
        assertEquals(5.0, request.accuracyM!!, 1e-9)
        assertEquals("jolie", request.notes)
    }

    @Test
    fun toCreateRequest_carriesVisibilityAndFeedGps() {
        val entity = FlowerEntity(
            id = 1,
            imagePath = "/p.jpg",
            createdAt = epoch,
            visibility = "friends",
            feedIncludeGps = false,
        )
        val request = entity.toCreateRequest()
        assertEquals("friends", request.visibility)
        assertEquals(false, request.feedIncludeGps)
    }

    @Test
    fun applyTo_marksSyncedAndKeepsLocalId() {
        val local = FlowerEntity(
            id = 7,
            imagePath = "/p.jpg",
            createdAt = epoch,
        )
        val dto = FlowerDto(
            id = "srv-1",
            ownerId = "o",
            imageUrl = "https://x/y.jpg",
            latitude = 1.0,
            longitude = 2.0,
            accuracyM = 3.0,
            takenAt = "2026-06-21T09:00:00Z",
            notes = "maj",
            visibility = "friends",
            feedIncludeGps = false,
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        )

        val merged = dto.applyTo(local)
        assertEquals(7L, merged.id) // id local conservé
        assertEquals("srv-1", merged.serverId)
        assertEquals(SyncState.SYNCED.name, merged.syncState)
        assertEquals("maj", merged.notes)
        assertEquals("https://x/y.jpg", merged.remoteImageUrl)
        assertEquals("friends", merged.visibility)
        assertEquals(false, merged.feedIncludeGps)
        assertEquals(
            Instant.parse("2026-06-21T10:00:00Z").toEpochMilli(),
            merged.updatedAt,
        )
    }

    @Test
    fun toEntity_remoteFlowerHasUrlAndNoLocalPath() {
        val dto = FlowerDto(
            id = "srv-2",
            ownerId = "o",
            imageUrl = "https://x/remote.jpg",
            takenAt = "2026-06-21T09:00:00Z",
            notes = "distante",
            visibility = "private",
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        )

        val entity = dto.toEntity()
        assertEquals("", entity.imagePath)
        assertEquals("https://x/remote.jpg", entity.remoteImageUrl)
        assertEquals("srv-2", entity.serverId)
        assertEquals(SyncState.SYNCED.name, entity.syncState)
    }

    @Test
    fun toPushItem_usesLocalIdAsString() {
        val entity = FlowerEntity(id = 12, imagePath = "/p.jpg", createdAt = epoch)
        assertEquals("12", entity.toPushItem().localId)
    }

    @Test
    fun toCreateRequest_carriesSpeciesAndTags() {
        val entity = FlowerEntity(
            id = 1,
            imagePath = "/p.jpg",
            createdAt = epoch,
            species = "Rosa",
            tags = listOf("rouge", "jardin"),
        )

        val request = entity.toCreateRequest()
        assertEquals("Rosa", request.species)
        assertEquals(listOf("rouge", "jardin"), request.tags)
    }

    @Test
    fun toCreateRequest_emptyTagsBecomeNull() {
        val entity = FlowerEntity(id = 1, imagePath = "/p.jpg", createdAt = epoch)
        assertEquals(null, entity.toCreateRequest().tags)
    }

    @Test
    fun toEntity_carriesSpeciesAndTags() {
        val dto = FlowerDto(
            id = "srv-3",
            ownerId = "o",
            imageUrl = "https://x/y.jpg",
            takenAt = "2026-06-21T09:00:00Z",
            notes = "",
            visibility = "private",
            species = "Tulipa",
            tags = listOf("printemps"),
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        )

        val entity = dto.toEntity()
        assertEquals("Tulipa", entity.species)
        assertEquals(listOf("printemps"), entity.tags)
    }

    @Test
    fun toEntity_carriesResolvedSpeciesReference() {
        val dto = FlowerDto(
            id = "srv-4",
            ownerId = "o",
            imageUrl = "https://x/y.jpg",
            takenAt = "2026-06-21T09:00:00Z",
            notes = "",
            visibility = "private",
            species = "Rosa canina",
            speciesId = "sp-1",
            speciesRef = SpeciesRefDto(
                id = "sp-1",
                scientificName = "Rosa canina",
                commonName = "Églantier",
            ),
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        )

        val entity = dto.toEntity()
        assertEquals("sp-1", entity.speciesId)
        assertEquals("Rosa canina", entity.speciesScientificName)
        assertEquals("Églantier", entity.speciesCommonName)
    }

    @Test
    fun applyTo_updatesResolvedSpeciesAndKeepsCacheWhenAbsent() {
        val local = FlowerEntity(
            id = 9,
            imagePath = "/p.jpg",
            createdAt = epoch,
            speciesId = "old",
            speciesScientificName = "Old name",
            speciesCommonName = "Ancien",
        )
        // DTO sans speciesRef ni speciesId : le cache local est conservé.
        val withoutRef = FlowerDto(
            id = "srv-5",
            ownerId = "o",
            imageUrl = "u",
            takenAt = "2026-06-21T09:00:00Z",
            notes = "",
            visibility = "private",
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        ).applyTo(local)
        assertEquals("old", withoutRef.speciesId)
        assertEquals("Old name", withoutRef.speciesScientificName)

        // DTO avec speciesRef : remplace le cache.
        val withRef = FlowerDto(
            id = "srv-5",
            ownerId = "o",
            imageUrl = "u",
            takenAt = "2026-06-21T09:00:00Z",
            notes = "",
            visibility = "private",
            speciesId = "new",
            speciesRef = SpeciesRefDto("new", "Bellis perennis", "Pâquerette"),
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        ).applyTo(local)
        assertEquals("new", withRef.speciesId)
        assertEquals("Bellis perennis", withRef.speciesScientificName)
        assertEquals("Pâquerette", withRef.speciesCommonName)
    }

    @Test
    fun toEntity_withoutSpeciesReferenceLeavesCacheNull() {
        val entity = FlowerDto(
            id = "srv-6",
            ownerId = "o",
            imageUrl = "u",
            takenAt = "2026-06-21T09:00:00Z",
            notes = "",
            visibility = "private",
            createdAt = "2026-06-21T09:00:00Z",
            updatedAt = "2026-06-21T10:00:00Z",
        ).toEntity()
        assertNull(entity.speciesId)
        assertNull(entity.speciesScientificName)
    }
}
