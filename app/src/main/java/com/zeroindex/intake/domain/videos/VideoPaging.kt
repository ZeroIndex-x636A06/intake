package com.zeroindex.intake.domain.videos

/**
 * Paging through a search with YouTube's `nextPageToken`. Every page is one `search.list` (100 units) and one `videos.list`
 * (1 unit), so paging is capped and stops when the day's estimate runs low. Pages are cached like any search (six hours),
 * each under its own key, so scrolling back or repeating a search costs nothing.
 */
object VideoPaging {
    /** Results per page. */
    const val PAGE_SIZE = 25

    /** A search stops at this many pages (125 results, about 505 units). */
    const val MAX_SEARCH_PAGES = 5

    /** A Recommended row stops at this many pages (75 videos). */
    const val MAX_ROW_PAGES = 3

    /** The next page loads when the person is within this many items of the end. */
    const val LOAD_AHEAD = 5

    /** Paging never takes the day's estimate below this many searches, so a typed search still works. */
    const val RESERVED_SEARCHES = 5

    /** The cache key of page [page] (1 is the plain search key, so older cached searches still match). */
    fun pageKey(baseKey: String, page: Int): String = if (page <= 1) baseKey else "$baseKey|page$page"

    /** The cache key of the token that leads from page [page] to the next one. The row's ids hold the token, blank at the end. */
    fun tokenKey(baseKey: String, page: Int): String = "$baseKey|next$page"

    fun canLoadMore(page: Int, maxPages: Int, hasMore: Boolean, usedUnits: Int): Boolean =
        hasMore && page < maxPages && VideoQuota.searchesLeft(usedUnits) > RESERVED_SEARCHES

    /** True when the last visible item is within [LOAD_AHEAD] of the end of [total] items. */
    fun nearEnd(lastVisibleIndex: Int, total: Int, ahead: Int = LOAD_AHEAD): Boolean =
        total > 0 && lastVisibleIndex >= total - 1 - ahead
}
