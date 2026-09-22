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
 * At version 1 there is nothing to migrate yet, so this test asserts the setup that
 * makes future migrations testable: schemas are exported, and the database opens and
 * keeps its rows. When version 2 arrives, add a migration and a test here in the same
 * commit -- an untested migration on this database is a data-loss bug on a timer.
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
        db.execSQL(
            "INSERT INTO catch_record " +
                "(variantId, copyIndex, caught, originGameId, caughtAt, notes, favourite, priority, updatedAt) " +
                "VALUES ('unown', 1, 1, 'la', 1700000000000, 'the second copy', 0, 0, 1700000000000)",
        )
        db.close()

        val reopened = helper.runMigrationsAndValidate(TEST_DB, 1, true)
        reopened.query("SELECT variantId, copyIndex FROM catch_record").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("unown")
            assertThat(cursor.getInt(1)).isEqualTo(1)
        }
        reopened.close()
    }

    private companion object {
        const val TEST_DB = "user-migration-test.db"
    }
}
