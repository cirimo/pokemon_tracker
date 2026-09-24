package net.pokedex.feature.dex.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import net.pokedex.designsystem.component.ActionButton
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.NoteField
import net.pokedex.designsystem.component.PokedexBottomSheet
import net.pokedex.designsystem.component.PokedexDatePickerDialog
import net.pokedex.designsystem.component.PokedexDialog
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.theme.PokedexTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Where, when and how a catch happened, under the toggle it belongs to.
 *
 * Never modal at the moment of catching: the toggle marks the slot in one tap and this row
 * appears with the prefilled game, so a run of catches from one game costs one tap each and
 * a wrong prefill is on screen to be noticed. Everything is edited in one sheet, later or
 * never. Unticked, the row stays, dimmed into "previously recorded", because the details
 * were not deleted and re-ticking brings them back.
 */
@Composable
internal fun CatchDetails(
    caught: Boolean,
    record: RecordUi?,
    origins: List<OriginUi>,
    onSave: (originGameId: String?, caughtAt: Long?, notes: String?) -> Unit,
    onForget: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    when {
        caught -> SettingRow(
            title = record?.originName?.let { "Caught in $it" } ?: "Where was it caught?",
            summary = detailsSummary(record) ?: "Add the game, the date and a note",
            onClick = { editing = true },
        )
        record?.hasDetails == true -> Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
            SettingRow(
                title = "Previously recorded",
                summary = detailsSummary(record, withGame = true) + ". Kept, and restored if you mark it caught again.",
                onClick = { editing = true },
            )
            ActionButton(
                label = "Forget these details",
                onClick = { confirmForget = true },
                primary = false,
                destructive = true,
            )
        }
    }

    if (editing) {
        CatchSheet(
            record = record,
            origins = origins,
            onDismiss = { editing = false },
            onSave = { origin, at, notes ->
                editing = false
                onSave(origin, at, notes)
            },
        )
    }
    if (confirmForget) {
        PokedexDialog(
            onDismissRequest = { confirmForget = false },
            title = "Forget these details?",
            body = "The game, date and notes for this slot are deleted. " +
                "Automatic backups made before now still hold them.",
            confirmLabel = "Forget",
            onConfirm = {
                confirmForget = false
                onForget()
            },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatchSheet(
    record: RecordUi?,
    origins: List<OriginUi>,
    onDismiss: () -> Unit,
    onSave: (originGameId: String?, caughtAt: Long?, notes: String?) -> Unit,
) {
    val dimens = PokedexTheme.dimens
    var origin by rememberSaveable { mutableStateOf(record?.originGameId) }
    var caughtAt by rememberSaveable { mutableStateOf(record?.caughtAt) }
    var notes by rememberSaveable { mutableStateOf(record?.notes.orEmpty()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    PokedexBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Save sits beside the title, not under the note: the keyboard that comes up for the
        // note covers the bottom of the sheet, and a Save you cannot reach is no Save.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            Text(
                "Catch details",
                style = PokedexTheme.text.boxTitle,
                color = PokedexTheme.colors.onCase,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            ActionButton(label = "Cancel", onClick = onDismiss, primary = false)
            ActionButton(label = "Save", onClick = { onSave(origin, caughtAt, notes) })
        }
        Column(
            // imePadding: without it the keyboard covers Save while the note is being typed.
            modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            val (shiny, other) = origins.partition { it.shinyHere }
            OriginChips(
                title = "Shiny here",
                games = shiny,
                selected = origin,
                onSelect = { origin = it },
                // "Not recorded" leads, so leaving it blank is as easy as choosing.
                leading = {
                    FilterChip(label = "Not recorded", selected = origin == null, onSelectedChange = { origin = null })
                },
            )
            if (other.isNotEmpty()) {
                OriginChips(title = "Other games", games = other, selected = origin, onSelect = { origin = it })
            }
            SettingRow(
                title = "Caught on",
                summary = caughtAt?.let(::formatDay) ?: "Not recorded",
                onClick = { pickingDate = true },
            )
            NoteField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                placeholder = "Method, resets, anything",
            )
        }
    }

    if (pickingDate) {
        PokedexDatePickerDialog(
            initialUtcMillis = caughtAt?.let(::toPickerMillis),
            todayUtcMillis = toPickerMillis(System.currentTimeMillis()),
            onPick = { picked ->
                caughtAt = fromPickerMillis(picked, keep = caughtAt)
                pickingDate = false
            },
            onDismissRequest = { pickingDate = false },
        )
    }
}

/**
 * One group of games. Two groups rather than one sorted list, because an order is not a
 * signal anyone reads: "Shiny here" says in words which games could have produced this one.
 */
@Composable
private fun OriginChips(
    title: String,
    games: List<OriginUi>,
    selected: String?,
    onSelect: (String) -> Unit,
    leading: @Composable () -> Unit = {},
) {
    val dimens = PokedexTheme.dimens
    Text(title, style = MaterialTheme.typography.titleSmall, color = PokedexTheme.colors.onCase)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        leading()
        games.forEach { game ->
            FilterChip(label = game.name, selected = selected == game.id, onSelectedChange = { onSelect(game.id) })
        }
    }
}

/** Date and notes; the game too when the row's title does not already name it. */
private fun detailsSummary(record: RecordUi?, withGame: Boolean = false): String? {
    record ?: return null
    val parts = listOfNotNull(
        record.originName.takeIf { withGame },
        record.caughtAt?.let(::formatDay),
        record.notes,
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun localDay(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatDay(epochMillis: Long): String = DAY.format(localDay(epochMillis))

/** M3's picker speaks UTC midnight of the day as it appears on this phone. */
private fun toPickerMillis(epochMillis: Long): Long =
    localDay(epochMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * The picked day as an instant. The same day as before keeps its original time; a different
 * day gets local noon, which cannot slide to a neighbouring date in any timezone the phone
 * is likely to travel to.
 */
private fun fromPickerMillis(utcMillis: Long, keep: Long?): Long {
    val day = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    if (keep != null && localDay(keep) == day) return keep
    return day.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
