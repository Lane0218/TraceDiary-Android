package com.tracediary.app.ui.review

internal const val SearchExcerptMaxCodePoints: Int = 48
internal const val SearchExcerptLeadingContextCodePoints: Int = 18

internal data class SearchHighlightRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must be non-negative" }
        require(endExclusive > start) { "endExclusive must be greater than start" }
    }
}

internal data class SearchExcerpt(
    val text: String,
    val highlightRanges: List<SearchHighlightRange>,
)

/**
 * 生成围绕首个关键词命中的搜索结果摘要。
 *
 * 摘要窗口以 Unicode 代码点计算，返回的高亮范围则使用 Compose 文本 API 所需的
 * UTF-16 索引。展示前会折叠空白，因此范围始终对应最终显示文本。
 */
internal fun buildSearchExcerpt(
    content: String,
    keyword: String,
    maxCodePoints: Int = SearchExcerptMaxCodePoints,
    leadingContextCodePoints: Int = SearchExcerptLeadingContextCodePoints,
): SearchExcerpt {
    require(maxCodePoints > 0) { "maxCodePoints must be positive" }
    require(leadingContextCodePoints >= 0) { "leadingContextCodePoints must be non-negative" }

    val displayContent = content.collapseWhitespace()
    if (displayContent.isEmpty()) return SearchExcerpt(text = "", highlightRanges = emptyList())

    val displayKeyword = keyword.collapseWhitespace()
    if (displayKeyword.isEmpty()) {
        return displayContent.startingExcerpt(maxCodePoints)
    }

    val firstMatchStart = displayContent.indexOf(displayKeyword, ignoreCase = true)
    if (firstMatchStart < 0) {
        return displayContent.startingExcerpt(maxCodePoints)
    }

    val boundaries = displayContent.codePointBoundaries()
    val matchStartCodePoint = boundaries.boundaryPosition(firstMatchStart)
    val matchEndCodePoint = boundaries.boundaryPosition(firstMatchStart + displayKeyword.length)
    val matchLengthCodePoints = matchEndCodePoint - matchStartCodePoint
    val targetLength = maxOf(maxCodePoints, matchLengthCodePoints)
    val availableContext = targetLength - matchLengthCodePoints
    val preferredLeadingContext = minOf(leadingContextCodePoints, availableContext)

    var startCodePoint = maxOf(0, matchStartCodePoint - preferredLeadingContext)
    var endCodePoint = minOf(
        boundaries.lastIndex,
        matchEndCodePoint + (availableContext - preferredLeadingContext),
    )

    val missingAfterInitialWindow = targetLength - (endCodePoint - startCodePoint)
    if (missingAfterInitialWindow > 0) {
        startCodePoint = maxOf(0, startCodePoint - missingAfterInitialWindow)
    }

    val remainingMissing = targetLength - (endCodePoint - startCodePoint)
    if (remainingMissing > 0) {
        endCodePoint = minOf(boundaries.lastIndex, endCodePoint + remainingMissing)
    }

    val omittedStart = startCodePoint > 0
    val omittedEnd = endCodePoint < boundaries.lastIndex
    val window = displayContent.substring(
        startIndex = boundaries[startCodePoint],
        endIndex = boundaries[endCodePoint],
    )
    val prefix = if (omittedStart) "…" else ""
    val suffix = if (omittedEnd) "…" else ""
    val text = prefix + window + suffix
    val ranges = window.findMatches(displayKeyword, offset = prefix.length)

    return SearchExcerpt(text = text, highlightRanges = ranges)
}

private val CollapsibleWhitespace = Regex("\\s+")

private fun String.collapseWhitespace(): String =
    trim().replace(CollapsibleWhitespace, " ")

private fun String.startingExcerpt(maxCodePoints: Int): SearchExcerpt {
    val boundaries = codePointBoundaries()
    val endCodePoint = minOf(boundaries.lastIndex, maxCodePoints)
    val omittedEnd = endCodePoint < boundaries.lastIndex
    return SearchExcerpt(
        text = substring(0, boundaries[endCodePoint]) + if (omittedEnd) "…" else "",
        highlightRanges = emptyList(),
    )
}

private fun String.findMatches(keyword: String, offset: Int): List<SearchHighlightRange> {
    val ranges = mutableListOf<SearchHighlightRange>()
    var searchStart = 0
    while (searchStart <= length - keyword.length) {
        val matchStart = indexOf(keyword, startIndex = searchStart, ignoreCase = true)
        if (matchStart < 0) break
        ranges += SearchHighlightRange(
            start = offset + matchStart,
            endExclusive = offset + matchStart + keyword.length,
        )
        searchStart = matchStart + keyword.length
    }
    return ranges
}

private fun String.codePointBoundaries(): IntArray {
    val boundaries = IntArray(codePointCount(0, length) + 1)
    var utf16Index = 0
    var boundaryIndex = 0
    boundaries[boundaryIndex++] = utf16Index
    while (utf16Index < length) {
        utf16Index += Character.charCount(codePointAt(utf16Index))
        boundaries[boundaryIndex++] = utf16Index
    }
    return boundaries
}

private fun IntArray.boundaryPosition(utf16Index: Int): Int {
    val position = binarySearch(utf16Index)
    check(position >= 0) { "Index $utf16Index is not a Unicode code point boundary" }
    return position
}
