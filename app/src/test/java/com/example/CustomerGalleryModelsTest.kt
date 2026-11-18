package com.example

import androidx.paging.PagingSource
import com.example.data.TransactionEntity
import com.example.ui.gallery.CustomerGalleryPagingSource
import com.example.ui.gallery.flattenCustomerGalleryItems
import com.example.utils.LedgerDateFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class CustomerGalleryModelsTest {

    @Test
    fun flattenCustomerGalleryItems_ordersNewestFirstWithIdTiebreakerAndSplitsPipes() {
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                customerId = 10L,
                transactionType = "I_PAID",
                amount = 100.0,
                dateTimestamp = 1_000L,
                attachmentPath = "/tmp/old.jpg"
            ),
            TransactionEntity(
                id = 2L,
                customerId = 10L,
                transactionType = "I_RECEIVED",
                amount = 200.0,
                dateTimestamp = 2_000L,
                attachmentPath = "/tmp/mid_a.jpg|/tmp/mid_b.jpg"
            ),
            TransactionEntity(
                id = 5L,
                customerId = 10L,
                transactionType = "I_PAID",
                amount = 300.0,
                dateTimestamp = 2_000L,
                attachmentPath = "/tmp/tiebreak_newer_id.jpg"
            ),
            TransactionEntity(
                id = 3L,
                customerId = 10L,
                transactionType = "I_PAID",
                amount = 50.0,
                dateTimestamp = 3_000L,
                attachmentPath = null
            )
        )

        val flattened = flattenCustomerGalleryItems(txs)

        assertEquals(
            listOf("/tmp/tiebreak_newer_id.jpg", "/tmp/mid_a.jpg", "/tmp/mid_b.jpg", "/tmp/old.jpg"),
            flattened.map { it.imagePath }
        )
        assertEquals(0, flattened[1].indexInTransaction)
        assertEquals(1, flattened[2].indexInTransaction)
    }

    @Test
    fun customerGalleryPagingSource_slicesPagesAccurately() = runBlocking {
        val sampleItems = (1..30).map { i ->
            com.example.ui.gallery.CustomerGalleryItem(
                id = "item_$i",
                transaction = TransactionEntity(
                    id = i.toLong(),
                    customerId = 1L,
                    transactionType = "I_PAID",
                    amount = i * 10.0,
                    dateTimestamp = 100_000L - i
                ),
                imagePath = "/tmp/img_$i.jpg",
                indexInTransaction = 0
            )
        }
        val source = CustomerGalleryPagingSource(sampleItems)

        val page0 = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        assertEquals(20, page0.data.size)
        assertNull(page0.prevKey)
        assertEquals(1, page0.nextKey)

        val page1 = source.load(
            PagingSource.LoadParams.Append(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        assertEquals(10, page1.data.size)
        assertEquals(0, page1.prevKey)
        assertNull(page1.nextKey)
    }

    @Test
    fun ledgerDateFormatter_alwaysIncludesYear() {
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 16)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val formattedDate = LedgerDateFormatter.formatDateWithYear(cal.timeInMillis, Locale.US)
        val formattedDateTime = LedgerDateFormatter.formatDateTimeWithYear(cal.timeInMillis, Locale.US)

        assertEquals("16 Nov 2026", formattedDate)
        assertTrue(formattedDateTime.startsWith("16 Nov 2026"))
        assertEquals("", LedgerDateFormatter.formatDateWithYear(0L, Locale.US))
    }
}
