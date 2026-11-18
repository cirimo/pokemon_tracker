package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.ImportMode
import net.pokedex.core.model.backup.ImportPlan
import net.pokedex.core.model.backup.PrepInfo
import net.pokedex.core.model.backup.SettingsInfo
import net.pokedex.core.model.backup.toPrepInfo
import org.junit.Test

/** Prep ticks in a backup (prompt 11): additive, and a missing key says nothing. */
class PrepBackupTest {

    private val sword = GameId("swsh-sw")
    private val charm = PrepTick(sword, "shiny-charm", 1_763_000_000_000)
    private val catcher = PrepTick(sword, "false-swipe-catcher", 1_763_100_000_000)

    private fun file(prep: List<PrepInfo>?) = BackupFile(
        schema = BackupFile.CURRENT_SCHEMA,
        exportedAt = "2026-11-18T10:00:00Z",
        app = AppInfo("0.1.0", 1),
        dataset = DatasetInfo("grouped-balanced", 1, 1),
        settings = SettingsInfo(activePresetId = "grouped-balanced", prep = prep),
        records = emptyList(),
    )

    private fun plan(local: Set<PrepTick>, prep: List<PrepInfo>?, mode: ImportMode) =
        ImportPlan.of(emptyMap(), emptyMap(), file(prep), mode, local)

    @Test
    fun `a file from before prompt 11 leaves the ticks here alone, whatever the mode`() {
        assertThat(plan(setOf(charm), null, ImportMode.MERGE).prep).isNull()
        assertThat(plan(setOf(charm), null, ImportMode.REPLACE).prep).isNull()
    }

    @Test
    fun `replace takes the file's ticks, and an empty list really means none`() {
        assertThat(plan(setOf(charm), listOf(catcher.toPrepInfo()), ImportMode.REPLACE).prep)
            .containsExactly(catcher)
        assertThat(plan(setOf(charm), emptyList(), ImportMode.REPLACE).prep).isEmpty()
    }

    @Test
    fun `merge never unticks, and keeps this device's date where both have one`() {
        val older = charm.copy(doneAt = 1_700_000_000_000)
        val merged = plan(setOf(charm), listOf(older.toPrepInfo(), catcher.toPrepInfo()), ImportMode.MERGE).prep

        assertThat(merged).containsExactly(charm, catcher)
        assertThat(plan(setOf(charm), emptyList(), ImportMode.MERGE).prep).isNull()
    }

    @Test
    fun `ticks survive encode and decode, and an old file decodes with no key`() {
        val decoded = (BackupCodec.decode(BackupCodec.encode(file(listOf(charm.toPrepInfo())))) as Outcome.Ok).value
        assertThat(decoded.settings.prep).containsExactly(charm.toPrepInfo())

        val old = BackupCodec.encode(file(null))
        assertThat(old).doesNotContain("\"prep\"")
        assertThat((BackupCodec.decode(old) as Outcome.Ok).value.settings.prep).isNull()
    }
}
