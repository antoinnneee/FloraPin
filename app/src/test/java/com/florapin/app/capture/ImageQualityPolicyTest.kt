package com.florapin.app.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageQualityPolicyTest {
    @Test
    fun beta_grantsPremiumToEveryAccount() {
        val profile = ImageQualityPolicy.profileFor(
            premiumEntitlement = false,
            betaGrantsPremium = true,
        )
        assertEquals(ImageQualityPolicy.premium, profile)
    }

    @Test
    fun stable_nonPremiumAccountUsesStandardProfile() {
        val profile = ImageQualityPolicy.profileFor(
            premiumEntitlement = false,
            betaGrantsPremium = false,
        )
        assertEquals(ImageQualityPolicy.standard, profile)
        assertEquals(3_200, profile.maxEdge)
        assertEquals(70, profile.webpQuality)
    }

    @Test
    fun stable_premiumAccountKeepsPremiumProfile() {
        val profile = ImageQualityPolicy.profileFor(
            premiumEntitlement = true,
            betaGrantsPremium = false,
        )
        assertEquals(ImageQualityPolicy.premium, profile)
        assertEquals(4_000, profile.maxEdge)
        assertEquals(90, profile.webpQuality)
    }
}
