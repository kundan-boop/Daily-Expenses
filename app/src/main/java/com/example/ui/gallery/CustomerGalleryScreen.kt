package com.example.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.theme.*
import com.example.utils.LedgerDateFormatter
import com.example.utils.LocalAppStrings
import com.example.utils.MediaStoreSaver

@Composable
fun CustomerGalleryScreen(
    customer: CustomerEntity,
    transactions: List<TransactionEntity>,
    onBackClick: () -> Unit,
    onDeleteImage: (TransactionEntity, String) -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val galleryItems = remember(transactions) {
        flattenCustomerGalleryItems(transactions)
    }
    val pagedFlow = remember(galleryItems) {
        createCustomerGalleryPagerFlow(galleryItems)
    }
    val pagedItems = pagedFlow.collectAsLazyPagingItems()

    var selectedViewerIndex by remember { mutableIntStateOf(-1) }

    BackHandler(enabled = true) {
        if (selectedViewerIndex >= 0) {
            selectedViewerIndex = -1
        } else {
            onBackClick()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = c.card,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("gallery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s.back,
                            tint = c.textPrimary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${customer.name} ${s.viewGallery}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${galleryItems.size} photo${if (galleryItems.size == 1) "" else "s"} • Newest first",
                            fontSize = 12.sp,
                            color = c.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(c.background)
                .navigationBarsPadding()
        ) {
            val columnCount = when {
                maxWidth < 450.dp -> 2
                maxWidth < 700.dp -> 3
                else -> 4
            }

            if (galleryItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = c.textMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No images yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Attach bill photos or receipts when adding entries for ${customer.name}.",
                            fontSize = 12.sp,
                            color = c.textMuted
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id }
                    ) { index ->
                        val item = pagedItems[index]
                        if (item != null) {
                            GalleryThumbnailTile(
                                item = item,
                                onClick = { selectedViewerIndex = index }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedViewerIndex >= 0 && galleryItems.isNotEmpty()) {
        FullScreenImageViewer(
            items = galleryItems,
            initialIndex = selectedViewerIndex,
            onDismiss = { selectedViewerIndex = -1 },
            onDeleteImage = { tx, imagePath ->
                onDeleteImage(tx, imagePath)
            }
        )
    }
}

@Composable
private fun GalleryThumbnailTile(
    item: CustomerGalleryItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val model = remember(item.imagePath) {
        MediaStoreSaver.resolveImageModel(context, item.imagePath)
    }
    val dateText = remember(item.transaction.dateTimestamp) {
        LedgerDateFormatter.formatDateWithYear(item.transaction.dateTimestamp)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.cardElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .size(360)
                    .crossfade(true)
                    .build(),
                contentDescription = "Receipt on $dateText",
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(c.cardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = c.greenPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(c.cardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Missing photo",
                            tint = c.textMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = dateText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
