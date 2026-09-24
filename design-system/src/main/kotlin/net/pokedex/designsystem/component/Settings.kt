package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * Settings rows, notices and plain action buttons.
 *
 * None of these uses gold. A setting is a choice and a backup succeeding is housekeeping.
 * Neither is a shiny, and gold spent on them would make the progress numerals mean less.
 */

/**
 * A setting that opens something or does something: a title and what it is set to now.
 *
 * The summary carries the state ("Documents/Pokedex", "Last backup 5 minutes ago") so the
 * screen can be read without opening anything. TalkBack reads title then summary, as one
 * node.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .clip(PokedexShapes.field)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = dimens.spaceSm),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) colors.onCase else colors.onCaseMuted,
        )
        if (summary != null) {
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = colors.onCaseMuted)
        }
    }
}

/**
 * An on/off setting. The whole row toggles, not just the thumb.
 *
 * The switch is recoloured away from M3's primary, which this theme maps to gold. On is
 * carried by the thumb's position and a tick inside it, not by colour alone.
 */
@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .clip(PokedexShapes.field)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.onCase)
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = colors.onCaseMuted)
            }
        }
        Switch(
            checked = checked,
            // The row owns the toggle, so the switch is a picture of the state, not a second target.
            onCheckedChange = null,
            thumbContent = if (checked) {
                { Icon(PokedexIcons.Check, contentDescription = null, modifier = Modifier.size(dimens.iconSm)) }
            } else {
                null
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.case,
                checkedIconColor = colors.onCase,
                checkedTrackColor = colors.onCase,
                checkedBorderColor = colors.onCase,
                uncheckedThumbColor = colors.onCaseMuted,
                uncheckedTrackColor = colors.caseSurfaceHigh,
                uncheckedBorderColor = colors.onCaseMuted,
            ),
        )
    }
}

/**
 * Something the user should know about a setting's consequences: "backups stay inside the
 * app, so an uninstall deletes them".
 *
 * [warning] swaps the info glyph for the warning one and colours it with the error role.
 * The glyph changes shape as well as colour, so the difference is not carried by colour alone.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PokedexShapes.card)
            .background(colors.caseSurfaceHigh)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.card)
            .padding(dimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Icon(
            imageVector = if (warning) PokedexIcons.Error else PokedexIcons.Info,
            contentDescription = null,
            tint = if (warning) colors.errorGraphic else colors.onCaseMuted,
            modifier = Modifier.size(dimens.iconLg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.onCase)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.onCaseMuted)
            if (actionLabel != null && onAction != null) {
                ActionButton(label = actionLabel, onClick = onAction, primary = false)
            }
        }
    }
}

/**
 * A button that is not gold.
 *
 * M3's buttons draw in `colorScheme.primary`, which this theme maps to gold, because gold is
 * what M3 components *should* pick up for progress. An action ("Back up now", "Merge",
 * "Show in box") is not progress. [primary] is the one high-emphasis action on a screen:
 * filled, in the text colour. The rest are outlined in the muted text colour. Not the rim:
 * that is 2.99:1 against the case in dark, too faint to find a button by.
 */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val container = if (primary && enabled) colors.onCase else colors.case
    val content = when {
        !enabled -> colors.onCaseMuted
        primary -> colors.case
        destructive -> colors.errorText
        else -> colors.onCase
    }
    Row(
        modifier = modifier
            .heightIn(min = dimens.touchTargetMin)
            .clip(PokedexShapes.field)
            .background(container)
            .border(dimens.rimWidth, if (primary && enabled) container else colors.onCaseMuted, PokedexShapes.field)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceLg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = PokedexTheme.text.badgeLabel, color = content)
    }
}

@Composable
internal fun SettingsSamples() {
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
        SettingRow(title = "Backup folder", summary = "Documents/Pokedex", onClick = {})
        SettingSwitch(
            title = "Automatic backups",
            summary = "After changes, and daily",
            checked = true,
            onCheckedChange = {},
        )
        SettingSwitch(title = "Automatic backups", summary = "Off", checked = false, onCheckedChange = {})
        NoticeCard(
            title = "Backups stay inside the app",
            body = "Uninstalling deletes them with everything else. Pick a folder to keep them safe.",
            warning = true,
            actionLabel = "Pick a folder",
            onAction = {},
        )
        NoticeCard(title = "Backed up", body = "412 caught, 5 minutes ago, to Documents/Pokedex.")
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            ActionButton(label = "Merge", onClick = {})
            ActionButton(label = "Replace", onClick = {}, primary = false, destructive = true)
            ActionButton(label = "Later", onClick = {}, primary = false)
        }
    }
}

@Preview(name = "Settings dark", widthDp = 380)
@Composable
private fun SettingsDarkPreview() {
    PokedexTheme(darkTheme = true) { SettingsSamples() }
}

@Preview(name = "Settings light", widthDp = 380)
@Composable
private fun SettingsLightPreview() {
    PokedexTheme(darkTheme = false) { SettingsSamples() }
}

@Preview(name = "Settings 200%", widthDp = 380, fontScale = 2f)
@Composable
private fun SettingsLargeFontPreview() {
    PokedexTheme(darkTheme = true) { SettingsSamples() }
}
