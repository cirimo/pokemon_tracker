package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

        val plan = ImportPlan.of(emptyMap(), emptyMap(), file, ImportMode.MERGE)

        assertThat(plan.snapshotFirst).isFalse()
        assertThat(plan.write.map { it.key.variantId.value }).containsExactly("bulbasaur", "ivysaur")
    }

    @Test
    fun `any import over existing records snapshots first, in either mode`() {
        val local = Fixtures.records(Fixtures.caught("bulbasaur"))
        val file = fileOf(Fixtures.caught("ivysaur"))

        assertThat(ImportPlan.of(local, emptyMap(), file, ImportMode.MERGE).snapshotFirst).isTrue()
        assertThat(ImportPlan.of(local, emptyMap(), file, ImportMode.REPLACE).snapshotFirst).isTrue()
    }

    @Test
    fun `replace clears first and writes the whole file, merge does neither`() {
        val local = Fixtures.records(Fixtures.caught("bulbasaur").copy(updatedAt = tonight))
        val file = fileOf(Fixtures.caught("bulbasaur").copy(caught = false, updatedAt = lastWeek))

        val merge = ImportPlan.of(local, emptyMap(), file, ImportMode.MERGE)
        val replace = ImportPlan.of(local, emptyMap(), file, ImportMode.REPLACE)

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
        val plan = ImportPlan.of(emptyMap(), emptyMap(), file, ImportMode.REPLACE)

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

        val plan = ImportPlan.of(emptyMap(), emptyMap(), decoded, ImportMode.MERGE)

        assertThat(plan.write).containsExactlyElementsIn(records)
    }

    private fun withGames(file: BackupFile, vararg games: String) =
        file.copy(settings = file.settings.copy(myGames = games.toList()))

    private fun games(vararg ids: String) = ids.associate { GameId(it) to 0 }

    @Test
    fun `a file from before my games leaves the games here alone, in either mode`() {
        val file = fileOf(Fixtures.caught("bulbasaur"))

        assertThat(ImportPlan.of(emptyMap(), games("la"), file, ImportMode.MERGE).myGames).isNull()
        assertThat(ImportPlan.of(emptyMap(), games("la"), file, ImportMode.REPLACE).myGames).isNull()
    }

    @Test
    fun `replace takes the file's games and merge keeps both`() {
        val file = withGames(fileOf(), "sv-s", "sv-v")

        assertThat(ImportPlan.of(emptyMap(), games("la"), file, ImportMode.REPLACE).myGames?.keys)
            .containsExactlyElementsIn(games("sv-s", "sv-v").keys)
        assertThat(ImportPlan.of(emptyMap(), games("la"), file, ImportMode.MERGE).myGames?.keys)
            .containsExactlyElementsIn(games("la", "sv-s", "sv-v").keys)
    }

    @Test
    fun `games the same on both sides are not rewritten`() {
        val file = withGames(fileOf(), "la")

        assertThat(ImportPlan.of(emptyMap(), games("la"), file, ImportMode.MERGE).myGames).isNull()
    }

    @Test
    fun `games alone are worth a snapshot, since a replace can change them`() {
        val file = withGames(fileOf(), "sv-s")

        assertThat(ImportPlan.of(emptyMap(), games("la"), file, ImportMode.REPLACE).snapshotFirst).isTrue()
    }

    @Test
    fun `my games survive encode and decode`() {
        val file = withGames(fileOf(Fixtures.caught("bulbasaur")), "la", "sv-s")

        val decoded = (BackupCodec.decode(BackupCodec.encode(file)) as Outcome.Ok).value

        assertThat(decoded.settings.myGames).containsExactly("la", "sv-s").inOrder()
    }

    private fun withOrder(file: BackupFile, vararg order: String) =
        file.copy(settings = file.settings.copy(gameOrder = order.toList()))

    private val la = GameId("la")
    private val lza = GameId("lza")
    private val violet = GameId("sv-v")
    private val scarlet = GameId("sv-s")

    @Test
    fun `a file from before the order restores the games and keeps the order here`() {
        // Every file written before prompt 7: games sorted, no order.
        val file = withGames(fileOf(), "la", "lza", "sv-v")
        val local = mapOf(lza to 0, la to 1)

        val merged = ImportPlan.of(emptyMap(), local, file, ImportMode.MERGE).myGames
        val replaced = ImportPlan.of(emptyMap(), local, file, ImportMode.REPLACE).myGames

        assertThat(merged).containsExactly(lza, 0, la, 1, violet, 2)
        assertThat(replaced).containsExactly(lza, 0, la, 1, violet, 2)
    }

    @Test
    fun `replace takes the file's order`() {
        val file = withOrder(withGames(fileOf(), "la", "lza", "sv-v"), "lza", "la", "sv-v")

        val plan = ImportPlan.of(emptyMap(), mapOf(violet to 0, la to 1), file, ImportMode.REPLACE)

        assertThat(plan.myGames).containsExactly(lza, 0, la, 1, violet, 2)
    }

    @Test
    fun `merge keeps the order here and puts new games after it in the file's order`() {
        val file = withOrder(withGames(fileOf(), "la", "sv-s", "sv-v"), "sv-v", "la", "sv-s")

        val plan = ImportPlan.of(emptyMap(), mapOf(la to 0, lza to 1), file, ImportMode.MERGE)

        assertThat(plan.myGames).containsExactly(la, 0, lza, 1, violet, 2, scarlet, 3)
    }

    @Test
    fun `an order naming a game the file does not own is ignored`() {
        val file = withOrder(withGames(fileOf(), "la"), "sv-v", "la")

        val plan = ImportPlan.of(emptyMap(), emptyMap(), file, ImportMode.REPLACE)

        assertThat(plan.myGames).containsExactly(la, 0)
    }

    @Test
    fun `the same games in the same order are not rewritten`() {
        val file = withOrder(withGames(fileOf(), "la", "lza"), "lza", "la")

        assertThat(ImportPlan.of(emptyMap(), mapOf(lza to 0, la to 1), file, ImportMode.REPLACE).myGames).isNull()
    }

    @Test
    fun `the order survives encode and decode, and an older build's shape still reads it`() {
        val file = withOrder(withGames(fileOf(), "la", "lza"), "lza", "la")
        val text = BackupCodec.encode(file)

        val decoded = (BackupCodec.decode(text) as Outcome.Ok).value

        assertThat(decoded.settings.gameOrder).containsExactly("lza", "la").inOrder()
        // What a build from before the order does with this file: it does not know the key,
        // and ignoring unknown keys is what lets it restore the records and the games anyway.
        val settings = (Json.parseToJsonElement(text) as JsonObject).getValue("settings").toString()
        val older = Json { ignoreUnknownKeys = true }.decodeFromString(OlderSettings.serializer(), settings)
        assertThat(older.myGames).containsExactly("la", "lza").inOrder()
    }

    @Serializable
    private data class OlderSettings(val activePresetId: String, val myGames: List<String> = emptyList())
}
