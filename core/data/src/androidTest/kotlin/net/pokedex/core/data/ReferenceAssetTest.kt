package net.pokedex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import net.pokedex.core.data.reference.ReferenceDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does the database we actually ship open, and is it the one we think it is?
 *
 * This catches the two ways a prepopulated asset fails in production, neither of which
 * any JVM test can see:
 *
 *  1. The identity hash in room_master_table does not match the compiled entities, so
 *     Room throws on first open. The pipeline reads the hash out of the exported schema
 *     precisely so this cannot happen -- this test is what proves it did not.
 *  2. The asset opens fine but has the wrong CONTENT, because the pipeline ran against
 *     a different upstream tag or a half-edited curated layer. Room has no opinion
 *     about that; these counts do.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceAssetTest {

    private lateinit var database: ReferenceDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Built exactly as DataModule builds it, so the test exercises the real path.
        database = Room.databaseBuilder(
            context,
            ReferenceDatabase::class.java,
            "reference-asset-test.db",
        )
            .createFromAsset(ReferenceDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase("reference-asset-test.db")
    }

    @Test
    fun theShippedPresetHas52BoxesAnd1394FilledSlots() = runTest {
        val dao = database.referenceDao()
        assertThat(dao.boxCount(PRESET_ID)).isEqualTo(EXPECTED_BOXES)
        assertThat(dao.slotCount(PRESET_ID)).isEqualTo(EXPECTED_FILLED_SLOTS)
    }

    @Test
    fun theSevenDuplicatedVariantsAreStillDuplicated() = runTest {
        val dao = database.referenceDao()
        val distinct = dao.distinctVariantCount(PRESET_ID)
        assertThat(distinct).isEqualTo(EXPECTED_DISTINCT_VARIANTS)
        // 1394 slots over 1387 variants is exactly the seven second copies.
        assertThat(dao.slotCount(PRESET_ID) - distinct).isEqualTo(EXPECTED_DUPLICATES)
    }

    @Test
    fun everySlotResolvesToARealVariant() = runTest {
        val dao = database.referenceDao()
        val variantIds = dao.variants().map { it.id }.toSet()
        val unresolved = dao.slots(PRESET_ID).filterNot { it.variantId in variantIds }
        assertThat(unresolved).isEmpty()
    }

    @Test
    fun datasetMetadataIsPresent() = runTest {
        val meta = database.referenceDao().datasetMeta()
        assertThat(meta).isNotNull()
        assertThat(meta!!.upstreamTag).isNotEmpty()
        assertThat(meta.contentHash).isNotEmpty()
    }

    private companion object {
        const val PRESET_ID = "grouped-balanced"
        const val EXPECTED_BOXES = 52
        const val EXPECTED_FILLED_SLOTS = 1394
        const val EXPECTED_DISTINCT_VARIANTS = 1387
        const val EXPECTED_DUPLICATES = 7
    }
}
