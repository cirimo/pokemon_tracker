package net.pokedex.feature.dex.boxes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.pokedex.core.model.CatchKey
import net.pokedex.designsystem.component.BoxPager
import net.pokedex.designsystem.component.BoxSummary
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.LoadingState
import net.pokedex.designsystem.component.PokedexBottomSheet
import net.pokedex.designsystem.component.ProgressBar
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.SearchField
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.slotOrigin

/**
 * [jumpRequests] is the box a detail screen asked to be shown, or null. It arrives on the
 * NavBackStackEntry's own SavedStateHandle, which is NOT the handle Hilt gives the
 * ViewModel -- so it is forwarded here as an event and cleared via [onJumpForwarded].
 */
@Composable
internal fun BoxesDestination(
    onOpenSlot: (CatchKey) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenHunt: () -> Unit,
    jumpRequests: StateFlow<Int?>,
    onJumpForwarded: () -> Unit,
    neededRequests: StateFlow<String?>,
    onNeededForwarded: () -> Unit,
    viewModel: BoxesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nextHunt by viewModel.nextHunt.collectAsStateWithLifecycle()
    val jump by jumpRequests.collectAsStateWithLifecycle()
    val needed by neededRequests.collectAsStateWithLifecycle()
    LaunchedEffect(jump) {
        jump?.let {
            viewModel.onEvent(BoxesEvent.JumpRequested(it))
            onJumpForwarded()
        }
    }
    LaunchedEffect(needed) {
        needed?.let {
            viewModel.onEvent(BoxesEvent.ShowNeededIn(it))
            onNeededForwarded()
        }
    }
    BoxesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSlot = onOpenSlot,
        onOpenSettings = onOpenSettings,
        onOpenProgress = onOpenProgress,
        nextHunt = nextHunt,
        onOpenHunt = onOpenHunt,
    )
}

/**
 * The start destination: the boxes, with search one tap away at the top.
 *
 * The start destination is the pager rather than search because the pager is the mirror
 * of HOME and answers "what do I still need" without being asked; the search field sits
 * above it so that the other reason to open the app, "find me this one", costs one tap.
 * It opens on the box you were last on, because mid-hunt that is usually the one you are
 * filling.
 */
@Composable
internal fun BoxesScreen(
    state: BoxesUiState,
    onEvent: (BoxesEvent) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
    nextHunt: NextHuntUi? = null,
    onOpenHunt: () -> Unit = {},
) {
    val dimens = PokedexTheme.dimens
    val focusManager = LocalFocusManager.current
    // The field's text lives here, not in the ViewModel's flow: a text field fed through an
    // asynchronous state round trip drops characters when you type fast. The ViewModel gets
    // every change as an event and owns the filter; this owns the glyphs on screen.
    var query by rememberSaveable { mutableStateOf(state.search.filter.query) }
    val searchFocus = remember { FocusRequester() }

    // Cleared when search closes -- but never while loading. After process death the first
    // state is the loading one, whose search is inactive, and clearing on it would wipe the
    // restored text while the restored filter kept narrowing the results underneath it.
    LaunchedEffect(state.loading, state.search.active) {
        if (!state.loading && !state.search.active) query = ""
    }
    BackHandler(enabled = state.search.active) {
        focusManager.clearFocus()
        onEvent(BoxesEvent.CloseSearch)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PokedexTheme.colors.case)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.search.active) {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    onEvent(BoxesEvent.CloseSearch)
                }) {
                    Icon(PokedexIcons.Back, contentDescription = "Close search")
                }
            }
            SearchField(
                value = query,
                onValueChange = {
                    query = it
                    onEvent(BoxesEvent.QueryChanged(it))
                },
                placeholder = "Name or dex number",
                onSearch = { focusManager.clearFocus() },
                focusRequester = searchFocus,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) onEvent(BoxesEvent.OpenSearch) },
            )
            // Not in search mode: there the same gear glyph is the filter button.
            if (!state.search.active) {
                IconButton(onClick = onOpenSettings) {
                    Icon(PokedexIcons.Settings, contentDescription = "Settings and backups")
                }
            }
        }

        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                actionLabel = null,
                modifier = Modifier.padding(dimens.spaceLg),
            )
            state.loading -> LoadingState(Modifier.padding(dimens.spaceLg))
            else -> LoadedContent(
                state = state,
                onEvent = onEvent,
                onOpenSlot = { key ->
                    focusManager.clearFocus()
                    onOpenSlot(key)
                },
                onOpenProgress = onOpenProgress,
                nextHunt = nextHunt,
                onOpenHunt = onOpenHunt,
            )
        }
    }
}

/**
 * Owns the pager state above the search/box switch, on purpose: if it lived inside the box
 * branch, opening search would discard it and closing search would land on the cold-start
 * box instead of the one you were on.
 */
@Composable
private fun LoadedContent(
    state: BoxesUiState,
    onEvent: (BoxesEvent) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenProgress: () -> Unit,
    nextHunt: NextHuntUi?,
    onOpenHunt: () -> Unit,
) {
    // rememberPagerState is saveable: after process death it restores the page it was on and
    // ignores startBox. startBox only decides a cold start.
    val pager = rememberPagerState(initialPage = state.startBox) { state.pages.size }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { onEvent(BoxesEvent.BoxSettled(it)) }
    }

    if (state.search.active) {
        SearchContent(search = state.search, onEvent = onEvent, onOpenResult = onOpenSlot)
    } else {
        BoxContent(
            state = state,
            pager = pager,
            onEvent = onEvent,
            onOpenSlot = onOpenSlot,
            onOpenProgress = onOpenProgress,
            nextHunt = nextHunt,
            onOpenHunt = onOpenHunt,
        )
    }
}

@Composable
private fun BoxContent(
    state: BoxesUiState,
    pager: PagerState,
    onEvent: (BoxesEvent) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenProgress: () -> Unit,
    nextHunt: NextHuntUi?,
    onOpenHunt: () -> Unit,
) {
    val dimens = PokedexTheme.dimens
    val scope = rememberCoroutineScope()
    val motion = PokedexTheme.motion
    var overviewOpen by rememberSaveable { mutableStateOf(false) }
    // The one tile that carries the shared-element origin: the one being opened. Every
    // sprite carrying it registered thirty entries with the app-wide transition scope per
    // page, and adding and removing those made each swipe's settle frame late
    // (docs/architecture.md §8). Only the tapped slot has a matching destination, so the
    // other twenty-nine never animated anyway. Saveable, because the grid leaves
    // composition while the detail is open and the return transition needs it back.
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Here and not above the search switch: PagerState.scrollToPage waits for the pager's
    // first layout, so it can only complete where the pager is actually on screen. The
    // ViewModel closes search when a jump arrives, which is what brings this into being.
    LaunchedEffect(state.jumpToBox) {
        val target = state.jumpToBox ?: return@LaunchedEffect
        pager.scrollToPage(target)
        onEvent(BoxesEvent.JumpHandled)
    }

    // One vertical scroll for the whole screen. At 100% text everything fits a phone and it
    // never moves; at 200% the header and the box header grow and the box would otherwise be
    // cut off at the bottom. The grid inside is not lazy, so nesting it here is safe -- see
    // BoxGrid's KDoc for the crash that rule came from.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        OverallHeader(state = state, onOpenOverview = { overviewOpen = true }, onOpenProgress = onOpenProgress)

        BoxPager(
            pages = state.pages,
            state = pager,
            onSlotClick = { box, position ->
                val item = state.pages.getOrNull(box)?.slots?.getOrNull(position)
                val key = item?.let { state.slots.key(it.key) }
                if (item != null && key != null) {
                    // Set in the same event as the navigation, so the origin and the
                    // destination enter composition in the same frame and match.
                    openedKey = item.key
                    onOpenSlot(key)
                }
            },
            sprite = { item, rendering ->
                val file = state.slots.sprite(item.key)
                if (file != null) {
                    val origin = if (item.key == openedKey) Modifier.slotOrigin(item.key) else Modifier
                    DexSprite(file, rendering, origin)
                }
            },
        )

        BoxStepper(
            pager = pager,
            names = state.pages.map { it.name },
            onPrevious = { scope.launch { pager.step(-1, motion.interactive()) } },
            onNext = { scope.launch { pager.step(1, motion.interactive()) } },
        )

        // Below the pager, outside it: nothing here is per page or per tile.
        if (nextHunt != null) {
            SettingRow(
                title = "Hunt next",
                summary = nextHunt.summary,
                onClick = onOpenHunt,
                modifier = Modifier.padding(horizontal = dimens.spaceLg),
            )
        }
    }

    if (overviewOpen) {
        BoxOverviewSheet(
            state = state,
            onDismiss = { overviewOpen = false },
            onPick = { index ->
                overviewOpen = false
                scope.launch { pager.scrollToPage(index) }
            },
        )
    }
}

@Composable
private fun OverallHeader(state: BoxesUiState, onOpenOverview: () -> Unit, onOpenProgress: () -> Unit) {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The headline number opens the dashboards: it is the thing you tap when you want
            // to know more about it. The chevron says it can be tapped.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Open progress", role = Role.Button, onClick = onOpenProgress),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressReadout(caught = state.overall.caught, total = state.overall.total, label = "shiny")
                Icon(PokedexIcons.NextBox, contentDescription = null, tint = PokedexTheme.colors.onCaseMuted)
            }
            OutlinedButton(onClick = onOpenOverview) {
                Text("All boxes", style = PokedexTheme.text.badgeLabel)
            }
        }
        ProgressBar(caught = state.overall.caught, total = state.overall.total, label = "Whole dex")
    }
}

private suspend fun PagerState.step(by: Int, spec: FiniteAnimationSpec<Float>) =
    animateScrollToPage(currentPage + by, animationSpec = spec)

/**
 * Previous and next, with where you are between them.
 *
 * The pager already swipes. This row is for the two people it does not serve: someone using
 * TalkBack, for whom a horizontal swipe means "next element", and someone holding the phone
 * one-handed who wants to step a single box without a thumb-length drag.
 */
@Composable
private fun BoxStepper(
    pager: PagerState,
    names: List<String>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val dimens = PokedexTheme.dimens
    val colors = PokedexTheme.colors
    val current = pager.currentPage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = dimens.spaceSm, end = dimens.spaceSm, bottom = dimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = current > 0) {
            Icon(
                PokedexIcons.PreviousBox,
                contentDescription = names.getOrNull(current - 1)?.let { "Previous box, $it" } ?: "Previous box",
            )
        }
        Text(
            text = "Box ${current + 1} of ${names.size}",
            style = PokedexTheme.text.dexNumber,
            color = colors.onCaseMuted,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = current < names.lastIndex) {
            Icon(
                PokedexIcons.NextBox,
                contentDescription = names.getOrNull(current + 1)?.let { "Next box, $it" } ?: "Next box",
            )
        }
    }
}

/**
 * All 52 boxes as rows, for jumping. No tiles: see BoxSummary's KDoc for why an overview of
 * miniature grids would be the 1394-tile screen the pager exists to avoid.
 */
// PokedexBottomSheet's signature carries M3's experimental SheetState, so every caller opts in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxOverviewSheet(state: BoxesUiState, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val dimens = PokedexTheme.dimens
    val colors = PokedexTheme.colors
    PokedexBottomSheet(onDismissRequest = onDismiss, title = "All boxes") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            item {
                Column(
                    modifier = Modifier.padding(bottom = dimens.spaceSm),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                ) {
                    ProgressReadout(
                        caught = state.overall.caught,
                        total = state.overall.total,
                        label = "shiny",
                        compact = true,
                    )
                    if (state.noShinyRemaining > 0) {
                        // Counted, never hidden: PokePC counts these slots, so the total does too.
                        // Saying so is what stops the last few percent reading as failure.
                        Text(
                            text = "${state.noShinyRemaining} of the slots still needed have no shiny " +
                                "released in any game yet.",
                            style = PokedexTheme.text.dexNumber,
                            color = colors.onCaseMuted,
                        )
                    }
                }
            }
            items(state.pages, key = { it.index }) { page ->
                BoxSummary(
                    name = page.name,
                    caught = page.caught,
                    total = page.filled,
                    onClick = { onPick(page.index) },
                )
            }
        }
    }
}
