package fr.manu.traducteur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.manu.traducteur.TraducteurApp
import fr.manu.traducteur.data.Direction
import fr.manu.traducteur.data.Translation
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
    val models: ModelState = ModelState.CHECKING,
    val error: String? = null,
)

class TranslatorViewModel(app: Application) : AndroidViewModel(app) {
    private val translator = (app as TraducteurApp).translator
    private val dao = app.database.dao()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<Translation>> = query
        .flatMapLatest { q -> if (q.isBlank()) dao.all() else dao.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ready = runCatching { translator.modelsReady() }.getOrDefault(false)
            _state.update { it.copy(models = if (ready) ModelState.READY else ModelState.MISSING) }
        }
    }

    fun onInput(text: String) = _state.update { it.copy(input = text, error = null) }

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
            direction = Direction.entries.first { d -> d.source == t.sourceLang },
            error = null,
        )
    }

    fun toggleFavorite(t: Translation) = viewModelScope.launch { dao.update(t.copy(favorite = !t.favorite)) }
    fun delete(t: Translation) = viewModelScope.launch { dao.delete(t) }
    fun clearHistory() = viewModelScope.launch { dao.clear() }
}
