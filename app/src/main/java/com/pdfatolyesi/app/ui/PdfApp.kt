@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pdfatolyesi.app.ui

import android.content.ClipData
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import com.pdfatolyesi.app.PdfApplication
import com.pdfatolyesi.app.R
import com.pdfatolyesi.app.data.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.pdfatolyesi.app.workshop.*

@Composable fun PdfApp(app: PdfApplication, model: LibraryViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val docs by model.documents.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::import) }
    val open = { picker.launch(arrayOf("application/pdf")) }
    LaunchedEffect(state.openId) { state.openId?.let { nav.navigate("viewer/$it"); model.clearEvent() } }
    Scaffold(bottomBar = {
        if (route in listOf("home","documents","tools","settings")) NavigationBar {
            listOf("home" to R.string.nav_home, "documents" to R.string.nav_documents,
                "tools" to R.string.nav_tools, "settings" to R.string.nav_settings).forEachIndexed { index, (path, label) ->
                NavigationBarItem(selected = route == path, onClick = {
                    nav.navigate(path) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                }, icon = { Text(listOf("▤", "▥", "+", "◐")[index]) }, label = { Text(stringResource(label)) })
            }
        }
    }) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { Catalog(docs, true, open, { nav.navigate("viewer/$it") }, model) }
            composable("documents") { Catalog(docs, false, open, { nav.navigate("viewer/$it") }, model) }
            composable("tools") { com.pdfatolyesi.app.workshop.Catalog { nav.navigate(if(it in listOf("photos","camera"))"photos"else "work/$it") } }
            composable("photos") {
                val work: Model = viewModel(factory=viewModelFactory { initializer { Model(app) } })
                Photos(work,{nav.popBackStack()},{nav.navigate("viewer/$it")})
            }
            composable("work/{kind}") { entry ->
                val work: Model = viewModel(factory=viewModelFactory { initializer { Model(app) } })
                ToolScreen(requireNotNull(entry.arguments?.getString("kind")),work,docs,{nav.popBackStack()},{nav.navigate("viewer/$it")},{nav.navigate("edit/$it")},open)
            }
            composable("edit/{id}") { entry ->
                val editor: EditModel = viewModel(factory=viewModelFactory { initializer { EditModel(app,requireNotNull(entry.arguments?.getString("id"))) } })
                Editor(editor,{nav.popBackStack()},{nav.navigate("viewer/$it")})
            }
            composable("settings") { Settings(theme, model::theme) }
            composable("viewer/{id}") { backStack ->
                val id = requireNotNull(backStack.arguments?.getString("id"))
                val viewer: ViewerViewModel = viewModel(factory = viewModelFactory { initializer { ViewerViewModel(app, id) } })
                Viewer(app, id, viewer, { nav.popBackStack() })
            }
        }
    }
    if (state.busy) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.importing)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.bytes_copied, Formatter.formatFileSize(LocalContext.current, state.bytes)))
        } }, confirmButton = { TextButton(onClick = model::cancelImport) { Text(stringResource(R.string.cancel)) } })
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearEvent,
        title = { Text(stringResource(R.string.operation_failed)) }, text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = model::clearEvent) { Text(stringResource(R.string.ok)) } }) }
}

@Composable private fun Catalog(documents: List<Document>, home: Boolean, open: () -> Unit,
    view: (String) -> Unit, model: LibraryViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Document?>(null) }
    var rename by remember { mutableStateOf<Document?>(null) }
    var delete by remember { mutableStateOf<Document?>(null) }
    val context = LocalContext.current
    var actionError by remember { mutableStateOf(false) }
    val locale = Locale.forLanguageTag("tr")
    val filtered = documents.filter { (!favorites || it.favorite) && it.name.lowercase(locale).contains(query.lowercase(locale)) }
        .let { when (sort) { 1 -> it.sortedBy { d -> d.name.lowercase(locale) }; 2 -> it.sortedByDescending(Document::bytes); else -> it } }
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 1000.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.brand_eyebrow), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
        item { Text(stringResource(if (home) R.string.home_title else R.string.nav_documents), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        if (home) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.hero_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.hero_body))
                    Button(onClick = open) { Text(stringResource(R.string.open_pdf)) }
                }
            }
        } else item { Button(onClick = open) { Text(stringResource(R.string.add_pdf)) } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.search_documents)) }, shape = RoundedCornerShape(18.dp)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(favorites, { favorites = !favorites }, label = { Text(stringResource(R.string.favorites)) })
            listOf(R.string.sort_recent, R.string.sort_name, R.string.sort_size).forEachIndexed { i, label ->
                FilterChip(sort == i, { sort = i }, label = { Text(stringResource(label)) })
            }
        } }
        item { Text(stringResource(if (home) R.string.recent_documents else R.string.document_count, filtered.size), style = MaterialTheme.typography.titleMedium) }
        if (filtered.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (documents.isEmpty()) R.string.empty_title else R.string.no_results), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (documents.isEmpty()) R.string.empty_body else R.string.change_filter))
            } }
        }
        items(if (home) filtered.take(8) else filtered, key = Document::id) { doc ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { view(doc.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(stringResource(R.string.pdf_badge), Modifier.padding(horizontal = 12.dp, vertical = 16.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(doc.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(Formatter.formatFileSize(context, doc.bytes) + " · " +
                            if (doc.pages > 0) stringResource(R.string.page_count, doc.pages) else stringResource(R.string.protected_document),
                            style = MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.openedAt)), style = MaterialTheme.typography.bodySmall)
                        if (doc.favorite) Text(stringResource(R.string.favorite_mark), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { selected = doc }) { Text(stringResource(R.string.more)) }
                }
            }
        }
        item { Text(stringResource(R.string.snapshot_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    selected?.let { doc -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(doc.name) }, text = {
        Column {
            TextButton(onClick = { model.favorite(doc.id); selected = null }) { Text(stringResource(if (doc.favorite) R.string.unfavorite else R.string.favorite)) }
            TextButton(onClick = { rename = doc; selected = null }) { Text(stringResource(R.string.rename)) }
            TextButton(onClick = {
                try { shareDocument(context, doc, (context.applicationContext as PdfApplication)) } catch (_: Exception) { actionError = true }
                selected = null
            }) { Text(stringResource(R.string.share)) }
            TextButton(onClick = { delete = doc; selected = null }) { Text(stringResource(R.string.remove)) }
        }
    }, confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } }) }
    rename?.let { doc ->
        var name by remember(doc.id) { mutableStateOf(doc.name) }
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.file_name)) }) },
            confirmButton = { TextButton(onClick = { model.rename(doc.id, name); rename = null }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text(stringResource(R.string.cancel)) } })
    }
    delete?.let { doc -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(stringResource(R.string.remove)) },
        text = { Text(stringResource(R.string.remove_notice)) },
        confirmButton = { TextButton(onClick = { model.remove(doc.id); delete = null }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (actionError) AlertDialog(onDismissRequest = { actionError = false }, text = { Text(stringResource(R.string.error_share)) },
        confirmButton = { TextButton(onClick = { actionError = false }) { Text(stringResource(R.string.ok)) } })
}

fun shareDocument(context: android.content.Context, doc: Document, app: PdfApplication) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", app.documents.file(doc.id), doc.name)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, doc.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
@Composable private fun Settings(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().clickable { onTheme(mode) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = theme == mode, onClick = { onTheme(mode) })
                Text(stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.privacy_body))
        Text(stringResource(R.string.version), style = MaterialTheme.typography.labelMedium)
    }
}
