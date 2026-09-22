package net.pokedex.core.data.di

import javax.inject.Qualifier

/**
 * Dispatchers are injected, never referenced directly, so tests can substitute a test
 * dispatcher without a global rule.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher
