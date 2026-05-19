package com.webstudio.lumagallery

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.webstudio.lumagallery.ads.UnityAdState
import com.webstudio.lumagallery.ads.RewardedAdGate
import com.webstudio.lumagallery.ui.navigation.Screen
import com.webstudio.lumagallery.ui.screens.*
import com.webstudio.lumagallery.ui.screens.edit.EditScreen
import com.webstudio.lumagallery.ui.theme.LumaGalleryTheme
import com.webstudio.lumagallery.ui.viewmodel.GalleryUiState
import com.webstudio.lumagallery.ui.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(VideoFrameDecoder.Factory()) }
                .build()
        )

        UnityAds.initialize(
            this,
            BuildConfig.UNITY_GAME_ID,
            BuildConfig.DEBUG,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    UnityAdState.markInitialized()
                    RewardedAdGate.configure(BuildConfig.UNITY_REWARDED_PLACEMENT_ID)
                }
                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) = Unit
            }
        )

        setContent {
            LumaGalleryTheme {
                val navController = rememberNavController()
                val viewModel: GalleryViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val pendingDeleteIntent by viewModel.pendingDeleteIntent.collectAsStateWithLifecycle()
                val pendingWriteIntent by viewModel.pendingWriteIntent.collectAsStateWithLifecycle()
                val toastContext = LocalContext.current
                LaunchedEffect(Unit) {
                    viewModel.userMessage.collect { msg ->
                        Toast.makeText(toastContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                val permissions = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                val permissionState = rememberMultiplePermissionsState(permissions)
                // On Android 14+ "Allow selected" grants only READ_MEDIA_VISUAL_USER_SELECTED;
                // accept any granted visual-media permission as enough to proceed.
                val hasMediaAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    permissionState.permissions.any { it.status.isGranted }
                } else {
                    permissionState.allPermissionsGranted
                }
                LaunchedEffect(hasMediaAccess) {
                    if (hasMediaAccess) {
                        viewModel.loadPhotos()
                    }
                }

                if (!hasMediaAccess) {
                    PermissionScreen(
                        onPermissionGranted = {},
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Gallery.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Screen.Gallery.route) {
                            GalleryScreen(
                                uiState = uiState,
                                onFolderClick = { folderPath ->
                                    navController.navigate(Screen.CategoryDetail.createRoute(folderPath))
                                },
                                onPhotoClick = { photoId ->
                                    navController.navigate(Screen.PhotoDetail.createRoute(photoId))
                                },
                                onSearchQueryChange = { query ->
                                    viewModel.searchPhotos(query)
                                },
                                onViewModeChange = { mode ->
                                    viewModel.setViewMode(mode)
                                },
                                onRecentlyDeletedClick = {
                                    navController.navigate(Screen.RecycleBin.route)
                                },
                                onHiddenCollectionClick = {
                                    navController.navigate(Screen.HiddenCollection.route)
                                },
                                onBulkMoveToRecycleBin = { ids -> viewModel.bulkMoveToRecycleBin(ids) },
                                onBulkToggleHidden = { ids -> viewModel.bulkToggleHidden(ids) },
                                onRefresh = { viewModel.refresh() }
                            )
                        }

                        composable(
                            route = Screen.CategoryDetail.route,
                            arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
                            enterTransition = {
                                fadeIn(animationSpec = tween(280)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(220)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = tween(220))
                            }
                        ) { backStackEntry ->
                            // Decode the URL-encoded folder path
                            val encodedFolderPath = backStackEntry.arguments?.getString("categoryName") ?: ""
                            val folderPath = Uri.decode(encodedFolderPath)

                            when (val state = uiState) {
                                is GalleryUiState.Success -> {
                                    val photos = viewModel.getPhotosForFolder(folderPath)
                                    val folderName = state.folderGroups
                                        .find { it.folderPath == folderPath }
                                        ?.folderName ?: "Folder"

                                    CategoryDetailScreen(
                                        categoryName = folderName,
                                        photos = photos,
                                        onPhotoClick = { photo ->
                                            navController.navigate(Screen.PhotoDetail.createRoute(photo.id))
                                        },
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                else -> {}
                            }
                        }

                        composable(
                            route = Screen.PhotoDetail.route,
                            arguments = listOf(navArgument("photoId") { type = NavType.LongType }),
                            enterTransition = {
                                fadeIn(animationSpec = tween(300)) +
                                        scaleIn(initialScale = 0.9f, animationSpec = tween(300))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(300)) +
                                        scaleOut(targetScale = 0.9f, animationSpec = tween(300))
                            }
                        ) { backStackEntry ->
                            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L

                            when (val state = uiState) {
                                is GalleryUiState.Success -> {
                                    val photo = state.allPhotos.find { it.id == photoId }
                                        ?: state.hiddenPhotos.find { it.id == photoId }
                                    val photoList = if (state.allPhotos.any { it.id == photoId })
                                        state.allPhotos
                                    else
                                        state.hiddenPhotos
                                    if (photo != null) {
                                        PhotoDetailScreen(
                                            photo = photo,
                                            allPhotos = photoList,
                                            onNavigateBack = { navController.popBackStack() },
                                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                            onToggleHidden = { id -> viewModel.toggleHidden(id) },
                                            onSetHiddenPassword = { password -> viewModel.setHiddenPassword(password) },
                                            onDelete = { id -> viewModel.moveToRecycleBin(id) },
                                            hasHiddenPassword = viewModel.hasHiddenPassword(),
                                            onRenamePhoto = { id, uri, name -> viewModel.renamePhoto(id, uri, name) },
                                            onCopyPhoto = { p, path -> viewModel.copyPhoto(p, path) },
                                            onMovePhoto = { p, path -> viewModel.movePhoto(p, path) },
                                            onEditPhoto = { id ->
                                                navController.navigate(Screen.EditPhoto.createRoute(id))
                                            },
                                            folderGroups = state.folderGroups,
                                            pendingWriteIntent = pendingWriteIntent,
                                            onWriteIntentConsumed = { viewModel.clearPendingWriteIntent() },
                                            onWriteGranted = { viewModel.onWritePermissionGranted() },
                                            onWriteDenied = { viewModel.onWritePermissionDenied() }
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }

                        composable(
                            route = Screen.RecycleBin.route,
                            enterTransition = {
                                fadeIn(animationSpec = tween(280)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(220)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = tween(220))
                            }
                        ) {
                            when (val state = uiState) {
                                is GalleryUiState.Success -> {
                                    RecycleBinScreen(
                                        items = state.recycleBinItems,
                                        onNavigateBack = { navController.popBackStack() },
                                        onRestore = { item ->
                                            viewModel.restoreFromRecycleBin(item.photo.id)
                                        },
                                        onPermanentDelete = { item ->
                                            viewModel.permanentlyDelete(item.photo.id, item.photo.uri)
                                        },
                                        onBulkRestore = { items ->
                                            viewModel.bulkRestore(items)
                                        },
                                        onBulkDelete = { items ->
                                            viewModel.bulkDelete(items)
                                        },
                                        pendingDeleteIntent = pendingDeleteIntent,
                                        onDeleteIntentConsumed = { viewModel.clearPendingDeleteIntent() },
                                        onDeleteGranted = { viewModel.onDeleteGranted() }
                                    )
                                }
                                else -> {}
                            }
                        }

                        composable(
                            route = Screen.EditPhoto.route,
                            arguments = listOf(navArgument("photoId") { type = NavType.LongType }),
                            enterTransition = {
                                fadeIn(animationSpec = tween(280)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(220)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = tween(220))
                            }
                        ) { backStackEntry ->
                            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
                            when (val state = uiState) {
                                is GalleryUiState.Success -> {
                                    val photoUri = state.allPhotos.find { it.id == photoId }?.uri
                                        ?: state.hiddenPhotos.find { it.id == photoId }?.uri
                                    if (photoUri != null) {
                                        EditScreen(
                                            photoUri = photoUri,
                                            onNavigateBack = { navController.popBackStack() },
                                            onSaved = {
                                                viewModel.onMediaCreated()
                                                navController.popBackStack()
                                            }
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }

                        composable(
                            route = Screen.HiddenCollection.route,
                            enterTransition = {
                                fadeIn(animationSpec = tween(280)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(220)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = tween(220))
                            }
                        ) {
                            when (val state = uiState) {
                                is GalleryUiState.Success -> {
                                    HiddenCollectionScreen(
                                        photos = state.hiddenPhotos,
                                        onPhotoClick = { photo ->
                                            navController.navigate(Screen.PhotoDetail.createRoute(photo.id))
                                        },
                                        onNavigateBack = { navController.popBackStack() },
                                        onUnlock = { password ->
                                            viewModel.verifyHiddenPassword(password)
                                        },
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
