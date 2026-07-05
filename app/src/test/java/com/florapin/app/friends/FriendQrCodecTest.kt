package com.florapin.app.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendQrCodecTest {

    private val uuid = "22222222-2222-2222-2222-222222222222"

    @Test
    fun encode_thenDecode_roundTrips() {
        val payload = FriendQrCodec.encode(uuid)
        assertEquals(uuid, FriendQrCodec.decode(payload))
    }

    @Test
    fun decode_toleratesSurroundingWhitespace() {
        assertEquals(uuid, FriendQrCodec.decode("  " + FriendQrCodec.encode(uuid) + "\n"))
    }

    @Test
    fun decode_rejectsForeignPayload() {
        assertNull(FriendQrCodec.decode("https://example.com/$uuid"))
        assertNull(FriendQrCodec.decode(uuid))
        assertNull(FriendQrCodec.decode(""))
    }

    @Test
    fun decode_rejectsMalformedUuid() {
        assertNull(FriendQrCodec.decode("florapin:friend:not-a-uuid"))
        assertNull(FriendQrCodec.decode("florapin:friend:1234"))
    }
}
