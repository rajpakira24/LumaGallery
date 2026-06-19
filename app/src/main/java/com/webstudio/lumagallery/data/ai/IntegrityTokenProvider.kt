package com.webstudio.lumagallery.data.ai

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.tasks.await

/** Returns a Play Integrity token bound to [requestHash]. */
interface IntegrityTokenProvider {
    suspend fun token(requestHash: String): String
}

class PlayIntegrityTokenProvider(
    context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityTokenProvider {

    private val appContext = context.applicationContext
    @Volatile private var provider: StandardIntegrityTokenProvider? = null

    private suspend fun ensureProvider(): StandardIntegrityTokenProvider {
        provider?.let { return it }
        val manager = IntegrityManagerFactory.createStandard(appContext)
        val prepared = manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build(),
        ).await()
        provider = prepared
        return prepared
    }

    override suspend fun token(requestHash: String): String {
        val p = ensureProvider()
        val response: StandardIntegrityToken = p.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build(),
        ).await()
        return response.token()
    }
}
