package fr.manu.traducteur.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.manu.traducteur.data.Translation
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: TranslatorViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Traducteur FR ↔ IT" else "Historique") },
                actions = {
                    if (tab == 1) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Tout effacer")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Translate, null) }, label = { Text("Traduire") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.History, null) }, label = { Text("Historique") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) TranslateTab(vm) else HistoryTab(vm, onReuse = { vm.reuse(it); tab = 0 })
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Toutes les traductions enregistrées seront supprimées.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun TranslateTab(vm: TranslatorViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Lecture à voix haute
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val speak = { text: String, lang: String -> speaker.speak(text, lang)?.let(vm::showError); Unit }

    // Saisie vocale : écran de dictée du téléphone, dans la langue de départ
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.let(vm::onSpoken)
        }
    }
    val startVoice = {
        val tag = if (s.direction.source == "fr") "fr-FR" else "it-IT"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, if (tag == "fr-FR") "Parlez en français" else "Parla in italiano")
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            vm.showError("Aucune reconnaissance vocale sur ce téléphone (installe l'application Google).")
        }
    }

    // Photo : appareil photo (image pleine résolution) ou galerie
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) vm.scanImage(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scanImage(uri)
    }
    val takePhoto = {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "photo.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        photoUri = uri
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.showError("Aucune application appareil photo trouvée.")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModelBanner(s.models, onDownload = vm::downloadModels)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.direction.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::swap) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Inverser les langues")
            }
        }

        OutlinedTextField(
            value = s.input,
            onValueChange = vm::onInput,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(if (s.direction.source == "fr") "Texte en français" else "Testo in italiano") },
            trailingIcon = {
                if (s.input.isNotEmpty()) {
                    IconButton(onClick = vm::clear) { Icon(Icons.Filled.Close, contentDescription = "Effacer") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.translate() }),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.Filled.Mic, "Dicter", onClick = startVoice)
            ActionButton(Icons.Filled.CameraAlt, "Photo", onClick = takePhoto)
            ActionButton(Icons.Filled.Image, "Galerie") {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ActionButton(Icons.AutoMirrored.Filled.VolumeUp, "Écouter", enabled = s.input.isNotBlank()) {
                speak(s.input, s.direction.source)
            }
        }

        if (s.scanning) {
            Text("Lecture du texte de l'image…", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Button(
            onClick = vm::translate,
            enabled = s.models == ModelState.READY && s.input.isNotBlank() && !s.translating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (s.translating) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Traduire")
        }

        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (s.output.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SelectionContainer { Text(s.output, style = MaterialTheme.typography.bodyLarge) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { speak(s.output, s.direction.target) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Écouter la traduction")
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(s.output)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copier")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.FilledTonalIconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModelBanner(state: ModelState, onDownload: (Boolean) -> Unit) {
    when (state) {
        ModelState.READY -> Text(
            "✓ Hors ligne : les langues sont installées sur le téléphone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        ModelState.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth())
        ModelState.DOWNLOADING -> Column {
            Text("Téléchargement des langues en cours (environ 60 Mo)…")
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        ModelState.MISSING, ModelState.ERROR -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Première utilisation", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pour traduire sans connexion, il faut d'abord télécharger le français et l'italien " +
                        "(environ 60 Mo, une seule fois)."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDownload(true) }) { Text("En Wi-Fi") }
                    OutlinedButton(onClick = { onDownload(false) }) { Text("Avec les données") }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(vm: TranslatorViewModel, onReuse: (Translation) -> Unit) {
    val items by vm.history.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Rechercher") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
        )
        if (items.isEmpty()) {
            Text(
                if (query.isBlank()) "Aucune traduction pour l'instant." else "Aucun résultat.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { onReuse(t) }) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                        Column(Modifier.weight(1f).padding(top = 4.dp)) {
                            Text(
                                "${t.sourceLang.uppercase()} → ${t.targetLang.uppercase()} · ${fmt.format(Date(t.createdAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(t.sourceText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                t.translatedText, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column {
                            IconButton(onClick = { vm.toggleFavorite(t) }) {
                                Icon(
                                    if (t.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "Favori",
                                )
                            }
                            IconButton(onClick = { vm.delete(t) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                            }
                        }
                    }
                }
            }
        }
    }
}
