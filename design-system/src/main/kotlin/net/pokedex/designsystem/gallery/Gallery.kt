package net.pokedex.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The live component gallery -- the review surface for the whole design system.
 *
 * Lives in the library rather than in :app so components and their examples move together.
 * It is only ever REFERENCED from the app's debug source set, so R8 strips it from release.
 * That is how "reachable in debug, absent from release" is met without a separate module
 * and without a flag anyone can get wrong.
 *
 * The two controls at the top are the point of the screen. Theme and font scale are the
 * two axes where a design system quietly breaks, and making them one tap away means the
 * breakage gets found while the component is being built rather than three milestones
 * later on somebody's actual phone.
 */
@Composable
fun DesignSystemGallery(modifier: Modifier = Modifier) {
    var dark by remember { mutableStateOf(true) }
    var largeText by remember { mutableStateOf(false) }

    PokedexTheme(darkTheme = dark) {
        val density = LocalDensity.current
        // Overriding fontScale rather than relying on the device setting, so the 200%
        // requirement can be checked on any device, and captured in a screenshot test.
        val scaled = Density(density.density, if (largeText) LARGE_FONT_SCALE else 1f)

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PokedexTheme.colors.case),
        ) {
            CompositionLocalProvider(LocalDensity provides scaled) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.systemBars),
                    verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceXl),
                ) {
                    GalleryControls(
                        dark = dark,
                        largeText = largeText,
                        onDarkChange = { dark = it },
                        onLargeTextChange = { largeText = it },
                    )
                    GallerySections()
                }
            }
        }
    }
}

private const val LARGE_FONT_SCALE = 2f

@Composable
private fun GalleryControls(
    dark: Boolean,
    largeText: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
) {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Text(
            text = "Design system",
            style = PokedexTheme.text.displayNumerals,
            color = PokedexTheme.colors.onCase,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            GalleryToggle("Dark", dark) { onDarkChange(true) }
            GalleryToggle("Light", !dark) { onDarkChange(false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            GalleryToggle("100% text", !largeText) { onLargeTextChange(false) }
            GalleryToggle("200% text", largeText) { onLargeTextChange(true) }
        }
    }
}

@Composable
private fun GalleryToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Box(
        modifier = Modifier
            .clip(PokedexShapes.badge)
            .background(if (selected) colors.caseSurfaceHigh else colors.case)
            .border(dimens.rimWidth, if (selected) colors.rimFocus else colors.rim, PokedexShapes.badge)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PokedexTheme.text.badgeLabel,
            color = if (selected) colors.onCase else colors.onCaseMuted,
        )
    }
}

@Composable
internal fun GallerySection(title: String, content: @Composable () -> Unit) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
        Text(
            text = title,
            style = PokedexTheme.text.boxTitle,
            color = colors.onCaseMuted,
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
        )
        content()
    }
}

@Preview(name = "Gallery", widthDp = 400, heightDp = 900)
@Composable
private fun GalleryPreview() {
    DesignSystemGallery()
}
