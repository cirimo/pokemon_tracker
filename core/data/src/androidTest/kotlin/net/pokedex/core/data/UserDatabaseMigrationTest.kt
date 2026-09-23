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
