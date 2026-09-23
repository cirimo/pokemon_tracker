package net.pokedex.core.data.user

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        const val VERSION = 2
        const val FILE_NAME = "user.db"

        /**
         * 1 -> 2: user_settings.lastBoxIndex, so the box view reopens where it was left.
         *
         * Additive, with a default, so every existing row is valid the moment it runs and
         * no catch record is touched. Tested in UserDatabaseMigrationTest.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN lastBoxIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migrations, in order.
         *
         * When you add one, add a MigrationTestHelper test alongside it. An untested
         * migration on this database is a data-loss bug waiting for a release.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
