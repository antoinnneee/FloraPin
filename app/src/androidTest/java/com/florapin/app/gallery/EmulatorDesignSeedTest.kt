package com.florapin.app.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.florapin.app.data.FloraDatabase
import com.florapin.app.data.FlowerEntity
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.onboarding.OnboardingPrefs
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Prépare uniquement un émulateur debug pour les contrôles visuels adaptatifs. */
@RunWith(AndroidJUnit4::class)
class EmulatorDesignSeedTest {

    @Test
    fun seedGalleryForVisualChecks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OnboardingPrefs(context).setDone()
        EncryptedTokenStore(context).apply {
            // Conserve une éventuelle session de test déjà présente sur l'AVD.
            if (refreshToken() == null) {
                save("emulator-design-access", "emulator-design-refresh")
                saveUserId("emulator-design-user")
                saveDisplayName("Galerie de démonstration")
            }
        }

        val dao = FloraDatabase.getInstance(context).flowerDao()
        dao.deleteAll()
        val names = listOf(
            "Violette", "Pétunia", "Capucine", "Lavande",
            "Anémone", "Iris", "Camélia", "Myosotis",
        )
        val colors = listOf(
            0xFF6D74C9, 0xFFD06B9A, 0xFFF09B58, 0xFF7652A8,
            0xFFC94F6D, 0xFF536CB3, 0xFFD5798F, 0xFF5B91C7,
        )
        val directory = File(context.filesDir, "design-seed").apply { mkdirs() }
        val now = System.currentTimeMillis()

        names.forEachIndexed { index, name ->
            val image = File(directory, "flower-$index.png")
            drawFlower(image, colors[index])
            dao.insert(
                FlowerEntity(
                    imagePath = image.absolutePath,
                    createdAt = now - index * 86_400_000L,
                    updatedAt = now - index * 86_400_000L,
                    species = name,
                    notes = "Image de démonstration émulateur",
                ),
            )
        }
    }

    private fun drawFlower(file: File, background: Long) {
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background.toInt())
        val petal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 226, 255) }
        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 214, 100) }
        val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(56, 106, 83) }
        canvas.drawOval(330f, 350f, 390f, 650f, leaf)
        repeat(5) { i ->
            val angle = Math.toRadians((i * 72 - 90).toDouble())
            val x = 360f + (145 * kotlin.math.cos(angle)).toFloat()
            val y = 315f + (145 * kotlin.math.sin(angle)).toFloat()
            canvas.drawCircle(x, y, 118f, petal)
        }
        canvas.drawCircle(360f, 315f, 76f, center)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 92, it) }
        bitmap.recycle()
    }
}
