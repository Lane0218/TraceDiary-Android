package com.tracediary.app.data.ai

import com.tracediary.app.domain.model.AiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class AiDefaultModelTest {
    @Test
    fun unconfiguredModelDefaultsToDeepSeekFlash() {
        assertEquals("deepseek-flash", DataStoreAiPreferencesRepository.DEFAULT_MODEL)
        assertEquals(DataStoreAiPreferencesRepository.DEFAULT_MODEL, AiConfiguration().model)
    }
}
