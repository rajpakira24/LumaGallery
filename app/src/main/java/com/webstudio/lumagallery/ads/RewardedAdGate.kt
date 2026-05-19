package com.webstudio.lumagallery.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener

object RewardedAdGate {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: PendingReward? = null
    private var rewardedAd: LevelPlayRewardedAd? = null
    private var configuredAdUnitId: String? = null
    private var loading = false

    fun configure(adUnitId: String) {
        val normalizedAdUnitId = adUnitId.trim()
        if (normalizedAdUnitId.isEmpty() || configuredAdUnitId == normalizedAdUnitId) return

        configuredAdUnitId = normalizedAdUnitId
        rewardedAd = LevelPlayRewardedAd(normalizedAdUnitId).apply {
            setListener(object : LevelPlayRewardedAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    loading = false
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    loading = false
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) = Unit

                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    finishPending(success = true)
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    loading = false
                    finishPending(success = false)
                    loadAd()
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    val current = pending
                    if (current != null && !current.rewarded) {
                        mainHandler.postDelayed({
                            if (pending === current && !current.rewarded) {
                                finishPending(success = false)
                            }
                        }, REWARD_CLOSE_GRACE_MS)
                    }
                    loadAd()
                }

                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
            })
        }
        loadAd()
    }

    fun showForReward(
        activity: Activity,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (pending != null) {
            onUnavailable()
            return
        }
        if (ad == null || !ad.isAdReady()) {
            loadAd()
            onUnavailable()
            return
        }

        pending = PendingReward(onRewarded = onRewarded, onUnavailable = onUnavailable)
        ad.showAd(activity)
    }

    fun loadAd() {
        val ad = rewardedAd ?: return
        if (loading || ad.isAdReady()) return
        loading = true
        ad.loadAd()
    }

    private fun finishPending(success: Boolean) {
        val current = pending ?: return
        if (success) current.rewarded = true
        pending = null
        mainHandler.post {
            if (success) current.onRewarded() else current.onUnavailable()
        }
    }

    private data class PendingReward(
        val onRewarded: () -> Unit,
        val onUnavailable: () -> Unit,
        var rewarded: Boolean = false
    )

    private const val REWARD_CLOSE_GRACE_MS = 1_000L
}
