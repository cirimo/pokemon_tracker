package net.pokedex.core.data.user

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Everything the user created. The half of the app that cannot be regenerated.
 *
 * Rules, enforced by review and by docs/adr/0002-two-databases.md:
 *  - NEVER fallbackToDestructiveMigration on this database.
 *  - Every version bump ships a Migration and a MigrationTestHelper test.
 *  - exportSchema stays true so those tests have something to migrate from.
 */
@Database(
    entities = [
        CatchRecordEntity::class,
        UserSettingsEntity::class,
        BackupLogEntity::class,
    ],
    version = UserDatabase.VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun catchRecordDao(): CatchRecordDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun backupLogDao(): BackupLogDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "user.db"

        /**
         * Migrations, in order. Empty at version 1.
         *
         * When you add one, add a MigrationTestHelper test alongside it. An untested
         * migration on this database is a data-loss bug waiting for a release.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
