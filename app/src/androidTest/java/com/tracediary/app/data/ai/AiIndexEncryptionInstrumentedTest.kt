package com.tracediary.app.data.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tracediary.app.data.security.CryptoBox
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiIndexEncryptionInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        context.deleteDatabase(AiIndexStore.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(AiIndexStore.DATABASE_NAME)
    }

    @Test
    fun index_reopensWithOnlySameApplicationPasswordAndRejectsWrongPassword() = runBlocking {
        val password = "唯一应用密码"
        val correct = AiIndexStore(context) { CryptoBox.deriveDatabasePassphrase(password) }
        correct.write { dao ->
            dao.upsertMetadata(
                AiIndexMetadataEntity(
                    modelId = AiModelManager.MODEL_ID,
                    sourceSignature = "fixture",
                    complete = true,
                    updatedAtEpochMillis = 1L,
                ),
            )
        }
        correct.close()

        val wrong = AiIndexStore(context) { CryptoBox.deriveDatabasePassphrase("错误密码") }
        val wrongResult = runCatching { wrong.read { it.getMetadata() } }
        assertTrue(wrongResult.isFailure)
        wrong.close()

        val restored = AiIndexStore(context) { CryptoBox.deriveDatabasePassphrase(password) }
        assertEquals(AiModelManager.MODEL_ID, restored.read { it.getMetadata() }?.modelId)
        restored.close()
    }
}
