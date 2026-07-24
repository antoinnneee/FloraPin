package com.florapin.app.profile

import android.content.Context
import android.os.Build
import android.os.Process
import com.florapin.app.BuildConfig
import com.florapin.app.network.api.DiagnosticsApi
import com.florapin.app.network.dto.CreateClientLogRequest
import com.florapin.app.sync.PrefsLastSyncStore
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.sync.SyncStatusStore
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Collecte locale bornée et envoi explicite d'un rapport technique. */
fun interface ProfileDiagnostics {
    suspend fun send()

    companion object {
        val NOOP = ProfileDiagnostics {}

        fun from(context: Context, api: DiagnosticsApi): ProfileDiagnostics =
            AndroidProfileDiagnostics(context.applicationContext, api)
    }
}

private class AndroidProfileDiagnostics(
    private val context: Context,
    private val api: DiagnosticsApi,
) : ProfileDiagnostics {

    override suspend fun send(): Unit = withContext(Dispatchers.IO) {
        val sync = SyncStatusStore(context).read()
        val logs = buildString {
            appendLine("Rapport généré : ${Instant.now()}")
            appendLine("Synchronisation automatique : ${SyncPreferences(context).isEnabled()}")
            appendLine(
                "Dernière synchronisation réussie : " +
                    (PrefsLastSyncStore(context).get() ?: "jamais"),
            )
            appendLine()
            append(recentProcessLogs())
        }.takeLast(MAX_REPORT_CHARS)

        api.sendLogs(
            CreateClientLogRequest(
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(160),
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                locale = Locale.getDefault().toLanguageTag().take(40),
                syncStatus = sync.outcome.name,
                syncError = sync.errorMessage?.let(::redact)?.take(1_000),
                logs = logs,
            ),
        )
        Unit
    }

    /**
     * `--pid` limite la collecte au processus FloraPin et `*:W` exclut les corps
     * HTTP de debug (journalisés au niveau INFO). Une seconde passe masque les
     * secrets et adresses qui auraient malgré tout été inclus dans une erreur.
     */
    private fun recentProcessLogs(): String = runCatching {
        val process = ProcessBuilder(
            "logcat",
            "--pid=${Process.myPid()}",
            "-d",
            "-t",
            LOGCAT_LINES.toString(),
            "*:W",
        ).redirectErrorStream(true).start()
        if (!process.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            return@runCatching "Journaux Android indisponibles (délai dépassé)."
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        redact(output).ifBlank { "Aucun avertissement ou erreur récent." }
    }.getOrElse {
        "Journaux Android indisponibles (${it.javaClass.simpleName})."
    }

    private fun redact(value: String): String = value
        .replace(BEARER_PATTERN, "Bearer [masqué]")
        .replace(JWT_PATTERN, "[jeton masqué]")
        .replace(SECRET_FIELD_PATTERN, "\$1=[masqué]")
        .replace(EMAIL_PATTERN, "[email masqué]")
        .takeLast(MAX_REPORT_CHARS)

    private companion object {
        const val LOGCAT_LINES = 200
        const val LOGCAT_TIMEOUT_SECONDS = 2L
        // Reste sous la limite JSON par défaut de Nest/Express, même en UTF-8.
        const val MAX_REPORT_CHARS = 20_000
        val BEARER_PATTERN = Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/=-]+""")
        val JWT_PATTERN = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b""")
        val SECRET_FIELD_PATTERN = Regex(
            """(?i)\b(password|access[_-]?token|refresh[_-]?token|token)\b\s*[:=]\s*["']?[^,\s"'}]+""",
        )
        val EMAIL_PATTERN = Regex(
            """\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
