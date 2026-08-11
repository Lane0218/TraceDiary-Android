package com.tracediary.app.data.ai

import com.tracediary.app.domain.model.AiSourceRef
import com.tracediary.app.domain.model.AiSourceType
import com.tracediary.app.domain.model.AiEvidence
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEvidenceValidatorTest {
    @Test
    fun parseAndValidate_acceptsOnlyCurrentVerbatimEvidence() = runTest {
        val answer = AiEvidenceValidator.parseAndValidate(
            raw = """{"answer":"根据你的记录，你在三月洗过牙。","evidence":[{"sourceType":"diary","sourceKey":"2026-03-18","quote":"今天下午去洗牙"}]}""",
            sourceReader = { ref -> if (ref == AiSourceRef(AiSourceType.Diary, "2026-03-18")) "今天下午去洗牙，医生说恢复不错。" else null },
        )

        assertEquals("根据你的记录，你在三月洗过牙。", answer.text)
        assertEquals("今天下午去洗牙", answer.evidence.single().quote)
    }

    @Test
    fun parseAndValidate_rejectsFabricatedQuote() = runTest {
        val error = runCatching {
            AiEvidenceValidator.parseAndValidate(
                raw = """{"answer":"去了上海","evidence":[{"sourceType":"diary","sourceKey":"2026-01-01","quote":"去了上海"}]}""",
                sourceReader = { "只是计划去上海，后来取消了。" },
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun parseAndValidate_acceptsCurrentDeterministicStatisticAndRejectsStaleValue() = runTest {
        val current = """{"referenceDate":"2025-12-31","annualRecordCount":123}"""
        val valid = AiEvidenceValidator.parseAndValidate(
            raw = """{"answer":"根据本地统计，2025 年记录了 123 天。","evidence":[{"sourceType":"statistics","sourceKey":"2025-12-31","quote":"\"annualRecordCount\":123"}]}""",
            sourceReader = { ref -> if (ref == AiSourceRef(AiSourceType.Statistics, "2025-12-31")) current else null },
        )
        assertEquals(AiSourceType.Statistics, valid.evidence.single().source.type)

        val stale = runCatching {
            AiEvidenceValidator.parseAndValidate(
                raw = """{"answer":"根据本地统计，2025 年记录了 122 天。","evidence":[{"sourceType":"statistics","sourceKey":"2025-12-31","quote":"\"annualRecordCount\":122"}]}""",
                sourceReader = { current },
            )
        }
        assertTrue(stale.isFailure)
    }

    @Test
    fun validate_acceptsStructuredSubmissionWithoutJsonRoundTrip() = runTest {
        val answer = AiEvidenceValidator.validate(
            answerText = "根据你的记录，你在三月洗过牙。",
            insufficientEvidence = false,
            evidence = listOf(
                AiEvidence(
                    AiSourceRef(AiSourceType.Diary, "2026-03-18"),
                    "今天下午去洗牙",
                ),
            ),
            sourceReader = { "今天下午去洗牙，医生说恢复不错。" },
        )

        assertEquals("根据你的记录，你在三月洗过牙。", answer.text)
        assertEquals(1, answer.evidence.size)
    }

    @Test
    fun validate_rejectsStructuredSubmissionWithMissingCurrentSource() = runTest {
        val result = runCatching {
            AiEvidenceValidator.validate(
                answerText = "去了上海",
                insufficientEvidence = false,
                evidence = listOf(
                    AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-01-01"), "去了上海"),
                ),
                sourceReader = { null },
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun validate_keepsValidEvidenceAndDropsInvalidItems() = runTest {
        val validRef = AiSourceRef(AiSourceType.Diary, "2026-03-18")
        val answer = AiEvidenceValidator.validate(
            answerText = "根据你的记录，三月洗过牙。",
            insufficientEvidence = false,
            evidence = listOf(
                AiEvidence(validRef, "今天下午去洗牙"),
                AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-03-19"), "不存在的内容"),
                AiEvidence(validRef, "模型改写后的内容"),
            ),
            sourceReader = { ref -> if (ref == validRef) "今天下午去洗牙，医生说恢复不错。" else null },
        )

        assertEquals(listOf(AiEvidence(validRef, "今天下午去洗牙")), answer.evidence)
    }

    @Test
    fun validate_toleratesWhitespaceAndFullWidthFormattingDifferences() = runTest {
        val answer = AiEvidenceValidator.validate(
            answerText = "根据你的记录，完成了复诊。",
            insufficientEvidence = false,
            evidence = listOf(
                AiEvidence(
                    AiSourceRef(AiSourceType.Diary, "2026-03-18"),
                    "复诊完成, 恢复正常",
                ),
            ),
            sourceReader = { "复诊完成，\n恢复正常" },
        )

        assertEquals(1, answer.evidence.size)
    }
}
