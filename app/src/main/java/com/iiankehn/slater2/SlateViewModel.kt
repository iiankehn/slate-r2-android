package com.iiankehn.slater2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iiankehn.slater2.data.DocumentRepository
import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.DocumentTitlePolicy
import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.io.ImportedDocument
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlateUiState(
    val documents: List<Document> = emptyList(),
    val loading: Boolean = true,
    val savingDocumentIds: Set<String> = emptySet(),
    val history: Map<String, List<Document>> = emptyMap(),
)

class SlateViewModel(
    private val repository: DocumentRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SlateUiState())
    val uiState: StateFlow<SlateUiState> = mutableUiState.asStateFlow()

    private val pendingSaves = mutableMapOf<String, Job>()
    private val saveGenerations = mutableMapOf<String, Int>()

    init {
        viewModelScope.launch {
            repository.initialize(starterDocuments)
            repository.documents.collect { storedDocuments ->
                val localDocuments = mutableUiState.value.documents.associateBy(Document::id)
                val merged = storedDocuments.map { stored ->
                    if (pendingSaves.containsKey(stored.id)) localDocuments[stored.id] ?: stored else stored
                } + localDocuments.values.filter { local ->
                    pendingSaves.containsKey(local.id) && storedDocuments.none { it.id == local.id }
                }
                mutableUiState.value = SlateUiState(
                    documents = merged.sortedForLibrary(),
                    loading = false,
                    savingDocumentIds = mutableUiState.value.savingDocumentIds,
                    history = mutableUiState.value.history,
                )
            }
        }
    }

    fun createDocument(): Document {
        val document = Document(
            id = UUID.randomUUID().toString(),
            title = "",
            body = RichTextDocument(),
            updatedLabel = "Just now",
        )
        updateLocal(document)
        scheduleSave(document, delayMillis = 0)
        return document
    }

    fun importDocument(imported: ImportedDocument): Document {
        val document = Document(
            id = UUID.randomUUID().toString(),
            title = imported.title,
            body = imported.body,
            updatedLabel = "Imported now",
        )
        updateLocal(document)
        scheduleSave(document, delayMillis = 0)
        return document
    }

    fun updateDocument(document: Document) {
        val changed = document.copy(
            updatedLabel = "Just now",
            updatedAtEpochMillis = nextTimestamp(document),
        )
        updateLocal(changed)
        viewModelScope.launch { repository.checkpoint(changed) }
        scheduleSave(changed, journalFirst = false)
    }

    fun duplicateDocument(source: Document): Document {
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${DocumentTitlePolicy.displayTitle(source.title, source.body.text)} copy",
            updatedLabel = "Just now",
            isPinned = false,
            isArchived = false,
            isDeleted = false,
            updatedAtEpochMillis = nextTimestamp(source),
        )
        updateLocal(duplicate)
        scheduleSave(duplicate, delayMillis = 0)
        return duplicate
    }

    fun togglePin(source: Document): Document {
        val changed = source.copy(
            isPinned = !source.isPinned,
            updatedLabel = "Just now",
            updatedAtEpochMillis = nextTimestamp(source),
        )
        updateLocal(changed)
        scheduleSave(changed, delayMillis = 0)
        return changed
    }

    fun toggleArchive(source: Document): Document {
        val changed = source.copy(
            isArchived = !source.isArchived,
            updatedLabel = "Just now",
            updatedAtEpochMillis = nextTimestamp(source),
        )
        updateLocal(changed)
        scheduleSave(changed, delayMillis = 0)
        return changed
    }

    fun toggleFavorite(source: Document): Document = saveImmediately(
        source.copy(isFavorite = !source.isFavorite),
    )

    fun updateOrganization(source: Document, folder: String, tags: Set<String>): Document = saveImmediately(
        source.copy(folder = folder.trim(), tags = tags.map(String::trim).filter(String::isNotEmpty).toSet()),
    )

    fun moveToTrash(source: Document): Document = saveImmediately(
        source.copy(isDeleted = true, isArchived = false, isPinned = false),
    )

    fun restoreFromTrash(source: Document): Document = saveImmediately(source.copy(isDeleted = false))

    fun loadHistory(documentId: String) {
        viewModelScope.launch {
            val versions = repository.history(documentId)
            mutableUiState.update { it.copy(history = it.history + (documentId to versions)) }
        }
    }

    fun restoreVersion(current: Document, version: Document) {
        updateDocument(
            current.copy(
                title = version.title,
                body = version.body,
                folder = version.folder,
                tags = version.tags,
            ),
        )
    }

    fun permanentlyDelete(source: Document) {
        pendingSaves.remove(source.id)?.cancel()
        mutableUiState.update { state -> state.copy(documents = state.documents.filterNot { it.id == source.id }) }
        viewModelScope.launch { repository.permanentlyDelete(source) }
    }

    fun deleteDocument(source: Document) {
        pendingSaves.remove(source.id)?.cancel()
        saveGenerations.remove(source.id)
        mutableUiState.update { state ->
            state.copy(
                documents = state.documents.filterNot { it.id == source.id },
                savingDocumentIds = state.savingDocumentIds - source.id,
            )
        }
        viewModelScope.launch { repository.delete(source) }
    }

    private fun saveImmediately(source: Document): Document {
        val changed = source.copy(updatedLabel = "Just now", updatedAtEpochMillis = nextTimestamp(source))
        updateLocal(changed)
        scheduleSave(changed, delayMillis = 0)
        return changed
    }

    private fun updateLocal(document: Document) {
        mutableUiState.update { state ->
            val exists = state.documents.any { it.id == document.id }
            val documents = if (exists) {
                state.documents.map { if (it.id == document.id) document else it }
            } else {
                listOf(document) + state.documents
            }
            state.copy(documents = documents.sortedForLibrary(), loading = false)
        }
    }

    private fun scheduleSave(
        document: Document,
        delayMillis: Long = AUTOSAVE_DELAY_MILLIS,
        journalFirst: Boolean = true,
    ) {
        pendingSaves.remove(document.id)?.cancel()
        val generation = (saveGenerations[document.id] ?: 0) + 1
        saveGenerations[document.id] = generation
        mutableUiState.update { state ->
            state.copy(savingDocumentIds = state.savingDocumentIds + document.id)
        }
        pendingSaves[document.id] = viewModelScope.launch {
            try {
                if (delayMillis > 0) delay(delayMillis)
                repository.save(document, journalFirst = journalFirst)
            } finally {
                if (saveGenerations[document.id] == generation) {
                    pendingSaves.remove(document.id)
                    saveGenerations.remove(document.id)
                    mutableUiState.update { state ->
                        state.copy(savingDocumentIds = state.savingDocumentIds - document.id)
                    }
                }
            }
        }
    }

    class Factory(
        private val repository: DocumentRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SlateViewModel::class.java))
            return SlateViewModel(repository) as T
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MILLIS = 450L

        val starterDocuments = emptyList<Document>()
    }
}

private fun List<Document>.sortedForLibrary(): List<Document> = sortedWith(
    compareBy<Document> { it.isDeleted }
        .thenBy { it.isArchived }
        .thenByDescending { it.isPinned }
        .thenByDescending { it.isFavorite }
        .thenByDescending(Document::updatedAtEpochMillis),
)

private fun nextTimestamp(document: Document): Long = maxOf(
    System.currentTimeMillis(),
    document.updatedAtEpochMillis + 1,
)
