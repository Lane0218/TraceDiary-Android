package com.tracediary.app.ui.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchExcerptTest {

    @Test
    fun `关键词位于正文后部时摘要围绕命中位置`() {
        val excerpt = buildSearchExcerpt(
            content = "前".repeat(80) + "关键词" + "后".repeat(50),
            keyword = "关键词",
        )

        assertEquals(
            "…" + "前".repeat(18) + "关键词" + "后".repeat(27) + "…",
            excerpt.text,
        )
        assertEquals(
            listOf(SearchHighlightRange(start = 19, endExclusive = 22)),
            excerpt.highlightRanges,
        )
    }

    @Test
    fun `关键词靠近开头或结尾时仅添加实际需要的省略号`() {
        val atStart = buildSearchExcerpt(
            content = "目标" + "后".repeat(100),
            keyword = "目标",
        )
        val atEnd = buildSearchExcerpt(
            content = "前".repeat(100) + "目标",
            keyword = "目标",
        )

        assertFalse(atStart.text.startsWith("…"))
        assertTrue(atStart.text.endsWith("…"))
        assertTrue(atEnd.text.startsWith("…"))
        assertFalse(atEnd.text.endsWith("…"))
        assertTrue(atEnd.text.endsWith("目标"))
    }

    @Test
    fun `摘要高亮全部可见命中并保持原始大小写`() {
        val excerpt = buildSearchExcerpt(
            content = "使用 Kotlin 编写，kotlin 测试通过",
            keyword = "kotlin",
        )

        assertEquals(
            listOf("Kotlin", "kotlin"),
            excerpt.highlightRanges.map { range ->
                excerpt.text.substring(range.start, range.endExclusive)
            },
        )
    }

    @Test
    fun `换行和连续空白折叠后高亮范围与显示文本一致`() {
        val excerpt = buildSearchExcerpt(
            content = "前文\n\n\t  关键词   后文",
            keyword = "关键词",
        )

        assertEquals("前文 关键词 后文", excerpt.text)
        val range = excerpt.highlightRanges.single()
        assertEquals("关键词", excerpt.text.substring(range.start, range.endExclusive))
    }

    @Test
    fun `摘要边界不会拆分 emoji 代理对`() {
        val excerpt = buildSearchExcerpt(
            content = "😀".repeat(40) + "目标" + "😀".repeat(40),
            keyword = "目标",
        )

        assertEquals("目标", excerpt.highlightedTexts().single())
        excerpt.text.forEachIndexed { index, character ->
            if (Character.isHighSurrogate(character)) {
                assertTrue(index + 1 < excerpt.text.length)
                assertTrue(Character.isLowSurrogate(excerpt.text[index + 1]))
            }
            if (Character.isLowSurrogate(character)) {
                assertTrue(index > 0)
                assertTrue(Character.isHighSurrogate(excerpt.text[index - 1]))
            }
        }
    }

    @Test
    fun `关键词超过默认摘要长度时仍完整展示`() {
        val keyword = "长".repeat(SearchExcerptMaxCodePoints + 8)
        val excerpt = buildSearchExcerpt(
            content = "前文" + keyword + "后文",
            keyword = keyword,
        )

        assertTrue(excerpt.text.startsWith("…"))
        assertTrue(excerpt.text.endsWith("…"))
        assertEquals(keyword, excerpt.highlightedTexts().single())
    }

    private fun SearchExcerpt.highlightedTexts(): List<String> =
        highlightRanges.map { range -> text.substring(range.start, range.endExclusive) }
}
