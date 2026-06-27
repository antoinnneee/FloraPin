package com.florapin.app.network

import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.CreateFlowerResponse
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ImageUrlResponse
import com.florapin.app.network.dto.PresignedUpload
import com.florapin.app.network.dto.UpdateFlowerRequest
import com.florapin.app.network.upload.ImageUploader
import java.io.File
import retrofit2.Response
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun sampleFlower(id: String) = FlowerDto(
    id = id,
    ownerId = "o",
    imageUrl = "https://x/$id.jpg",
    takenAt = "2026-06-21T09:00:00Z",
    notes = "",
    visibility = "private",
    createdAt = "2026-06-21T09:00:00Z",
    updatedAt = "2026-06-21T09:00:00Z",
)

/** FlowersApi factice : create renvoie une URL d'upload paramétrable. */
private class FakeFlowersApi(private val uploadUrl: String) : FlowersApi {
    override suspend fun create(body: CreateFlowerRequest): CreateFlowerResponse =
        CreateFlowerResponse(
            flower = sampleFlower("server-1"),
            upload = PresignedUpload(uploadUrl, "PUT", 600),
        )

    override suspend fun list(species: String?, tag: String?): List<FlowerDto> =
        emptyList()
    override suspend fun get(id: String): FlowerDto = sampleFlower(id)
    override suspend fun uploadImage(
        id: String,
        file: okhttp3.MultipartBody.Part,
    ): FlowerDto = sampleFlower(id)
    override suspend fun imageUrl(id: String): ImageUrlResponse =
        ImageUrlResponse("https://x/$id.jpg?sig")
    override suspend fun update(id: String, body: UpdateFlowerRequest): FlowerDto =
        sampleFlower(id)
    override suspend fun delete(id: String): Response<Unit> =
        Response.success(null)
}

class RemoteFlowerSourceTest {

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
    fun uploadFlower_createsThenPutsImage() {
        server.enqueue(MockResponse().setResponseCode(200))

        val api = FakeFlowersApi(server.url("/bucket/obj.jpg").toString())
        val source = RemoteFlowerSource(api, ImageUploader(OkHttpClient()))

        val file = File.createTempFile("flora", ".jpg").apply {
            writeBytes(ByteArray(64) { 1 })
            deleteOnExit()
        }

        val flower = runBlocking {
            source.uploadFlower(
                CreateFlowerRequest(takenAt = "2026-06-21T09:00:00Z"),
                file,
            )
        }

        assertEquals("server-1", flower.id)

        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("image/jpeg", put.getHeader("Content-Type"))
        assertEquals(64L, put.bodySize)
    }
}
