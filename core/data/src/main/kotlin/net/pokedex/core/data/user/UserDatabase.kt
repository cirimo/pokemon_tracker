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
        MyGameEntity::class,
    ],
    version = UserDatabase.VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun catchRecordDao(): CatchRecordDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun backupLogDao(): BackupLogDao
    abstract fun myGameDao(): MyGameDao

    companion object {
        const val VERSION = 5
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
         * 2 -> 3: where automatic backups go, the game the catch sheet prefills, and whether
         * the first-launch restore offer was turned down.
         *
         * Additive again: two nullable columns and a flag defaulting to false, so no
         * existing row changes meaning and catch_record is not touched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN backupTreeUri TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN lastOriginGameId TEXT")
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN restoreOfferDismissed INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * 3 -> 4: my_game, the games the user owns and plays. A new, empty table: nothing
         * existing is read or rewritten, and an empty set means "not chosen yet", which the
         * app treats as no game being unavailable rather than every game.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `my_game` (`gameId` TEXT NOT NULL, PRIMARY KEY(`gameId`))")
            }
        }

        /**
         * 4 -> 5: my_game.farmOrder, the order the user farms their games in. Every existing
         * row gets 0, and a tie reads in release order, so the games already chosen come out
         * in the order they did before and no catch_record row is touched.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE my_game ADD COLUMN farmOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migrations, in order.
         *
         * When you add one, add a MigrationTestHelper test alongside it. An untested
         * migration on this database is a data-loss bug waiting for a release.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
