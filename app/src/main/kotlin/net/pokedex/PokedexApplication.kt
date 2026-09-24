package net.pokedex

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.pokedex.core.data.backup.BackupScheduler
import net.pokedex.core.data.repository.DexRepository
import javax.inject.Inject

@HiltAndroidApp
class PokedexApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var dexRepository: DexRepository

    @Inject lateinit var backupScheduler: BackupScheduler

    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * WorkManager is initialised on demand with Hilt's factory, so the backup worker gets its
     * repository injected. The default initializer is removed in the manifest for this.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * Starts reading the dex before the first Activity exists.
     *
     * Both database opens and the join take ~200ms in a release build on the emulator, and
     * the Activity plus the first Compose frame take about as long again. Started here, the
     * two overlap and the box view has its data the moment it asks, instead of the load
     * queueing behind the UI. DexRepository caches, so the ViewModel's own call is then a
     * hit rather than a second load.
     */
    override fun onCreate() {
        super.onCreate()
        appScope.launch { dexRepository.dex() }
        backupScheduler.start(appScope)
    }

    /**
     * The one image loader, tuned for 1387 small local sprites and nothing else.
     *
     *  - No disk cache. Every sprite is already a file in the APK's assets, so a disk cache
     *    would be a second copy of the same bytes plus the I/O to write it. There is no
     *    network fetcher either -- coil-network is not a dependency, and the app has no
     *    INTERNET permission.
     *  - A memory cache sized as a share of the heap. A decoded tile is ~150px square, so
     *    two or three boxes' worth either side of the current one fit many times over and
     *    swiping back is a cache hit, not a decode.
     *  - No crossfade. Tiles arrive in the pager's first frame or close to it, and thirty
     *    tiles fading in at once reads as the grid flickering, not as polish.
     *
     * Decoding is downsampled to the tile: AsyncImage resolves its size from layout
     * constraints, so a 256px asset is decoded at the ~50dp a tile actually occupies.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, SPRITE_CACHE_SHARE).build() }
            .diskCache(null)
            .build()

    private companion object {
        const val SPRITE_CACHE_SHARE = 0.2
    }
}
