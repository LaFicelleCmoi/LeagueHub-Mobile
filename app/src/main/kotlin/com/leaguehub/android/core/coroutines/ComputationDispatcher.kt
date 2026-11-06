package com.leaguehub.android.core.coroutines

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Dispatcher des calculs hors interface : décodage JSON, classements, simulations.
 * Injecté plutôt qu'écrit en dur, pour qu'un test puisse le remplacer.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComputationDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @ComputationDispatcher
    fun computation(): CoroutineDispatcher = Dispatchers.Default
}
