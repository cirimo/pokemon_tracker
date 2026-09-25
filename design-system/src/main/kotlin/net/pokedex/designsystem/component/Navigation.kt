package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The box view's top bar: the search field, with "Close search" before it while searching
 * and one icon action after it while not.
 *
 * The two never show together. Search mode replaces the box view underneath, so the action
 * that belongs to the box view (settings) steps aside for the field's own controls. It lives
 * here rather than in the feature because it is chrome with its own states, and because a
 * screenshot is the only thing that notices when an icon in it stops being visible.
 */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    onCloseSearch: () -> Unit,
    action: TopBarAction,
    modifier: Modifier = Modifier,
    placeholder: String = "Search the dex",
    onFocused: () -> Unit = {},
    onSearch: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            IconButton(onClick = onCloseSearch, modifier = Modifier.size(dimens.touchTargetMin)) {
                Icon(PokedexIcons.Back, contentDescription = "Close search")
            }
        }
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            onSearch = onSearch,
            focusRequester = focusRequester,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocused() },
        )
        if (!searching) {
            IconButton(onClick = action.onClick, modifier = Modifier.size(dimens.touchTargetMin)) {
                Icon(action.icon, contentDescription = action.description)
            }
        }
    }
}

/** One icon button's worth of action: what it shows, what TalkBack says, what it does. */
class TopBarAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/**
 * Previous and next, with where you are between them.
 *
 * A pager already swipes. This row is for the two people a swipe does not serve: someone
 * using TalkBack, for whom a horizontal swipe means "next element", and someone holding the
 * phone one-handed who wants a single step without a thumb-length drag. It is also the only
 * visible sign that a screen pages at all, which is why slot detail shows it whenever it
 * can browse.
 *
 * Each button names where it goes ("Next slot, Charmander"), not just its direction. At an
 * end the button disables rather than disappearing, so the label does not shift under a
 * thumb. [announce] makes the label a polite live region, for screens where arriving
 * somewhere new is the point and TalkBack should say where.
 */
@Composable
fun Stepper(
    label: String,
    previousDescription: String,
    nextDescription: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
) {
    // M3 1.4 draws an IconButton at 40dp and widens only its touch area; sizing it to the
    // minimum target makes the button itself 48dp, which is what TalkBack's focus box shows.
    val target = Modifier.size(PokedexTheme.dimens.touchTargetMin)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = hasPrevious, modifier = target) {
            Icon(PokedexIcons.PreviousBox, contentDescription = previousDescription)
        }
        Text(
            text = label,
            style = PokedexTheme.text.dexNumber,
            color = PokedexTheme.colors.onCaseMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        )
        IconButton(onClick = onNext, enabled = hasNext, modifier = target) {
            Icon(PokedexIcons.NextBox, contentDescription = nextDescription)
        }
    }
}

@Composable
internal fun NavigationSamples() {
    val dimens = PokedexTheme.dimens
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    Column(modifier = Modifier.background(PokedexTheme.colors.case)) {
        SearchTopBar(
            query = query,
            onQueryChange = { query = it },
            searching = searching,
            onCloseSearch = { searching = false },
            action = TopBarAction(PokedexIcons.Settings, "Settings and backups") {},
            placeholder = "Name or dex number",
            onFocused = { searching = true },
        )
        SearchTopBar(
            query = "Unown",
            onQueryChange = {},
            searching = true,
            onCloseSearch = {},
            action = TopBarAction(PokedexIcons.Settings, "Settings and backups") {},
        )
        Stepper(
            label = "Box 3 of 52",
            previousDescription = "Previous box, Kanto 2",
            nextDescription = "Next box, Kanto 4",
            hasPrevious = true,
            hasNext = true,
            onPrevious = {},
            onNext = {},
            modifier = Modifier.padding(horizontal = dimens.spaceSm),
        )
        Stepper(
            label = "Kanto 1 · 1 of 30",
            previousDescription = "Previous slot",
            nextDescription = "Next slot, Ivysaur",
            hasPrevious = false,
            hasNext = true,
            onPrevious = {},
            onNext = {},
            modifier = Modifier.padding(horizontal = dimens.spaceSm),
        )
    }
}

@Preview(name = "Navigation dark", widthDp = 380)
@Composable
private fun NavigationDarkPreview() {
    PokedexTheme(darkTheme = true) { NavigationSamples() }
}

@Preview(name = "Navigation light", widthDp = 380)
@Composable
private fun NavigationLightPreview() {
    PokedexTheme(darkTheme = false) { NavigationSamples() }
}

@Preview(name = "Navigation 200%", widthDp = 380, fontScale = 2f)
@Composable
private fun NavigationLargeFontPreview() {
    PokedexTheme(darkTheme = true) { NavigationSamples() }
}
