package com.example.ui.gallery

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class CustomerGalleryItem(
    val id: String,
    val transaction: TransactionEntity,
    val imagePath: String,
    val indexInTransaction: Int
)

fun flattenCustomerGalleryItems(transactions: List<TransactionEntity>): List<CustomerGalleryItem> {
    if (transactions.isEmpty()) return emptyList()
    val sortedTxs = transactions.sortedWith(
        compareByDescending<TransactionEntity> { it.dateTimestamp }
            .thenByDescending { it.id }
    )
    val result = ArrayList<CustomerGalleryItem>()
    sortedTxs.forEach { tx ->
        val raw = tx.attachmentPath
        if (!raw.isNullOrBlank()) {
            val parts = raw.split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            parts.forEachIndexed { idx, path ->
                result.add(
                    CustomerGalleryItem(
                        id = "${tx.id}_${idx}_${path.hashCode()}",
                        transaction = tx,
                        imagePath = path,
                        indexInTransaction = idx
                    )
                )
            }
        }
    }
    return result
}

class CustomerGalleryPagingSource(
    private val items: List<CustomerGalleryItem>
) : PagingSource<Int, CustomerGalleryItem>() {

    override fun getRefreshKey(state: PagingState<Int, CustomerGalleryItem>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CustomerGalleryItem> {
        val page = params.key ?: 0
        val pageSize = params.loadSize.coerceAtLeast(1)
        val fromIndex = page * pageSize
        if (fromIndex >= items.size) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = if (page > 0) page - 1 else null,
                nextKey = null
            )
        }
        val toIndex = minOf(fromIndex + pageSize, items.size)
        val slice = items.subList(fromIndex, toIndex)
        return LoadResult.Page(
            data = slice,
            prevKey = if (page > 0) page - 1 else null,
            nextKey = if (toIndex < items.size) page + 1 else null
        )
    }
}

fun createCustomerGalleryPagerFlow(items: List<CustomerGalleryItem>): Flow<PagingData<CustomerGalleryItem>> {
    return Pager(
        config = PagingConfig(
            pageSize = 24,
            initialLoadSize = 36,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { CustomerGalleryPagingSource(items) }
    ).flow
}
