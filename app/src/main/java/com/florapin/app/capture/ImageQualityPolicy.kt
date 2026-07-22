package com.florapin.app.capture

import com.florapin.app.BuildConfig

enum class ImageQualityTier { STANDARD, PREMIUM }

data class ImageEncodingProfile(
    val tier: ImageQualityTier,
    val maxEdge: Int,
    val webpQuality: Int,
)

/** Centralise l'avantage photo premium et le passe bêta temporaire. */
object ImageQualityPolicy {
    val standard = ImageEncodingProfile(
        tier = ImageQualityTier.STANDARD,
        maxEdge = 3_200,
        webpQuality = 70,
    )
    val premium = ImageEncodingProfile(
        tier = ImageQualityTier.PREMIUM,
        maxEdge = 4_000,
        webpQuality = 90,
    )

    /**
     * Pendant la bêta, [BuildConfig.PREMIUM_FOR_ALL_BETA] rend tous les comptes
     * premium. En version stable, [premiumEntitlement] viendra du compte.
     */
    fun currentProfile(premiumEntitlement: Boolean = false): ImageEncodingProfile =
        profileFor(premiumEntitlement, BuildConfig.PREMIUM_FOR_ALL_BETA)

    fun profileFor(
        premiumEntitlement: Boolean,
        betaGrantsPremium: Boolean,
    ): ImageEncodingProfile =
        if (betaGrantsPremium || premiumEntitlement) premium else standard
}
