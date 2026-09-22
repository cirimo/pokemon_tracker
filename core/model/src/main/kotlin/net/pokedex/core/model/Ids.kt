package net.pokedex.core.model

/**
 * The identity of a single Pokemon a slot can demand: the upstream PokePC variant id.
 *
 * Examples: "bulbasaur", "venusaur-f", "growlithe-hisui", "alcremie-ruby-cream-berry".
 * This is the exact token the preset JSON stores in its slot arrays, which is why it is
 * the join key rather than a surrogate integer.
 */
@JvmInline
value class VariantId(val value: String) {
    override fun toString(): String = value
}

/**
 * The identity of a catch record. THE most load-bearing type in the app.
 *
 * A catch is a fact about a Pokemon you own, not about a position in a box. Keying on
 * (variantId, copyIndex) means the record survives the preset being reordered,
 * extended, or swapped for a different preset entirely. Keying on (box, slot) would
 * silently corrupt every record the first time upstream inserts a form.
 *
 * [copyIndex] exists because grouped-balanced demands seven Pokemon twice: unown,
 * vivillon, flabebe, floette, florges, furfrou and alcremie each appear in their
 * generation box AND in a dedicated form box. A living dex needs two of each, so the
 * second occurrence is copyIndex 1. Everything else is copyIndex 0.
 *
 * See docs/adr/0001-slot-identity.md.
 */
data class CatchKey(
    val variantId: VariantId,
    val copyIndex: Int,
) {
    init {
        require(copyIndex >= 0) { "copyIndex must be non-negative, was $copyIndex" }
    }

    override fun toString(): String =
        if (copyIndex == 0) variantId.value else "${variantId.value}#$copyIndex"
}

@JvmInline
value class GameId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class PresetId(val value: String) {
    override fun toString(): String = value
}
