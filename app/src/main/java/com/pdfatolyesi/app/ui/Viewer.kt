@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pdfatolyesi.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfatolyesi.app.PdfApplication
import com.pdfatolyesi.app.R
import com.pdfatolyesi.app.core.PageInput
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable fun Viewer(app: PdfApplication, id: String, model: ViewerViewModel, back: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var restored by rememberSaveable(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var goTo by rememberSaveable { mutableStateOf(false) }
    var thumbnails by rememberSaveable { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") } // Never saved in SavedState, disk or logs.
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(model::export) }
    LaunchedEffect(state.pages) {
        if (state.pages > 0) {
            if (!restored) { list.scrollToItem(state.initialPage); restored = true }
            snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { model.position(it) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(state.document?.name ?: stringResource(R.string.open_pdf), maxLines = 1) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = { TextButton(onClick = back) { Text(stringResource(R.string.back)) } })
        if (state.pages > 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { goTo = true }) { Text(stringResource(R.string.page_indicator, list.firstVisibleItemIndex + 1, state.pages)) }
                TextButton(onClick = { thumbnails = !thumbnails }) { Text(stringResource(R.string.thumbnails)) }
                TextButton(onClick = { saver.launch(state.document?.name ?: "document.pdf") }, enabled = !state.exporting) { Text(stringResource(R.string.save_copy)) }
                TextButton(onClick = { try { state.document?.let { shareDocument(context, it, app) } } catch (_: Exception) { shareError = true } }) { Text(stringResource(R.string.share)) }
            }
            if (thumbnails) LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.pages) { page ->
                    Column(Modifier.width(82.dp).clickable { scope.launch { list.scrollToItem(page) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PageImage(model, page, 140, Modifier.height(100.dp).fillMaxWidth())
                        Text(stringResource(R.string.page_number, page + 1), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.pages, key = { it }) { page -> ZoomablePage(model, page) }
            }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.password -> Text(stringResource(R.string.password_required))
                else -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(state.error ?: R.string.error_pdf))
                    TextButton(onClick = back) { Text(stringResource(R.string.choose_another)) }
                }
            }
        }
    }
    if (state.password) AlertDialog(onDismissRequest = back, title = { Text(stringResource(R.string.password_required)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.password_help))
            OutlinedTextField(password, { password = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.password)) })
        } }, confirmButton = { TextButton(onClick = { model.open(password); password = "" }) { Text(stringResource(R.string.unlock)) } },
        dismissButton = { TextButton(onClick = back) { Text(stringResource(R.string.cancel)) } })
    if (goTo) {
        var input by remember { mutableStateOf((list.firstVisibleItemIndex + 1).toString()) }
        val target = PageInput.index(input, state.pages)
        AlertDialog(onDismissRequest = { goTo = false }, title = { Text(stringResource(R.string.go_to_page)) },
            text = { OutlinedTextField(input, { input = it }, label = { Text(stringResource(R.string.page_range_hint, state.pages)) }, isError = target == null, singleLine = true) },
            confirmButton = { TextButton(enabled = target != null, onClick = { scope.launch { list.scrollToItem(target!!); goTo = false } }) { Text(stringResource(R.string.go)) } },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (state.exporting) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.saving)) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = model::cancelExport) { Text(stringResource(R.string.cancel)) } })
    val message = when { shareError -> R.string.error_share; state.exported -> R.string.saved; state.pages > 0 -> state.error; else -> null }
    message?.let { AlertDialog(onDismissRequest = { shareError = false; model.clearMessage() }, text = { Text(stringResource(it)) },
        confirmButton = { TextButton(onClick = { shareError = false; model.clearMessage() }) { Text(stringResource(R.string.ok)) } }) }
}
@Composable private fun ZoomablePage(model: ViewerViewModel, page: Int) {
    var zoom by rememberSaveable(page) { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.page_number, page + 1), style = MaterialTheme.typography.labelMedium)
            Row {
                TextButton(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(1f); offset = Offset.Zero }) { Text(stringResource(R.string.zoom_out)) }
                TextButton(onClick = { zoom = (zoom * 1.5f).coerceAtMost(4f) }) { Text(stringResource(R.string.zoom_in)) }
                TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Text(stringResource(R.string.fit_page)) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 160.dp).background(androidx.compose.ui.graphics.Color.White)) {
            val pixels = with(LocalDensity.current) { maxWidth.toPx().toInt() }
            // Image bounds stay fixed; pan is confined to the enlarged page and clipped.
            PageImage(model, page, pixels.coerceAtMost(2048), Modifier.fillMaxWidth()
                .graphicsLayer { clip = true }
                .pointerInput(page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || zoom > 1f) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                val pan = event.calculatePan()
                                val maxX = size.width * (zoom - 1f) / 2f
                                val maxY = size.height * (zoom - 1f) / 2f
                                offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }, zoom, offset)
        }
    }
}
@Composable private fun PageImage(model: ViewerViewModel, page: Int, width: Int, modifier: Modifier, zoom: Float = 1f, offset: Offset = Offset.Zero) {
    var bitmap by remember(model, page, width) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(model, page, width) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(model, page, width, attempt) {
        failed = false
        try { bitmap = model.render(page, width) } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { image ->
            Image(image.asImageBitmap(), stringResource(R.string.page_number, page + 1),
                Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit)
        } ?: if (failed) TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry_page)) }
        else CircularProgressIndicator(Modifier.padding(24.dp))
    }
}
