package fr.manu.traducteur.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import fr.manu.traducteur.data.TextScanner
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.manu.traducteur.TraducteurApp
import fr.manu.traducteur.data.Direction
import fr.manu.traducteur.data.Translation
import fr.manu.traducteur.data.TranslationDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModelState { CHECKING, MISSING, DOWNLOADING, READY, ERROR }

data class UiState(
    val input: String = "",
    val output: String = "",
    val direction: Direction = Direction.FR_IT,
    val translating: Boolean = false,
    val scanning: Boolean = false,
    /** Image en attente de rognage (écran de recadrage affiché tant qu'elle n'est pas nulle). */
    val cropImage: Bitmap? = null,
    val listening: Boolean = false,
    val models: ModelState = ModelState.CHECKING,
    val error: String? = null,
)

class TranslatorViewModel(app: Application) : AndroidViewModel(app) {
    private val traducteurApp = app as TraducteurApp
    private val translator = traducteurApp.translator
    private val scanner = traducteurApp.scanner
    private val dao: TranslationDao = traducteurApp.database.dao()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<Translation>> = query
        .flatMapLatest { q: String -> if (q.isBlank()) dao.all() else dao.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ready = runCatching { translator.modelsReady() }.getOrDefault(false)
            _state.update { it.copy(models = if (ready) ModelState.READY else ModelState.MISSING) }
        }
    }

    fun onInput(text: String) = _state.update { it.copy(input = text, error = null) }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    /** Texte en cours de dictée (affiché au fur et à mesure). */
    fun onSpeaking(text: String) = _state.update { it.copy(input = text, listening = true, error = null) }

    fun onListening(on: Boolean) = _state.update { it.copy(listening = on, error = null) }

    /** Fin de la dictée (bouton relâché) : on traduit ce qui a été dit. */
    fun onSpoken(text: String) {
        _state.update { it.copy(input = text, listening = false, output = "") }
        if (text.isNotBlank() && _state.value.models == ModelState.READY) translate()
    }

    /** Photo prise ou choisie : on l'ouvre dans l'écran de rognage. */
    fun openForCrop(uri: Uri) {
        _state.update { it.copy(scanning = true, error = null) }
        viewModelScope.launch {
            runCatching { TextScanner.load(getApplication(), uri) }
                .onSuccess { bmp -> _state.update { it.copy(scanning = false, cropImage = bmp) } }
                .onFailure { e -> scanFailed(e) }
        }
    }

    fun rotateCrop() {
        val bmp = _state.value.cropImage ?: return
        viewModelScope.launch {
            val rotated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                TextScanner.rotate(bmp, 90f)
            }
            _state.update { it.copy(cropImage = rotated) }
        }
    }

    fun cancelCrop() = _state.update { it.copy(cropImage = null) }

    /** Zone validée : on lit le texte de la zone rognée (OCR hors ligne) puis on le traduit. */
    fun confirmCrop(left: Float, top: Float, right: Float, bottom: Float) {
        val bmp = _state.value.cropImage ?: return
        _state.update { it.copy(cropImage = null, scanning = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val part = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    TextScanner.crop(bmp, left, top, right, bottom)
                }
                scanner.read(part)
            }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _state.update { it.copy(scanning = false, error = "Aucun texte trouvé dans la zone choisie.") }
                    } else {
                        _state.update { it.copy(scanning = false, input = text, output = "") }
                        if (_state.value.models == ModelState.READY) translate()
                    }
                }
                .onFailure { e -> scanFailed(e) }
        }
    }

    private fun scanFailed(e: Throwable) = _state.update {
        it.copy(scanning = false, error = "Lecture de l'image impossible : ${e.localizedMessage ?: "erreur inconnue"}")
    }

    fun swap() = _state.update {
        it.copy(direction = it.direction.reversed(), input = it.output, output = it.input)
    }

    fun clear() = _state.update { it.copy(input = "", output = "", error = null) }

    fun downloadModels(wifiOnly: Boolean) {
        _state.update { it.copy(models = ModelState.DOWNLOADING, error = null) }
        viewModelScope.launch {
            runCatching { translator.downloadModels(wifiOnly) }
                .onSuccess { _state.update { it.copy(models = ModelState.READY) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            models = ModelState.ERROR,
                            error = "Téléchargement impossible : ${e.localizedMessage ?: "erreur inconnue"}",
                        )
                    }
                }
        }
    }

    fun translate() {
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.translating) return
        _state.update { it.copy(translating = true, error = null) }
        viewModelScope.launch {
            runCatching { translator.translate(text, s.direction) }
                .onSuccess { result ->
                    _state.update { it.copy(output = result, translating = false) }
                    dao.insert(
                        Translation(
                            sourceLang = s.direction.source,
                            targetLang = s.direction.target,
                            sourceText = text,
                            translatedText = result,
                        )
                    )
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            translating = false,
                            error = "Traduction impossible : ${e.localizedMessage ?: "erreur inconnue"}",
                        )
                    }
                }
        }
    }

    fun reuse(t: Translation) = _state.update {
        it.copy(
            input = t.sourceText,
            output = t.translatedText,
            direction = Direction.entries.first { d: Direction -> d.source == t.sourceLang },
            error = null,
        )
    }

    fun toggleFavorite(t: Translation) = viewModelScope.launch { dao.update(t.copy(favorite = !t.favorite)) }
    fun delete(t: Translation) = viewModelScope.launch { dao.delete(t) }
    fun clearHistory() = viewModelScope.launch { dao.clear() }
}
