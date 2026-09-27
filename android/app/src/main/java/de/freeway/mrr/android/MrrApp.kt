package de.freeway.mrr.android

import android.app.Application
import android.content.Context

import de.freeway.mrr.android.api.ApiClient
import de.freeway.mrr.android.api.MrrApi
import de.freeway.mrr.android.data.DefaultMrrRepository
import de.freeway.mrr.android.data.MrrRepository
import de.freeway.mrr.android.data.SecureTokenStore
import de.freeway.mrr.android.data.TokenStore

/**
 * Composition root: wires the token store, API client and repository together.
 * Everything is created once per process.
 */
class AppContainer(context: Context) {

    val tokenStore: TokenStore = SecureTokenStore(context)

    val api: MrrApi = ApiClient.create(BuildConfig.MRR_BASE_URL) { tokenStore.getToken() }

    val repository: MrrRepository = DefaultMrrRepository(api, tokenStore)
}

class MrrApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
