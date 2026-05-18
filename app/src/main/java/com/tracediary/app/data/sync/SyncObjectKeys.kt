package com.tracediary.app.data.sync

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 远端对象 key 命名约定。同步对象按对象拆分：
 *   diary/yyyy-MM-dd.json   —— 一条日记一个文件
 *   annual/yyyy.json        —— 一年总结一个文件
 *
 * 全部为纯 Kotlin / java.time，不依赖任何 Android API，便于单元测试。
 */
object SyncObjectKeys {

    const val DIARY_PREFIX = "diary/"
    const val ANNUAL_PREFIX = "annual/"
    private const val SUFFIX = ".json"

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val diaryRegex = Regex("""^diary/(\d{4}-\d{2}-\d{2})\.json$""")
    private val annualRegex = Regex("""^annual/(\d{4})\.json$""")

    fun diaryObjectKey(date: LocalDate): String =
        "$DIARY_PREFIX${date.format(dateFormatter)}$SUFFIX"

    fun annualObjectKey(year: Int): String =
        "$ANNUAL_PREFIX$year$SUFFIX"

    /** 反向解析远端 key；解析失败返回 null。也容忍 list() 给出的带前缀绝对 key。 */
    fun parseDiaryKey(key: String): LocalDate? {
        val tail = stripBucketPrefix(key)
        val match = diaryRegex.matchEntire(tail) ?: return null
        return runCatching { LocalDate.parse(match.groupValues[1], dateFormatter) }.getOrNull()
    }

    fun parseAnnualKey(key: String): Int? {
        val tail = stripBucketPrefix(key)
        val match = annualRegex.matchEntire(tail) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * RemoteStorage.list() 通常会回传含 pathPrefix 的绝对 key，例如
     * `TraceDiary/diary/2025-01-02.json`。这里只保留以 `diary/` 或 `annual/`
     * 起始的尾段，用于命中本项目的命名约定。
     */
    private fun stripBucketPrefix(key: String): String {
        val idx = key.indexOf("$DIARY_PREFIX")
            .let { if (it >= 0) it else key.indexOf(ANNUAL_PREFIX) }
        return if (idx >= 0) key.substring(idx) else key
    }
}

fun diaryObjectKey(date: LocalDate): String = SyncObjectKeys.diaryObjectKey(date)
fun annualObjectKey(year: Int): String = SyncObjectKeys.annualObjectKey(year)
fun parseDiaryKey(key: String): LocalDate? = SyncObjectKeys.parseDiaryKey(key)
fun parseAnnualKey(key: String): Int? = SyncObjectKeys.parseAnnualKey(key)
