package net.pokedex.core.model

import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * What the search screen is asking for.
 *
 * Categories combine with AND; the values inside one category combine with OR. "Needed,
 * in Scarlet/Violet or Legends Z-A, Fire" means needed AND (SV OR Z-A) AND Fire. That is
 * the one composition rule, and it is the one people already expect from every shop filter
 * they have used.
 *
 * Serializable so the whole query survives process death as one string in a
 * SavedStateHandle, without the screen knowing its shape.
 */
@Serializable
data class DexFilter(
    val query: String = "",
    val caught: CaughtFilter = CaughtFilter.All,
    /** [GameSet.id]s. Empty means no game restriction. */
    val gameSets: Set<String> = emptySet(),
    /** Dataset type ids, e.g. "fire". Empty means no type restriction. */
    val types: Set<String> = emptySet(),
    val noShiny: NoShinyFilter = NoShinyFilter.Any,
    /**
     * Added in prompt 7, with a default, so a filter encoded before it -- in a saved state or
     * a browse route -- still decodes. Null means no farm restriction.
     */
    val farm: FarmFilter? = null,
) {
    /** True when anything other than the text query narrows the result. */
    val hasRefinements: Boolean
        get() = caught != CaughtFilter.All || gameSets.isNotEmpty() || types.isNotEmpty() ||
            noShiny != NoShinyFilter.Any || farm != null

    val refinementCount: Int
        get() = (if (caught != CaughtFilter.All) 1 else 0) + gameSets.size + types.size +
            (if (noShiny != NoShinyFilter.Any) 1 else 0) + (if (farm != null) 1 else 0)
}

/**
 * "Farm in": the slots that belong to one of my games, see [FarmPlan]. One game rather than
 * any-of, unlike [DexFilter.gameSets]: "only here" in two games at once asks nothing useful.
 */
@Serializable
data class FarmFilter(val gameId: String, val scope: FarmScope = FarmScope.HereFirst)

@Serializable
enum class CaughtFilter { All, Needed, Caught }

/** The 39 variants no game has released a shiny of. */
@Serializable
enum class NoShinyFilter { Any, Hide, Only }

/**
 * The slots matching [filter], best match first.
 *
 * Ranking only applies when there is a text query, and only coarsely: the exact name, then
 * a name starting with what you typed, then a word inside the name starting with it, then a
 * bare substring. Within a rank, preset order. Typing "mew" should put Mew above Mewtwo above
 * Shaymin's form list, and "chu" should still find every Pikachu in box order.
 *
 * Pure and allocation-light on purpose: it runs on every keystroke over 1394 entries, and
 * the budget in docs/architecture.md is 5 ms.
 *
 * @param always a slot that passes the refinements regardless of its record: the one a
 *   browsing detail is showing (see [browseKeys]). It must still match the text, which no
 *   record can change, so it lands exactly where it ranked before.
 * @param farm where each slot is farmed, for [DexFilter.farm]. Built by the caller when my
 *   games or their order change, never here: a keystroke must not walk my games per slot.
 *   Without one, a farm filter matches nothing rather than everything.
 */
fun searchDex(
    dex: Dex,
    records: Map<CatchKey, CatchRecord>,
    filter: DexFilter,
    always: CatchKey? = null,
    farm: FarmPlan? = null,
): List<DexEntry> {
    val query = parseQuery(filter.query)
    val matches = dex.entries.filter { entry ->
        (entry.key == always || matchesRefinements(entry, records, filter, farm)) && query.matches(entry)
    }
    return if (query is Query.Text) {
        matches.sortedWith(compareBy({ query.rank(it) }, { it.order }))
    } else {
        matches
    }
}

internal fun matchesRefinements(
    entry: DexEntry,
    records: Map<CatchKey, CatchRecord>,
    filter: DexFilter,
    farm: FarmPlan?,
): Boolean {
    val status = statusOf(entry, records)
    val caughtOk = when (filter.caught) {
        CaughtFilter.All -> true
        CaughtFilter.Caught -> status == SlotStatus.Caught
        // A slot with no shiny in existence is still a slot you need. Whether to see it is
        // the no-shiny filter's question, not this one's.
        CaughtFilter.Needed -> status != SlotStatus.Caught
    }
    val gameOk = filter.gameSets.isEmpty() || filter.gameSets.any { it in entry.shinyGameSets }
    val typeOk = filter.types.isEmpty() ||
        entry.variant.type1 in filter.types ||
        entry.variant.type2 in filter.types
    val noShinyOk = when (filter.noShiny) {
        NoShinyFilter.Any -> true
        NoShinyFilter.Hide -> entry.variant.shinyReleased
        NoShinyFilter.Only -> !entry.variant.shinyReleased
    }
    return caughtOk && gameOk && typeOk && noShinyOk && matchesFarm(entry, filter.farm, farm)
}

private fun matchesFarm(entry: DexEntry, filter: FarmFilter?, plan: FarmPlan?): Boolean =
    filter == null || plan?.matches(entry, GameId(filter.gameId), filter.scope) == true

/**
 * Lowercase, accents stripped, punctuation to spaces, whitespace collapsed.
 *
 * Flabébé is the reason this exists: nobody types the accents, and a search that cannot find
 * three of the seven duplicated variants from a phone keyboard is broken.
 */
fun normalizeForSearch(text: String): String {
    // Decomposing is only needed for the handful of names with a non-ASCII letter, and it
    // is the expensive step, so the common case never pays for it. This runs 2788 times
    // while the dex assembles on a cold start, which is why it is a loop and not a chain
    // of regexes.
    val source = if (text.any { it.code > ASCII_MAX }) Normalizer.normalize(text, Normalizer.Form.NFD) else text
    val out = StringBuilder(source.length)
    var pendingSpace = false
    for (c in source) {
        val folded = when (c) {
            in 'a'..'z', in '0'..'9' -> c
            in 'A'..'Z' -> c + ('a' - 'A')
            else -> null
        }
        when {
            folded != null -> {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(folded)
            }
            // A combining mark left behind by decomposition is part of the letter before
            // it, not a word break: "Flabébé" must become "flabebe", not "flabe be".
            Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
            else -> pendingSpace = true
        }
    }
    return out.toString()
}

private const val ASCII_MAX = 127

internal sealed interface Query {
    fun matches(entry: DexEntry): Boolean

    data object Everything : Query {
        override fun matches(entry: DexEntry) = true
    }

    /**
     * A dex number matches exactly. "25" is Pikachu, not 250-259 as well: a prefix match
     * would bury the one result you meant under ten you did not.
     */
    data class DexNumber(val number: Int) : Query {
        override fun matches(entry: DexEntry) = entry.variant.dexNum == number
    }

    /** Every token must appear somewhere in the name, form or id. */
    data class Text(val normalized: String, val tokens: List<String>) : Query {
        override fun matches(entry: DexEntry) = tokens.all { it in entry.searchText }

        fun rank(entry: DexEntry): Int = when {
            entry.nameText == normalized -> RANK_EXACT
            entry.nameText.startsWith(normalized) -> RANK_NAME_PREFIX
            entry.nameText.split(' ').any { it.startsWith(tokens.first()) } -> RANK_WORD_PREFIX
            else -> RANK_SUBSTRING
        }
    }
}

private const val RANK_EXACT = 0
private const val RANK_NAME_PREFIX = 1
private const val RANK_WORD_PREFIX = 2
private const val RANK_SUBSTRING = 3

internal fun parseQuery(raw: String): Query {
    val normalized = normalizeForSearch(raw)
    if (normalized.isEmpty()) return Query.Everything
    // "25", "#25", "025" and "No. 25" all mean the dex number -- the last is how the
    // detail screen itself prints it, so it is what someone copying from it will type.
    val number = normalized.removePrefix("no ").takeIf { it.all(Char::isDigit) }?.toIntOrNull()
    if (number != null) return Query.DexNumber(number)
    return Query.Text(normalized, normalized.split(' '))
}
