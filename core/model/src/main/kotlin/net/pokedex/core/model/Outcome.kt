package net.pokedex.core.model

/**
 * Result type for operations that can fail in ways the UI must distinguish.
 *
 * Deliberately not kotlin.Result: we want an exhaustive, enumerated error surface, and
 * Result erases that into Throwable.
 */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>

    companion object {
        inline fun <T> of(block: () -> T): Outcome<T> = Ok(block())
    }
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T> Outcome<T>.onErr(block: (AppError) -> Unit): Outcome<T> = also {
    if (it is Outcome.Err) block(it.error)
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value

/**
 * Every way this app is allowed to fail.
 *
 * The split matters: [Fatal] errors mean the shipped reference data is unusable and the
 * only fix is reinstalling. They must NEVER be raised for anything touching user data,
 * because the handling for them is destructive.
 */
sealed interface AppError {

    /** Unrecoverable: the bundled reference dataset cannot be used. */
    sealed interface Fatal : AppError

    data object DatasetMissing : Fatal
    data class DatasetCorrupt(val detail: String) : Fatal
    data class DatasetVersionUnsupported(val found: Int, val supported: Int) : Fatal

    /** Recoverable: the user can retry, pick another file, or cancel. */
    sealed interface Recoverable : AppError

    /**
     * The backup file was written by a newer version of the app. We refuse rather than
     * import partially -- a partial import of an unknown schema is how records get lost.
     */
    data class ImportSchemaTooNew(val found: Int, val supported: Int) : Recoverable
    data class ImportMalformed(val pointer: String, val reason: String) : Recoverable
    data class StorageFailure(val detail: String) : Recoverable
    data class Unexpected(val detail: String) : Recoverable
}
