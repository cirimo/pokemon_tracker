package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.ImportMode
import net.pokedex.core.model.backup.ImportPlan
import net.pokedex.core.model.backup.RestorePreview
import net.pokedex.core.model.backup.SettingsInfo
import net.pokedex.core.model.backup.toRecordInfo
import org.junit.Test
import java.time.Instant

class RestoreTest {

    private val lastWeek = 1_700_000_000_000L
    private val tonight = lastWeek + 7 * 24 * 3_600_000L

    private fun fileOf(vararg records: CatchRecord) = BackupFile(
        schema = BackupFile.CURRENT_SCHEMA,
        exportedAt = "2026-09-24T07:15:00Z",
        app = AppInfo("0.3.0", 3),
        dataset = DatasetInfo("grouped-balanced", 1, 1),
        settings = SettingsInfo("grouped-balanced"),
        records = records.map { it.toRecordInfo() },
    )

    private val preset = setOf("bulbasaur", "ivysaur", "venusaur").map { CatchKey(VariantId(it), 0) }.toSet()

    @Test
    fun `restoring into an empty database writes everything and takes no snapshot`() {
        val file = fileOf(Fixtures.caught("bulbasaur"), Fixtures.caught("ivysaur"))

        val plan = ImportPlan.of(emptyMap(), file, ImportMode.MERGE)

        assertThat(plan.snapshotFirst).isFalse()
        assertThat(plan.write.map { it.key.variantId.value }).containsExactly("bulbasaur", "ivysaur")
    }

    @Test
    fun `any import over existing records snapshots first, in either mode`() {
        val local = Fixtures.records(Fixtures.caught("bulbasaur"))
        val file = fileOf(Fixtures.caught("ivysaur"))

        assertThat(ImportPlan.of(local, file, ImportMode.MERGE).snapshotFirst).isTrue()
        assertThat(ImportPlan.of(local, file, ImportMode.REPLACE).snapshotFirst).isTrue()
    }

    @Test
    fun `replace clears first and writes the whole file, merge does neither`() {
        val local = Fixtures.records(Fixtures.caught("bulbasaur").copy(updatedAt = tonight))
        val file = fileOf(Fixtures.caught("bulbasaur").copy(caught = false, updatedAt = lastWeek))

        val merge = ImportPlan.of(local, file, ImportMode.MERGE)
        val replace = ImportPlan.of(local, file, ImportMode.REPLACE)

        assertThat(merge.clearFirst).isFalse()
        assertThat(merge.write).isEmpty()
        assertThat(replace.clearFirst).isTrue()
        assertThat(replace.write.single().caught).isFalse()
    }

    @Test
    fun `the preview shows what a replace would cost before it happens`() {
        val local = Fixtures.records(
            Fixtures.caught("bulbasaur").copy(updatedAt = tonight),
            Fixtures.caught("venusaur").copy(updatedAt = tonight),
        )
        val file = fileOf(
            Fixtures.caught("bulbasaur").copy(caught = false, updatedAt = lastWeek),
            Fixtures.caught("ivysaur").copy(updatedAt = lastWeek),
        )

        val preview = RestorePreview.of(file, local, preset)

        assertThat(preview.recordCount).isEqualTo(2)
        assertThat(preview.caughtCount).isEqualTo(1)
        assertThat(preview.mergeWrites).isEqualTo(1)
        assertThat(preview.replaceRemoves).isEqualTo(1)
        assertThat(preview.replaceUncatches).isEqualTo(2)
        assertThat(preview.localIsEmpty).isFalse()
        assertThat(preview.exportedAt).isEqualTo(Instant.parse("2026-09-24T07:15:00Z"))
    }

    @Test
    fun `records the preset has no slot for are counted, not dropped`() {
        val file = fileOf(Fixtures.caught("bulbasaur"), Fixtures.caught("pikachu-cosplay"))

        val preview = RestorePreview.of(file, emptyMap(), preset)
        val plan = ImportPlan.of(emptyMap(), file, ImportMode.REPLACE)

        assertThat(preview.orphanCount).isEqualTo(1)
        assertThat(plan.write.map { it.key.variantId.value }).contains("pikachu-cosplay")
    }

    @Test
    fun `a file survives encode, decode and restore into an empty database unchanged`() {
        val records = listOf(
            Fixtures.caught("bulbasaur").copy(notes = "sandwich, 412 resets"),
            Fixtures.caught("unown", copy = 1).copy(caught = false, originGameId = null, caughtAt = null),
        )
        val decoded = (BackupCodec.decode(BackupCodec.encode(fileOf(*records.toTypedArray()))) as Outcome.Ok).value

        val plan = ImportPlan.of(emptyMap(), decoded, ImportMode.MERGE)

        assertThat(plan.write).containsExactlyElementsIn(records)
    }
}
