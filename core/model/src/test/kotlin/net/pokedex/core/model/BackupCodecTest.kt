package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.RecordInfo
import net.pokedex.core.model.backup.SettingsInfo
import org.junit.Test

class BackupCodecTest {

    private fun sample() = BackupFile(
        schema = BackupFile.CURRENT_SCHEMA,
        exportedAt = "2026-09-22T18:04:11Z",
        app = AppInfo(versionName = "0.1.0", versionCode = 1),
        dataset = DatasetInfo(presetId = "grouped-balanced", presetVersion = 2, datasetVersion = 1),
        settings = SettingsInfo(activePresetId = "grouped-balanced"),
        records = listOf(
            RecordInfo(
                variantId = "venusaur-f",
                copyIndex = 0,
                caught = true,
                originGameId = "sv-s",
                caughtAt = "2026-03-04T21:10:00Z",
                notes = "sandwich, 412 resets",
            ),
            RecordInfo(variantId = "unown", copyIndex = 1, caught = false),
        ),
    )

    @Test
    fun `round trips without losing a field`() {
        val original = sample()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertThat(decoded).isInstanceOf(Outcome.Ok::class.java)
        assertThat((decoded as Outcome.Ok).value).isEqualTo(original)
    }

    @Test
    fun `refuses a file from a newer schema rather than importing part of it`() {
        val text = BackupCodec.encode(sample()).replace("\"schema\": 1", "\"schema\": 2")

        val result = BackupCodec.decode(text)

        assertThat(result).isInstanceOf(Outcome.Err::class.java)
        val error = (result as Outcome.Err).error
        assertThat(error).isInstanceOf(AppError.ImportSchemaTooNew::class.java)
        error as AppError.ImportSchemaTooNew
        assertThat(error.found).isEqualTo(2)
        assertThat(error.supported).isEqualTo(1)
    }

    @Test
    fun `ignores unknown keys so an additive change from a newer minor still restores`() {
        val text = BackupCodec.encode(sample())
            .replace("\"schema\": 1", "\"schema\": 1,\n  \"someFutureField\": {\"a\": 1}")

        val result = BackupCodec.decode(text)

        assertThat(result).isInstanceOf(Outcome.Ok::class.java)
        assertThat((result as Outcome.Ok).value.records).hasSize(2)
    }

    @Test
    fun `copy index defaults to zero so hand written files can omit it`() {
        val text = """
            {
              "schema": 1,
              "exportedAt": "2026-09-22T18:04:11Z",
              "app": { "versionName": "0.1.0", "versionCode": 1 },
              "dataset": { "presetId": "grouped-balanced", "presetVersion": 2, "datasetVersion": 1 },
              "settings": { "activePresetId": "grouped-balanced" },
              "records": [ { "variantId": "bulbasaur", "caught": true } ]
            }
        """.trimIndent()

        val result = BackupCodec.decode(text)

        assertThat(result).isInstanceOf(Outcome.Ok::class.java)
        assertThat((result as Outcome.Ok).value.records.single().copyIndex).isEqualTo(0)
    }

    @Test
    fun `reports malformed input with a pointer instead of throwing`() {
        val result = BackupCodec.decode("{ not json")
        assertThat(result).isInstanceOf(Outcome.Err::class.java)
        assertThat((result as Outcome.Err).error).isInstanceOf(AppError.ImportMalformed::class.java)
    }

    @Test
    fun `a missing schema field is malformed, not schema zero`() {
        val result = BackupCodec.decode("""{ "records": [] }""")
        val error = (result as Outcome.Err).error as AppError.ImportMalformed
        assertThat(error.pointer).isEqualTo("/schema")
    }
}
