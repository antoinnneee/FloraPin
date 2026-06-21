package com.florapin.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formate un epoch millis en date/heure courte locale (ex. "21 juin 2026 09:14"). */
fun formatCaptureDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
