package net.pokedex.core.data.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Outcome

/**
 * Runs file or database work and turns any failure into a recoverable [AppError].
 *
 * Never [AppError.Fatal]: that one means "reinstall", which is the one suggestion a
 * failing backup must never lead to.
 */
@Suppress("TooGenericExceptionCaught") // A provider can throw anything; all of it is "try again".
internal suspend fun <T> storage(io: CoroutineDispatcher, what: String, block: suspend () -> T): Outcome<T> =
    withContext(io) {
        try {
            Outcome.Ok(block())
        } catch (e: Exception) {
            Outcome.Err(AppError.StorageFailure(e.message ?: what))
        }
    }
