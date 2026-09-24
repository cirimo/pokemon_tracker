package net.pokedex.feature.dex

import net.pokedex.core.model.MethodOdds
import net.pokedex.core.model.Odds
import net.pokedex.core.model.OutOfReach
import net.pokedex.core.model.Priority
import net.pokedex.core.model.Standing
import java.net.URI
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The hunt planner's words, in one place, so the hunt list and slot detail say a thing the
 * same way. Odds in particular: "1 in 128" on one screen and "0.78%" on the other would read
 * as two different claims.
 */

/** "1 in 1,366". Rounded to a whole encounter; the exact figure is in the model. */
internal fun oddsLabel(odds: Odds): String =
    "1 in " + NumberFormat.getIntegerInstance().format(odds.oneIn.roundToInt())

/**
 * The best case, what it assumes, and what you get without it:
 * "1 in 128 with Research perfect and Shiny Charm (1 in 158 without)". Just the figure when
 * nothing needs setting up.
 */
internal fun oddsSentence(odds: MethodOdds): String {
    if (odds.assumed.isEmpty()) return oddsLabel(odds.best)
    val setup = odds.assumed.map { it.label }.let { labels ->
        if (labels.size == 1) labels.single() else labels.dropLast(1).joinToString(", ") + " and " + labels.last()
    }
    return "${oddsLabel(odds.best)} with $setup (${oddsLabel(odds.plain)} without)"
}

internal fun standingSentence(standing: Standing, lockReason: String?): String = when (standing) {
    Standing.Shiny -> "Can be shiny here."
    Standing.ShinyLocked -> "Shiny-locked." + (lockReason?.let { " $it" } ?: "")
    Standing.EventOnly -> "Only from an event distribution."
    Standing.TransferOnly -> "Not caught here. It can only arrive through HOME."
    Standing.NoShinyYet -> "In this game, but no shiny has been released."
    Standing.Absent -> "Not in this game."
}

internal val Priority.label: String
    get() = when (this) {
        Priority.Want -> "Want"
        Priority.Normal -> "Normal"
        Priority.Later -> "Later"
    }

internal fun outOfReachSentence(reason: OutOfReach.Reason, games: List<String>): String = when (reason) {
    OutOfReach.Reason.OtherGames -> "Shiny in " + games.joinToString(", ")
    OutOfReach.Reason.ShinyLocked -> "Shiny-locked in every game that has it"
    OutOfReach.Reason.EventOnly -> "Event only"
    OutOfReach.Reason.TransferOnly -> "Not in any Switch game; arrives through HOME"
}

/** "bulbapedia.bulbagarden.net": enough to know where a claim came from, short enough to show. */
internal fun sourceLabel(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
