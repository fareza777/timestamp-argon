package com.phototimestamp.pro.util

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.phototimestamp.pro.data.AppRepository

/**
 * Fair & non-intrusive AdMob Manager:
 * - Never interrupts live camera shooting
 * - Uses official Google Test Ad Unit IDs out of the box (ready for production swap)
 * - Gracefully handles offline mode so the app works 100% offline without errors
 * - Supports Rewarded Ad to grant 24-hour Ad-Free Pro Pass
 */
class AdMobManager(
    private val context: Context,
    private val repository: AppRepository
) {
    companion object {
        const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        runCatching {
            MobileAds.initialize(context) {
                preloadInterstitial()
                preloadRewarded()
            }
        }
    }

    fun isAdFreeActive(): Boolean {
        return System.currentTimeMillis() < repository.settings.value.adFreeUntilMillis
    }

    fun preloadInterstitial() {
        if (isAdFreeActive()) return
        runCatching {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                TEST_INTERSTITIAL_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                    }
                }
            )
        }
    }

    fun preloadRewarded() {
        runCatching {
            val request = AdRequest.Builder().build()
            RewardedAd.load(
                context,
                TEST_REWARDED_UNIT_ID,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                    }
                }
            )
        }
    }

    /**
     * Shows interstitial fairly (only every 3rd batch/PDF export, never during photo capture).
     */
    fun maybeShowFairInterstitialAfterExport(activity: Activity, onComplete: () -> Unit = {}) {
        if (isAdFreeActive()) {
            onComplete()
            return
        }
        val nextCount = repository.settings.value.exportCountForInterstitial + 1
        repository.updateSettings { it.copy(exportCountForInterstitial = nextCount) }

        val ad = interstitialAd
        if (nextCount % 3 == 0 && ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial()
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onComplete()
                }
            }
            ad.show(activity)
        } else {
            if (ad == null) preloadInterstitial()
            onComplete()
        }
    }

    /**
     * Shows Rewarded Ad to grant 24 hours of Ad-Free Pro Pass.
     * If device is offline and ad is not cached, grants offline courtesy bonus so user is never stuck.
     */
    fun showRewardedAdForProPass(activity: Activity, onGranted: () -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadRewarded()
                }
            }
            ad.show(activity) {
                grant24HourAdFreePass()
                onGranted()
            }
        } else {
            // Offline-first fallback: grant pass and trigger background reload
            grant24HourAdFreePass()
            preloadRewarded()
            Toast.makeText(
                context,
                "Mode Offline terdeteksi: Pro Pass Bebas Iklan 24 Jam diaktifkan!",
                Toast.LENGTH_SHORT
            ).show()
            onGranted()
        }
    }

    private fun grant24HourAdFreePass() {
        val until = System.currentTimeMillis() + (24L * 3600L * 1000L)
        repository.updateSettings { it.copy(adFreeUntilMillis = until) }
    }
}
