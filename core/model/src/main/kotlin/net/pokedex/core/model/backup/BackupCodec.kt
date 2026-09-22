package net.pokedex.core.model.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Outcome

/**
 * Serialises and parses [BackupFile].
 *
 * Pure and Android-free on purpose: this is the code path that must not lose records,
 * so it is unit-tested on the JVM rather than through an instrumentation test.
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * Parses a backup, refusing anything we cannot faithfully represent.
     *
     * The version check happens before deserialisation of the body so that a
     * future-schema file is rejected cleanly rather than half-parsed.
     */
    fun decode(text: String): Outcome<BackupFile> {
        val schema = peekSchema(text)
            ?: return Outcome.Err(
                AppError.ImportMalformed(pointer = "/schema", reason = "missing or not an integer"),
            )

        if (schema > BackupFile.CURRENT_SCHEMA) {
            return Outcome.Err(
                AppError.ImportSchemaTooNew(
                    found = schema,
                    supported = BackupFile.CURRENT_SCHEMA,
                ),
            )
        }
        if (schema < BackupFile.OLDEST_SUPPORTED_SCHEMA) {
            return Outcome.Err(
                AppError.ImportMalformed(
                    pointer = "/schema",
                    reason = "schema $schema is older than the oldest supported " +
                        "(${BackupFile.OLDEST_SUPPORTED_SCHEMA})",
                ),
            )
        }

        return try {
            Outcome.Ok(upgrade(json.decodeFromString(BackupFile.serializer(), text)))
        } catch (e: SerializationException) {
            Outcome.Err(AppError.ImportMalformed(pointer = "/", reason = e.message ?: "malformed JSON"))
        } catch (e: IllegalArgumentException) {
            Outcome.Err(AppError.ImportMalformed(pointer = "/", reason = e.message ?: "invalid value"))
        }
    }

    /**
     * Reads just the schema number without committing to the rest of the shape.
     *
     * Uses the JSON parser rather than a regex so a "schema" string inside a note
     * cannot be mistaken for the real field.
     */
    private fun peekSchema(text: String): Int? = try {
        val element = json.parseToJsonElement(text)
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
        val prim = obj["schema"] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        prim.content.toIntOrNull()
    } catch (_: SerializationException) {
        null
    }

    /**
     * Applies the chain of upgrades from an older schema to CURRENT_SCHEMA.
     *
     * Empty today because CURRENT_SCHEMA is 1. When schema 2 arrives, add a pure
     * function here and a fixture under core/model/src/test/resources.
     */
    private fun upgrade(file: BackupFile): BackupFile = file
}
