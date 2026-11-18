package com.example.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.TransactionEntity
import com.example.ui.theme.LedgerTheme
import com.example.utils.LedgerDateFormatter
import com.example.utils.LocalAppStrings
import com.example.utils.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@Composable
fun FullScreenImageViewer(
    items: List<CustomerGalleryItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDeleteImage: (TransactionEntity, String) -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(items.size) {
        if (items.isEmpty()) {
            onDismiss()
        }
    }
    if (items.isEmpty()) return

    val safeInitial = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { items.size })

    var showOverlays by remember { mutableStateOf(true) }
    var currentPageScale by remember { mutableFloatStateOf(1f) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    fun saveCurrentToGallery() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaStoreSaver.saveImageToGallery(context, currentItem.imagePath)
            }
            isSaving = false
            if (result.isSuccess) {
                snackbarHostState.showSnackbar("Saved to gallery")
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Failed to save image"
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveCurrentToGallery()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Storage permission denied")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false
        )
    ) {
        BackHandler(enabled = true) {
            if (showDeleteConfirm) {
                showDeleteConfirm = false
            } else {
                onDismiss()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentPageScale <= 1.02f,
                key = { idx -> items.getOrNull(idx)?.id ?: idx },
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val item = items[pageIndex]
                val isCurrentPage = pageIndex == pagerState.currentPage

                ZoomableImagePage(
                    imagePath = item.imagePath,
                    onScaleChanged = { newScale ->
                        if (isCurrentPage) {
                            currentPageScale = newScale
                        }
                    },
                    onToggleOverlays = { showOverlays = !showOverlays },
                    onSwipeDownDismiss = onDismiss
                )
            }

            // Reset tracked scale when switching pages
            LaunchedEffect(pagerState.currentPage) {
                currentPageScale = 1f
            }

            // Top Action Overlay Bar
            AnimatedVisibility(
                visible = showOverlays,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Viewer",
                                tint = Color.White
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            val tx = currentItem.transaction
                            val isPaid = tx.transactionType == "I_PAID"
                            val amtText = if (tx.amount % 1.0 == 0.0) {
                                String.format(Locale.US, "₹%,.0f", tx.amount)
                            } else {
                                String.format(Locale.US, "₹%,.2f", tx.amount)
                            }
                            Text(
                                text = "$amtText • ${if (isPaid) "Paid" else "Received"}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = LedgerDateFormatter.formatDateTimeWithYear(tx.dateTimestamp),
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        saveCurrentToGallery()
                                    } else {
                                        writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                } else {
                                    saveCurrentToGallery()
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("viewer_save_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to gallery",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val uri = withContext(Dispatchers.IO) {
                                        MediaStoreSaver.createShareableImageUri(context, currentItem.imagePath)
                                    }
                                    if (uri != null) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Receipt Photo"))
                                    } else {
                                        Toast.makeText(context, "Could not prepare image for sharing", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("viewer_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Photo",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("viewer_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Image",
                                tint = Color(0xFFFF8A80)
                            )
                        }
                    }
                }
            }

            // Bottom Caption + Page Counter Overlay Bar
            AnimatedVisibility(
                visible = showOverlays,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val commentText = currentItem.transaction.comment.trim()
                        Text(
                            text = if (commentText.isNotEmpty()) commentText else currentItem.transaction.paymentMode,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${items.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 16.dp),
            title = { Text("Delete Image?", fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = {
                Text(
                    "Remove this attached photo from the transaction entry? The transaction amount and details will be kept.",
                    color = c.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tx = currentItem.transaction
                        val path = currentItem.imagePath
                        showDeleteConfirm = false
                        onDeleteImage(tx, path)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.redDebit),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(s.remove, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirm = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.borderStrong)
                ) {
                    Text(s.cancel, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun ZoomableImagePage(
    imagePath: String,
    onScaleChanged: (Float) -> Unit,
    onToggleOverlays: () -> Unit,
    onSwipeDownDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 120.dp.toPx() }

    var scale by remember(imagePath) { mutableFloatStateOf(1f) }
    var offset by remember(imagePath) { mutableStateOf(Offset.Zero) }
    var dismissDragY by remember(imagePath) { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) {
        onScaleChanged(scale)
    }

    fun clampOffset(raw: Offset, curScale: Float): Offset {
        if (curScale <= 1f || containerSize.width == 0 || containerSize.height == 0) {
            return Offset.Zero
        }
        val maxX = ((containerSize.width * curScale) - containerSize.width) / 2f
        val maxY = ((containerSize.height * curScale) - containerSize.height) / 2f
        return Offset(
            x = raw.x.coerceIn(-maxX.coerceAtLeast(0f), maxX.coerceAtLeast(0f)),
            y = raw.y.coerceIn(-maxY.coerceAtLeast(0f), maxY.coerceAtLeast(0f))
        )
    }

    val model = remember(imagePath) {
        MediaStoreSaver.resolveImageModel(context, imagePath)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(imagePath) {
                detectTapGestures(
                    onTap = { onToggleOverlays() },
                    onDoubleTap = { tap ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onScaleChanged(1f)
                        } else {
                            val targetScale = 2.5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val shift = Offset(
                                x = (centerX - tap.x) * (targetScale - 1f),
                                y = (centerY - tap.y) * (targetScale - 1f)
                            )
                            scale = targetScale
                            offset = clampOffset(shift, targetScale)
                            onScaleChanged(targetScale)
                        }
                    }
                )
            }
            .pointerInput(imagePath) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var verticalDragTotal = 0f
                    var draggingDownToDismiss = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2 || scale > 1.01f) {
                            draggingDownToDismiss = false
                            dismissDragY = 0f
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            if (newScale <= 1.02f && zoomChange < 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = newScale
                                offset = clampOffset(offset + panChange, newScale)
                            }
                            onScaleChanged(scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressedCount == 1) {
                            val change = event.changes.first()
                            val delta = change.position - change.previousPosition
                            if (!draggingDownToDismiss) {
                                // Only claim vertical downward swipe if clearly vertical and downward
                                if (delta.y > 4f && abs(delta.y) > abs(delta.x) * 1.4f) {
                                    draggingDownToDismiss = true
                                }
                            }
                            if (draggingDownToDismiss) {
                                verticalDragTotal = (verticalDragTotal + delta.y).coerceAtLeast(0f)
                                dismissDragY = verticalDragTotal
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (draggingDownToDismiss && dismissDragY > dismissThresholdPx) {
                        onSwipeDownDismiss()
                    } else {
                        dismissDragY = 0f
                    }
                    if (scale <= 1.03f) {
                        scale = 1f
                        offset = Offset.Zero
                        onScaleChanged(1f)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = "Full screen transaction attachment",
            contentScale = ContentScale.Fit,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.greenPrimary)
                }
            },
            error = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Image unavailable",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Image file unavailable",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = if (scale <= 1.01f) dismissDragY else offset.y
                    alpha = if (dismissDragY > 0f && scale <= 1.01f) {
                        (1f - (dismissDragY / (dismissThresholdPx * 2.5f))).coerceIn(0.45f, 1f)
                    } else {
                        1f
                    }
                }
        )
    }
}
