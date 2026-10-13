package com.leaguehub.android.app.di

import android.content.Context
import androidx.room.Room
import com.leaguehub.android.BuildConfig
import com.leaguehub.android.data.FootballRepositoryImpl
import com.leaguehub.android.data.db.AppDatabase
import com.leaguehub.android.data.espn.EspnApi
import com.leaguehub.android.data.espn.EspnJson
import com.leaguehub.android.data.settings.SettingsRepositoryImpl
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        // Timeout sur chaque requête : un ESPN lent ne bloque jamais l'écran.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "LeagueHub-Android/${BuildConfig.VERSION_NAME}")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun espnApi(client: OkHttpClient): EspnApi = Retrofit.Builder()
        .baseUrl(EspnApi.BASE_URL)
        .client(client)
        .addConverterFactory(EspnJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(EspnApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "leaguehub.db")
            // Base de cache : en cas de changement de schéma, elle se reconstruit depuis ESPN.
            .fallbackToDestructiveMigration()
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun football(impl: FootballRepositoryImpl): FootballRepository

    @Binds
    abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
}
