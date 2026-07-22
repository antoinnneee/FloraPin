package com.florapin.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.florapin.app.R

/** Avatar FloraPin préinstallé, optimisé en WebP 256 px Q80. */
data class DefaultAvatar(
    val id: String,
    @DrawableRes val resourceId: Int,
    val label: String,
)

/**
 * Bibliothèque des compagnons botaniques. Un utilisateur sans photo reçoit
 * toujours le même avatar à partir de son identifiant stable.
 */
object DefaultAvatars {
    val all: List<DefaultAvatar> = listOf(
        DefaultAvatar("fox", R.drawable.avatar_default_fox, "Renard fleuri"),
        DefaultAvatar("owl", R.drawable.avatar_default_owl, "Chouette fleurie"),
        DefaultAvatar("frog", R.drawable.avatar_default_frog, "Grenouille au nénuphar"),
        DefaultAvatar("bee", R.drawable.avatar_default_bee, "Abeille à la lavande"),
        DefaultAvatar("snail", R.drawable.avatar_default_snail, "Escargot fleuri"),
        DefaultAvatar("hedgehog", R.drawable.avatar_default_hedgehog, "Hérisson fleuri"),
        DefaultAvatar("rabbit", R.drawable.avatar_default_rabbit, "Lapin au trèfle"),
        DefaultAvatar("cat", R.drawable.avatar_default_cat, "Chat dans les fleurs"),
        DefaultAvatar("dog", R.drawable.avatar_default_dog, "Chien à la marguerite"),
        DefaultAvatar("butterfly", R.drawable.avatar_default_butterfly, "Papillon bleu"),
        DefaultAvatar("squirrel", R.drawable.avatar_default_squirrel, "Écureuil au gland"),
    )

    fun assignedTo(seed: String): DefaultAvatar {
        val hash = seed.ifBlank { "florapin" }.hashCode()
        return all[Math.floorMod(hash, all.size)]
    }
}

/** Affiche la photo distante ou le compagnon botanique attribué par défaut. */
@Composable
fun FloraAvatar(
    avatarUrl: String?,
    seed: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val avatar = DefaultAvatars.assignedTo(seed)
            Image(
                painter = painterResource(avatar.resourceId),
                contentDescription = contentDescription ?: avatar.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
