package com.matedroid.data.sync

/**
 * Pages a list endpoint without relying on unsupported cursors or totals.
 * A repeated full page is reported as a failure rather than being silently
 * accepted as a complete history.
 */
internal sealed interface HistoryPageResult {
    data class Complete(val nextPage: Int) : HistoryPageResult
    data class Failed(val nextPage: Int, val reason: String) : HistoryPageResult
}

internal suspend fun <T, Id> syncHistoryPages(
    startPage: Int,
    pageSize: Int,
    fetch: suspend (page: Int, show: Int) -> PageFetch<T>,
    idOf: (T) -> Id,
    store: suspend (List<T>) -> Unit,
    checkpoint: suspend (nextPage: Int) -> Unit,
    progress: (page: Int, received: Int) -> Unit
): HistoryPageResult {
    var page = startPage.coerceAtLeast(1)
    val seenIds = mutableSetOf<Id>()
    while (true) {
        when (val fetched = fetch(page, pageSize)) {
            is PageFetch.Error -> return HistoryPageResult.Failed(page, fetched.message)
            is PageFetch.Success -> {
                val records = fetched.records
                val unique = records.filter { seenIds.add(idOf(it)) }
                if (records.size == pageSize && unique.isEmpty()) {
                    return HistoryPageResult.Failed(page, "Server repeated a full history page")
                }
                store(unique)
                progress(page, records.size)
                val nextPage = page + 1
                checkpoint(nextPage)
                if (records.size < pageSize) return HistoryPageResult.Complete(nextPage)
                page = nextPage
            }
        }
    }
}

internal sealed interface PageFetch<out T> {
    data class Success<T>(val records: List<T>) : PageFetch<T>
    data class Error(val message: String) : PageFetch<Nothing>
}
