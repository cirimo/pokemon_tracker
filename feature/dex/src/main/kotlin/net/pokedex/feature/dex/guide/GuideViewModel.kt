package net.pokedex.feature.dex.guide

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pokedex.core.data.di.DefaultDispatcher
import net.pokedex.core.data.repository.CurationRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.GuideBookRepository
import net.pokedex.core.data.repository.PrepRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.Curation
import net.pokedex.core.model.Dex
import net.pokedex.core.model.Dlc
import net.pokedex.core.model.Game
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Guide
import net.pokedex.core.model.GuideBook
import net.pokedex.core.model.GuideSection
import net.pokedex.core.model.MethodOdds
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.PrepTick
import net.pokedex.core.model.hasShinyCharm
import net.pokedex.core.model.leadingOdds
import net.pokedex.core.model.prepListOf
import net.pokedex.feature.dex.GuideRoute
import net.pokedex.feature.dex.adviceLabel
import net.pokedex.feature.dex.game.PrepRowUi
import net.pokedex.feature.dex.game.prepRow
import net.pokedex.feature.dex.oddsLabel
import net.pokedex.feature.dex.partLabel
import net.pokedex.feature.dex.sectionTitle
import net.pokedex.feature.dex.splitStep
import java.text.NumberFormat
import javax.inject.Inject

@Immutable
data class GuideUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val gameId: String = "",
    val title: String = "",
    val target: TargetUi? = null,
    val figures: List<FigureUi> = emptyList(),
    val oneInSave: Boolean = false,
    val requirements: List<PrepRowUi> = emptyList(),
    val builds: List<BuildUi> = emptyList(),
    val advice: List<AdviceUi> = emptyList(),
    val sections: List<StepSectionUi> = emptyList(),
    val mistakes: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
)

/** The hunt the guide is for, as the screen's one lit card. */
@Immutable
data class TargetUi(
    val lead: CatchKey?,
    val name: String,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val lines: List<String>,
)

@Immutable
data class FigureUi(val value: String, val label: String)

@Immutable
data class BuildUi(
    val tick: PrepRowUi,
    val purpose: String,
    val role: String?,
    val parts: List<PartUi>,
    /** How to get it, in the order you would walk it. */
    val farm: List<StepUi>,
)

@Immutable
data class PartUi(val label: String, val value: String, val why: String)

@Immutable
data class AdviceUi(val label: String, val subject: String, val why: String)

@Immutable
data class StepSectionUi(val title: String, val steps: List<StepUi>)

@Immutable
data class StepUi(val title: String, val body: String?)

sealed interface GuideEvent {
    data class TogglePrep(val refId: String, val done: Boolean) : GuideEvent
    data object Retry : GuideEvent
}

/**
 * One guide in one game: the odds as figures, what it needs (ticked per game, ADR 0016), the
 * builds and how to farm them, rental advice, and the steps, the procedure's first.
 */
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    private val curations: CurationRepository,
    private val guideBooks: GuideBookRepository,
    private val prepRepository: PrepRepository,
    savedState: SavedStateHandle,
    @DefaultDispatcher default: CoroutineDispatcher,
) : ViewModel() {

    private val route = savedState.toRoute<GuideRoute>()
    private val game = GameId(route.gameId)
    private val loaded = MutableStateFlow<Outcome<Loaded>?>(null)

    val state: StateFlow<GuideUiState> = combine(loaded, prepRepository.observe()) { loaded, ticks ->
        when (loaded) {
            null -> GuideUiState()
            is Outcome.Err -> GuideUiState(loading = false, error = loaded.error)
            is Outcome.Ok -> build(loaded.value, ticks)
        }
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GuideUiState())

    init {
        load()
    }

    fun onEvent(event: GuideEvent) {
        when (event) {
            is GuideEvent.TogglePrep -> viewModelScope.launch { prepRepository.setDone(game, event.refId, event.done) }
            GuideEvent.Retry -> load()
        }
    }

    private class Loaded(val dex: Dex, val curation: Curation, val book: GuideBook)

    private fun load() {
        viewModelScope.launch {
            loaded.value = null
            val dex = dexRepository.dex()
            val curation = curations.curation()
            val book = guideBooks.guideBook()
            loaded.value = when {
                dex is Outcome.Err -> dex
                curation is Outcome.Err -> curation
                book is Outcome.Err -> book
                else -> Outcome.Ok(
                    Loaded((dex as Outcome.Ok).value, (curation as Outcome.Ok).value, (book as Outcome.Ok).value),
                )
            }
        }
    }

    private fun build(loaded: Loaded, ticks: Set<PrepTick>): GuideUiState {
        val book = loaded.book
        val game = loaded.dex.games.firstOrNull { it.id == this.game }
        val guide = book.guide(route.guideId)
        if (game == null || guide == null) {
            return GuideUiState(
                loading = false,
                error = AppError.Unexpected("no guide ${route.guideId} for ${route.gameId}")
            )
        }
        val prep = prepListOf(book, game.gameSet, game.id, ticks).associateBy { it.refId }
        val odds = oddsOf(loaded, guide, game)
        val steps = book.steps(guide)
        val location = { id: String? -> book.location(guide.gameSet, id)?.name }
        return GuideUiState(
            loading = false,
            gameId = game.id.value,
            title = guide.title,
            target = target(loaded, guide, game),
            figures = odds?.let { figures(it, hasShinyCharm(ticks, game.id)) }.orEmpty(),
            oneInSave = guide.oneInSave,
            requirements = book.requirements(guide).mapNotNull { (r, _) -> prep[r.id]?.let(::prepRow) },
            builds = book.builds(guide).mapNotNull { (b, role) ->
                val tick = prep[b.id]?.let(::prepRow) ?: return@mapNotNull null
                BuildUi(
                    tick = tick,
                    purpose = b.purpose,
                    role = role,
                    parts = b.parts.map { PartUi(partLabel(it.kind), it.value, it.why) },
                    farm = book.farmChain(b).map { StepUi(it.detail, location(it.locationId)) },
                )
            },
            advice = guide.advice.map { AdviceUi(adviceLabel(it.kind), it.subject, it.why) },
            sections = GuideSection.entries.filter { it != GuideSection.Mistake }.mapNotNull { section ->
                steps.filter { it.section == section }.takeIf { it.isNotEmpty() }?.let { rows ->
                    StepSectionUi(
                        title = sectionTitle(section),
                        steps = rows.map { step ->
                            val (title, rest) = splitStep(step.text)
                            StepUi(
                                title,
                                listOfNotNull(rest, location(step.locationId)).joinToString("\n").ifEmpty { null }
                            )
                        },
                    )
                }
            },
            mistakes = steps.filter { it.section == GuideSection.Mistake }.map { it.text },
            sources = sourcesOf(book, guide),
        )
    }

    /** The guide's encounter in this game, so an exclusion on it (the charm on a giant) is honoured. */
    private fun oddsOf(loaded: Loaded, guide: Guide, game: Game): MethodOdds? {
        val encounter = guide.targets.asSequence()
            .flatMap { loaded.curation.encounters(it, game.id) }
            .firstOrNull { it.methodId == guide.methodId && !it.shinyLocked }
        return encounter?.let { loaded.curation.odds(it) } ?: loaded.curation.odds(game.id, guide.methodId)
    }

    private fun target(loaded: Loaded, guide: Guide, game: Game): TargetUi? {
        val dex = loaded.dex
        val entries = dex.entries.filter { it.variant.id in guide.targets }.sortedBy { it.order }
        val lead = entries.firstOrNull() ?: return null
        val variant = lead.variant
        val species = dex.species(variant.dexNum)?.name
        val method = loaded.curation.method(guide.methodId)?.name ?: guide.methodId
        val dlc = loaded.book.dlcOf(guide).joinToString(" and ") { dlcName(it) }
        return TargetUi(
            lead = lead.key,
            // Several forms (Tornadus Incarnate and Therian) read as the species; one reads as itself.
            name = if (entries.map { it.variant.id }.distinct().size > 1) {
                species ?: variant.displayName
            } else {
                variant.displayName
            },
            type1 = variant.type1,
            type2 = variant.type2,
            spriteFile = variant.spriteFile,
            lines = listOfNotNull(
                "$method in ${game.name}",
                guide.summary,
                dlc.takeIf { it.isNotEmpty() }?.let { "Needs $it" },
            ),
        )
    }

    /**
     * Always both figures where the charm applies (ADR 0016), the one I have leading; then the
     * attempts for even odds at the leading figure, so "1 in 100" is not read as "by the 100th".
     */
    private fun figures(odds: MethodOdds, charm: Boolean): List<FigureUi> {
        val withCharm = odds.withCharm
        val rates = if (withCharm == null) {
            listOf(FigureUi(oddsLabel(odds.plain), "the Shiny Charm does not apply"))
        } else {
            val plain = FigureUi(oddsLabel(odds.plain), "without the charm")
            val charmed = FigureUi(oddsLabel(withCharm), "with the Shiny Charm")
            if (charm) listOf(charmed, plain) else listOf(plain, charmed)
        }
        val attempts = leadingOdds(odds, charm).attemptsFor(EVEN_ODDS)
        return rates + FigureUi(NumberFormat.getIntegerInstance().format(attempts), "attempts for even odds")
    }

    private fun sourcesOf(book: GuideBook, guide: Guide): List<String> {
        val builds = book.builds(guide).map { it.first }
        return buildList {
            book.steps(guide).forEach { add(it.sourceUrl) }
            book.requirements(guide).forEach { (r, _) -> add(r.sourceUrl) }
            builds.forEach { b ->
                b.parts.forEach { add(it.sourceUrl) }
                book.farmChain(b).forEach { add(it.sourceUrl) }
            }
            guide.advice.forEach { add(it.sourceUrl) }
        }.distinct()
    }

    private fun dlcName(dlc: Dlc): String = when (dlc) {
        Dlc.IsleOfArmor -> "The Isle of Armor"
        Dlc.CrownTundra -> "The Crown Tundra"
    }

    private companion object {
        const val EVEN_ODDS = 0.5
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
