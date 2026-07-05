package com.florapin.app.detail

import com.florapin.app.network.api.SpeciesApi
import com.florapin.app.network.dto.PaginatedSpeciesDto
import com.florapin.app.network.dto.SpeciesDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun sp(name: String) = SpeciesDto(
    id = "id-$name",
    scientificName = name,
    commonName = name,
    family = "Rosaceae",
)

private class FakeSpeciesApi(private val results: List<SpeciesDto>) : SpeciesApi {
    val queries = mutableListOf<String>()
    override suspend fun list(page: Int?, limit: Int?) = PaginatedSpeciesDto()
    override suspend fun search(query: String, limit: Int?): List<SpeciesDto> {
        queries += query
        return results
    }
    override suspend fun herbier(): com.florapin.app.network.dto.HerbierDto =
        com.florapin.app.network.dto.HerbierDto()
    override suspend fun get(id: String): SpeciesDto =
        throw UnsupportedOperationException()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun shortQuery_doesNotCallApi() = runTest {
        val api = FakeSpeciesApi(listOf(sp("Rosa canina")))
        val vm = SpeciesPickerViewModel(api)
        val seen = mutableListOf<List<SpeciesDto>>()
        val job = launch { vm.suggestions.toList(seen) }

        vm.onQueryChange("r")
        advanceUntilIdle()

        assertTrue(api.queries.isEmpty())
        assertEquals(emptyList<SpeciesDto>(), vm.suggestions.value)
        job.cancel()
    }

    @Test
    fun query_returnsDebouncedSuggestions() = runTest {
        val api = FakeSpeciesApi(listOf(sp("Rosa canina")))
        val vm = SpeciesPickerViewModel(api)
        val seen = mutableListOf<List<SpeciesDto>>()
        val job = launch { vm.suggestions.toList(seen) }

        vm.onQueryChange("rosa")
        advanceUntilIdle()

        assertEquals(listOf("rosa"), api.queries)
        assertEquals(1, vm.suggestions.value.size)
        assertEquals("Rosa canina", vm.suggestions.value.first().scientificName)
        job.cancel()
    }

    @Test
    fun apiFailure_yieldsEmptySuggestions() = runTest {
        val api = object : SpeciesApi {
            override suspend fun list(page: Int?, limit: Int?) = PaginatedSpeciesDto()
            override suspend fun search(query: String, limit: Int?): List<SpeciesDto> =
                throw RuntimeException("réseau")
            override suspend fun herbier(): com.florapin.app.network.dto.HerbierDto =
                com.florapin.app.network.dto.HerbierDto()
            override suspend fun get(id: String): SpeciesDto =
                throw UnsupportedOperationException()
        }
        val vm = SpeciesPickerViewModel(api)
        val seen = mutableListOf<List<SpeciesDto>>()
        val job = launch { vm.suggestions.toList(seen) }

        vm.onQueryChange("rosa")
        advanceUntilIdle()

        assertEquals(emptyList<SpeciesDto>(), vm.suggestions.value)
        job.cancel()
    }
}
