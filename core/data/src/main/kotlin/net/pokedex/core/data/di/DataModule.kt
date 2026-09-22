package net.pokedex.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.pokedex.core.data.reference.ReferenceDatabase
import net.pokedex.core.data.user.UserDatabase
import javax.inject.Singleton

/**
 * Wiring for both databases.
 *
 * The asymmetry between them is the point, and it is load-bearing:
 *
 *  - reference.db is prepopulated from an asset and allowed to be destroyed. If the
 *    schema version moves, Room drops it and recopies the new asset. Nothing is lost
 *    because nothing here belongs to the user.
 *  - user.db gets real migrations and must NEVER be given a destructive fallback.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun referenceDatabase(@ApplicationContext context: Context): ReferenceDatabase =
        Room.databaseBuilder(context, ReferenceDatabase::class.java, ReferenceDatabase.FILE_NAME)
            .createFromAsset(ReferenceDatabase.ASSET_PATH)
            // Safe here and only here: a dataset bump replaces this file wholesale.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    // Room takes migrations as a vararg, so the spread is forced on us. It happens
    // once, at startup, over an array that is empty today.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun userDatabase(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.FILE_NAME)
            .addMigrations(*UserDatabase.MIGRATIONS)
            // No fallbackToDestructiveMigration. Deliberate. Do not add one.
            .build()

    @Provides
    fun catchRecordDao(db: UserDatabase) = db.catchRecordDao()

    @Provides
    fun userSettingsDao(db: UserDatabase) = db.userSettingsDao()

    @Provides
    fun backupLogDao(db: UserDatabase) = db.backupLogDao()

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
