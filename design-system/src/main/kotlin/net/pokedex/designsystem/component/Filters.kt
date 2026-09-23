package net.pokedex.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * Search, filter and sort: the quality-of-life load.
 *
 * These three carry the difference between a dex you browse and a dex you *use*. The app
 * has 1394 slots and the honest answer to "what should I hunt next" usually starts with
 * narrowing them, so these get the same care as the grid.
 *
 * None of them uses gold. Filter state is a selection, not an achievement.
 */

/**
 * A filter chip.
 *
 * Selected state is carried by a filled container *and* a tick, never by colour alone.
 * The tick also means the chip changes width when selected, which is a third signal --
 * you can see which chips are on from peripheral vision without reading them.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** e.g. "412". Shown after the label; tabular so a row of chips does not jitter. */
    count: Int? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens

    val container by animateColorAsState(
        targetValue = if (selected) colors.caseSurfaceHigh else colors.case,
        animationSpec = PokedexTheme.motion.stateChange(),
        label = "chipContainer",
    )
    val outline = when {
        !enabled -> colors.rim
        selected -> colors.rimFocus
        else -> colors.rim
    }
    val content = if (enabled) colors.onCase else colors.onCaseMuted

    Row(
        modifier = modifier
            .heightIn(min = dimens.touchTargetMin)
            .clip(PokedexShapes.badge)
            .background(container)
            .border(dimens.rimWidth, outline, PokedexShapes.badge)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onSelectedChange(!selected) },
            )
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = dimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = PokedexIcons.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(dimens.iconSm),
            )
        }
        Text(label, style = PokedexTheme.text.badgeLabel, color = content)
        if (count != null) {
            Text(count.toString(), style = PokedexTheme.text.dexNumber, color = colors.onCaseMuted)
        }
    }
}

@Composable
fun FilterChipRow(
    filters: List<FilterOption>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = PokedexTheme.dimens
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.spaceLg),
    ) {
        items(filters, key = { it.id }) { option ->
            FilterChip(
                label = option.label,
                selected = option.selected,
                enabled = option.enabled,
                count = option.count,
                onSelectedChange = { onToggle(option.id) },
            )
        }
    }
}

/** A filter, with no domain type in sight. */
@androidx.compose.runtime.Immutable
data class FilterOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val count: Int? = null,
    val enabled: Boolean = true,
)

/**
 * The search field.
 *
 * Built on M3's `OutlinedTextField` rather than from scratch, because text editing is the
 * one place where reimplementing the platform loses you selection handles, autofill, IME
 * behaviour and accessibility that took years to get right. What we override is only the
 * colouring and the shape.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search the dex",
    onSearch: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val keyboard = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        singleLine = true,
        shape = PokedexShapes.field,
        textStyle = PokedexTheme.text.statValue,
        placeholder = { Text(placeholder, style = PokedexTheme.text.dexNumber) },
        leadingIcon = {
            Icon(
                imageVector = PokedexIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = PokedexIcons.ClearInput,
                        // Named, because an unlabelled X in a search field is a guess.
                        contentDescription = "Clear search",
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                onSearch?.invoke()
            },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.rimFocus,
            unfocusedBorderColor = colors.rim,
            focusedContainerColor = colors.caseSurface,
            unfocusedContainerColor = colors.caseSurface,
            focusedTextColor = colors.onCase,
            unfocusedTextColor = colors.onCase,
            cursorColor = colors.accentGraphic,
            focusedLeadingIconColor = colors.onCase,
            unfocusedLeadingIconColor = colors.onCaseMuted,
            focusedTrailingIconColor = colors.onCase,
            unfocusedTrailingIconColor = colors.onCaseMuted,
            focusedPlaceholderColor = colors.onCaseMuted,
            unfocusedPlaceholderColor = colors.onCaseMuted,
        ),
    )
}

/**
 * Sort order.
 *
 * A segmented row rather than a dropdown: there are four sort orders, you switch between
 * them constantly, and a menu would cost two taps every time to save space this screen
 * has. At 200% font scale the labels wrap to two lines rather than truncating -- the row
 * is allowed to get taller, because sort is the thing a low-vision user most needs to read.
 */
@Composable
fun SortControl(
    options: List<SortOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PokedexShapes.field)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.field)
            .semantics { }
            .padding(dimens.spaceXxs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
    ) {
        options.forEach { option ->
            val selected = option.id == selectedId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SEGMENT_HEIGHT)
                    .clip(PokedexShapes.badge)
                    .background(if (selected) colors.caseSurfaceHigh else colors.case)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.id) },
                    )
                    .padding(horizontal = dimens.spaceSm, vertical = dimens.spaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = PokedexTheme.text.badgeLabel,
                    color = if (selected) colors.onCase else colors.onCaseMuted,
                )
            }
        }
    }
}

private val SEGMENT_HEIGHT = 40.dp

@androidx.compose.runtime.Immutable
data class SortOption(val id: String, val label: String)

@Composable
private fun FilterSamples() {
    val dimens = PokedexTheme.dimens
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
        modifier = Modifier.padding(vertical = dimens.spaceLg),
    ) {
        Box(Modifier.padding(horizontal = dimens.spaceLg)) {
            SearchField(value = "", onValueChange = {})
        }
        Box(Modifier.padding(horizontal = dimens.spaceLg)) {
            SearchField(value = "charmander", onValueChange = {})
        }
        FilterChipRow(
            filters = listOf(
                FilterOption("needed", "Needed", selected = true, count = 982),
                FilterOption("caught", "Caught", selected = false, count = 412),
                FilterOption("locked", "Shiny locked", selected = false, count = 43),
                FilterOption("sv", "Scarlet / Violet", selected = false, count = 700),
                FilterOption("events", "Event only", selected = false, enabled = false),
            ),
            onToggle = {},
        )
        Box(Modifier.padding(horizontal = dimens.spaceLg)) {
            SortControl(
                options = listOf(
                    SortOption("box", "Box order"),
                    SortOption("dex", "Dex no."),
                    SortOption("name", "Name"),
                    SortOption("odds", "Odds"),
                ),
                selectedId = "box",
                onSelect = {},
            )
        }
    }
}

@Preview(name = "Filters dark", widthDp = 380)
@Composable
private fun FiltersDarkPreview() {
    PokedexTheme(darkTheme = true) { FilterSamples() }
}

@Preview(name = "Filters light", widthDp = 380)
@Composable
private fun FiltersLightPreview() {
    PokedexTheme(darkTheme = false) { FilterSamples() }
}

@Preview(name = "Filters 200% font", widthDp = 380, fontScale = 2f)
@Composable
private fun FiltersLargeFontPreview() {
    PokedexTheme(darkTheme = true) { FilterSamples() }
}
