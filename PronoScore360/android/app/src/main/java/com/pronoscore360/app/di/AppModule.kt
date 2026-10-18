package com.pronoscore360.app.di

import android.content.Context
import androidx.room.Room
import com.pronoscore360.app.BuildConfig
import com.pronoscore360.app.data.AuthEvents
import com.pronoscore360.app.data.Prefs
import com.pronoscore360.app.data.Repository
import com.pronoscore360.app.data.TokenStoreImpl
import com.pronoscore360.app.data.local.AppDb
import com.pronoscore360.core.api.ApiClient
import com.pronoscore360.core.api.PronoApi
import com.pronoscore360.core.api.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun tokenStore(@ApplicationContext c: Context): TokenStore = TokenStoreImpl(c)
    @Provides @Singleton fun prefs(@ApplicationContext c: Context) = Prefs(c)
    @Provides @Singleton fun db(@ApplicationContext c: Context): AppDb = Room.databaseBuilder(c, AppDb::class.java, "ps360.db").fallbackToDestructiveMigration().build()
    @Provides @Singleton fun api(store: TokenStore): PronoApi =
        ApiClient.create(BuildConfig.API_BASE_URL, store, onLogout = { AuthEvents.signalLogout() }, debug = BuildConfig.DEBUG)
    @Provides @Singleton fun repository(api: PronoApi, store: TokenStore, db: AppDb) = Repository(api, store, db.cache())
}

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides @Singleton fun billing(@ApplicationContext c: Context, repo: Repository) = com.pronoscore360.app.billing.BillingManager(c, repo)
}
