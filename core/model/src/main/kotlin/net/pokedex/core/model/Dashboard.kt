package net.pokedex.core.model

/**
 * Where the collection stands, in the cuts a living-dex hunter works by. Derived on read,
 * like every other count in this app: nothing here is stored.
 *
 * Four views, chosen over the many possible charts because each one answers "what do I do
 * next" rather than "what does my history look like":
 * - the whole dex, and how much of what is left cannot be shiny yet;
 * - boxes by region, and the ones closest to finished, because finishing a box is the unit
 *   of progress in HOME;
 * - what is still needed per game, because a hunt happens in one game at a time;
 * - recent catches, which is also where a wrong prefilled game gets noticed.
 */
data class Dashboard(
    val overall: Progress,
    /** Uncaught slots whose variant has no released shiny. Part of [overall]'s total, and unreachable. */
    val noShinyYet: Int,
    val regions: List<RegionProgress>,
    /** Unfinished boxes with the fewest slots left, fewest first. */
    val closest: List<BoxProgress>,
    val neededByGame: List<GameNeed>,
    val recent: List<RecentCatch>,
    /** Caught records the active preset has no slot for. Kept and shown, never dropped (ADR 0001). */
    val orphans: List<CatchRecord>,
)

data class BoxProgress(val boxIndex: Int, val name: String, val progress: Progress)

data class RegionProgress(val name: String, val progress: Progress, val boxes: List<BoxProgress>)

/** Slots still needed that can be caught shiny in [gameSet]. A slot counts in every game that has it. */
data class GameNeed(val gameSet: GameSet, val needed: Int)

data class RecentCatch(val entry: DexEntry, val record: CatchRecord)

fun dashboardOf(
    dex: Dex,
    records: Map<CatchKey, CatchRecord>,
    closestLimit: Int = CLOSEST_LIMIT,
    recentLimit: Int = RECENT_LIMIT,
): Dashboard {
    val slots = dex.entries.map { it.slot }
    val byBox = progressByBox(slots, records)
    val boxes = dex.boxes.map { BoxProgress(it.boxIndex, it.name, byBox[it.boxIndex] ?: Progress.ZERO) }
    val needed = dex.entries.filter { statusOf(it, records) == SlotStatus.Needed }

    return Dashboard(
        overall = progressOf(slots, records),
        noShinyYet = dex.entries.count { statusOf(it, records) == SlotStatus.NoShinyExists },
        regions = regionsOf(boxes),
        closest = boxes
            .filter { !it.progress.isComplete && it.progress.caught > 0 }
            .sortedWith(compareBy({ it.progress.remaining }, { it.boxIndex }))
            .take(closestLimit),
        neededByGame = dex.gameSets.map { set -> GameNeed(set, needed.count { set.id in it.shinyGameSets }) },
        recent = records.values
            .filter { it.caught && it.caughtAt != null }
            .sortedByDescending { it.caughtAt }
            .mapNotNull { record -> dex.entry(record.key)?.let { RecentCatch(it, record) } }
            .take(recentLimit),
        orphans = orphanedRecords(slots, records),
    )
}

/**
 * Boxes grouped into regions by their names, in preset order.
 *
 * The preset names a region's boxes "Kanto 1" to "Kanto 6", and parks that region's form
 * boxes after them ("Cap Pikachu", "Unown Dex", "Vivillon Patt."). So "Word N" starts or
 * continues the group Word, and anything else joins the group before it. A single word
 * with no number ("Hisui") is a region of its own.
 *
 * Derived from names because the dataset carries no region per box. If upstream renames a
 * box, the grouping follows the name, and the worst case is a box listed under its
 * neighbour. Its counts are still exact.
 */
fun regionsOf(boxes: List<BoxProgress>): List<RegionProgress> {
    val groups = LinkedHashMap<String, MutableList<BoxProgress>>()
    var current: String? = null
    for (box in boxes) {
        val numbered = NUMBERED.matchEntire(box.name)?.groupValues?.get(1)
        val single = box.name.takeIf { SINGLE.matches(it) }
        current = numbered ?: single ?: current ?: box.name
        groups.getOrPut(current) { mutableListOf() } += box
    }
    return groups.map { (name, members) ->
        RegionProgress(name, members.fold(Progress.ZERO) { acc, b -> acc + b.progress }, members)
    }
}

private val NUMBERED = Regex("""^(\p{L}+) \d+.*$""")
private val SINGLE = Regex("""^\p{L}+$""")

private const val CLOSEST_LIMIT = 5
private const val RECENT_LIMIT = 20
