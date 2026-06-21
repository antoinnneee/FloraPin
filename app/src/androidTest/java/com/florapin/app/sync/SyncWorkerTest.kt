package com.florapin.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exécution réelle de [SyncWorker] via WorkManager testing (NODE-60). */
@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun logout() {
        // Garantit l'état « non connecté ».
        EncryptedTokenStore(context).clear()
    }

    @Test
    fun notLoggedIn_returnsSuccessWithoutNetwork() {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
