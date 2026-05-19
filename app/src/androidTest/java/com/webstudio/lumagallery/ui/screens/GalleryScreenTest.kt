package com.webstudio.lumagallery.ui.screens

import android.net.Uri
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.webstudio.lumagallery.data.DateGroup
import com.webstudio.lumagallery.data.Photo
import com.webstudio.lumagallery.ui.viewmodel.GalleryUiState
import com.webstudio.lumagallery.ui.viewmodel.ViewMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GalleryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUri: Uri = Uri.fromFile(File("test.jpg"))

    private val testPhoto = Photo(
        id = 1L,
        uri = testUri,
        displayName = "test.jpg",
        dateAdded = 1000L,
        dateTaken = 1000L,
        size = 1024L,
        mimeType = "image/jpeg"
    )

    private val testDateGroup = DateGroup(
        date = "2023-10-27",
        displayDate = "October 27, 2023",
        photos = listOf(testPhoto)
    )

    @Test
    fun galleryScreen_loadingState_showsProgressIndicator() {
        composeTestRule.setContent {
            GalleryScreen(
                uiState = GalleryUiState.Loading
            )
        }

        composeTestRule.onNodeWithText("Loading photos...").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun galleryScreen_emptyState_showsNoPhotosFound() {
        composeTestRule.setContent {
            GalleryScreen(
                uiState = GalleryUiState.Success(
                    dateGroups = emptyList(),
                    folderGroups = emptyList(),
                    allPhotos = emptyList()
                )
            )
        }

        composeTestRule.onNodeWithText("No photos found").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_successState_showsPhotos() {
        composeTestRule.setContent {
            GalleryScreen(
                uiState = GalleryUiState.Success(
                    dateGroups = listOf(testDateGroup),
                    folderGroups = emptyList(),
                    allPhotos = listOf(testPhoto)
                )
            )
        }

        composeTestRule.onNodeWithText("October 27, 2023").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("test.jpg").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_photoClick_triggersCallback() {
        var clickedPhotoId: Long? = null
        composeTestRule.setContent {
            GalleryScreen(
                uiState = GalleryUiState.Success(
                    dateGroups = listOf(testDateGroup),
                    folderGroups = emptyList(),
                    allPhotos = listOf(testPhoto)
                ),
                onPhotoClick = { clickedPhotoId = it }
            )
        }

        composeTestRule.onNodeWithContentDescription("test.jpg").performClick()
        assert(clickedPhotoId == 1L)
    }

    @Test
    fun galleryScreen_selectionMode_showsSelectionActionBar() {
        composeTestRule.setContent {
            GalleryScreen(
                uiState = GalleryUiState.Success(
                    dateGroups = listOf(testDateGroup),
                    folderGroups = emptyList(),
                    allPhotos = listOf(testPhoto)
                )
            )
        }

        // Enter selection mode via long click
        composeTestRule.onNodeWithContentDescription("test.jpg").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_switchViewMode_showsCollections() {
        composeTestRule.setContent {
            GalleryScreen(
                uiState = GalleryUiState.Success(
                    dateGroups = listOf(testDateGroup),
                    folderGroups = emptyList(),
                    allPhotos = listOf(testPhoto),
                    viewMode = ViewMode.COLLECTIONS
                )
            )
        }

        composeTestRule.onNodeWithText("No collections found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recently Deleted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hidden Collection").assertIsDisplayed()
    }
}
