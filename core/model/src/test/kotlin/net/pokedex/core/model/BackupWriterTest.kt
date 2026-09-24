package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.BackupFolder
import net.pokedex.core.model.backup.BackupName
import net.pokedex.core.model.backup.BackupName.Kind
import net.pokedex.core.model.backup.BackupRetention
import net.pokedex.core.model.backup.BackupWriter
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.FileBackupFolder
import net.pokedex.core.model.backup.RecordInfo
import net.pokedex.core.model.backup.SettingsInfo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class BackupWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val t0 = Instant.parse("2026-09-24T07:15:00Z")
    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60)

    private fun file(caught: Int, uncaught: Int = 0) = BackupFile(
        schema = BackupFile.CURRENT_SCHEMA,
        exportedAt = "2026-09-24T07:15:00Z",
        app = AppInfo("0.1.0", 1),
        dataset = DatasetInfo("grouped-balanced", 1, 1),
        settings = SettingsInfo("grouped-balanced"),
        records = List(caught) { RecordInfo(variantId = "v$it", caught = true) } +
            List(uncaught) { RecordInfo(variantId = "u$it", caught = false) },
    )

    private fun folder() = FileBackupFolder(tmp.newFolder())
    private val writer = BackupWriter("pokedex")

    @Test
    fun `names round trip and carry the caught count`() {
        val name = BackupName("pokedex-debug", Kind.PRE_IMPORT, t0, 412)

        assertThat(name.fileName).isEqualTo("pokedex-debug-preimport-20260924T071500Z-c412.json")
        assertThat(BackupName.parse(name.fileName)).isEqualTo(name)
    }

    @Test
    fun `names a provider made up are not ours`() {
        assertThat(BackupName.parse("pokedex-auto-20260924T071500Z-c412 (1).json")).isNull()
        assertThat(BackupName.parse("notes.json")).isNull()
    }

    @Test
    fun `an empty database is never auto-backed up`() {
        val folder = folder()

        assertThat(writer.writeAuto(folder, file(caught = 0), t0, keep = 10))
            .isEqualTo(BackupWriter.AutoResult.SkippedEmpty)
        assertThat(folder.list()).isEmpty()
    }

    @Test
    fun `the same records are not written twice`() {
        val folder = folder()
        writer.writeAuto(folder, file(caught = 3), at(0), keep = 10)

        val again = writer.writeAuto(folder, file(caught = 3).copy(exportedAt = "later"), at(5), keep = 10)

        assertThat(again).isEqualTo(BackupWriter.AutoResult.SkippedUnchanged)
        assertThat(folder.list()).hasSize(1)
    }

    @Test
    fun `the rolling set keeps the newest n`() {
        val folder = folder()
        for (i in 1..5) writer.writeAuto(folder, file(caught = i), at(i.toLong()), keep = 3)

        val left = writer.restorable(folder).map { it.caughtCount }

        assertThat(left).containsExactly(5, 4, 3).inOrder()
    }

    @Test
    fun `a wiped database backing itself up cannot rotate out the last good copy`() {
        val folder = folder()
        writer.writeAuto(folder, file(caught = 400), at(0), keep = 3)
        // Something wiped the catches but left uncaught rows, so the database is not empty.
        for (i in 1..6) writer.writeAuto(folder, file(caught = 0, uncaught = i), at(i.toLong()), keep = 3)

        val left = writer.restorable(folder)

        assertThat(left.map { it.caughtCount }).contains(400)
        assertThat(left).hasSize(4)
    }

    @Test
    fun `pre-import snapshots have their own pool and survive a run of auto backups`() {
        val folder = folder()
        writer.writeSnapshot(folder, file(caught = 50), at(0))
        for (i in 1..12) writer.writeAuto(folder, file(caught = i), at(i.toLong()), keep = 2)

        val kinds = writer.restorable(folder).groupBy { it.kind }

        assertThat(kinds[Kind.PRE_IMPORT]!!.single().caughtCount).isEqualTo(50)
        // Two newest plus the high-water mark, which is the newest file with the most caught.
        assertThat(kinds[Kind.AUTO]!!.map { it.caughtCount }).containsExactly(12, 11)
    }

    @Test
    fun `only the newest three snapshots are kept`() {
        val folder = folder()
        for (i in 1..5) writer.writeSnapshot(folder, file(caught = i), at(i.toLong()))

        assertThat(writer.restorable(folder).map { it.caughtCount }).containsExactly(5, 4, 3).inOrder()
    }

    @Test
    fun `another build's files are listed for restore but never pruned`() {
        val folder = folder()
        val release = BackupWriter("pokedex")
        val debug = BackupWriter("pokedex-debug")
        release.writeAuto(folder, file(caught = 900), at(0), keep = 1)
        for (i in 1..4) debug.writeAuto(folder, file(caught = i), at(i.toLong()), keep = 1)

        val left = debug.restorable(folder)

        assertThat(left.filter { it.prefix == "pokedex" }.map { it.caughtCount }).containsExactly(900)
        assertThat(left.first().prefix).isEqualTo("pokedex-debug")
    }

    @Test
    fun `a write that does not read back is removed and reported`() {
        val folder = object : BackupFolder by FileBackupFolder(tmp.newFolder()) {
            override fun read(name: String) = "truncated"
        }

        val result = runCatching { writer.writeAuto(folder, file(caught = 1), t0, keep = 10) }

        assertThat(result.isFailure).isTrue()
        assertThat(folder.list()).isEmpty()
    }

    @Test
    fun `keep is clamped so the file just written is never the one deleted`() {
        val names = listOf(BackupName("pokedex", Kind.AUTO, t0, 1))

        assertThat(BackupRetention.toDelete(names, keepAuto = 0, keepPreImport = 3)).isEmpty()
    }
}
