package com.webstudio.lumagallery.ui.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webstudio.lumagallery.data.DateGroup
import com.webstudio.lumagallery.data.DeleteResult
import com.webstudio.lumagallery.data.FolderGroup
import com.webstudio.lumagallery.data.PendingWrite
import com.webstudio.lumagallery.data.Photo
import com.webstudio.lumagallery.data.PhotoRepository
import com.webstudio.lumagallery.data.RecycleBinItem
import com.webstudio.lumagallery.data.WriteResult
import com.webstudio.lumagallery.data.ai.AiEditRepository
import com.webstudio.lumagallery.data.ai.AiResult
import com.webstudio.lumagallery.data.edit.PhotoIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "GalleryViewModel"

sealed class ImageGenState {
    object Idle : ImageGenState()
    object Generating : ImageGenState()
    data class Error(val msg: String) : ImageGenState()
}

sealed class CaptionState {
    object None : CaptionState()
    object Loading : CaptionState()
    data class Loaded(val text: String) : CaptionState()
    data class Error(val msg: String) : CaptionState()
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(
        val dateGroups: List<DateGroup>,
        val folderGroups: List<FolderGroup>,
        val allPhotos: List<Photo>,
        val filteredPhotos: List<Photo> = allPhotos,
        val searchQuery: String = "",
        val viewMode: ViewMode = ViewMode.PHOTOS,
        val recycleBinItems: List<RecycleBinItem> = emptyList(),
        val hiddenPhotos: List<Photo> = emptyList()
    ) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

enum class ViewMode { PHOTOS, COLLECTIONS }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val aiRepo = AiEditRepository()
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private val _pendingDeleteIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingDeleteIntent: StateFlow<PendingIntent?> = _pendingDeleteIntent.asStateFlow()
    private val _pendingWriteIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingWriteIntent: StateFlow<PendingIntent?> = _pendingWriteIntent.asStateFlow()
    private var pendingWriteOperation: PendingWrite? = null
    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()
    private var hasLoadedPhotos = false
    private var pendingDeleteIds: List<Long> = emptyList()
    private val _captionState = MutableStateFlow<CaptionState>(CaptionState.None)
    val captionState: StateFlow<CaptionState> = _captionState.asStateFlow()
    private val _imageGenState = MutableStateFlow<ImageGenState>(ImageGenState.Idle)
    val imageGenState: StateFlow<ImageGenState> = _imageGenState.asStateFlow()
    val hasOpenRouterKey: Boolean get() = aiRepo.hasOpenRouterKey
    val hasGeminiKey: Boolean get() = aiRepo.hasGeminiKey

    init {
        viewModelScope.launch {
            try { repository.cleanupExpiredItems() } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up expired items", e)
            }
        }
    }

    fun loadPhotos(forceRefresh: Boolean = false) {
        if (hasLoadedPhotos && !forceRefresh) return

        viewModelScope.launch {
            try {
                _uiState.value = GalleryUiState.Loading
                if (forceRefresh) repository.invalidateCache()

                // Single MediaStore query warms the cache; transformations then run in parallel
                repository.loadAllMedia()

                val dateGroups: List<DateGroup>
                val folderGroups: List<FolderGroup>
                val recycleBinItems: List<RecycleBinItem>
                val hiddenPhotos: List<Photo>

                coroutineScope {
                    val d1 = async(Dispatchers.Default) { repository.getPhotosByDate() }
                    val d2 = async(Dispatchers.Default) { repository.loadFolderGroups() }
                    val d3 = async(Dispatchers.Default) { repository.getRecycleBinItems() }
                    val d4 = async(Dispatchers.Default) { repository.getHiddenPhotos() }
                    dateGroups = d1.await()
                    folderGroups = d2.await()
                    recycleBinItems = d3.await()
                    hiddenPhotos = d4.await()
                }

                val allPhotos = dateGroups.flatMap { it.photos }
                _uiState.value = GalleryUiState.Success(
                    dateGroups = dateGroups,
                    folderGroups = folderGroups,
                    allPhotos = allPhotos,
                    filteredPhotos = allPhotos,
                    recycleBinItems = recycleBinItems,
                    hiddenPhotos = hiddenPhotos
                )
                hasLoadedPhotos = true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading photos", e)
                _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error")
                hasLoadedPhotos = false
            }
        }
    }

    fun refresh() {
        hasLoadedPhotos = false
        loadPhotos(forceRefresh = true)
    }

    fun searchPhotos(query: String) {
        val state = _uiState.value as? GalleryUiState.Success ?: return
        viewModelScope.launch {
            try {
                val filtered = if (query.isEmpty()) state.allPhotos else repository.searchPhotos(query)
                _uiState.value = state.copy(filteredPhotos = filtered, searchQuery = query)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching photos", e)
            }
        }
    }

    fun setViewMode(mode: ViewMode) {
        val state = _uiState.value as? GalleryUiState.Success ?: return
        _uiState.value = state.copy(viewMode = mode)
    }

    fun clearSearch() {
        val state = _uiState.value as? GalleryUiState.Success ?: return
        _uiState.value = state.copy(filteredPhotos = state.allPhotos, searchQuery = "")
    }

    fun getPhotosForFolder(folderPath: String): List<Photo> {
        val state = _uiState.value as? GalleryUiState.Success ?: return emptyList()
        return when (folderPath) {
            "favorites" -> state.allPhotos.filter { it.isFavorite && !it.isInRecycleBin && !it.isHidden }
            else -> state.allPhotos.filter { it.folderPath == folderPath && !it.isInRecycleBin && !it.isHidden }
        }.sortedByDescending { maxOf(it.dateTaken, it.dateAdded * 1000) }
    }

    // Optimistic update: toggle the flag in-memory, no MediaStore reload needed
    fun toggleFavorite(photoId: Long) {
        repository.toggleFavorite(photoId)
        val state = _uiState.value as? GalleryUiState.Success ?: return
        val newFav = !(state.allPhotos.find { it.id == photoId }?.isFavorite ?: return)
        fun Photo.upd() = if (id == photoId) copy(isFavorite = newFav) else this
        val updatedAllPhotos = state.allPhotos.map { it.upd() }
        val favorites = updatedAllPhotos.filter { it.isFavorite && !it.isInRecycleBin && !it.isHidden }
        val favFolder = if (favorites.isNotEmpty()) listOf(
            FolderGroup(
                folderName = "Favorites",
                folderPath = "favorites",
                photos = favorites.take(4),
                count = favorites.size,
                isSpecialFolder = true
            )
        ) else emptyList()
        _uiState.value = state.copy(
            allPhotos = updatedAllPhotos,
            filteredPhotos = state.filteredPhotos.map { it.upd() },
            dateGroups = state.dateGroups.map { g -> g.copy(photos = g.photos.map { it.upd() }) },
            folderGroups = state.folderGroups.filter { !it.isSpecialFolder }
                .map { fg -> fg.copy(photos = fg.photos.map { it.upd() }) } + favFolder
        )
    }

    // Optimistic update: remove from visible lists immediately, reload only when unhiding
    fun toggleHidden(photoId: Long) {
        repository.toggleHidden(photoId)
        val state = _uiState.value as? GalleryUiState.Success ?: return
        val photo = state.allPhotos.find { it.id == photoId }
        if (photo != null && !photo.isHidden) {
            _uiState.value = state.copy(
                allPhotos = state.allPhotos.filter { it.id != photoId },
                filteredPhotos = state.filteredPhotos.filter { it.id != photoId },
                dateGroups = state.dateGroups.map { g ->
                    g.copy(photos = g.photos.filter { it.id != photoId })
                }.filter { it.photos.isNotEmpty() },
                folderGroups = state.folderGroups.map { fg ->
                    fg.copy(photos = fg.photos.filter { it.id != photoId })
                },
                hiddenPhotos = state.hiddenPhotos + photo.copy(isHidden = true)
            )
        } else {
            // Unhiding requires re-inserting into sorted date/folder groups — reload
            repository.invalidateCache()
            refresh()
        }
    }

    // Optimistic update: remove from gallery, add to recycle bin immediately
    fun moveToRecycleBin(photoId: Long) {
        repository.moveToRecycleBin(photoId)
        val state = _uiState.value as? GalleryUiState.Success ?: return
        val photo = state.allPhotos.find { it.id == photoId } ?: return
        val now = System.currentTimeMillis()
        val item = RecycleBinItem(photo.copy(isInRecycleBin = true, deletedAt = now), now)
        _uiState.value = state.copy(
            allPhotos = state.allPhotos.filter { it.id != photoId },
            filteredPhotos = state.filteredPhotos.filter { it.id != photoId },
            dateGroups = state.dateGroups.map { g ->
                g.copy(photos = g.photos.filter { it.id != photoId })
            }.filter { it.photos.isNotEmpty() },
            folderGroups = state.folderGroups.map { fg ->
                fg.copy(photos = fg.photos.filter { it.id != photoId })
            },
            recycleBinItems = state.recycleBinItems + item
        )
    }

    // Optimistic update: remove from recycle bin immediately, reload gallery in background
    fun restoreFromRecycleBin(photoId: Long) {
        repository.restoreFromRecycleBin(photoId)
        val state = _uiState.value as? GalleryUiState.Success ?: return
        _uiState.value = state.copy(
            recycleBinItems = state.recycleBinItems.filter { it.photo.id != photoId }
        )
        reloadGalleryInBackground()
    }

    fun permanentlyDelete(photoId: Long, photoUri: Uri) {
        viewModelScope.launch {
            when (val result = repository.permanentlyDelete(getApplication(), photoId, photoUri)) {
                is DeleteResult.NeedsPermission -> {
                    pendingDeleteIds = listOf(photoId)
                    _pendingDeleteIntent.value = result.pendingIntent
                }
                is DeleteResult.Success -> {
                    val state = _uiState.value as? GalleryUiState.Success ?: return@launch
                    _uiState.value = state.copy(
                        recycleBinItems = state.recycleBinItems.filter { it.photo.id != photoId }
                    )
                    reloadGalleryInBackground()
                }
                DeleteResult.Failure -> Log.e(TAG, "Failed to permanently delete $photoUri")
            }
        }
    }

    fun onDeleteGranted() {
        repository.clearPrefsForIds(pendingDeleteIds)
        pendingDeleteIds = emptyList()
        refresh()
    }

    fun clearPendingDeleteIntent() {
        _pendingDeleteIntent.value = null
    }

    fun bulkRestore(items: List<RecycleBinItem>) {
        items.forEach { repository.restoreFromRecycleBin(it.photo.id) }
        val state = _uiState.value as? GalleryUiState.Success ?: return
        val ids = items.map { it.photo.id }.toSet()
        _uiState.value = state.copy(recycleBinItems = state.recycleBinItems.filter { it.photo.id !in ids })
        reloadGalleryInBackground()
    }

    fun bulkDelete(items: List<RecycleBinItem>) {
        viewModelScope.launch {
            when (val result = repository.bulkPermanentlyDelete(getApplication(), items.map { it.photo })) {
                is DeleteResult.NeedsPermission -> {
                    pendingDeleteIds = items.map { it.photo.id }
                    _pendingDeleteIntent.value = result.pendingIntent
                }
                is DeleteResult.Success -> refresh()
                DeleteResult.Failure -> Log.e(TAG, "Bulk delete failed")
            }
        }
    }

    fun renamePhoto(photoId: Long, photoUri: Uri, newName: String) {
        viewModelScope.launch {
            when (val result = repository.renamePhoto(getApplication(), photoId, photoUri, newName)) {
                is WriteResult.NeedsPermission -> {
                    pendingWriteOperation = result.operation
                    _pendingWriteIntent.value = result.pendingIntent
                }
                WriteResult.Success -> applyRenameOptimistic(photoId, newName)
                WriteResult.Failure -> {
                    Log.e(TAG, "Rename failed for $photoUri")
                    _userMessage.tryEmit("Failed to rename photo")
                }
            }
        }
    }

    private fun applyRenameOptimistic(photoId: Long, newName: String) {
        val state = _uiState.value as? GalleryUiState.Success ?: return
        fun Photo.renamed() = if (id == photoId) copy(displayName = newName) else this
        _uiState.value = state.copy(
            allPhotos = state.allPhotos.map { it.renamed() },
            filteredPhotos = state.filteredPhotos.map { it.renamed() },
            dateGroups = state.dateGroups.map { g -> g.copy(photos = g.photos.map { it.renamed() }) },
            folderGroups = state.folderGroups.map { fg -> fg.copy(photos = fg.photos.map { it.renamed() }) }
        )
    }

    fun copyPhoto(photo: Photo, destFolderPath: String) {
        viewModelScope.launch {
            val ok = repository.copyPhoto(getApplication(), photo, destFolderPath)
            if (ok) {
                _userMessage.tryEmit("Copied to ${destFolderPath.substringAfterLast('/')}")
                reloadGalleryInBackground()
            } else {
                _userMessage.tryEmit("Failed to copy photo")
            }
        }
    }

    fun movePhoto(photo: Photo, destFolderPath: String) {
        viewModelScope.launch {
            when (val result = repository.movePhoto(getApplication(), photo, destFolderPath)) {
                is WriteResult.NeedsPermission -> {
                    pendingWriteOperation = result.operation
                    _pendingWriteIntent.value = result.pendingIntent
                }
                WriteResult.Success -> {
                    _userMessage.tryEmit("Moved to ${destFolderPath.substringAfterLast('/')}")
                    reloadGalleryInBackground()
                }
                WriteResult.Failure -> {
                    Log.e(TAG, "Move failed for ${photo.uri}")
                    _userMessage.tryEmit("Failed to move photo")
                }
            }
        }
    }

    fun onWritePermissionGranted() {
        val op = pendingWriteOperation ?: return
        pendingWriteOperation = null
        viewModelScope.launch {
            when (op) {
                is PendingWrite.Rename -> {
                    val ok = repository.applyRename(getApplication(), op.uri, op.newName)
                    if (ok) {
                        applyRenameOptimistic(op.photoId, op.newName)
                        _userMessage.tryEmit("Renamed")
                    } else {
                        _userMessage.tryEmit("Failed to rename photo")
                    }
                }
                is PendingWrite.Move -> {
                    val ok = repository.applyMove(getApplication(), op.photo, op.destFolderPath)
                    if (ok) {
                        _userMessage.tryEmit("Moved to ${op.destFolderPath.substringAfterLast('/')}")
                        reloadGalleryInBackground()
                    } else {
                        _userMessage.tryEmit("Failed to move photo")
                    }
                }
            }
        }
    }

    fun onWritePermissionDenied() {
        val op = pendingWriteOperation
        pendingWriteOperation = null
        when (op) {
            is PendingWrite.Rename -> _userMessage.tryEmit("Rename canceled")
            is PendingWrite.Move -> _userMessage.tryEmit("Move canceled")
            null -> Unit
        }
    }

    fun clearPendingWriteIntent() {
        _pendingWriteIntent.value = null
    }

    fun bulkMoveToRecycleBin(photoIds: Set<Long>) {
        val state = _uiState.value as? GalleryUiState.Success ?: return
        val now = System.currentTimeMillis()
        val newItems = state.allPhotos
            .filter { it.id in photoIds }
            .mapIndexed { idx, photo ->
                val itemTime = now + idx
                RecycleBinItem(photo.copy(isInRecycleBin = true, deletedAt = itemTime), itemTime)
            }
        photoIds.forEach { repository.moveToRecycleBin(it) }
        fun List<Photo>.remove() = filter { it.id !in photoIds }
        _uiState.value = state.copy(
            allPhotos = state.allPhotos.remove(),
            filteredPhotos = state.filteredPhotos.remove(),
            dateGroups = state.dateGroups.map { g -> g.copy(photos = g.photos.remove()) }
                .filter { it.photos.isNotEmpty() },
            folderGroups = state.folderGroups.map { fg -> fg.copy(photos = fg.photos.remove()) }
                .filter { it.photos.isNotEmpty() },
            recycleBinItems = state.recycleBinItems + newItems
        )
    }

    fun bulkToggleHidden(photoIds: Set<Long>) {
        val state = _uiState.value as? GalleryUiState.Success ?: return
        photoIds.forEach { repository.toggleHidden(it) }
        fun List<Photo>.remove() = filter { it.id !in photoIds }
        _uiState.value = state.copy(
            allPhotos = state.allPhotos.remove(),
            filteredPhotos = state.filteredPhotos.remove(),
            dateGroups = state.dateGroups.map { g -> g.copy(photos = g.photos.remove()) }
                .filter { it.photos.isNotEmpty() },
            folderGroups = state.folderGroups.map { fg -> fg.copy(photos = fg.photos.remove()) }
                .filter { it.photos.isNotEmpty() },
            hiddenPhotos = state.hiddenPhotos + state.allPhotos.filter { it.id in photoIds }
                .map { it.copy(isHidden = true) }
        )
    }

    suspend fun verifyHiddenPassword(password: String): Boolean =
        withContext(Dispatchers.Default) { repository.verifyHiddenPassword(password) }

    fun hasHiddenPassword(): Boolean = repository.hasHiddenPassword()

    fun setHiddenPassword(password: String) = repository.setHiddenPassword(password)

    /** Public entry point used by the photo editor when a new file has been saved into MediaStore. */
    fun onMediaCreated() = reloadGalleryInBackground()

    fun loadCaption(photoId: Long) {
        val cached = repository.getCaption(photoId)
        _captionState.value = if (cached != null) CaptionState.Loaded(cached) else CaptionState.None
    }

    fun generateCaption(photo: Photo) {
        viewModelScope.launch {
            _captionState.value = CaptionState.Loading
            try {
                val bitmap = PhotoIO.loadPreviewBitmap(getApplication(), photo.uri)
                _captionState.value = when (val result = aiRepo.describePhoto(bitmap)) {
                    is AiResult.TextSuccess -> {
                        repository.setCaption(photo.id, result.text)
                        CaptionState.Loaded(result.text)
                    }
                    is AiResult.MissingApiKey -> CaptionState.Error("No OpenRouter key configured")
                    is AiResult.NetworkError -> CaptionState.Error("Network error — check connection")
                    is AiResult.QuotaExceeded -> CaptionState.Error("API quota exceeded")
                    is AiResult.Failure -> CaptionState.Error(result.message)
                    else -> CaptionState.Error("Unexpected error")
                }
            } catch (e: Exception) {
                _captionState.value = CaptionState.Error(e.message ?: "Failed to load image")
            }
        }
    }

    fun resetCaption() {
        _captionState.value = CaptionState.None
    }

    fun generateImage(prompt: String) {
        viewModelScope.launch {
            _imageGenState.value = ImageGenState.Generating
            try {
                when (val result = aiRepo.generateImage(prompt)) {
                    is AiResult.Success -> {
                        PhotoIO.saveBitmap(getApplication(), result.bitmap)
                        _imageGenState.value = ImageGenState.Idle
                        _userMessage.tryEmit("Image saved to gallery")
                        reloadGalleryInBackground()
                    }
                    is AiResult.MissingApiKey ->
                        _imageGenState.value = ImageGenState.Error("Set GEMINI_API_KEY in local.properties")
                    is AiResult.QuotaExceeded ->
                        _imageGenState.value = ImageGenState.Error("Gemini quota exceeded — try later")
                    is AiResult.NetworkError ->
                        _imageGenState.value = ImageGenState.Error("Network error — check connection")
                    is AiResult.Failure ->
                        _imageGenState.value = ImageGenState.Error(result.message)
                    else -> _imageGenState.value = ImageGenState.Idle
                }
            } catch (e: Exception) {
                _imageGenState.value = ImageGenState.Error(e.message ?: "Generation failed")
            }
        }
    }

    fun resetImageGenState() { _imageGenState.value = ImageGenState.Idle }

    // Reloads date/folder groups in the background after a restore, without showing Loading state
    private fun reloadGalleryInBackground() {
        viewModelScope.launch {
            try {
                repository.invalidateCache()
                repository.loadAllMedia()
                val dateGroups: List<DateGroup>
                val folderGroups: List<FolderGroup>
                coroutineScope {
                    val d1 = async(Dispatchers.Default) { repository.getPhotosByDate() }
                    val d2 = async(Dispatchers.Default) { repository.loadFolderGroups() }
                    dateGroups = d1.await()
                    folderGroups = d2.await()
                }
                val allPhotos = dateGroups.flatMap { it.photos }
                (_uiState.value as? GalleryUiState.Success)?.let { s ->
                    _uiState.value = s.copy(
                        dateGroups = dateGroups,
                        folderGroups = folderGroups,
                        allPhotos = allPhotos,
                        filteredPhotos = if (s.searchQuery.isEmpty()) allPhotos
                        else allPhotos.filter {
                            it.displayName.contains(s.searchQuery, true) || it.folderName.contains(s.searchQuery, true)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading gallery in background", e)
                _userMessage.tryEmit("Reload failed — please refresh")
            }
        }
    }
}