package com.webstudio.lumagallery.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

object RewardedAdGate {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: PendingReward? = null
    private var placementId: String? = null
    private var loaded = false
    private var loading = false

    fun configure(adPlacementId: String) {
        val normalized = adPlacementId.trim()
        if (normalized.isEmpty() || placementId == normalized) return
        placementId = normalized
        loadAd()
    }

    fun showForReward(activity: Activity, onRewarded: () -> Unit, onUnavailable: () -> Unit) {
        val pid = placementId
        if (pid == null || !loaded || pending != null) {
            loadAd()
            onUnavailable()
            return
        }
        pending = PendingReward(onRewarded = onRewarded, onUnavailable = onUnavailable)
        loaded = false
        UnityAds.show(activity, pid, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                finishPending(success = false)
                loadAd()
            }
            override fun onUnityAdsShowStart(placementId: String) = Unit
            override fun onUnityAdsShowClick(placementId: String) = Unit
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                finishPending(success = state == UnityAds.UnityAdsShowCompletionState.COMPLETED)
                loadAd()
            }
        })
    }

    fun loadAd() {
        val pid = placementId ?: return
        if (loaded || loading) return
        loading = true
        UnityAds.load(pid, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                loaded = true
                loading = false
            }
            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                loaded = false
                loading = false
            }
        })
    }

    private fun finishPending(success: Boolean) {
        val current = pending ?: return
        pending = null
        mainHandler.post { if (success) current.onRewarded() else current.onUnavailable() }
    }

    private data class PendingReward(val onRewarded: () -> Unit, val onUnavailable: () -> Unit)
}
