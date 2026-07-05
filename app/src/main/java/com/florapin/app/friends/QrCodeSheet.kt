package com.florapin.app.friends

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Feuille modale affichant le QR code d'ajout d'ami de l'utilisateur courant
 * (TÂCHE 4.5). Un ami le scanne pour envoyer une demande. Le QR encode l'id
 * (UUID) de l'utilisateur, jamais son email.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeSheet(
    userId: String,
    displayName: String,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(userId) {
        runCatching { generateQrBitmap(FriendQrCodec.encode(userId), QR_SIZE_PX) }
            .getOrNull()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Mon QR code",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Montrez ce code à un ami : en le scannant, il vous " +
                    "envoie une demande d'amitié.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code d'ajout d'ami",
                    modifier = Modifier.size(240.dp),
                )
            } else {
                Text(
                    text = "Impossible de générer le QR code.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (displayName.isNotBlank()) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Côté (px) du bitmap QR généré. Suffisant pour un rendu net à ~240 dp. */
private const val QR_SIZE_PX = 640

/**
 * Génère un bitmap QR noir sur blanc pour [content]. Niveau de correction M
 * (bon compromis lisibilité/densité). Purement local (ZXing), aucune requête.
 */
private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
