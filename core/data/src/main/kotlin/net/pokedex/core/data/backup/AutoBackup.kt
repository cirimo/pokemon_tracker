package net.pokedex.core.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.Outcome
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When automatic backups are written.
 *
 * - **Debounced after changes.** Every change to the records re-arms one unique work
 *   request [DEBOUNCE_MINUTES] out, so thirty catches in a sitting produce one file, not
 *   thirty. WorkManager rather than a coroutine delay, because it survives the process.
 * - **Flushed when the app goes to the background.** Closing the app straight after a catch
 *   is the common case, and it should not wait out the debounce in a process that may not
 *   live that long.
 * - **Daily, as a net.** It writes only if the records differ from the newest backup, so it
 *   costs nothing on a day nothing changed, and it catches a debounce that never ran.
 *
 * All three go through [BackupRepository.autoBackup], which also skips an empty database
 * and unchanged records, so an extra trigger is harmless.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catches: CatchRepository,
    private val settings: SettingsRepository,
) {
    private val pending = AtomicBoolean(false)
    private val work get() = WorkManager.getInstance(context)

    /** Called once, from Application.onCreate. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.observe().map { it.autoBackupEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) schedulePeriodic() else cancelAll()
            }
        }
        scope.launch {
            // The first emission is the table as it stands at launch, not a change.
            catches.observeRecords().distinctUntilChanged().drop(1).collect { afterChange() }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = flush()
            },
        )
    }

    private suspend fun afterChange() {
        if (!settings.get().autoBackupEnabled) return
        pending.set(true)
        enqueue(delayMinutes = DEBOUNCE_MINUTES, reason = "after changes")
    }

    private fun flush() {
        if (pending.getAndSet(false)) enqueue(delayMinutes = 0, reason = "app closed")
    }

    private fun enqueue(delayMinutes: Long, reason: String) {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(AutoBackupWorker.KEY_REASON to reason))
            .build()
        work.enqueueUniqueWork(DEBOUNCE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf(AutoBackupWorker.KEY_REASON to "daily"))
            .build()
        work.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancelAll() {
        pending.set(false)
        work.cancelUniqueWork(DEBOUNCE_WORK)
        work.cancelUniqueWork(DAILY_WORK)
    }

    private companion object {
        const val DEBOUNCE_MINUTES = 2L
        const val DEBOUNCE_WORK = "auto-backup"
        const val DAILY_WORK = "auto-backup-daily"
    }
}

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (backups.autoBackup(reason = inputData.getString(KEY_REASON) ?: "auto")) {
            is Outcome.Ok -> Result.success()
            // A cloud-backed folder can be briefly unreachable. Retry with WorkManager's
            // backoff a few times, then give up until the next change or the daily run.
            is Outcome.Err -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

    companion object {
        const val KEY_REASON = "reason"
        private const val MAX_ATTEMPTS = 3
    }
}
