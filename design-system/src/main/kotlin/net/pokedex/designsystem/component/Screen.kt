package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The frame every pushed screen shares: a back button, a title, and one vertical scroll.
 *
 * One scroll for the whole screen rather than fixed regions, because at 200% text a
 * detail hero, a toggle and a game list together are taller than a phone, and a screen that
 * clips its own content is worse than one that scrolls.
 *
 * It began as slot detail's layout in `:feature:dex`. It moved here when Settings became
 * the second screen to need it, which is the rule in `docs/design-usage.md`: shared by two
 * screens means it is a component, not something to copy.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * False for a screen whose body is its own lazy list. A list of hundreds of rows inside
     * a scrolling Column composes every row at once; the list has to own the scrolling.
     */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PokedexTheme.colors.case)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(PokedexIcons.Back, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PokedexTheme.colors.onCase,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            actions()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scrollable) {
                        Modifier.verticalScroll(rememberScrollState()).padding(bottom = dimens.spaceXl)
                    } else {
                        Modifier
                    },
                )
                .padding(start = dimens.spaceLg, end = dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
            content = content,
        )
    }
}

/**
 * A titled block inside a [ScreenScaffold]. The title is a TalkBack heading, so a long
 * screen can be skimmed by section.
 */
@Composable
fun ScreenSection(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dimens = PokedexTheme.dimens
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
        Text(
            text = title,
            style = PokedexTheme.text.boxTitle,
            color = PokedexTheme.colors.onCase,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PokedexTheme.colors.onCaseMuted)
        }
        content()
    }
}

@Composable
internal fun ScreenSample() {
    ScreenScaffold(title = "Settings", onBack = {}) {
        ScreenSection(title = "Backups", body = "Written after changes, and once a day.") {
            SettingRow(title = "Back up now", summary = "Last backup 5 minutes ago", onClick = {})
        }
    }
}

@Preview(name = "Screen dark", widthDp = 380, heightDp = 300)
@Composable
private fun ScreenDarkPreview() {
    PokedexTheme(darkTheme = true) { ScreenSample() }
}

@Preview(name = "Screen light", widthDp = 380, heightDp = 300)
@Composable
private fun ScreenLightPreview() {
    PokedexTheme(darkTheme = false) { ScreenSample() }
}

@Preview(name = "Screen 200%", widthDp = 380, heightDp = 400, fontScale = 2f)
@Composable
private fun ScreenLargeFontPreview() {
    PokedexTheme(darkTheme = true) { ScreenSample() }
}
