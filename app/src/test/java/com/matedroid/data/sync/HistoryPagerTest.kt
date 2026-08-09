package com.matedroid.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPagerTest {
    @Test
    fun `initial sync pages beyond the former fifty thousand record limit`() = runTest {
        val records = (1..50_250).toList()
        val stored = mutableSetOf<Int>()
        val checkpoints = mutableListOf<Int>()

        val result = syncHistoryPages(
            startPage = 1,
            pageSize = 250,
            fetch = { page, size -> PageFetch.Success(records.drop((page - 1) * size).take(size)) },
            idOf = { it },
            store = { stored.addAll(it) },
            checkpoint = { checkpoints += it },
            progress = { _, _ -> }
        )

        assertTrue(result is HistoryPageResult.Complete)
        assertEquals(50_250, stored.size)
        assertEquals(203, checkpoints.last())
    }

    @Test
    fun `resume starts from persisted page and duplicate entries remain idempotent`() = runTest {
        val stored = mutableSetOf<Int>()
        val result = syncHistoryPages(
            startPage = 2,
            pageSize = 3,
            fetch = { page, _ ->
                when (page) {
                    2 -> PageFetch.Success(listOf(3, 4, 4))
                    else -> PageFetch.Success(listOf(5))
                }
            },
            idOf = { it },
            store = { stored.addAll(it) },
            checkpoint = {},
            progress = { _, _ -> }
        )
        assertTrue(result is HistoryPageResult.Complete)
        assertEquals(setOf(3, 4, 5), stored)
    }

    @Test
    fun `api failure preserves the page to retry`() = runTest {
        val result = syncHistoryPages<Int, Int>(
            startPage = 8,
            pageSize = 250,
            fetch = { _, _ -> PageFetch.Error("offline") },
            idOf = { it },
            store = {},
            checkpoint = {},
            progress = { _, _ -> }
        )
        assertEquals(HistoryPageResult.Failed(8, "offline"), result)
    }

    @Test
    fun `repeated full page fails instead of silently truncating`() = runTest {
        val result = syncHistoryPages(
            startPage = 1,
            pageSize = 2,
            fetch = { _, _ -> PageFetch.Success(listOf(1, 1)) },
            idOf = { it },
            store = {},
            checkpoint = {},
            progress = { _, _ -> }
        )
        assertEquals(HistoryPageResult.Failed(2, "Server repeated a full history page"), result)
    }
}
