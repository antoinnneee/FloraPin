package com.florapin.app.albums

import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.MemAlbumDao
import com.florapin.app.data.SyncState
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.GroupsApi
import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.AddFriendByIdRequest
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.AlbumPermissionDto
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.CreateGroupRequest
import com.florapin.app.network.dto.FriendProfileDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.GroupDto
import com.florapin.app.network.dto.GroupMemberDto
import com.florapin.app.network.dto.InviteMemberRequest
import com.florapin.app.network.dto.SetAlbumCoverRequest
import com.florapin.app.network.dto.SetAlbumGroupRequest
import com.florapin.app.network.dto.SetAlbumPermissionsRequest
import com.florapin.app.network.dto.UpdateAlbumRequest
import com.florapin.app.network.dto.UpdateGroupRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private const val ME = "user-me"
private const val ALICE = "user-alice"
private const val BOB = "user-bob"

private fun member(userId: String, role: String = "member") =
    GroupMemberDto(userId = userId, displayName = userId, role = role, status = "accepted")

private fun group(
    ownerId: String = ME,
    members: List<GroupMemberDto> = listOf(member(ME, "owner")),
) = GroupDto(
    id = "grp-1",
    ownerId = ownerId,
    name = "Balade",
    role = if (ownerId == ME) "owner" else "member",
    status = "accepted",
    members = members,
    createdAt = "2026-07-20T10:00:00Z",
)

private fun friendship(userId: String, status: String = "accepted") = FriendshipDto(
    id = "fr-$userId",
    status = status,
    direction = "outgoing",
    user = FriendUserDto(id = userId, displayName = userId, email = "$userId@flora.pin"),
    createdAt = "2026-07-20T10:00:00Z",
)

private fun albumDto(
    permissionMode: String = "open",
    permissions: List<AlbumPermissionDto> = emptyList(),
    name: String = "Nom serveur",
    groupId: String? = "grp-1",
) = AlbumDto(
    id = "srv-1",
    ownerId = ME,
    name = name,
    clientId = "cli-1",
    groupId = groupId,
    permissionMode = permissionMode,
    permissions = permissions,
    createdAt = "2026-07-20T10:00:00Z",
)

/** GroupsApi de test : les routes non câblées échouent bruyamment. */
private class FakeGroupsApi(
    private val onGet: suspend (String) -> GroupDto = { group() },
    private val onCreate: suspend (CreateGroupRequest) -> GroupDto = { group() },
    private val onInvite: suspend (String, InviteMemberRequest) -> GroupDto = { _, _ -> group() },
    private val onRemoveMember: suspend (String, String) -> Response<Unit> =
        { _, _ -> Response.success(Unit) },
) : GroupsApi {
    val invited = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override suspend fun get(id: String): GroupDto = onGet(id)
    override suspend fun create(body: CreateGroupRequest): GroupDto = onCreate(body)
    override suspend fun invite(id: String, body: InviteMemberRequest): GroupDto {
        invited += body.userId
        return onInvite(id, body)
    }
    override suspend fun removeMember(id: String, userId: String): Response<Unit> {
        removed += userId
        return onRemoveMember(id, userId)
    }

    override suspend fun list(): List<GroupDto> = unsupported()
    override suspend fun rename(id: String, body: UpdateGroupRequest): GroupDto = unsupported()
    override suspend fun delete(id: String): Response<Unit> = unsupported()
    override suspend fun accept(id: String): GroupDto = unsupported()

    private fun unsupported(): Nothing =
        throw AssertionError("Route non attendue dans ce test.")
}

/** AlbumsApi de test, limitée aux routes utilisées par la collaboration. */
private class FakeCollabAlbumsApi(
    private val onGet: suspend (String) -> AlbumDto = { albumDto() },
    private val onSetGroup: suspend (String, SetAlbumGroupRequest) -> AlbumDto =
        { _, _ -> albumDto() },
    private val onSetPermissions: suspend (String, SetAlbumPermissionsRequest) -> AlbumDto =
        { _, _ -> albumDto() },
) : AlbumsApi {
    val permissionCalls = mutableListOf<SetAlbumPermissionsRequest>()
    val groupCalls = mutableListOf<SetAlbumGroupRequest>()

    override suspend fun get(id: String): AlbumDto = onGet(id)
    override suspend fun setGroup(id: String, body: SetAlbumGroupRequest): AlbumDto {
        groupCalls += body
        return onSetGroup(id, body)
    }
    override suspend fun setPermissions(
        id: String,
        body: SetAlbumPermissionsRequest,
    ): AlbumDto {
        permissionCalls += body
        return onSetPermissions(id, body)
    }

    override suspend fun list(): List<AlbumDto> = unsupported()
    override suspend fun create(body: CreateAlbumRequest): AlbumDto = unsupported()
    override suspend fun rename(id: String, body: UpdateAlbumRequest): AlbumDto = unsupported()
    override suspend fun delete(id: String): Response<Unit> = unsupported()
    override suspend fun addFlower(id: String, body: AddFlowerToAlbumRequest): AlbumDto =
        unsupported()
    override suspend fun removeFlower(id: String, flowerId: String): AlbumDto = unsupported()
    override suspend fun setCover(id: String, body: SetAlbumCoverRequest): AlbumDto =
        unsupported()

    private fun unsupported(): Nothing =
        throw AssertionError("Route non attendue dans ce test.")
}

/** FriendshipsApi de test : seule `list` sert à proposer des invitations. */
private class FakeFriendshipsApi(
    private val friends: List<FriendshipDto> = emptyList(),
) : FriendshipsApi {
    override suspend fun list(): List<FriendshipDto> = friends

    override suspend fun request(body: CreateFriendshipRequest): FriendshipDto = unsupported()
    override suspend fun requestById(body: AddFriendByIdRequest): FriendshipDto = unsupported()
    override suspend fun accept(id: String): FriendshipDto = unsupported()
    override suspend fun remove(id: String): Response<Unit> = unsupported()
    override suspend fun profile(id: String): FriendProfileDto = unsupported()

    private fun unsupported(): Nothing =
        throw AssertionError("Route non attendue dans ce test.")
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumCollaborationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        dao: MemAlbumDao = MemAlbumDao(),
        albums: AlbumsApi = FakeCollabAlbumsApi(),
        groups: GroupsApi = FakeGroupsApi(),
        friendships: FriendshipsApi = FakeFriendshipsApi(),
        selfUserId: String? = ME,
    ) = AlbumCollaborationViewModel(
        AlbumRepository(dao) { 100L },
        albums,
        groups,
        friendships,
    ) { selfUserId }

    private fun album(
        id: Long = 1L,
        serverId: String? = "srv-1",
        groupId: String? = "grp-1",
        name: String = "Balade",
        permissionMode: String = "open",
        syncState: String = SyncState.SYNCED.name,
    ) = AlbumEntity(
        id = id,
        serverId = serverId,
        clientId = "cli-1",
        name = name,
        groupId = groupId,
        permissionMode = permissionMode,
        createdAt = 1L,
        updatedAt = 1L,
        syncState = syncState,
    )

    // --- Chargement ---

    @Test
    fun load_soloAlbum_staysOfflineWithoutNetworkCall() = runTest(dispatcher) {
        // Aucune API n'est câblée : un album solo ne doit déclencher aucun appel.
        val viewModel = vm(
            albums = FakeCollabAlbumsApi(onGet = { throw AssertionError("aucun appel attendu") }),
            groups = FakeGroupsApi(onGet = { throw AssertionError("aucun appel attendu") }),
        )

        viewModel.load(album(groupId = null, permissionMode = "restricted"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.group)
        assertFalse(state.loading)
        assertNull(state.error)
        // Le régime local est conservé tel quel pour l'affichage.
        assertEquals("restricted", state.permissionMode)
        assertFalse(state.isOwner)
    }

    @Test
    fun load_collaborativeAlbum_exposesGroupAndServerPermissions() = runTest(dispatcher) {
        val viewModel = vm(
            albums = FakeCollabAlbumsApi(
                onGet = {
                    albumDto(
                        permissionMode = "restricted",
                        permissions = listOf(
                            AlbumPermissionDto(ALICE, canEdit = true),
                            AlbumPermissionDto(BOB, canEdit = false),
                        ),
                    )
                },
            ),
            groups = FakeGroupsApi(onGet = { group(members = listOf(member(ME, "owner"), member(ALICE))) }),
            friendships = FakeFriendshipsApi(listOf(friendship(ALICE), friendship(BOB))),
        )

        viewModel.load(album())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.group)
        // Le régime ET les droits viennent du serveur, pas de la copie locale.
        assertEquals("restricted", state.permissionMode)
        assertEquals(mapOf(ALICE to true, BOB to false), state.permissions)
        // Alice est déjà membre : seul Bob reste invitable.
        assertEquals(listOf(BOB), state.invitableFriends.map { it.user.id })
        assertTrue(state.isOwner)
        assertNull(state.error)
    }

    @Test
    fun load_nonOwner_isNotOwner() = runTest(dispatcher) {
        val viewModel = vm(
            groups = FakeGroupsApi(
                onGet = { group(ownerId = ALICE, members = listOf(member(ALICE, "owner"))) },
            ),
        )

        viewModel.load(album())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOwner)
    }

    @Test
    fun load_pendingFriendRequests_areNotInvitable() = runTest(dispatcher) {
        val viewModel = vm(
            friendships = FakeFriendshipsApi(
                listOf(friendship(ALICE, status = "pending"), friendship(BOB)),
            ),
        )

        viewModel.load(album())
        advanceUntilIdle()

        // Seules les amitiés acceptées sont proposées à l'invitation.
        assertEquals(listOf(BOB), viewModel.state.value.invitableFriends.map { it.user.id })
    }

    @Test
    fun load_offline_reportsErrorAndStopsLoading() = runTest(dispatcher) {
        val viewModel = vm(groups = FakeGroupsApi(onGet = { throw IOException("réseau indisponible") }))

        viewModel.load(album())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.error)
        assertFalse(state.loading)

        viewModel.clearError()
        assertNull(viewModel.state.value.error)
    }

    // --- Passage en collaboratif ---

    @Test
    fun makeCollaborative_unsyncedAlbum_asksForSyncFirst() = runTest(dispatcher) {
        val viewModel = vm(
            albums = FakeCollabAlbumsApi(onGet = { throw AssertionError("aucun appel attendu") }),
            groups = FakeGroupsApi(onGet = { throw AssertionError("aucun appel attendu") }),
        )

        viewModel.load(album(serverId = null, groupId = null))
        viewModel.makeCollaborative()
        advanceUntilIdle()

        // Un album jamais poussé n'a pas d'id serveur : rien n'est tenté côté réseau.
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun makeCollaborative_createsGroupAndPersistsAttachment() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val local = album(groupId = null, name = "Nom local")
        dao.albums[local.id] = local
        val albumsApi = FakeCollabAlbumsApi(
            onSetGroup = { _, _ -> albumDto(name = "Nom serveur") },
            onGet = { albumDto(name = "Nom serveur") },
        )
        val viewModel = vm(dao = dao, albums = albumsApi)

        viewModel.load(local)
        viewModel.makeCollaborative()
        advanceUntilIdle()

        // Le groupe créé est rattaché à l'album, en régime ouvert par défaut.
        assertEquals(listOf("grp-1"), albumsApi.groupCalls.map { it.groupId })
        assertEquals(listOf("open"), albumsApi.groupCalls.map { it.permissionMode })
        assertEquals("grp-1", dao.albums[local.id]!!.groupId)
    }

    @Test
    fun makeCollaborative_keepsLocalNameAndSyncState() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        // Renommage local pas encore poussé : la réponse serveur ne doit pas l'écraser.
        val local = album(groupId = null, name = "Nom local", syncState = SyncState.PENDING.name)
        dao.albums[local.id] = local
        val viewModel = vm(
            dao = dao,
            albums = FakeCollabAlbumsApi(
                onSetGroup = { _, _ -> albumDto(name = "Nom serveur") },
                onGet = { albumDto(name = "Nom serveur") },
            ),
        )

        viewModel.load(local)
        viewModel.makeCollaborative()
        advanceUntilIdle()

        val saved = dao.albums[local.id]!!
        assertEquals("Nom local", saved.name)
        assertEquals(SyncState.PENDING.name, saved.syncState)
    }

    // --- Membres ---

    @Test
    fun invite_forwardsFriendIdToGroup() = runTest(dispatcher) {
        val groupsApi = FakeGroupsApi()
        val viewModel = vm(groups = groupsApi)

        viewModel.load(album())
        viewModel.invite(ALICE)
        advanceUntilIdle()

        assertEquals(listOf(ALICE), groupsApi.invited)
    }

    @Test
    fun removeMember_forwardsUserIdToGroup() = runTest(dispatcher) {
        val groupsApi = FakeGroupsApi()
        val viewModel = vm(groups = groupsApi)

        viewModel.load(album())
        viewModel.removeMember(BOB)
        advanceUntilIdle()

        assertEquals(listOf(BOB), groupsApi.removed)
    }

    @Test
    fun invite_soloAlbum_isNoOp() = runTest(dispatcher) {
        val groupsApi = FakeGroupsApi()
        val viewModel = vm(groups = groupsApi)

        viewModel.load(album(groupId = null))
        viewModel.invite(ALICE)
        advanceUntilIdle()

        // Sans groupe, il n'y a personne à inviter.
        assertTrue(groupsApi.invited.isEmpty())
    }

    @Test
    fun invite_failure_reportsError() = runTest(dispatcher) {
        val viewModel = vm(
            groups = FakeGroupsApi(onInvite = { _, _ -> throw IOException("hors-ligne") }),
        )

        viewModel.load(album())
        viewModel.invite(ALICE)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    // --- Régime de droits ---

    @Test
    fun setPermissionMode_sendsModeWithExistingEntries() = runTest(dispatcher) {
        val albumsApi = FakeCollabAlbumsApi(
            onGet = {
                albumDto(
                    permissionMode = "restricted",
                    permissions = listOf(AlbumPermissionDto(ALICE, canEdit = true)),
                )
            },
            onSetPermissions = { _, _ -> albumDto(permissionMode = "open") },
        )
        val viewModel = vm(albums = albumsApi)

        viewModel.load(album())
        advanceUntilIdle()
        viewModel.setPermissionMode("open")
        advanceUntilIdle()

        val call = albumsApi.permissionCalls.single()
        assertEquals("open", call.mode)
        // Les droits déjà accordés sont conservés lors du changement de régime.
        assertEquals(listOf(AlbumPermissionDto(ALICE, canEdit = true)), call.entries)
    }

    @Test
    fun setMemberCanEdit_mergesEntryAndForcesRestrictedMode() = runTest(dispatcher) {
        val albumsApi = FakeCollabAlbumsApi(
            onGet = {
                albumDto(
                    permissionMode = "restricted",
                    permissions = listOf(AlbumPermissionDto(ALICE, canEdit = true)),
                )
            },
            onSetPermissions = { _, _ -> albumDto(permissionMode = "restricted") },
        )
        val viewModel = vm(albums = albumsApi)

        viewModel.load(album())
        advanceUntilIdle()
        viewModel.setMemberCanEdit(BOB, canEdit = true)
        advanceUntilIdle()

        val call = albumsApi.permissionCalls.single()
        // Accorder un droit au cas par cas implique le régime restreint.
        assertEquals("restricted", call.mode)
        // L'entrée de Bob s'ajoute sans effacer celle d'Alice.
        assertEquals(
            mapOf(ALICE to true, BOB to true),
            call.entries.associate { it.userId to it.canEdit },
        )
    }

    @Test
    fun setMemberCanEdit_revokingKeepsExplicitFalseEntry() = runTest(dispatcher) {
        val albumsApi = FakeCollabAlbumsApi(
            onGet = {
                albumDto(
                    permissionMode = "restricted",
                    permissions = listOf(AlbumPermissionDto(ALICE, canEdit = true)),
                )
            },
            onSetPermissions = { _, _ -> albumDto(permissionMode = "restricted") },
        )
        val viewModel = vm(albums = albumsApi)

        viewModel.load(album())
        advanceUntilIdle()
        viewModel.setMemberCanEdit(ALICE, canEdit = false)
        advanceUntilIdle()

        // Le retrait est explicite (false), pas une simple absence d'entrée.
        assertEquals(
            mapOf(ALICE to false),
            albumsApi.permissionCalls.single().entries.associate { it.userId to it.canEdit },
        )
    }

    @Test
    fun setMemberCanEdit_unsyncedAlbum_isNoOp() = runTest(dispatcher) {
        val albumsApi = FakeCollabAlbumsApi()
        val viewModel = vm(albums = albumsApi)

        viewModel.load(album(serverId = null, groupId = null))
        viewModel.setMemberCanEdit(ALICE, canEdit = true)
        advanceUntilIdle()

        assertTrue(albumsApi.permissionCalls.isEmpty())
    }

    @Test
    fun setPermissionMode_failure_reportsError() = runTest(dispatcher) {
        val viewModel = vm(
            albums = FakeCollabAlbumsApi(
                onSetPermissions = { _, _ -> throw IOException("hors-ligne") },
            ),
        )

        viewModel.load(album())
        advanceUntilIdle()
        viewModel.setPermissionMode("restricted")
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }
}
