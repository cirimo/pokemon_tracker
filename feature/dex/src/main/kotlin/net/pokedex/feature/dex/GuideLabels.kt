package net.pokedex.feature.dex

import net.pokedex.core.model.AdviceKind
import net.pokedex.core.model.BuildPartKind
import net.pokedex.core.model.FarmStanding
import net.pokedex.core.model.GuideSection
import net.pokedex.core.model.PrepItem

/**
 * The guide layer's words, shared by the game page and the guide screen so a tick, a section
 * or a standing reads the same on both.
 */

internal fun sectionTitle(section: GuideSection): String = when (section) {
    GuideSection.Prepare -> "Before you start"
    GuideSection.Setup -> "Set up once"
    GuideSection.Loop -> "The loop"
    GuideSection.Check -> "How to tell it is shiny"
    GuideSection.After -> "When it is shiny"
    GuideSection.Mistake -> "Mistakes that lose it"
}

internal fun partLabel(kind: BuildPartKind): String = when (kind) {
    BuildPartKind.Ability -> "Ability"
    BuildPartKind.Nature -> "Nature"
    BuildPartKind.Move -> "Move"
    BuildPartKind.Item -> "Held item"
    BuildPartKind.Level -> "Level"
    BuildPartKind.Evs -> "EVs"
}

internal fun adviceLabel(kind: AdviceKind): String = when (kind) {
    AdviceKind.Take -> "Take"
    AdviceKind.Avoid -> "Avoid"
    AdviceKind.Note -> "Note"
}

/** The word in a prep row's cell; the tick box beside it is the second signal, never the only one. */
internal fun prepValue(item: PrepItem): String = when {
    item.done -> "Done"
    item.optional -> "Optional"
    else -> "To do"
}

internal fun usedBySentence(count: Int): String? = when (count) {
    0 -> null
    1 -> "One guide asks for it."
    else -> "$count guides ask for it."
}

internal fun standingLine(standing: FarmStanding, game: String): String = when (standing) {
    FarmStanding.OnlyHere -> "Of your games, only $game has it shiny"
    FarmStanding.HereFirst -> "$game is first in your farm order for it"
    FarmStanding.EarlierElsewhere -> "An earlier game in your order has it too"
}

/**
 * A guide step as a title and a body: the first sentence leads, the rest explains. Curated
 * steps are written as sentences, not headings, and a whole paragraph set as a title is a wall.
 */
internal fun splitStep(text: String): Pair<String, String?> {
    val end = text.indexOf(". ")
    if (end < 0) return text to null
    return text.substring(0, end + 1) to text.substring(end + 2).trim().ifEmpty { null }
}
