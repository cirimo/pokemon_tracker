package net.pokedex.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import net.pokedex.core.data.backup.BackupLocation
import net.pokedex.core.data.backup.BackupRepository
import net.pokedex.core.data.reference.ReferenceDatabase
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.ReferenceRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.data.user.UserDatabase
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.VariantId
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupName
import net.pokedex.core.model.backup.BackupWriter.AutoResult
import net.pokedex.core.model.backup.ImportMode
import net.pokedex.core.model.backup.toRecordInfo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The durability path end to end, against real Room and a real folder: what the JVM tests
 * decide, carried out. Runs on the emulator; never on a phone holding real records.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val io = Dispatchers.IO
    private val now = Instant.parse("2026-09-24T07:15:00Z")
    private val lastWeek = now.minusSeconds(7 * 24 * 3600).toEpochMilli()
    private val tonight = now.minusSeconds(3600).toEpochMilli()

    private lateinit var user: UserDatabase
    private lateinit var reference: ReferenceDatabase
    private lateinit var catches: CatchRepository
    private lateinit var location: BackupLocation
    private lateinit var backups: BackupRepository
    private val backupDir get() = File(context.filesDir, "backups")

    @Before
    fun setUp() {
        backupDir.deleteRecursively()
        user = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()
        reference = Room.databaseBuilder(context, ReferenceDatabase::class.java, REF_DB)
            .createFromAsset(ReferenceDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        val settings = SettingsRepository(user.userSettingsDao(), io)
        val referenceRepository = ReferenceRepository(reference, io)
        catches = CatchRepository(user.catchRecordDao(), io)
        location = BackupLocation(context, settings, io)
        backups = BackupRepository(
            context = context,
            db = user,
            catchRepository = catches,
            settingsRepository = settings,
            referenceRepository = referenceRepository,
            dexRepository = DexRepository(referenceRepository, settings, Dispatchers.Default),
            location = location,
            log = user.backupLogDao(),
            appInfo = AppInfo("test", 1),
            io = io,
        )
    }

    @After
    fun tearDown() {
        user.close()
        reference.close()
        context.deleteDatabase(REF_DB)
        backupDir.deleteRecursively()
    }

    private fun record(variant: String, caught: Boolean, updatedAt: Long, copy: Int = 0) = CatchRecord(
        key = CatchKey(VariantId(variant), copy),
        caught = caught,
        originGameId = GameId("sv-s"),
        caughtAt = if (caught) updatedAt else null,
        notes = "$variant notes",
        favourite = false,
        priority = 0,
        updatedAt = updatedAt,
    )

    /** A backup file holding exactly [records], in the shape the app writes one. */
    private suspend fun fileOf(vararg records: CatchRecord): String =
        BackupCodec.encode(backups.currentFile(now).copy(records = records.map { it.toRecordInfo() }))

    private suspend fun local() = catches.allRecords().associateBy { it.key.toString() }

    private fun files() = backupDir.list().orEmpty().filterNot { it.endsWith(".tmp") }

    @Test
    fun restoreIntoAnEmptyDatabaseWritesEverythingAndNeedsNoSnapshot() = runTest {
        val text = fileOf(record("bulbasaur", true, lastWeek), record("unown", true, lastWeek, copy = 1))

        val result = backups.import(text, ImportMode.MERGE, now) as Outcome.Ok

        assertThat(result.value.written).isEqualTo(2)
        assertThat(result.value.snapshot).isNull()
        assertThat(local().keys).containsExactly("bulbasaur", "unown#1")
        assertThat(local().getValue("unown#1").notes).isEqualTo("unown notes")
        assertThat(files()).isEmpty()
    }

    @Test
    fun anExportRestoresIntoAFreshDatabaseFieldForField() = runTest {
        val original = listOf(record("bulbasaur", true, tonight), record("ivysaur", false, lastWeek))
        catches.merge(original)
        val exported = BackupCodec.encode(backups.currentFile(now))
        catches.replaceAll(emptyList())

        backups.import(exported, ImportMode.REPLACE, now)

        assertThat(catches.allRecords()).containsExactlyElementsIn(original)
    }

    @Test
    fun mergeSnapshotsFirstAndKeepsTheNewerSide() = runTest {
        catches.merge(listOf(record("bulbasaur", true, tonight)))
        val text = fileOf(record("bulbasaur", false, lastWeek), record("ivysaur", true, lastWeek))

        val result = backups.import(text, ImportMode.MERGE, now) as Outcome.Ok

        // Tonight's catch survives last week's file; the new record arrives.
        assertThat(local().getValue("bulbasaur").caught).isTrue()
        assertThat(local().keys).containsExactly("bulbasaur", "ivysaur")
        val snapshot = requireNotNull(result.value.snapshot)
        assertThat(snapshot.kind).isEqualTo(BackupName.Kind.PRE_IMPORT)
        val saved = BackupCodec.decode(File(backupDir, snapshot.fileName).readText()) as Outcome.Ok
        assertThat(saved.value.records.map { it.variantId }).containsExactly("bulbasaur")
    }

    @Test
    fun replaceSwapsTheTableAndTheSnapshotHoldsWhatItRemoved() = runTest {
        catches.merge(listOf(record("bulbasaur", true, tonight), record("venusaur", true, tonight)))
        val text = fileOf(record("ivysaur", true, lastWeek))

        val result = backups.import(text, ImportMode.REPLACE, now) as Outcome.Ok

        assertThat(local().keys).containsExactly("ivysaur")
        val snapshot = File(backupDir, requireNotNull(result.value.snapshot).fileName).readText()
        val saved = BackupCodec.decode(snapshot) as Outcome.Ok
        assertThat(saved.value.records.map { it.variantId }).containsExactly("bulbasaur", "venusaur")
    }

    @Test
    fun aNewerSchemaIsRefusedWithBothNumbersAndTouchesNothing() = runTest {
        catches.merge(listOf(record("bulbasaur", true, tonight)))
        val text = fileOf(record("ivysaur", true, lastWeek)).replace("\"schema\": 1", "\"schema\": 9")

        val result = backups.import(text, ImportMode.REPLACE, now)
        val preview = backups.preview(text)

        val error = (result as Outcome.Err).error as AppError.ImportSchemaTooNew
        assertThat(error.found).isEqualTo(9)
        assertThat(error.supported).isEqualTo(1)
        assertThat((preview as Outcome.Err).error).isEqualTo(error)
        assertThat(local().keys).containsExactly("bulbasaur")
        assertThat(files()).isEmpty()
    }

    @Test
    fun ifTheSnapshotCannotBeWrittenNothingIsImported() = runTest {
        catches.merge(listOf(record("bulbasaur", true, tonight)))
        // Occupy the snapshot's exact name with a non-empty directory, so writing it fails.
        val blocked = BackupName(location.prefix, BackupName.Kind.PRE_IMPORT, now, caughtCount = 1)
        File(backupDir, blocked.fileName).apply { mkdirs() }.resolve("occupied").writeText("x")
        val text = fileOf(record("ivysaur", true, lastWeek))

        val result = backups.import(text, ImportMode.REPLACE, now)

        assertThat(result).isInstanceOf(Outcome.Err::class.java)
        assertThat(local().keys).containsExactly("bulbasaur")
    }

    @Test
    fun thePreviewCountsWhatEachModeWouldDo() = runTest {
        catches.merge(listOf(record("bulbasaur", true, tonight), record("venusaur", true, tonight)))
        val text = fileOf(record("bulbasaur", false, lastWeek), record("ivysaur", true, lastWeek))

        val preview = (backups.preview(text) as Outcome.Ok).value

        assertThat(preview.recordCount).isEqualTo(2)
        assertThat(preview.caughtCount).isEqualTo(1)
        assertThat(preview.mergeWrites).isEqualTo(1)
        assertThat(preview.replaceRemoves).isEqualTo(1)
        assertThat(preview.replaceUncatches).isEqualTo(2)
        assertThat(preview.orphanCount).isEqualTo(0)
    }

    @Test
    fun autoBackupSkipsAnEmptyDatabaseThenWritesOnceAndSkipsUnchanged() = runTest {
        assertThat(backups.autoBackup("test", now)).isEqualTo(
            Outcome.Ok(AutoResult.SkippedEmpty),
        )
        catches.merge(listOf(record("bulbasaur", true, tonight)))

        val first = backups.autoBackup("test", now)
        val second = backups.autoBackup("test", now.plusSeconds(120))

        assertThat((first as Outcome.Ok).value)
            .isInstanceOf(AutoResult.Written::class.java)
        assertThat((second as Outcome.Ok).value)
            .isEqualTo(AutoResult.SkippedUnchanged)
        assertThat(files()).hasSize(1)
    }

    private companion object {
        const val REF_DB = "reference-backup-test.db"
    }
}
