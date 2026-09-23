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
) {
    /** True when anything other than the text query narrows the result. */
    val hasRefinements: Boolean
        get() = caught != CaughtFilter.All || gameSets.isNotEmpty() || types.isNotEmpty() ||
            noShiny != NoShinyFilter.Any

    val refinementCount: Int
        get() = (if (caught != CaughtFilter.All) 1 else 0) + gameSets.size + types.size +
            (if (noShiny != NoShinyFilter.Any) 1 else 0)
}

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
 */
fun searchDex(dex: Dex, records: Map<CatchKey, CatchRecord>, filter: DexFilter): List<DexEntry> {
    val query = parseQuery(filter.query)
    val matches = dex.entries.filter { entry ->
        matchesRefinements(entry, records, filter) && query.matches(entry)
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
    return caughtOk && gameOk && typeOk && noShinyOk
}

/**
 * Lowercase, accents stripped, punctuation to spaces, whitespace collapsed.
 *
 * Flabébé is the reason this exists: nobody types the accents, and a search that cannot find
 * three of the seven duplicated variants from a phone keyboard is broken.
 */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(WHITESPACE, " ")

private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val WHITESPACE = Regex("\\s+")

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
