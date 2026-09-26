package net.pokedex.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import net.pokedex.core.data.user.UserDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The user database is the half that can never be regenerated, so every schema version
 * it has ever had must have a tested path forward.
 *
 * Each migration gets a test here in the same commit that adds it -- an untested
 * migration on this database is a data-loss bug on a timer. The assertion that matters in
 * every one of them is that catch_record comes out the other side untouched.
 */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun version1SchemaIsExportedAndOpens() {
        val db = helper.createDatabase(TEST_DB, 1)
        insertSecondUnown(db)
        db.close()

        val reopened = helper.runMigrationsAndValidate(TEST_DB, 1, true)
        reopened.query("SELECT variantId, copyIndex FROM catch_record").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("unown")
            assertThat(cursor.getInt(1)).isEqualTo(1)
        }
        reopened.close()
    }

    @Test
    fun migration1To2KeepsRecordsAndSettingsAndDefaultsTheLastBox() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertSecondUnown(this)
            execSQL(
                "INSERT INTO user_settings " +
                    "(id, activePresetId, lastSeenPresetVersion, lastSeenDatasetVersion, " +
                    "autoBackupEnabled, autoBackupKeepCount) " +
                    "VALUES (1, 'grouped-balanced', 3, 7, 1, 10)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, UserDatabase.MIGRATION_1_2)

        migrated.query("SELECT variantId, copyIndex, caught, notes FROM catch_record").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("unown")
            assertThat(cursor.getInt(1)).isEqualTo(1)
            assertThat(cursor.getInt(2)).isEqualTo(1)
            assertThat(cursor.getString(3)).isEqualTo("the second copy")
        }
        migrated.query("SELECT lastSeenDatasetVersion, lastBoxIndex FROM user_settings").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(7)
            assertThat(cursor.getInt(1)).isEqualTo(0)
        }
        migrated.close()
    }

    @Test
    fun migration2To3KeepsRecordsAndSettingsAndAddsNoBackupFolder() {
        helper.createDatabase(TEST_DB, 2).apply {
            insertSecondUnown(this)
            execSQL(
                "INSERT INTO user_settings " +
                    "(id, activePresetId, lastSeenPresetVersion, lastSeenDatasetVersion, " +
                    "autoBackupEnabled, autoBackupKeepCount, lastBoxIndex) " +
                    "VALUES (1, 'grouped-balanced', 3, 7, 0, 4, 12)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, UserDatabase.MIGRATION_2_3)

        migrated.query("SELECT variantId, copyIndex, caught, originGameId, notes FROM catch_record").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("unown")
            assertThat(cursor.getInt(1)).isEqualTo(1)
            assertThat(cursor.getInt(2)).isEqualTo(1)
            assertThat(cursor.getString(3)).isEqualTo("la")
            assertThat(cursor.getString(4)).isEqualTo("the second copy")
        }
        migrated.query(
            "SELECT autoBackupEnabled, autoBackupKeepCount, lastBoxIndex, " +
                "backupTreeUri, lastOriginGameId, restoreOfferDismissed FROM user_settings",
        ).use { cursor ->
            cursor.moveToFirst()
            // The user's choices survive; the new columns start empty.
            assertThat(cursor.getInt(0)).isEqualTo(0)
            assertThat(cursor.getInt(1)).isEqualTo(4)
            assertThat(cursor.getInt(2)).isEqualTo(12)
            assertThat(cursor.isNull(3)).isTrue()
            assertThat(cursor.isNull(4)).isTrue()
            assertThat(cursor.getInt(5)).isEqualTo(0)
        }
        migrated.close()
    }

    @Test
    fun migration3To4KeepsRecordsAndSettingsAndStartsWithNoGames() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertSecondUnown(this)
            execSQL(
                "INSERT INTO user_settings " +
                    "(id, activePresetId, lastSeenPresetVersion, lastSeenDatasetVersion, " +
                    "autoBackupEnabled, autoBackupKeepCount, lastBoxIndex, backupTreeUri, " +
                    "lastOriginGameId, restoreOfferDismissed) " +
                    "VALUES (1, 'grouped-balanced', 3, 7, 1, 4, 12, 'content://tree', 'sv-s', 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, UserDatabase.MIGRATION_3_4)

        migrated.query("SELECT variantId, copyIndex, caught, notes FROM catch_record").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("unown")
            assertThat(cursor.getInt(1)).isEqualTo(1)
            assertThat(cursor.getInt(2)).isEqualTo(1)
            assertThat(cursor.getString(3)).isEqualTo("the second copy")
        }
        migrated.query("SELECT backupTreeUri, lastOriginGameId, lastBoxIndex FROM user_settings").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("content://tree")
            assertThat(cursor.getString(1)).isEqualTo("sv-s")
            assertThat(cursor.getInt(2)).isEqualTo(12)
        }
        migrated.query("SELECT COUNT(*) FROM my_game").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        // The primary key is what stops a game being owned twice.
        migrated.execSQL("INSERT INTO my_game (gameId) VALUES ('la')")
        migrated.execSQL("INSERT OR IGNORE INTO my_game (gameId) VALUES ('la')")
        migrated.query("SELECT COUNT(*) FROM my_game").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        migrated.close()
    }

    @Test
    fun migration4To5KeepsRecordsAndGamesAndStartsEveryGameAtTheSameRank() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertSecondUnown(this)
            // The real install's shape: games chosen in M4, in no particular order.
            execSQL("INSERT INTO my_game (gameId) VALUES ('sv-v')")
            execSQL("INSERT INTO my_game (gameId) VALUES ('lza')")
            execSQL("INSERT INTO my_game (gameId) VALUES ('la')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, UserDatabase.MIGRATION_4_5)

        migrated.query("SELECT variantId, copyIndex, caught, notes FROM catch_record").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("unown")
            assertThat(cursor.getInt(1)).isEqualTo(1)
            assertThat(cursor.getInt(2)).isEqualTo(1)
            assertThat(cursor.getString(3)).isEqualTo("the second copy")
        }
        // Every game kept, all tied at 0: release order decides until the user moves one.
        migrated.query("SELECT gameId, farmOrder FROM my_game ORDER BY gameId").use { cursor ->
            val rows = buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) }
            assertThat(rows).containsExactly("la" to 0, "lza" to 0, "sv-v" to 0).inOrder()
        }
        // A game ticked after the migration goes to the end, the way the DAO inserts it.
        migrated.execSQL(
            "INSERT OR IGNORE INTO my_game (gameId, farmOrder) " +
                "SELECT 'sv-s', COALESCE(MAX(farmOrder) + 1, 0) FROM my_game",
        )
        migrated.query("SELECT farmOrder FROM my_game WHERE gameId = 'sv-s'").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        migrated.close()
    }

    @Test
    fun everyMigrationInOrderTakesVersion1ToCurrent() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertSecondUnown(this)
            close()
        }

        @Suppress("SpreadOperator")
        val migrated = helper.runMigrationsAndValidate(TEST_DB, UserDatabase.VERSION, true, *UserDatabase.MIGRATIONS)

        migrated.query("SELECT COUNT(*) FROM catch_record").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        migrated.close()
    }

    private fun insertSecondUnown(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO catch_record " +
                "(variantId, copyIndex, caught, originGameId, caughtAt, notes, favourite, priority, updatedAt) " +
                "VALUES ('unown', 1, 1, 'la', 1700000000000, 'the second copy', 0, 0, 1700000000000)",
        )
    }

    private companion object {
        const val TEST_DB = "user-migration-test.db"
    }
}
