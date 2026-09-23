package net.pokedex.feature.dex.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The frame both detail screens share: a back button, a title, and one vertical scroll.
 *
 * One scroll for the whole screen rather than fixed regions, because at 200% text the hero,
 * the toggle and the game list together are taller than a phone, and a detail screen that
 * clips its own content is worse than one that scrolls.
 *
 * This is feature layout, not a component: a Row with a back button. If a second feature
 * needs the same bar (Settings in M3 will), it moves to `:design-system` then, with a gallery
 * entry, rather than being copied.
 */
@Composable
internal fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier
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
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = dimens.spaceLg, end = dimens.spaceLg, bottom = dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
            content = content,
        )
    }
}

/** A titled block. The title is a TalkBack heading, so a long detail page can be skimmed by section. */
@Composable
internal fun DetailSection(
    title: String,
    body: String? = null,
    content: @Composable () -> Unit = {},
) {
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
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
internal fun ChipFlow(content: @Composable () -> Unit) {
    val dimens = PokedexTheme.dimens
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        content()
    }
}
